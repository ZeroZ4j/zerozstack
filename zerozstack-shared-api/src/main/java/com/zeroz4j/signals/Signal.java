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

/**
 * Generic interface representing a read-only reactive state value in zeroz4j's Signals framework.
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>Automatic Dependency Tracking:</b> When {@link #get()} is invoked inside a {@code Computed} or {@code Effect} context,
 *       the calling signal registers itself as an active dependency via {@link Effect#registerDependency(Signal)}.</li>
 * </ul>
 *
 * @param <T> the type of value stored in this signal
 */
public interface Signal<T> {
    /**
     * Reads the current reactive value, registering this signal as a dependency of any active tracking context.
     *
     * @return current reactive value
     */
    T get();
}
