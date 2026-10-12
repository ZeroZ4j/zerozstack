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
package com.zeroz4j.server.vidocq;

import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;

/**
 * Stands in for the WebSocket container's default configurator.
 *
 * <p>The Jakarta WebSocket API asks {@code ServiceLoader} for "the container's default
 * configurator" whenever a configurator calls {@code super.modifyHandshake(...)}, which
 * {@code RmiEndpointConfigurator} does. This binding has no Jakarta WebSocket container, so this
 * class is registered under {@code META-INF/services} and does nothing.</p>
 *
 * <p>Framework-internal; it is public only because {@code ServiceLoader} needs that.</p>
 */
public class NoContainerConfigurator extends ServerEndpointConfig.Configurator {

    /** Instantiated by {@code ServiceLoader}. */
    public NoContainerConfigurator() {
    }

    @Override
    public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request,
                                HandshakeResponse response) {
        // No container, nothing to add.
    }

    @Override
    public <T> T getEndpointInstance(Class<T> endpointClass) throws InstantiationException {
        try {
            return endpointClass.getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new InstantiationException(e.toString());
        }
    }
}
