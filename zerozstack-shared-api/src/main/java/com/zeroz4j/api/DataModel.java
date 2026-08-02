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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a plain Java class as part of the shared model: portable across tiers as-is —
 * RMI arguments and returns, event payloads, shared signal values, EclipseStore
 * persistence, and the Wasm UI all use the same instance class. One annotation, nothing
 * else: no interface to implement, no DTOs, no mapping.
 *
 * <p>Requirements: a public no-arg constructor, and for every serialized field either a public
 * field or standard accessors — public fields alone are enough. Validation annotations from
 * {@code com.zeroz4j.api.validation} may be declared on fields and are enforced on both
 * tiers.</p>
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>Compile-time Code Generation:</b> The zerozstack-apt processor generates a binary
 *       serializer ({@code <Model>_Serializer}), validation rules ({@code <Model>_Rules},
 *       when constraint annotations are present), and registrar entries for every
 *       {@code @DataModel} class.</li>
 *   <li><b>Wire Identity:</b> Instances travel with their runtime class name; the
 *       transport encoding is an internal detail applications never see.</li>
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DataModel {
}
