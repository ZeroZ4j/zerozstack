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
 * Marks an interface as a ZeroZ4j Remote Method Invocation (RMI) service contract.
 *
 * <p>The {@code zerozstack-apt} annotation processor generates client-side Wasm stubs
 * ({@code <InterfaceName>_Stub}) that marshal method arguments into binary payloads and suspend TeaVM coroutines until the server responds.</p>
 *
 * <p>On the backend, incoming binary frames are routed to the CDI bean implementing
 * this interface using Project Loom Virtual Threads for high scalability.</p>
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>Code Generation:</b> Triggers stub generation in `RmiAnnotationProcessor`.</li>
 *   <li><b>Server Dispatch:</b> `WasmRmiServerEngine` scans CDI bean manager for beans implementing `@RmiService` interfaces and Whitelists their methods.</li>
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RmiService {
}
