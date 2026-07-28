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
package com.zeroz4j.server;

import java.security.Principal;

/**
 * CDI callback invoked after a client mutation of a {@code @ClientWritable} live object
 * has been authorized, validated, and applied — the application's hook for persistence
 * (e.g. {@code storage.store(...)}), auditing, or derived business logic.
 *
 * <p>Implement as an {@code @ApplicationScoped} bean; all implementations are invoked.
 * Throwing here does not undo the applied mutation.</p>
 */
@FunctionalInterface
public interface LiveMutationListener {

    /**
     * Invoked after a client mutation was applied to the canonical server instance.
     *
     * @param model     the mutated live object (the server's canonical instance)
     * @param principal the authenticated caller, or null for anonymous sessions
     */
    void onMutated(Object model, Principal principal);
}
