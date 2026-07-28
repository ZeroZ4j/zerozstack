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
 * Tier-specific handling of EclipseStore {@code Lazy} references on the wire.
 *
 * <p>This exists so that {@link BinarySerializer} can support {@code Lazy} fields without
 * {@code zerozstack-shared-api} depending on EclipseStore. The server installs an adapter that
 * registers a handle for a real {@code Lazy.Default} and resolves it from storage; the client
 * installs one that materialises its own {@code Lazy} implementation, resolving over RMI on first
 * {@code get()}.</p>
 *
 * <p><b>A lazy reference never carries its contents on the wire</b> — only an opaque handle. That is
 * the entire point: a deferred subgraph stays deferred across the network, and the round trip that
 * resolves it is an ordinary suspending RMI call.</p>
 *
 * <p>Applications neither implement nor install this; each tier's runtime does.</p>
 */
public interface LazyAdapter {

    /**
     * @param value any value being serialized
     * @return true when it is a lazy reference this adapter owns
     */
    boolean isLazy(Object value);

    /**
     * Registers a lazy reference for later resolution and returns the handle to put on the wire.
     *
     * <p>Implementations must scope the handle to the session it is disclosed to, so that a handle
     * cannot be replayed by a session that was never permitted to see the data.</p>
     *
     * @param lazy   the lazy reference being written
     * @param mapper the object mapper for the frame being written
     * @return an opaque handle, never null
     */
    String handleFor(Object lazy, ObjectMapper mapper);

    /**
     * Reconstructs a lazy reference from a handle read off the wire.
     *
     * @param handle the handle written by {@link #handleFor}
     * @return an object implementing EclipseStore's {@code Lazy} interface
     */
    Object fromHandle(String handle);

    /**
     * Resolves a lazy reference to its contents. Server-side only — this is what a client's resolve
     * request ultimately calls, and on EclipseStore it loads the subgraph from storage.
     *
     * @param lazy the lazy reference to resolve
     * @return the contained value, possibly null
     */
    Object contentsOf(Object lazy);
}
