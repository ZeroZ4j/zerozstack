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
 * Optional escape hatch for hand-written binary serialization. Application models do NOT
 * implement this — annotate them {@link Portable} and the APT generates their serializer.
 * Implement this interface only when a class needs custom serialization logic outside the
 * annotation processor (rare; used mainly by framework-internal tests), registering it via
 * {@link BinaryRegistry#register(String, java.util.function.Supplier)}.
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>Not Required:</b> {@link Portable} models need no interface; serialization is
 *       dispatched via generated {@link BinarySerializerDelegate} instances in {@link BinaryRegistry}.</li>
 *   <li><b>State Mutations:</b> Method calls mutate either the target {@link GrowableBuffer} output during serialization
 *       or the implementing instance's internal field state during deserialization.</li>
 * </ul>
 */
public interface BinaryPackable {
    /**
     * Serializes this object's field state into the provided {@link GrowableBuffer}.
     *
     * @param buffer the auto-growing target buffer receiving binary fields
     * @param mapper the object mapper tracking reference handles
     * @throws UnsupportedOperationException if called without a generated serializer delegate
     *
     * <p><b>Under the hood:</b> Called during binary RPC marshalling. Default implementation throws exception
     * because serialization is dispatched via generated {@link BinarySerializerDelegate} instances in {@link BinaryRegistry}.</p>
     */
    default void writeToBuffer(GrowableBuffer buffer, ObjectMapper mapper) {
        throw new UnsupportedOperationException("Call via delegate");
    }

    /**
     * Deserializes this object's field state from the provided {@link ByteBuffer}.
     *
     * @param buffer the source binary buffer containing field payloads
     * @param mapper the object mapper resolving reference handles
     * @throws UnsupportedOperationException if called without a generated serializer delegate
     *
     * <p><b>Under the hood:</b> Called during binary RPC unmarshalling. Default implementation throws exception
     * because deserialization is dispatched via generated {@link BinarySerializerDelegate} instances in {@link BinaryRegistry}.</p>
     */
    default void readFromBuffer(ByteBuffer buffer, ObjectMapper mapper) {
        throw new UnsupportedOperationException("Call via delegate");
    }
}
