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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of whole-object validators keyed by model class name, populated by the
 * APT-generated registrar for every {@code @DataModel} class carrying validation
 * annotations.
 *
 * <p>The server engine consults this registry automatically for incoming RMI arguments
 * and client-written shared signal values — validation annotations on a model are
 * authoritative on the server regardless of what the client did.</p>
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>Lookup Identity:</b> Keyed by {@code getClass().getName()} — the same runtime
 *       class identity the binary serializer uses.</li>
 *   <li><b>No Rules, No Cost:</b> {@link #validate(Object)} returns an empty list for
 *       classes without registered validators.</li>
 * </ul>
 */
public final class ValidationRegistry {

    private static final Map<String, ObjectValidator<Object>> validators = new ConcurrentHashMap<>();

    private ValidationRegistry() {}

    /**
     * Registers a validator for a model class. Called by generated registrars.
     *
     * @param className model class FQCN
     * @param validator whole-object validator
     */
    @SuppressWarnings("unchecked")
    public static void register(String className, ObjectValidator<?> validator) {
        validators.put(className, (ObjectValidator<Object>) validator);
    }

    /**
     * Validates an object against its registered validator, if any.
     *
     * @param object the instance to check; may be null
     * @return violation messages; empty when valid, unregistered, or null
     */
    public static List<String> validate(Object object) {
        if (object == null) {
            return Collections.emptyList();
        }
        ObjectValidator<Object> validator = validators.get(object.getClass().getName());
        return validator == null ? Collections.emptyList() : validator.validate(object);
    }

    /**
     * Returns whether a validator is registered for the class name.
     *
     * @param className model class FQCN
     * @return true if registered
     */
    public static boolean hasValidator(String className) {
        return validators.containsKey(className);
    }
}
