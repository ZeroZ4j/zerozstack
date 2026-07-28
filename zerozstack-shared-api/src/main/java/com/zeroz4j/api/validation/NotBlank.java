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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotated String field must be non-null and contain at least one non-whitespace
 * character.
 *
 * <p>Declared once on the {@code @DataModel} field, enforced on both tiers: the APT
 * generates a reflection-free {@code <Model>_Rules} class used by UI field binding on the
 * Wasm client and automatically applied to incoming RMI arguments on the server.</p>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
public @interface NotBlank {

    /** Custom violation message; empty for the generated default. */
    String message() default "";
}
