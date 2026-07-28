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

import java.util.function.Consumer;

/**
 * A {@link Signal} whose changes can be observed via listener callbacks.
 *
 * <p>This is the contract {@link Effect} and {@link Computed} use to subscribe to their
 * dependencies. Any custom signal implementation (for example a network-backed signal)
 * must implement this interface — not just {@link Signal} — to participate in automatic
 * dependency tracking; a plain {@link Signal} can be read but will never trigger re-runs.</p>
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>Dependency Subscription:</b> When an {@link Effect} or {@link Computed} tracks a read of this signal,
 *       it calls {@link #addListener(Consumer)} to subscribe its invalidator and {@link #removeListener(Consumer)}
 *       on re-track or disposal.</li>
 *   <li><b>Listener Contract:</b> Implementations must invoke listeners with the new value after every
 *       observable change, and tolerate listeners being removed during notification.</li>
 * </ul>
 *
 * @param <T> the type of value stored in this signal
 */
public interface ObservableSignal<T> extends Signal<T> {

    /**
     * Adds a change listener callback invoked whenever this signal's value changes.
     *
     * @param listener change listener
     */
    void addListener(Consumer<T> listener);

    /**
     * Removes a previously registered change listener callback.
     *
     * @param listener change listener
     */
    void removeListener(Consumer<T> listener);
}
