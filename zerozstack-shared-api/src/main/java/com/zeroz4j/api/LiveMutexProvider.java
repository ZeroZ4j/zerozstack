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

import java.util.ServiceLoader;

/**
 * Service Provider Interface (SPI) base class for creating environment-specific {@link LiveMutex} instances.
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>SPI Discovery:</b> Uses {@link ServiceLoader} to load either {@code ClientLiveMutexProvider} (on TeaVM client)
 *       or {@code ServerLiveMutexProvider} (on JVM server).</li>
 *   <li><b>Singleton Lazy Load:</b> Caches provider instance in static field {@code instance}.</li>
 * </ul>
 */
public abstract class LiveMutexProvider {
    private static LiveMutexProvider instance;

    /**
     * Obtains the active singleton {@link LiveMutexProvider} for the current JVM or TeaVM environment.
     *
     * @return the discovered {@link LiveMutexProvider}
     * @throws IllegalStateException if no provider implementation is found on the classpath
     *
     * <p><b>Under the hood:</b> If {@code instance} is null, iterates through {@link ServiceLoader#load(Class)}.
     * Sets {@code instance} to the first found provider implementation and returns it.</p>
     */
    public static LiveMutexProvider get() {
        if (instance == null) {
            for (LiveMutexProvider p : ServiceLoader.load(LiveMutexProvider.class)) {
                instance = p;
                break;
            }
            if (instance == null) {
                throw new IllegalStateException("No LiveMutexProvider found in classpath.");
            }
        }
        return instance;
    }

    /**
     * Factory method creating a {@link LiveMutex} instance wrapping the given shared object.
     *
     * @param sharedObject the target object instance
     * @return a concrete {@link LiveMutex} wrapper
     */
    public abstract LiveMutex create(Object sharedObject);
}
