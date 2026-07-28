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
 * The annotated String field's length must lie within the given bounds (null is not
 * checked — combine with {@link NotBlank} to require a value).
 *
 * <p>Enforced on both tiers via the generated {@code <Model>_Rules} class.</p>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
public @interface Size {

    /** Minimum length, inclusive. */
    int min() default 0;

    /** Maximum length, inclusive. */
    int max() default Integer.MAX_VALUE;

    /** Custom violation message; empty for the generated default. */
    String message() default "";
}
