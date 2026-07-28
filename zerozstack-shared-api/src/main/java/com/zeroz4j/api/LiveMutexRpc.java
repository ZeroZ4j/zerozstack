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
package com.zeroz4j.api;

/**
 * Internal remote service contract for managing distributed live mutex locks over WebSocket binary RMI.
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>RMI Service Contract:</b> Annotated with {@link RmiService}, generating client stubs for remote lock acquire/release calls.</li>
 *   <li><b>Server Dispatch:</b> Dispatched on server side to {@code LiveMutexRpcImpl} which manages lock queues per object handle ID.</li>
 * </ul>
 */
@RmiService
public interface LiveMutexRpc {

    /**
     * Requests acquisition of the distributed lock for the specified object ID handle.
     *
     * @param objectId the unique handle ID of the target shared object
     *
     * <p><b>Under the hood:</b> Invoked over WebSocket RMI. Suspends calling coroutine/thread until server lock manager grants ownership.</p>
     */
    void acquireLock(String objectId);

    /**
     * Releases the distributed lock for the specified object ID handle.
     *
     * @param objectId the unique handle ID of the target shared object
     *
     * <p><b>Under the hood:</b> Invoked over WebSocket RMI. Server releases lock and resumes next queued session.</p>
     */
    void releaseLock(String objectId);
}
