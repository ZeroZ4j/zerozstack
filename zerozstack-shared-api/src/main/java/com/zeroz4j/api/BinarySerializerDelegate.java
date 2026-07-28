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

import java.nio.ByteBuffer;

/**
 * Generic delegate interface for reflection-free binary serialization and deserialization,
 * generated at compile time by the zeroz4j annotation processor (`zerozstack-apt`).
 *
 * @param <T> the type of object being serialized/deserialized
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>Reflection-Free Performance:</b> Generated implementations write and read fields directly using field getters/setters or direct field access, eliminating runtime reflection overhead in TeaVM WebAssembly compilation.</li>
 *   <li><b>State Mutations:</b> {@link #write} populates the {@link GrowableBuffer}. {@link #read} mutates the fields of {@code obj} in place.</li>
 * </ul>
 */
public interface BinarySerializerDelegate<T> {
    /**
     * Serializes object fields into the target growable buffer.
     *
     * @param obj    the object instance to serialize
     * @param buffer the auto-expanding target buffer
     * @param mapper the object mapper tracking reference handles
     *
     * <p><b>Under the hood:</b> Generated implementation reads each field in order, delegating primitive writes
     * to {@link GrowableBuffer} and nested object/field writes to {@link BinarySerializer#writeValue(GrowableBuffer, Object, ObjectMapper)}.</p>
     */
    void write(T obj, GrowableBuffer buffer, ObjectMapper mapper);

    /**
     * Deserializes binary buffer data into the target object instance.
     *
     * @param obj    the object instance whose fields will be populated
     * @param buffer the source binary byte buffer
     * @param mapper the object mapper resolving reference handles
     *
     * <p><b>Under the hood:</b> Generated implementation reads field values from {@code buffer} in exact order
     * and sets them on {@code obj}.</p>
     */
    void read(T obj, ByteBuffer buffer, ObjectMapper mapper);
}
