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
package com.zeroz4j.api.validation;

import java.util.List;

/**
 * Validation rule for a single field value, typically obtained from an APT-generated
 * {@code <Model>_Rules} class and attached to a UI field via
 * {@code AbstractField.withRule(...)}.
 *
 * <p>Reflection-free by design — the same generated rules run in the Wasm client and on
 * the JVM server.</p>
 *
 * @param <T> field value type
 */
@FunctionalInterface
public interface FieldRule<T> {

    /**
     * Validates a value.
     *
     * @param value the value to check (may be null)
     * @return violation messages; empty when valid
     */
    List<String> validate(T value);
}
