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
 * Declares the route path fragment for a client-side UI view component.
 *
 * <p>Used by the client router to map URL hash fragments (e.g. {@code #/dashboard}) to view classes.</p>
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>Route Registry:</b> Annotated classes are scanned and registered into {@code RouteRegistry} at client initialization.</li>
 *   <li><b>Navigation:</b> Browser hash change events trigger router navigation matching URL path against {@link #value()}.</li>
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Route {
    /**
     * The route URL path fragment (e.g., "/dashboard" or "").
     *
     * @return route path string
     */
    String value();

    /**
     * Display label used in navigation layouts. Defaults to empty string (derived from class name).
     *
     * @return display label string
     */
    String label() default "";

    /**
     * Sort order ranking in navigation menus (lower numbers appear further left/top).
     *
     * @return int order value
     */
    int order() default 100;
}
