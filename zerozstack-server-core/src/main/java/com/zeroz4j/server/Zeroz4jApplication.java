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

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * JAX-RS {@link Application} entry point configuring root path mapping {@code "/"} for zeroz4j server resources.
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>Context Mapping:</b> Binds all JAX-RS REST endpoints under the root web context path {@code "/"}.</li>
 * </ul>
 */
@ApplicationScoped
@ApplicationPath("/")
public class Zeroz4jApplication extends Application {
}
