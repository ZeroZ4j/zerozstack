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
package com.zeroz4j.server;

import com.zeroz4j.api.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * CDI producer providing the application-scoped singleton {@link ObjectMapper} bean instance.
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>CDI Injection:</b> Produces singleton {@code ObjectMapper} instance injected into {@code WasmRmiServerEngine} and {@code SyncEngine}.</li>
 * </ul>
 */
@ApplicationScoped
public class ObjectMapperProducer {

    /**
     * Produces the application-scoped {@link ObjectMapper} instance.
     *
     * @return new {@link ObjectMapper} singleton
     *
     * <p><b>Under the hood:</b> Executed by CDI container on first injection point.</p>
     */
    @Produces
    @ApplicationScoped
    public ObjectMapper produceObjectMapper() {
        return new ObjectMapper();
    }
}
