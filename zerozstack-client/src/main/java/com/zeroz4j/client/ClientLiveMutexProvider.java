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
     * Every mutex this client believes it holds. When the socket drops the server releases them
     * all, so this is exactly the set whose holders must be told. Identity-keyed on the mutex
     * instance; the value is its lost listener (possibly a no-op).
     */
    private static final java.util.Map<LiveMutex, Runnable> held =
            java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());

    /** Test support only: forgets all held locks. */
    static void resetForTesting() {
        held.clear();
    }

    /**
     * Marks every held lock as lost and tells its holder. Called by {@link WasmRmiClient} the
     * moment a drop is detected — not on reconnect, because the lock stopped protecting anything
     * the instant the server session closed, and an editor should stop accepting input now.
     * Listeners run on the UI scheduler when one is installed.
     */
    static void connectionLost() {
        java.util.List<Runnable> listeners;
        synchronized (held) {
            if (held.isEmpty()) {
                return;
            }
            listeners = new java.util.ArrayList<>(held.values());
            held.clear();
        }
        for (Runnable listener : listeners) {
            if (listener == null) {
                continue;
            }
            WasmRmiClient.PlatformScheduler scheduler = WasmRmiClient.getPlatformScheduler();
            if (scheduler != null) {
                scheduler.runLater(listener);
            } else {
                listener.run();
            }
        }
    }

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
            private Runnable lostListener;

            @Override
            public void lock() {
                String id = WasmRmiClient.MAPPER.getId(sharedObject);
                if (id == null) {
                    id = WasmRmiClient.MAPPER.register(sharedObject);
                }
                RmiClientExecutor.executeCall("com.zeroz4j.api.LiveMutexRpc", "acquireLock", new Object[]{id});
                // Only reached when acquireLock returned: the lock is genuinely held from here.
                held.put(this, lostListener);
            }

            @Override
            public void unlock() {
                held.remove(this);
                String id = WasmRmiClient.MAPPER.getId(sharedObject);
                if (id != null) {
                    RmiClientExecutor.executeCall("com.zeroz4j.api.LiveMutexRpc", "releaseLock", new Object[]{id});
                }
            }

            @Override
            public void setLostListener(Runnable listener) {
                this.lostListener = listener;
                // If already held, refresh the registered listener too.
                held.replace(this, listener);
            }
        };
    }
}
