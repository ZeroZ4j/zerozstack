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

import com.zeroz4j.api.LiveMutex;
import com.zeroz4j.api.LiveMutexProvider;
import jakarta.enterprise.inject.spi.CDI;
import com.zeroz4j.api.ObjectMapper;

/**
 * Server-side implementation of {@link LiveMutexProvider} for JVM server environments.
 *
 * <p>Creates server-side {@link LiveMutex} instances that interact directly with the local CDI {@link LiveMutexManager} bean.</p>
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>CDI Context Lookup:</b> Uses {@link CDI#current()} to dynamically locate {@link ObjectMapper} and {@link LiveMutexManager}.</li>
 *   <li><b>Thread Identity:</b> Locks on behalf of owner identifier string {@code "thread:" + Thread.currentThread().threadId()}.</li>
 * </ul>
 */
public class ServerLiveMutexProvider extends LiveMutexProvider {

    /**
     * Creates a {@link LiveMutex} instance bound to the specified shared object handle on the server.
     *
     * @param sharedObject the target shared model object instance
     * @return a new {@link LiveMutex} wrapper for server-side locking
     *
     * <p><b>Under the hood:</b> Dynamically fetches {@link ObjectMapper} and {@link LiveMutexManager} via CDI.
     * {@link LiveMutex#lock()} calls {@code manager.lock(id, "thread:" + threadId)}.
     * {@link LiveMutex#unlock()} calls {@code manager.unlock(id, "thread:" + threadId)}.</p>
     */
    @Override
    public LiveMutex create(Object sharedObject) {
        return new LiveMutex() {
            @Override
            public void lock() {
                ObjectMapper mapper = CDI.current().select(ObjectMapper.class).get();
                String id = mapper.getId(sharedObject);
                if (id == null) {
                    id = mapper.register(sharedObject);
                }
                LiveMutexManager manager = CDI.current().select(LiveMutexManager.class).get();
                manager.lock(id, "thread:" + Thread.currentThread().threadId());
            }

            @Override
            public void unlock() {
                ObjectMapper mapper = CDI.current().select(ObjectMapper.class).get();
                String id = mapper.getId(sharedObject);
                if (id != null) {
                    LiveMutexManager manager = CDI.current().select(LiveMutexManager.class).get();
                    manager.unlock(id, "thread:" + Thread.currentThread().threadId());
                }
            }
        };
    }
}
