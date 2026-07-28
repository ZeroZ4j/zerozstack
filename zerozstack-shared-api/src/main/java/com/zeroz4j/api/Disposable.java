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
 * Handle for releasing a subscription or other attached resource.
 *
 * <p>Returned by APIs that register long-lived callbacks — reactive effects
 * ({@code Effect.create}) and server event subscriptions ({@code ServerEvents.on}) —
 * so callers can detach them when the owning view or component is permanently removed.</p>
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>Idempotency:</b> Implementations must tolerate {@link #dispose()} being called more than once.</li>
 *   <li><b>Lifecycle Pattern:</b> Views typically collect their disposables in a list and release
 *       them all in a single {@code dispose()} method.</li>
 * </ul>
 */
@FunctionalInterface
public interface Disposable {

    /**
     * Releases the underlying subscription or resource.
     */
    void dispose();
}
