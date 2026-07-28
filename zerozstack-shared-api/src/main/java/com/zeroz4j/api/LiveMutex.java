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
 * Distributed mutex interface enabling synchronized access to shared object instances across
 * client WebAssembly instances and server Virtual Threads.
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>Suspension & Concurrency:</b> Calling {@link #lock()} suspends the current Wasm coroutine on the client
 *       or virtual thread on the server without blocking OS threads.</li>
 *   <li><b>Dependencies:</b> Uses {@link LiveMutexProvider} discovered via Java {@link ServiceLoader}.</li>
 * </ul>
 */
public interface LiveMutex {

    /**
     * Acquires the lock. If held by another session or thread, suspends execution until available.
     *
     * <p><b>Under the hood:</b> Sends an RPC request to {@link LiveMutexRpc#acquireLock(String)} using the object's
     * handle assigned by {@link ObjectMapper}. Suspends coroutine/thread until lock ACK is returned.</p>
     */
    void lock();

    /**
     * Releases the lock. Must be called by the same session/thread that acquired it.
     *
     * <p><b>Under the hood:</b> Sends an RPC request to {@link LiveMutexRpc#releaseLock(String)}. Notifies waiting sessions.</p>
     */
    void unlock();

    /**
     * Obtains a {@link LiveMutex} instance for the given shared object.
     *
     * @param sharedObject the object instance to lock (must be registered or registerable with {@link ObjectMapper})
     * @return {@link LiveMutex} instance wrapping the object handle
     *
     * <p><b>Under the hood:</b> Resolves the global SPI instance from {@link LiveMutexProvider#get()} and calls {@link LiveMutexProvider#create(Object)}.</p>
     */
    static LiveMutex get(Object sharedObject) {
        return LiveMutexProvider.get().create(sharedObject);
    }
}
