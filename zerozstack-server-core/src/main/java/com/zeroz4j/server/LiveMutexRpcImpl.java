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

import com.zeroz4j.api.LiveMutexRpc;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Application-scoped CDI implementation of {@link LiveMutexRpc} service.
 * Handles incoming client WebSocket lock/unlock RMI calls by delegating to {@link LiveMutexManager}.
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>Context Resolution:</b> Resolves session ID from thread-local {@link RmiRequestContext#getSessionId()}.</li>
 *   <li><b>Delegation:</b> Calls {@code LiveMutexManager.lock(objectId, "session:" + sessionId)} or {@code unlock}.</li>
 * </ul>
 */
@ApplicationScoped
public class LiveMutexRpcImpl implements LiveMutexRpc {

    @Inject
    LiveMutexManager manager;

    /**
     * Acquires the distributed live mutex lock for the specified object ID on behalf of the calling session.
     *
     * @param objectId the target object handle ID string
     * @throws IllegalStateException if no active WebSocket session exists on thread context
     *
     * <p><b>Under the hood:</b> Resolves session ID from {@link RmiRequestContext#getSessionId()} and calls {@code manager.lock(objectId, "session:" + sessionId)}.</p>
     */
    @Override
    public void acquireLock(String objectId) {
        String sessionId = RmiRequestContext.getSessionId();
        if (sessionId == null) {
            throw new IllegalStateException("No active WebSocket session for RPC.");
        }
        manager.lock(objectId, "session:" + sessionId);
    }

    /**
     * Releases the distributed live mutex lock for the specified object ID.
     *
     * @param objectId the target object handle ID string
     * @throws IllegalStateException if no active WebSocket session exists on thread context
     *
     * <p><b>Under the hood:</b> Resolves session ID from {@link RmiRequestContext#getSessionId()} and calls {@code manager.unlock(objectId, "session:" + sessionId)}.</p>
     */
    @Override
    public void releaseLock(String objectId) {
        String sessionId = RmiRequestContext.getSessionId();
        if (sessionId == null) {
            throw new IllegalStateException("No active WebSocket session for RPC.");
        }
        manager.unlock(objectId, "session:" + sessionId);
    }
}
