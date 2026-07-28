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
package com.zeroz4j.signals;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The signal behind a {@code @LiveSync} object: one instance is held by each APT-generated
 * {@code <Model>_Live}, making the object itself a first-class dependency of {@link Effect} and
 * {@link Computed}.
 *
 * <p>Holding the signal on the object rather than in a global registry is deliberate — there is no
 * identity map to consult and nothing to evict, so a discarded live object is simply garbage
 * collected along with its signal.</p>
 *
 * <p>Applications do not construct or use this directly; generated code does.</p>
 */
public final class LiveSignal implements ObservableSignal<Object> {

    private final Object owner;
    private final List<Consumer<Object>> listeners = new ArrayList<>();

    /**
     * @param owner the live object this signal represents
     */
    public LiveSignal(Object owner) {
        this.owner = owner;
    }

    /**
     * Returns the live object itself.
     *
     * <p>Deliberately does <b>not</b> register a dependency: tracking is driven by the generated
     * getters calling {@link #reportRead()}, and registering here as well would recurse when a
     * listener reads the object.</p>
     *
     * @return the owning live object
     */
    @Override
    public Object get() {
        return owner;
    }

    /**
     * Registers the owning object as a dependency of the effect or computed currently being tracked.
     * A no-op when nothing is tracking, which is the common case.
     */
    public void reportRead() {
        Effect.registerDependency(this);
    }

    /**
     * Notifies listeners that the owning object's fields have changed.
     *
     * <p>Iterates a copy so a listener may dispose itself, or create new effects, during
     * notification.</p>
     */
    public void notifyChanged() {
        List<Consumer<Object>> snapshot;
        synchronized (listeners) {
            if (listeners.isEmpty()) {
                return;
            }
            snapshot = new ArrayList<>(listeners);
        }
        for (Consumer<Object> listener : snapshot) {
            listener.accept(owner);
        }
    }

    @Override
    public void addListener(Consumer<Object> listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeListener(Consumer<Object> listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    /**
     * @return the number of registered listeners; intended for tests and diagnostics
     */
    public int listenerCount() {
        synchronized (listeners) {
            return listeners.size();
        }
    }
}
