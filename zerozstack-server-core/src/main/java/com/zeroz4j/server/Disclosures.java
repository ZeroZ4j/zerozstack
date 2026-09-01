/*
 * Copyright 2026 Franz Schöning
 * Project: https://www.zeroz4j.com
 * Author: Franz Schöning - Principal Enterprise Architect (https://www.franzschoning.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.zeroz4j.server;

import com.zeroz4j.api.ObjectMapper;
import jakarta.websocket.Session;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Remembers which object handles were actually sent to which browser.
 *
 * <p>A handle is the name an object travels under. Before this existed, presenting any handle was
 * enough to be given the object behind it, on the theory that a handle can only be learned by being
 * sent the object. That is not true: an object embedded in a broadcast event or a shared signal is
 * serialized with its handle, so everyone who received the outer payload learned the handles of
 * everything inside it, and could ask for those objects later — including after their access had
 * been taken away.</p>
 *
 * <p>So the server writes down what it sent. Every handle written toward a recipient is recorded
 * against that recipient, and a request to re-read an object is answered only when the record says
 * the object was sent there.</p>
 *
 * <h2>One record per server (0.8.0)</h2>
 *
 * <p>The record used to live in {@code static} fields, which meant two servers in one process shared
 * one record: an object server A sent could be read back from server B. Each server now owns its
 * own, reached through {@link ServerRuntime#disclosures()}. The public question below still takes
 * only a connection, because a connection knows which server it belongs to.</p>
 *
 * <h2>Keyed by browser, not by connection</h2>
 *
 * <p>The record is kept under the connection's <b>browser id</b> — the signed identifier the server
 * mints at the handshake and keeps in a cookie. Keying by WebSocket session id would defeat the
 * feature this protects: a reconnect is a brand-new session, so the record would always be empty and
 * re-sync would restore nothing. A connection carrying no browser id at all (a non-browser client
 * with no cookie) falls back to its session id, which means it re-fetches after a reconnect instead
 * of re-syncing. That is acceptable and is logged once.</p>
 *
 * <h2>Bounded</h2>
 *
 * <p>Two limits keep the record from growing for the life of the process:</p>
 *
 * <ul>
 *   <li>at most {@value #DEFAULT_MAX_PER_CLIENT} handles per browser, oldest dropped first
 *       ({@code zeroz.disclosure.maxHandlesPerClient});</li>
 *   <li>a browser's whole record is dropped after {@value #DEFAULT_IDLE_HOURS} hours with nothing
 *       written to it or read from it ({@code zeroz.disclosure.idleHours}).</li>
 * </ul>
 *
 * <p>Dropping a record is safe in the same way a server restart is: the client is told nothing was
 * found and re-fetches the objects the way it first obtained them.</p>
 *
 * <p>Framework-internal state, with one public question:
 * {@link #wasDisclosedTo(Session, String)}.</p>
 */
public final class Disclosures {

    private static final Logger LOG = Logger.getLogger(Disclosures.class.getName());

    /** Largest number of handles remembered for one browser. */
    static final int DEFAULT_MAX_PER_CLIENT = 10_000;
    /** How long a browser's record survives with no activity. */
    static final int DEFAULT_IDLE_HOURS = 24;

    static final String MAX_PER_CLIENT_PROPERTY = ServerSettings.DISCLOSURE_MAX_HANDLES_PER_CLIENT;
    static final String IDLE_HOURS_PROPERTY = ServerSettings.DISCLOSURE_IDLE_HOURS;

    /**
     * The one hook the serializer offers is a single slot for the whole process, so the recorder
     * installed in it must not belong to any one server. This one belongs to none: it looks up the
     * server that is being written to right now and records against that server's own ledger.
     */
    private static final AtomicBoolean RECORDER_INSTALLED = new AtomicBoolean();

    private final ServerRuntime runtime;

    /** WebSocket session id -> the ledger key its writes are recorded under. */
    private final Map<String, String> keyBySession = new ConcurrentHashMap<>();

    /** Ledger key (browser id, or a session-id fallback) -> what was sent there. */
    private final Map<String, Record> ledger = new ConcurrentHashMap<>();

    private final AtomicBoolean fallbackLogged = new AtomicBoolean();

