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
package com.zeroz4j.client;

import com.zeroz4j.api.LiveMutex;
import com.zeroz4j.api.LiveMutexProvider;
import com.zeroz4j.api.LiveMutexRpc;
import com.zeroz4j.api.RmiClientExecutor;

/**
 * Client-side implementation of {@link LiveMutexProvider} for TeaVM WebAssembly environments.
 *
 * <p>Generates client-side {@link LiveMutex} instances that communicate with the backend's {@code LiveMutexRpc} service over WebSocket binary RMI.</p>
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>SPI Discovery:</b> Discovered via {@link java.util.ServiceLoader} on the Wasm client heap.</li>
 *   <li><b>RMI Dispatch:</b> {@link LiveMutex#lock()} and {@link LiveMutex#unlock()} invoke {@link RmiClientExecutor#executeCall} targeting {@code "com.zeroz4j.api.LiveMutexRpc"}.</li>
 * </ul>
 */
public class ClientLiveMutexProvider extends LiveMutexProvider {

    /**
     * Creates a {@link LiveMutex} instance bound to the specified shared object handle.
     *
     * @param sharedObject the target shared model object instance
     * @return a new {@link LiveMutex} wrapper for client-side locking
     *
     * <p><b>Under the hood:</b> Registers {@code sharedObject} with {@link WasmRmiClient#MAPPER} to obtain an ID string.
     * Implements {@link LiveMutex#lock()} by invoking RMI call {@code LiveMutexRpc.acquireLock(id)} and {@link LiveMutex#unlock()} via {@code releaseLock(id)}.</p>
     */
    @Override
    public LiveMutex create(Object sharedObject) {
        return new LiveMutex() {
            @Override
            public void lock() {
                String id = WasmRmiClient.MAPPER.getId(sharedObject);
                if (id == null) {
                    id = WasmRmiClient.MAPPER.register(sharedObject);
                }
                RmiClientExecutor.executeCall("com.zeroz4j.api.LiveMutexRpc", "acquireLock", new Object[]{id});
            }

            @Override
            public void unlock() {
                String id = WasmRmiClient.MAPPER.getId(sharedObject);
                if (id != null) {
                    RmiClientExecutor.executeCall("com.zeroz4j.api.LiveMutexRpc", "releaseLock", new Object[]{id});
                }
            }
        };
    }
}
