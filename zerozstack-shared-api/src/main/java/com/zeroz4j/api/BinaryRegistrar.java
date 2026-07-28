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
 * Service Provider Interface (SPI) for auto-registering {@link BinaryPackable} model types.
 * Implementations are discovered via {@link java.util.ServiceLoader} and invoked
 * automatically by {@link BinaryRegistry#init()}.
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>AOT Code Generation:</b> The zeroz4j annotation processor (`zerozstack-apt`) generates an implementation
 *       of this interface for each compilation unit containing {@link DataModel} classes, outputting a registration manifest in META-INF/services.</li>
 *   <li><b>Side Effects:</b> Invoking {@link #registerAll()} registers supplier factories and serializer delegates into static maps in {@link BinaryRegistry}.</li>
 * </ul>
 */
public interface BinaryRegistrar {
    /**
     * Registers all known {@link BinaryPackable} model types and their serializer delegates with {@link BinaryRegistry}.
     *
     * <p><b>Under the hood:</b> Generated code calls {@link BinaryRegistry#register(String, java.util.function.Supplier, BinarySerializerDelegate)}
     * for every compiled {@code @DataModel} in the module.</p>
     */
    void registerAll();
}