    /** When the ledger was last swept for expired browsers. */
    private final java.util.concurrent.atomic.AtomicLong lastPrune =
            new java.util.concurrent.atomic.AtomicLong();

    /** Shortest gap between two sweeps. Recording happens per object written, sweeping need not. */
    private static final long PRUNE_INTERVAL_MILLIS = 60_000L;

    Disclosures(ServerRuntime runtime) {
        this.runtime = runtime;
    }

    /** One browser's record: what it was sent, and when it was last active. */
    private static final class Record {
        private final LinkedHashMap<String, Boolean> handles;
        private volatile long lastTouchedMillis = System.currentTimeMillis();

        Record(final int max) {
            // Access-ordered, so the handles a client keeps using are the ones that survive the cap.
            this.handles = new LinkedHashMap<String, Boolean>(64, 0.75f, true) {
                private static final long serialVersionUID = 1L;
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > max;
                }
            };
        }

        void add(String handleId) {
            lastTouchedMillis = System.currentTimeMillis();
            synchronized (handles) {
                handles.put(handleId, Boolean.TRUE);
            }
        }

        boolean contains(String handleId) {
            lastTouchedMillis = System.currentTimeMillis();
            synchronized (handles) {
                return handles.containsKey(handleId);
            }
        }

        int size() {
            synchronized (handles) {
                return handles.size();
            }
        }
    }

    /**
     * Starts recording, for every server in this process. Idempotent.
     *
     * <p>Recording rides on the bracket the engine already puts around every write to a connection
     * ({@code LazyHandles}), so no write site has to know this class exists — and that bracket names
     * the server as well as the connection, which is how a handle lands in the right server's
     * ledger.</p>
     */
    public static void install() {
        if (RECORDER_INSTALLED.compareAndSet(false, true)) {
            ObjectMapper.setDisclosureRecorder(Disclosures::recordForCurrentWrite);
        }
    }

    /**
     * Notes which browser a connection belongs to, so its writes land in the right record.
     *
     * @param session a connection that has just opened
     */
    public void sessionOpened(Session session) {
        if (session == null) {
            return;
        }
        install();
        keyBySession.put(session.getId(), keyFor(session));
    }

    /**
     * Forgets the connection-to-browser mapping. The browser's record is deliberately kept: the
     * whole point of keying by browser is that the next connection can still re-read what this one
     * was sent.
     *
     * @param sessionId the connection that closed
     */
    public void sessionClosed(String sessionId) {
        if (sessionId != null) {
            keyBySession.remove(sessionId);
        }
    }

    /**
     * Whether this connection's browser was sent the object behind a handle.
     *
     * <p>This is the question to ask before doing anything on a client's behalf with an object it
     * named by handle: re-reading it, or taking a lock on it. A {@code false} answer means the
     * client either never held the object, or held it long enough ago that the record has been
     * dropped; in both cases the correct response is to act as though the handle is unknown, not to
     * report an error.</p>
     *
     * <p>Asked of the server the connection belongs to, so an object one server sent is not readable
     * from another server running beside it.</p>
     *
     * @param session  the connection presenting the handle
     * @param handleId the handle it presented
     * @return true when this browser was sent that object and the record still holds it
     * @throws IllegalStateException when the connection belongs to no running server
     */
    public static boolean wasDisclosedTo(Session session, String handleId) {
        if (session == null || handleId == null) {
            return false;
        }
        return ServerRuntime.of(session).disclosures().wasDisclosedToSession(session, handleId);
    }

    /**
     * The same question, asked of this one server.
     *
     * @param session  the connection presenting the handle
     * @param handleId the handle it presented
     * @return true when this browser was sent that object and the record still holds it
     */
    public boolean wasDisclosedToSession(Session session, String handleId) {
        if (session == null || handleId == null) {
            return false;
        }
        return wasDisclosedTo(
                (String) session.getUserProperties().get(RmiEndpointConfigurator.CLIENT_KEY),
                session.getId(), handleId);
    }

    /**
     * The same question asked from a caller's identity rather than from the socket.
     *
     * <p>An RMI method is handed who is calling, not which connection they are on, so it cannot
     * produce a {@link Session}. It has the two things this record is keyed by, which is all the
     * question consults: the browser id when there is one, the connection id when there is not.</p>
     *
     * @param clientId  the signed browser id, or null when the client carries none
     * @param sessionId the connection id, used as the key when there is no browser id
     * @param handleId  the handle the client presented
     * @return true when this browser was sent that object and the record still holds it
     */
    public boolean wasDisclosedTo(String clientId, String sessionId, String handleId) {
        if (handleId == null) {
            return false;
        }
        String key = keyFor(clientId, sessionId);
        if (key == null) {
            return false;
        }
        pruneExpired();
        Record record = ledger.get(key);
        return record != null && record.contains(handleId);
    }

    /**
     * Records a handle as sent to a connection.
     *
     * @param sessionId the recipient connection
     * @param handleId  the handle written toward it
     */
    public void record(String sessionId, String handleId) {
        if (sessionId == null || handleId == null) {
            return;
        }
        pruneExpired();
        String key = keyBySession.get(sessionId);
        if (key == null) {
            // A write for a connection that never came through onOpen: record under the connection
            // id, so the disclosure is at least remembered for this connection's lifetime.
            key = sessionId;
        }
        final int max = maxPerClient();
        ledger.computeIfAbsent(key, k -> new Record(max)).add(handleId);
    }

    /**
     * @param session a connection
     * @return how many handles that connection's browser is currently remembered as holding
     */
    public int disclosedCount(Session session) {
        Record record = session == null ? null : ledger.get(keyFor(session));
        return record == null ? 0 : record.size();
    }

    /** Empties this server's record. */
    public void clear() {
        keyBySession.clear();
        ledger.clear();
        fallbackLogged.set(false);
        lastPrune.set(0L);
    }

    // ------------------------------------------------------------------ internals

    /**
     * Records a handle against the server currently being written to.
     *
     * <p>The write bracket names both the server and the connection. Outside a write there is
     * neither, which means the handle is not being disclosed to anybody — a serializability probe,
     * or a server-side copy — so there is nothing to remember.</p>
     */
    private static void recordForCurrentWrite(String handleId) {
        LazyHandles.Write write = LazyHandles.currentWrite();
        if (write == null) {
            return;
        }
        write.runtime().disclosures().record(write.sessionId(), handleId);
    }

    /**
     * The browser id this connection carries, or its connection id when it carries none.
     */
    private String keyFor(Session session) {
        return keyFor((String) session.getUserProperties().get(RmiEndpointConfigurator.CLIENT_KEY),
                session.getId());
    }

    /** The ledger key for an identity: the browser id when there is one, else the connection id. */
    private String keyFor(String clientId, String sessionId) {
        if (clientId != null && !clientId.isEmpty()) {
            return clientId;
        }
        if (fallbackLogged.compareAndSet(false, true)) {
            LOG.info("[zeroz4j] A connection carries no browser id, so what was sent to it is "
                    + "remembered per connection instead. Such a client re-fetches its objects after "
                    + "a reconnect rather than re-syncing them. Browsers always carry one; this "
                    + "means a non-browser client with no cookie.");
        }
        return sessionId;
    }

    /**
     * Drops browsers nothing has been written to or read from for a while.
     *
     * <p>Called from the recording path, which runs once per object written, so it sweeps at most
     * once a minute rather than walking the ledger on every object of every frame.</p>
     */
    private void pruneExpired() {
        if (ledger.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        long last = lastPrune.get();
        if (now - last < PRUNE_INTERVAL_MILLIS || !lastPrune.compareAndSet(last, now)) {
            return;
        }
        long cutoff = now - idleMillis();
        ledger.entrySet().removeIf(entry -> entry.getValue().lastTouchedMillis < cutoff);
    }

    private int maxPerClient() {
        return config().positiveInt(MAX_PER_CLIENT_PROPERTY, DEFAULT_MAX_PER_CLIENT);
    }

    private long idleMillis() {
        return config().positiveInt(IDLE_HOURS_PROPERTY, DEFAULT_IDLE_HOURS) * 60L * 60L * 1000L;
    }

    private ServerConfig config() {
        return runtime != null ? runtime.config() : ServerConfig.fromSystemProperties();
    }
}
