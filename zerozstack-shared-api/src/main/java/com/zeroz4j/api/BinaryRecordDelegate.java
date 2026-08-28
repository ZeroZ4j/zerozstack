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
 * Generated read/write pair for a {@code record} {@link DataModel}, produced at compile time by
 * the zeroz4j annotation processor (`zerozstack-apt`).
 *
 * <p>This exists because a record cannot be filled in after it is made. The delegate used for an
 * ordinary class, {@link BinarySerializerDelegate}, is handed an instance that already exists and
 * writes the fields into it — which is what lets the reader register the instance before its
 * fields are read, and so lets an object graph point back at something still being read. A
 * record's components are final and are set only by its canonical constructor, so its reader must
 * read every component first and construct last. Hence a {@link #read} that <i>returns</i> the
 * value instead of filling one.</p>
 *
 * <p>The direct consequence: a record cannot take part in a reference cycle. See
 * {@link BinarySerializer#TAG_RECORD}.</p>
 *
 * @param <T> the record type
 */
public interface BinaryRecordDelegate<T> {

    /**
     * Writes the record's components into the buffer, in canonical component order.
     *
     * @param obj    the record instance to write (may be null, written as a single absence byte)
     * @param buffer the auto-expanding target buffer
     * @param mapper the object mapper tracking reference handles
     */
    void write(T obj, GrowableBuffer buffer, ObjectMapper mapper);

    /**
     * Reads every component and then calls the record's canonical constructor.
     *
     * @param buffer the source buffer, positioned at the record's component bytes
     * @param mapper the object mapper resolving reference handles
     * @return the newly constructed record, or null if the writer wrote a null
     */
    T read(ByteBuffer buffer, ObjectMapper mapper);
}
