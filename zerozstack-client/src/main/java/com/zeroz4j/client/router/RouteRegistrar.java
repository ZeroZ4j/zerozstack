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
package com.zeroz4j.client.router;

/**
 * Adds a module's routes to the table.
 *
 * <p>Implemented by a class the annotation processor generates from the {@code @Route} annotations
 * it finds, and discovered through {@link java.util.ServiceLoader} — the same mechanism the
 * generated serializer registrars use, and for the same reason: the browser runtime has no
 * reflection, so the route table has to be assembled from code written at compile time.</p>
 *
 * <p>Applications never implement this.</p>
 */
public interface RouteRegistrar {

    /**
     * Registers every route this module declares.
     */
    void registerAll();
}
