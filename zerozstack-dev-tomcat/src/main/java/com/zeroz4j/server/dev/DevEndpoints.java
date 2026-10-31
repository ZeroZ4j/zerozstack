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
package com.zeroz4j.server.dev;

import com.zeroz4j.server.WasmRmiServerEngine;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.server.ServerContainer;

/**
 * Publishes the framework's WebSocket endpoint at {@code <context>/wasm-rmi}.
 *
 * <p>A WAR does this with {@code Zeroz4jWebSocketConfig}, which the container finds by scanning.
 * The dev server scans nothing, so the same endpoint is registered here, one listener after
 * Tomcat's own {@code WsContextListener} has created the WebSocket container. Public because Tomcat
 * creates it from its class name.</p>
 */
public final class DevEndpoints implements ServletContextListener {

    /** Created by Tomcat from the listener list. */
    public DevEndpoints() {
    }

    @Override
    public void contextInitialized(ServletContextEvent event) {
        ServerContainer container = (ServerContainer)
                event.getServletContext().getAttribute(ServerContainer.class.getName());
        if (container == null) {
            throw new IllegalStateException("No WebSocket container: WsContextListener must come "
                    + "before this listener");
        }
        try {
            container.addEndpoint(WasmRmiServerEngine.class);
        } catch (DeploymentException failure) {
            throw new IllegalStateException("Could not publish the RMI endpoint", failure);
        }
    }
}
