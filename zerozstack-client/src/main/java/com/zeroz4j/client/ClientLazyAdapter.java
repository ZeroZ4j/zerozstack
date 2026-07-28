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

import com.zeroz4j.api.BinaryRegistry;
import com.zeroz4j.api.LazyAdapter;
import com.zeroz4j.api.ObjectMapper;
import org.eclipse.serializer.reference.Lazy;

/**
 * Client-side lazy handling: turns an inbound handle into a {@link ClientLazy}.
 *
 * <p>Installed by {@link Zeroz4jClient} during bootstrap.</p>
 */
final class ClientLazyAdapter implements LazyAdapter {

    static void install() {
        BinaryRegistry.setLazyAdapter(new ClientLazyAdapter());
    }

    @Override
    public boolean isLazy(Object value) {
        return value instanceof Lazy;
    }

    /**
     * Returns the handle a {@link ClientLazy} already carries, so echoing an object back to the server
     * preserves the reference instead of trying to send the deferred contents.
     */
    @Override
    public String handleFor(Object lazy, ObjectMapper mapper) {
        if (lazy instanceof ClientLazy) {
            return ((ClientLazy<?>) lazy).handle();
        }
        throw new IllegalArgumentException(
                "Cannot send a client-created lazy reference to the server: lazy references originate "
                + "on the server. Assign the resolved value instead.");
    }

    @Override
    public Object fromHandle(String handle) {
        return new ClientLazy<>(handle);
    }

    /**
     * Not used on the client: resolution happens through {@link ClientLazy#get()}, which performs the
     * RMI round trip.
     */
    @Override
    public Object contentsOf(Object lazy) {
        return ((Lazy<?>) lazy).get();
    }
}
