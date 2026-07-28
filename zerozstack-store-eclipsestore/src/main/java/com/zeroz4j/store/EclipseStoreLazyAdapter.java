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
package com.zeroz4j.store;

import com.zeroz4j.api.BinaryRegistry;
import com.zeroz4j.api.LazyAdapter;
import com.zeroz4j.api.ObjectMapper;
import com.zeroz4j.server.LazyHandles;
import org.eclipse.serializer.reference.Lazy;

/**
 * Server-side handling of EclipseStore {@code Lazy} fields.
 *
 * <p>Writing a lazy reference emits only a session-scoped handle, so a deferred subgraph is not
 * loaded from storage merely because the owning object was synced to a browser. The client resolves
 * it later, if it ever needs to, and that resolution is what finally calls {@code Lazy.get()}.</p>
 *
 * <p>This is the only place in the framework outside {@code zerozstack-store-eclipsestore} that
 * mentions EclipseStore's {@code Lazy} type — everything else goes through {@link LazyAdapter}, which
 * is why the client tier can support the same field type without any EclipseStore dependency.</p>
 */
public final class EclipseStoreLazyAdapter implements LazyAdapter {

    /** Installs this adapter as the tier's lazy handling. Called during server startup. */
    public static void install() {
        BinaryRegistry.setLazyAdapter(new EclipseStoreLazyAdapter());
    }

    @Override
    public boolean isLazy(Object value) {
        return value instanceof Lazy;
    }

    @Override
    public String handleFor(Object lazy, ObjectMapper mapper) {
        return LazyHandles.register(lazy);
    }

    /**
     * Resolves a handle arriving <em>from</em> a client — for example inside a LiveSync mutation that
     * echoes back an object carrying a lazy field.
     *
     * <p>The client cannot invent contents for a lazy field, so an inbound handle can only be one the
     * server already issued to that session. Anything else is rejected rather than silently treated as
     * empty.</p>
     */
    @Override
    public Object fromHandle(String handle) {
        Object lazy = LazyHandles.resolve(handle, LazyHandles.currentSession());
        if (lazy == null) {
            throw new IllegalArgumentException(
                    "Unknown lazy handle for this session: " + handle);
        }
        return lazy;
    }

    @Override
    public Object contentsOf(Object lazy) {
        return ((Lazy<?>) lazy).get();
    }
}
