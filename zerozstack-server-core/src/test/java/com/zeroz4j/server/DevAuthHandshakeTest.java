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

import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DevAuthHandshakeTest {

    static class FakeHandshakeRequest implements HandshakeRequest {
        private final Map<String, List<String>> params;
        FakeHandshakeRequest(Map<String, List<String>> params) { this.params = params; }
        @Override public Map<String, List<String>> getHeaders() { return Collections.emptyMap(); }
        @Override public Principal getUserPrincipal() { return null; }
        @Override public URI getRequestURI() { return URI.create("/wasm-rmi"); }
        @Override public boolean isUserInRole(String role) { return false; }
        @Override public Object getHttpSession() { return null; }
        @Override public Map<String, List<String>> getParameterMap() { return params; }
        @Override public String getQueryString() { return ""; }
    }

    static class FakeHandshakeResponse implements HandshakeResponse {
        @Override public Map<String, List<String>> getHeaders() { return Collections.emptyMap(); }
    }

    static class FakeEndpointConfig implements ServerEndpointConfig {
        private final Map<String, Object> userProperties = new java.util.HashMap<>();
        @Override public Class<?> getEndpointClass() { return Object.class; }
        @Override public String getPath() { return "/wasm-rmi"; }
        @Override public List<String> getSubprotocols() { return Collections.emptyList(); }
        @Override public List<jakarta.websocket.Extension> getExtensions() { return Collections.emptyList(); }
        @Override public Configurator getConfigurator() { return null; }
        @Override public List<Class<? extends jakarta.websocket.Encoder>> getEncoders() { return Collections.emptyList(); }
        @Override public List<Class<? extends jakarta.websocket.Decoder>> getDecoders() { return Collections.emptyList(); }
        @Override public Map<String, Object> getUserProperties() { return userProperties; }
    }

    private ServerEndpointConfig handshake(Map<String, List<String>> params) {
        ServerEndpointConfig config = new FakeEndpointConfig();
        new RmiEndpointConfigurator().modifyHandshake(config,
                new FakeHandshakeRequest(params), new FakeHandshakeResponse());
        return config;
    }

    @AfterEach
    public void cleanup() {
        System.clearProperty("zeroz.security.mode");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testValidDevCredentialsAuthenticateTheSession() {
        System.setProperty("zeroz.security.mode", "dev");
        ServerEndpointConfig config = handshake(Map.of(
                "user", List.of("admin"), "password", List.of("admin")));

        Principal principal = (Principal) config.getUserProperties().get(RmiEndpointConfigurator.PRINCIPAL_KEY);
        Set<String> roles = (Set<String>) config.getUserProperties().get(RmiEndpointConfigurator.ROLES_KEY);
        assertEquals("admin", principal.getName());
        assertEquals(Set.of("user", "admin"), roles);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testInvalidCredentialsStayAnonymous() {
        System.setProperty("zeroz.security.mode", "dev");
        ServerEndpointConfig config = handshake(Map.of(
                "user", List.of("demo"), "password", List.of("wrong")));

        assertNull(config.getUserProperties().get(RmiEndpointConfigurator.PRINCIPAL_KEY));
        Set<String> roles = (Set<String>) config.getUserProperties().get(RmiEndpointConfigurator.ROLES_KEY);
        assertTrue(roles.isEmpty());
    }

    @Test
    public void testDevAuthIgnoredOutsideDevMode() {
        // property cleared -> not dev mode
        ServerEndpointConfig config = handshake(Map.of(
                "user", List.of("admin"), "password", List.of("admin")));

        assertNull(config.getUserProperties().get(RmiEndpointConfigurator.PRINCIPAL_KEY),
                "Dev credentials must be inert unless zeroz.security.mode=dev");
    }
}
