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
 * Restricts access to an {@link RmiService} interface or method to users possessing at least one
 * of the specified security roles.
 *
 * <p>Enforced on the server side by {@code WasmRmiServerEngine} before dispatching method calls.</p>
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>Server Enforcement:</b> Inspects caller's session security principal during WebSocket RMI frame dispatch.</li>
 *   <li><b>Access Control:</b> Throws a security exception and returns an ERROR frame (0x0F) if caller lacks required role.</li>
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RolesAllowed {
    /**
     * One or more roles required to invoke the method (evaluated as ANY-OF / disjunction).
     *
     * @return array of required role strings
     */
    String[] value();
}
