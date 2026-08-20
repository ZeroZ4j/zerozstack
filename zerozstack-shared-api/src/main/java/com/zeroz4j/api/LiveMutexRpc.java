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
 * <h2>Who may lock what</h2>
 *
 * <p><b>A client may lock only an object the server has actually sent it.</b> The server keeps a
 * record of every object handle it has written toward each browser, and a lock request for anything
 * else is refused straight away with a sentence saying so. Being sent an object is what earns the
 * right to lock it; knowing its name is not, because an object nested inside a broadcast event or a
 * shared signal travels with its name attached.</p>
 *
 * <p>No sign-in is required. That is deliberate: examples and applications with no login at all must
 * still be able to serialize their editors. A deployment that does have logins can additionally
 * restrict locking to signed-in connections by setting the server property
 * {@code zeroz.livemutex.requireAuthentication=true}. This interface deliberately carries no
 * {@code Secured} annotation, which would make a login compulsory for everyone.</p>
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>RMI Service Contract:</b> Annotated with {@link RmiService}, generating client stubs for remote lock acquire/release calls.</li>
 *   <li><b>Server Dispatch:</b> Dispatched on server side to {@code LiveMutexRpcImpl}, which checks the caller was sent the object before {@code LiveMutexManager} queues it per object handle.</li>
 * </ul>
 */
@RmiService
public interface LiveMutexRpc {

    /**
     * Requests acquisition of the distributed lock for the specified object ID handle.
     *
     * <p>Refused immediately, with an explanation, when the server never sent this client the object
     * behind the handle.</p>
     *
     * @param objectId the unique handle ID of the target shared object
     *
     * <p><b>Under the hood:</b> Invoked over WebSocket RMI. Suspends calling coroutine/thread until
     * the server lock manager grants ownership, or until the wait runs out —
     * {@code zeroz.livemutex.waitSeconds}, 30 seconds by default — after which the call fails with a
     * message naming the wait.</p>
     */
    void acquireLock(String objectId);

    /**
     * Releases the distributed lock for the specified object ID handle.
     *
     * <p>Does nothing unless this connection is the one holding that lock.</p>
     *
     * @param objectId the unique handle ID of the target shared object
     *
     * <p><b>Under the hood:</b> Invoked over WebSocket RMI. Server releases lock and resumes next queued session.</p>
     */
    void releaseLock(String objectId);
}
