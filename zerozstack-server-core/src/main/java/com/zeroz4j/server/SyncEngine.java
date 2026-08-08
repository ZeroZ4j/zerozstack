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

import com.zeroz4j.api.BinarySerializer;
import com.zeroz4j.api.GrowableBuffer;
import com.zeroz4j.api.ObjectMapper;
import com.zeroz4j.api.Scope;
import com.zeroz4j.api.SyncFrameTypes;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.logging.Logger;
import java.util.logging.Level;
import jakarta.inject.Inject;
import jakarta.websocket.Session;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application-scoped CDI singleton managing real-time LiveSync broadcasts for tracked domain objects.
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>LiveSync Propagation:</b> When backend code modifies an object annotated with {@code @LiveSync},
 *       calling {@link #notifyChanged(Object)} serializes the object state and pushes a 0x10 SUBSCRIBE/snapshot update
 *       frame across active client WebSocket sessions.</li>
 *   <li><b>Scope Filtering:</b> Supports {@code GLOBAL} (all connected clients), {@code SESSION} (specific session ID),
 *       or {@code USER} (specific authenticated username).</li>
 * </ul>
 */
@ApplicationScoped
public class SyncEngine {

    private static final Logger LOG = Logger.getLogger(SyncEngine.class.getName());

    @Inject
    ObjectMapper mapper;

    /** All active WebSocket sessions. */
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    /**
     * Registers an active WebSocket session with the LiveSync engine.
     *
     * @param wsSession the opening WebSocket session
     *
     * <p><b>Under the hood:</b> Inserts {@code (wsSession.getId(), wsSession)} into {@code sessions} concurrent map.</p>
     */
    public void addSession(Session wsSession) {
        sessions.put(wsSession.getId(), wsSession);
        LOG.info("[zeroz4j-Sync] Session added: " + wsSession.getId());
    }

    /**
     * Unregisters a WebSocket session from the LiveSync engine upon disconnect.
     *
     * @param sessionId the closing session ID
     *
     * <p><b>Under the hood:</b> Removes {@code sessionId} key from {@code sessions} map.</p>
     */
    public void removeSession(String sessionId) {
        if (sessions.remove(sessionId) != null) {
            LOG.info("[zeroz4j-Sync] Session removed: " + sessionId);
        }
    }

    /**
     * Broadcasts a LiveSync update for a modified object globally to all connected clients.
     *
     * @param obj the modified domain model instance
     *
     * <p><b>Under the hood:</b> Delegates to {@link #notifyChanged(Object, Scope, String)} passing {@code Scope.GLOBAL} and {@code null}.</p>
     */
    public void notifyChanged(Object obj) {
        notifyChanged(obj, Scope.GLOBAL, null);
    }

    /**
     * Broadcasts a LiveSync update for a modified object to clients matching the target scope and filter identifier.
     *
     * @param obj    the modified domain model instance
     * @param scope  how far the update reaches
     * @param target the session id, client id, user name or tenant id the scope filters on; ignored
     *               when the scope is {@link Scope#GLOBAL}
     *
     * <p><b>Under the hood:</b> Looks up object ID in {@link ObjectMapper#getId(Object)}. Iterates through {@code sessions}.
     * Applies scope filtering. Construct binary SUBSCRIBE frame (0x10) containing serialized object payload, and transmits via {@link WsWrites#send}.</p>
     */
    public void notifyChanged(Object obj, Scope scope, String target) {
        if (obj == null) {
            throw new IllegalArgumentException("notifyChanged(null): nothing to synchronize");
        }
        String id = mapper.getId(obj);
        if (id == null) {
            // Previously this returned silently, which made the most common LiveSync mistake
            // indistinguishable from a working sync.
            throw new IllegalStateException(
                    "Cannot sync " + obj.getClass().getName() + ": it has never been serialized to a "
                    + "client, so no client holds a reference to update. Return it from an @RmiService "
                    + "method at least once first -- that is what registers its handle.");
        }

        for (Session session : sessions.values()) {
            // One filter for every push mechanism: events and LiveSync answered "who receives this"
            // with two separate copies of the same rules, so a new scope had to be added twice and
            // the two could drift.
            if (!WasmRmiServerEngine.matchesScope(session, scope, target)) {
                continue;
            }

            try {
                GrowableBuffer buffer = new GrowableBuffer();
                buffer.putInt(0); // reqId (0 for broadcast)
                buffer.put(SyncFrameTypes.SUBSCRIBE); // MSG_SYNC_UPDATE
                LazyHandles.setCurrentSession(session.getId());
                try {
                    BinarySerializer.writeValue(buffer, obj, mapper);
                } finally {
                    LazyHandles.setCurrentSession(null);
                }
                sendFrame(session, buffer.toByteArray());
            } catch (Exception e) {
                LOG.log(Level.WARNING, "[zeroz4j-Sync] Sync notification error: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Thread-safe frame sending via the WebSocket async remote.
     */
    private void sendFrame(Session wsSession, byte[] frameData) {
        if (wsSession == null || !wsSession.isOpen()) {
            return;
        }
        WsWrites.send(wsSession, frameData);
    }
}
