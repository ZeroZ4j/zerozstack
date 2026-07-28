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
 * Annotation restricting access to a client-side UI view class to users possessing at least one
 * of the specified security roles.
 *
 * <p>Checked by the view navigation router before displaying a target view.</p>
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>Navigation Interception:</b> Router inspects this annotation on the target view class and evaluates
 *       it against {@link RmiSecurityContext#hasAnyRole(String...)}.</li>
 *   <li><b>Access Control:</b> If security check fails, navigation is aborted or redirected to login.</li>
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RequiresRole {
    /**
     * One or more roles required to access the view (evaluated as ANY-OF / disjunction).
     *
     * @return array of required role names
     */
    String[] value();
}
