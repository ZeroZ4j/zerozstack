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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session-scoped registry of lazy-reference handles.
 *
 * <p>A handle is a capability: presenting it asks the server to load and return a subgraph. It is
 * therefore bound to <b>the session it was disclosed to</b>, and a session can only resolve handles
 * it was itself sent. Without that binding a handle would be a way to read data the caller was never
 * permitted to see, which is a data-exfiltration primitive rather than a performance feature.</p>
 *
 * <p>Handles are released when the session closes, so they do not accumulate for the process
 * lifetime the way {@code ObjectMapper} entries do.</p>
 *
 * <p>Framework-internal; applications do not use this.</p>
 */
public final class LazyHandles {

    /** sessionId -> (handle -> the lazy reference it stands for). */
    private static final Map<String, Map<String, Object>> BY_SESSION = new ConcurrentHashMap<>();

    /**
     * The session a frame is currently being serialized for. Set by the engine around every write,
     * so that {@link #register} knows who is being told about the handle.
     */
    private static final ThreadLocal<String> CURRENT_SESSION = new ThreadLocal<>();

    private LazyHandles() {
    }

    /**
     * Marks the session a frame is being written for.
     *
     * @param sessionId the target session, or null to clear
     */
    public static void setCurrentSession(String sessionId) {
        if (sessionId == null) {
            CURRENT_SESSION.remove();
        } else {
            CURRENT_SESSION.set(sessionId);
        }
    }

    /**
     * @return the session currently being written to, or null outside a frame write
     */
    public static String currentSession() {
        return CURRENT_SESSION.get();
    }

    /**
     * Registers a lazy reference as disclosed to the current session and returns its handle.
     *
     * <p>Re-registering the same reference for the same session returns the existing handle, so a
     * repeatedly synced object does not grow the registry without bound.</p>
     *
     * @param lazy the lazy reference being written
     * @return the handle to put on the wire
     * @throws IllegalStateException when called outside a frame write, since a handle with no owning
     *                               session could be resolved by anyone
     */
    public static String register(Object lazy) {
        String sessionId = CURRENT_SESSION.get();
        if (sessionId == null) {
            throw new IllegalStateException(
                    "Cannot serialize a lazy reference outside a session-scoped frame write: the "
                    + "handle would not be bound to any session and could be resolved by any caller.");
        }
        Map<String, Object> handles = BY_SESSION.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>());
        for (Map.Entry<String, Object> entry : handles.entrySet()) {
            if (entry.getValue() == lazy) {
                return entry.getKey();
            }
        }
        String handle = UUID.randomUUID().toString();
        handles.put(handle, lazy);
        return handle;
    }

    /**
     * Looks up a handle previously disclosed to the given session.
     *
     * @param handle    the handle presented by the client
     * @param sessionId the session presenting it
     * @return the lazy reference, or null when this session was never given that handle
     */
    public static Object resolve(String handle, String sessionId) {
        if (handle == null || sessionId == null) {
            return null;
        }
        Map<String, Object> handles = BY_SESSION.get(sessionId);
        return handles == null ? null : handles.get(handle);
    }

    /**
     * Releases every handle held for a closed session.
     *
     * @param sessionId the session that closed
     */
    public static void sessionClosed(String sessionId) {
        if (sessionId != null) {
            BY_SESSION.remove(sessionId);
        }
    }

    /**
     * @param sessionId a session id
     * @return how many handles that session currently holds; for tests and diagnostics
     */
    public static int handleCount(String sessionId) {
        Map<String, Object> handles = BY_SESSION.get(sessionId);
        return handles == null ? 0 : handles.size();
    }

    /** Clears all state. Test support only. */
    public static void resetForTesting() {
        BY_SESSION.clear();
        CURRENT_SESSION.remove();
    }
}
