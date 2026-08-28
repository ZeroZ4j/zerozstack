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

import jakarta.websocket.Session;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of lazy-reference handles, and the bracket every outgoing frame is written inside.
 *
 * <p>A handle is a capability: presenting it asks the server to load and return a subgraph. It is
 * therefore bound to <b>the connection it was disclosed to</b>, and a connection can only resolve
 * handles it was itself sent. Without that binding a handle would be a way to read data the caller
 * was never permitted to see, which is a data-exfiltration primitive rather than a performance
 * feature.</p>
 *
 * <h2>The write bracket (0.8.0)</h2>
 *
 * <p>Serialization is deep inside the wire format and cannot be handed a server. So before writing
 * a frame the engine opens a <b>write bracket</b> naming the server and the connection being
 * written to, and closes it afterwards. Anything reached during that write — a lazy reference
 * registering itself, a handle being recorded as disclosed — finds both from the bracket.</p>
 *
 * <pre>{@code
 * try (var write = LazyHandles.writingTo(runtime, session)) {
 *     BinarySerializer.writeValue(buffer, object, mapper);
 * }
 * }</pre>
 *
 * <p>Before 0.8.0 the bracket named only the connection and the registry was a {@code static} map,
 * so two servers in one process shared one registry and a handle issued by one could be resolved on
 * the other. The bracket now carries the server too, and the map belongs to it.</p>
 *
 * <p>Handles are released when the connection closes, so they do not accumulate for the process
 * lifetime the way {@code ObjectMapper} entries do.</p>
 *
 * <p>Framework-internal; applications do not use this.</p>
 */
public final class LazyHandles {

    /** The server and connection a frame is being written to on this thread. */
    private static final ThreadLocal<Write> CURRENT = new ThreadLocal<>();

    private LazyHandles() {
    }

    /**
     * One server's lazy-reference handles. Owned by {@link ServerRuntime}.
     */
    static final class Registry {

        /** sessionId -> (handle -> the lazy reference it stands for). */
        private final Map<String, Map<String, Object>> bySession = new ConcurrentHashMap<>();

        String register(String sessionId, Object lazy) {
            Map<String, Object> handles =
                    bySession.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>());
            for (Map.Entry<String, Object> entry : handles.entrySet()) {
                if (entry.getValue() == lazy) {
                    return entry.getKey();
                }
            }
            String handle = UUID.randomUUID().toString();
            handles.put(handle, lazy);
            return handle;
        }

        Object resolve(String handle, String sessionId) {
            if (handle == null || sessionId == null) {
                return null;
            }
            Map<String, Object> handles = bySession.get(sessionId);
            return handles == null ? null : handles.get(handle);
        }

        void sessionClosed(String sessionId) {
            if (sessionId != null) {
                bySession.remove(sessionId);
            }
        }

        int handleCount(String sessionId) {
            Map<String, Object> handles = bySession.get(sessionId);
            return handles == null ? 0 : handles.size();
        }

        void clear() {
            bySession.clear();
        }
    }

    /**
     * The server and connection a frame is being written to.
     *
     * <p>Each bracket remembers the one it replaced, so a write started inside another write
     * restores the outer one on the way out rather than clearing the thread.</p>
     */
    public static final class Write implements AutoCloseable {

        private final ServerRuntime runtime;
        private final String sessionId;
        private final Write outer;

        private Write(ServerRuntime runtime, String sessionId, Write outer) {
            this.runtime = runtime;
            this.sessionId = sessionId;
            this.outer = outer;
        }

        /** @return the server doing the writing */
        public ServerRuntime runtime() {
            return runtime;
        }

        /** @return the connection being written to */
        public String sessionId() {
            return sessionId;
        }

        /** Ends the bracket and restores whatever was in force before it. */
        @Override
        public void close() {
            if (outer == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(outer);
            }
        }
    }

    /**
     * Opens a write bracket for a connection.
     *
     * @param runtime the server writing
     * @param session the connection being written to
     * @return the bracket; close it in a try-with-resources
     */
    public static Write writingTo(ServerRuntime runtime, Session session) {
        return writingTo(runtime, session == null ? null : session.getId());
    }

    /**
     * Opens a write bracket for a connection id.
     *
     * @param runtime   the server writing
     * @param sessionId the connection being written to
     * @return the bracket; close it in a try-with-resources
     */
    public static Write writingTo(ServerRuntime runtime, String sessionId) {
        if (runtime == null) {
            throw new IllegalStateException(
                    "Cannot write a frame without knowing which server is writing it. This is a "
                    + "framework fault; please report it.");
        }
        Write write = new Write(runtime, sessionId, CURRENT.get());
        CURRENT.set(write);
        return write;
    }

    /**
     * @return the server and connection being written to on this thread, or null outside a write
     */
    static Write currentWrite() {
        return CURRENT.get();
    }

    /**
     * @return the connection currently being written to, or null outside a frame write
     */
    public static String currentSession() {
        Write write = CURRENT.get();
        return write == null ? null : write.sessionId();
    }

    /**
     * Looks up a handle previously given to a connection, on the server currently writing.
     *
     * <p>Kept as a static because it is called from the persistence module's lazy adapter, which is
     * reached from deep inside serialization and has no server to hand. Outside a frame write there
     * is no server and no connection, so the answer is "not found" — the same answer as before
     * 0.8.0, when the connection alone was tracked.</p>
     *
     * @param handle    the handle presented
     * @param sessionId the connection presenting it
     * @return the lazy reference, or null when that connection was never given that handle
     */
    public static Object resolve(String handle, String sessionId) {
        Write write = CURRENT.get();
        if (write == null || handle == null || sessionId == null) {
            return null;
        }
        return write.runtime().lazyHandles().resolve(handle, sessionId);
    }

    /**
     * Registers a lazy reference as disclosed to the connection currently being written to, and
     * returns its handle.
     *
     * <p>Re-registering the same reference for the same connection returns the existing handle, so a
     * repeatedly synced object does not grow the registry without bound.</p>
     *
     * @param lazy the lazy reference being written
     * @return the handle to put on the wire
     * @throws IllegalStateException when called outside a frame write, since a handle with no owning
     *                               connection could be resolved by anyone
     */
    public static String register(Object lazy) {
        Write write = CURRENT.get();
        if (write == null || write.sessionId() == null) {
            throw new IllegalStateException(
                    "Cannot serialize a lazy reference outside a connection-scoped frame write: the "
                    + "handle would not be bound to any connection and could be resolved by any caller.");
        }
        return write.runtime().lazyHandles().register(write.sessionId(), lazy);
    }
}
