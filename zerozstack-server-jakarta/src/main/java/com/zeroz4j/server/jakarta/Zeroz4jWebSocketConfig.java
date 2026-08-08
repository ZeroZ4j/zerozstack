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
package com.zeroz4j.server.jakarta;

import com.zeroz4j.server.WasmRmiServerEngine;
import jakarta.websocket.Endpoint;
import jakarta.websocket.server.ServerApplicationConfig;
import jakarta.websocket.server.ServerEndpointConfig;

import java.util.Collections;
import java.util.Set;

/**
 * Publishes the framework's WebSocket endpoint at {@code /wasm-rmi}.
 *
 * <p>A servlet container scans for {@code @ServerEndpoint} classes, but only within the WAR — an
 * endpoint living in a dependency jar is not picked up unless a {@link ServerApplicationConfig} names
 * it. Every application previously had to write this class itself; now the module carries it.</p>
 *
 * <p>Note the shape: a container that finds <em>any</em> {@code ServerApplicationConfig} uses only
 * what the config returns. An application declaring its own endpoints must return them <b>and</b>
 * {@link WasmRmiServerEngine} from its own config rather than adding a second one.</p>
 */
public class Zeroz4jWebSocketConfig implements ServerApplicationConfig {

    /** Instantiated by the servlet container during WebSocket scanning, not by application code. */
    public Zeroz4jWebSocketConfig() {
        // Container-instantiated; nothing to set up here.
    }

    @Override
    public Set<ServerEndpointConfig> getEndpointConfigs(Set<Class<? extends Endpoint>> endpointClasses) {
        return Collections.emptySet();   // the engine is annotation-declared, not programmatic
    }

    @Override
    public Set<Class<?>> getAnnotatedEndpointClasses(Set<Class<?>> scanned) {
        return Collections.singleton(WasmRmiServerEngine.class);
    }
}
