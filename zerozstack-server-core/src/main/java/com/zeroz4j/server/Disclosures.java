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
 * <p>Framework-internal state, with one public question: {@link #wasDisclosedTo(Session, String)}.</p>
 */
public final class Disclosures {

    private static final Logger LOG = Logger.getLogger(Disclosures.class.getName());

    /** Largest number of handles remembered for one browser. */
    static final int DEFAULT_MAX_PER_CLIENT = 10_000;
    /** How long a browser's record survives with no activity. */
    static final int DEFAULT_IDLE_HOURS = 24;

    static final String MAX_PER_CLIENT_PROPERTY = "zeroz.disclosure.maxHandlesPerClient";
    static final String IDLE_HOURS_PROPERTY = "zeroz.disclosure.idleHours";

    /** WebSocket session id -> the ledger key its writes are recorded under. */
    private static final Map<String, String> KEY_BY_SESSION = new ConcurrentHashMap<>();

    /** Ledger key (browser id, or a session-id fallback) -> what was sent there. */
    private static final Map<String, Record> LEDGER = new ConcurrentHashMap<>();

    private static final AtomicBoolean FALLBACK_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    /** When the ledger was last swept for expired browsers. */
    private static final java.util.concurrent.atomic.AtomicLong LAST_PRUNE =
            new java.util.concurrent.atomic.AtomicLong();

    /** Shortest gap between two sweeps. Recording happens per object written, sweeping need not. */
    private static final long PRUNE_INTERVAL_MILLIS = 60_000L;

    private Disclosures() {
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
     * Starts recording. Idempotent, and safe to call from anywhere that might run first.
     *
     * <p>Recording rides on the bracket the engine already puts around every write to a session
     * ({@link LazyHandles#setCurrentSession(String)}), so no write site has to know this class
     * exists.</p>
     */
    public static void install() {
        if (INSTALLED.compareAndSet(false, true)) {
            ObjectMapper.setDisclosureRecorder(Disclosures::recordForCurrentWrite);
        }
    }

    /**
     * Notes which browser a session belongs to, so its writes land in the right record.
     *
     * @param session a connection that has just opened
     */
    public static void sessionOpened(Session session) {
        if (session == null) {
            return;
        }
        install();
        KEY_BY_SESSION.put(session.getId(), keyFor(session));
    }

    /**
     * Forgets the session-to-browser mapping. The browser's record is deliberately kept: the whole
     * point of keying by browser is that the next connection can still re-read what this one was
     * sent.
     *
     * @param sessionId the connection that closed
     */
    public static void sessionClosed(String sessionId) {
        if (sessionId != null) {
            KEY_BY_SESSION.remove(sessionId);
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
     * @param session  the connection presenting the handle
     * @param handleId the handle it presented
     * @return true when this browser was sent that object and the record still holds it
     */
    public static boolean wasDisclosedTo(Session session, String handleId) {
        if (session == null || handleId == null) {
            return false;
        }
        pruneExpired();
        Record record = LEDGER.get(keyFor(session));
        return record != null && record.contains(handleId);
    }

    /**
     * Records a handle as sent to a session. Called by the recorder; exposed for tests and for
     * bindings that write outside the engine's own brackets.
     *
     * @param sessionId the recipient connection
     * @param handleId  the handle written toward it
     */
    public static void record(String sessionId, String handleId) {
        if (sessionId == null || handleId == null) {
            return;
        }
        pruneExpired();
        String key = KEY_BY_SESSION.get(sessionId);
        if (key == null) {
            // A write for a session that never came through onOpen: record under the session id, so
            // the disclosure is at least remembered for this connection's lifetime.
            key = sessionId;
        }
        LEDGER.computeIfAbsent(key, k -> new Record(maxPerClient())).add(handleId);
    }

    /**
     * @param session a connection
     * @return how many handles that connection's browser is currently remembered as holding
     */
    public static int disclosedCount(Session session) {
        Record record = session == null ? null : LEDGER.get(keyFor(session));
        return record == null ? 0 : record.size();
    }

    /** Clears every record. Test support only. */
    public static void resetForTesting() {
        KEY_BY_SESSION.clear();
        LEDGER.clear();
        FALLBACK_LOGGED.set(false);
        LAST_PRUNE.set(0L);
    }

    // ------------------------------------------------------------------ internals

    private static void recordForCurrentWrite(String handleId) {
        String sessionId = LazyHandles.currentSession();
        if (sessionId == null) {
            // Not a write toward a client: a serializability probe, or a server-side copy. Nothing
            // was disclosed to anybody, so there is nothing to remember.
            return;
        }
        record(sessionId, handleId);
    }

    /**
     * The browser id this session carries, or its session id when it carries none.
     */
    private static String keyFor(Session session) {
        Object clientId = session.getUserProperties().get(RmiEndpointConfigurator.CLIENT_KEY);
        if (clientId instanceof String && !((String) clientId).isEmpty()) {
            return (String) clientId;
        }
        if (FALLBACK_LOGGED.compareAndSet(false, true)) {
            LOG.info("[zeroz4j] A connection carries no browser id, so what was sent to it is "
                    + "remembered per connection instead. Such a client re-fetches its objects after "
                    + "a reconnect rather than re-syncing them. Browsers always carry one; this "
                    + "means a non-browser client with no cookie.");
        }
        return session.getId();
    }

    /**
     * Drops browsers nothing has been written to or read from for a while.
     *
     * <p>Called from the recording path, which runs once per object written, so it sweeps at most
     * once a minute rather than walking the ledger on every object of every frame.</p>
     */
    private static void pruneExpired() {
        if (LEDGER.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        long last = LAST_PRUNE.get();
        if (now - last < PRUNE_INTERVAL_MILLIS || !LAST_PRUNE.compareAndSet(last, now)) {
            return;
        }
        long cutoff = now - idleMillis();
        LEDGER.entrySet().removeIf(entry -> entry.getValue().lastTouchedMillis < cutoff);
    }

    private static int maxPerClient() {
        return positiveIntProperty(MAX_PER_CLIENT_PROPERTY, DEFAULT_MAX_PER_CLIENT);
    }

    private static long idleMillis() {
        return positiveIntProperty(IDLE_HOURS_PROPERTY, DEFAULT_IDLE_HOURS) * 60L * 60L * 1000L;
    }

    private static int positiveIntProperty(String name, int fallback) {
        String configured = System.getProperty(name);
        if (configured == null || configured.trim().isEmpty()) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(configured.trim());
            return value > 0 ? value : fallback;
        } catch (NumberFormatException ex) {
            LOG.warning("[zeroz4j] Ignoring non-numeric " + name + "='" + configured + "'.");
            return fallback;
        }
    }
}
