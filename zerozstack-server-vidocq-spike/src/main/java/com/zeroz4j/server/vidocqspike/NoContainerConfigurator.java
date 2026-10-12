package com.zeroz4j.server.vidocqspike;

import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;

/**
 * SPIKE. The Jakarta WebSocket API asks ServiceLoader for "the container's default configurator"
 * whenever a configurator calls super.modifyHandshake(...), which RmiEndpointConfigurator does.
 * There is no container here, so this stands in and does nothing.
 */
public class NoContainerConfigurator extends ServerEndpointConfig.Configurator {
    @Override
    public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
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
