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
package com.zeroz4j.api.store;

/**
 * Application SPI interface providing the initial root graph object for EclipseStore persistence when a database is first created.
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>EclipseStore Initialization:</b> Called by `EclipseStoreProducer` during startup if the storage manager detects an uninitialized root object.</li>
 *   <li><b>State Mutations:</b> Creates and returns a new root entity graph which EclipseStore persists to disk.</li>
 * </ul>
 */
public interface DataRootProvider {
    /**
     * Creates the default initial root object graph for a specific tenant.
     *
     * @param tenantId the identifier string for the target tenant
     * @return the initial root object to be persisted as the EclipseStore root graph
     *
     * <p><b>Under the hood:</b> Instantiates domain root POJO and populates default collections or values.</p>
     */
    Object createDefaultRoot(String tenantId);
}
