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

import com.zeroz4j.server.RmiEndpointConfigurator;
import com.zeroz4j.server.WasmRmiServerEngine;
import io.vidocq.chappe.api.Headers;
import io.vidocq.chappe.api.Request;
import io.vidocq.chappe.api.Response;
import io.vidocq.chappe.api.WebSocket;
import io.vidocq.chappe.api.WebSocketHandler;
import io.vidocq.chappe.api.WebSocketUpgrade;
import jakarta.websocket.CloseReason;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.ByteBuffer;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The live connection at {@code /wasm-rmi}: turns a Chappe upgrade request into a call to the
 * engine's {@code onOpen}, {@code processIncomingBinaryPayload}, {@code onClose} and {@code onError}.
 *
 * <p>The handshake runs through {@link RmiEndpointConfigurator} exactly as under a Jakarta
 * WebSocket container, so the origin check, the host check and the authentication provider apply
 * unchanged. A refused handshake is accepted and then closed by the engine, as under Helidon.</p>
 *
 * <p><b>Two things differ from Helidon, both Chappe's.</b> A header the configurator wants on the 101
 * response (the browser-id cookie) cannot be written, because Chappe answers a fixed 101; the cookie
 * is set on the page instead (see {@link StaticContentHandler}). And a message larger than
 * {@code zeroz.ws.maxBinaryMessageBytes} is checked after Chappe has read it whole (Chappe reads up
 * to 64 MB), then the connection is closed with code 1009 without the engine seeing it.</p>
 */
final class WebSocketBridge {

    private static final Logger LOG = Logger.getLogger(WebSocketBridge.class.getName());

    /** The path of the live connection, the same as under Helidon. */
    static final String PATH = "/wasm-rmi";

    private final WasmRmiServerEngine engine;
    private final RmiEndpointConfigurator configurator = new RmiEndpointConfigurator();
    private final ServerEndpointConfig sharedConfig;
    private final IdleReaper reaper;

    WebSocketBridge(WasmRmiServerEngine engine, IdleReaper reaper) {
        this.engine = engine;
        this.reaper = reaper;
        this.sharedConfig = ServerEndpointConfig.Builder
                .create(WasmRmiServerEngine.class, PATH).configurator(configurator).build();
    }

    /** Answers the upgrade request for {@link #PATH}. */
    Response upgrade(Request request) {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Headers.Entry entry : request.headers()) {
            headers.computeIfAbsent(entry.name(), key -> new ArrayList<>()).add(entry.value());
        }
        Map<String, List<String>> parameters = new HashMap<>();
        request.queryParams().forEach((key, value) -> parameters.put(key, List.of(value)));
        URI uri = URI.create((request.isSecure() ? "wss" : "ws") + "://"
                + request.header("Host").orElse("localhost") + request.path()
                + (request.query() == null || request.query().isEmpty() ? "" : "?" + request.query()));

        // Each handshake gets a configuration of its own with its own user-property map, as a real
        // container gives it; the engine reads what the configurator stored there.
        Map<String, Object> handshakeProperties = new ConcurrentHashMap<>();
        ServerEndpointConfig perConnection = (ServerEndpointConfig) Proxy.newProxyInstance(
                WebSocketBridge.class.getClassLoader(), new Class<?>[] {ServerEndpointConfig.class},
                (proxy, method, args) -> "getUserProperties".equals(method.getName())
                        ? handshakeProperties : method.invoke(sharedConfig, args));

        HandshakeRequest handshakeRequest = new HandshakeRequest() {
            @Override public Map<String, List<String>> getHeaders() { return headers; }
            @Override public Principal getUserPrincipal() { return null; }
            @Override public URI getRequestURI() { return uri; }
            @Override public boolean isUserInRole(String role) { return false; }
            @Override public Object getHttpSession() { return null; }
            @Override public Map<String, List<String>> getParameterMap() { return parameters; }
            @Override public String getQueryString() { return request.query(); }
        };
        Map<String, List<String>> responseHeaders = new HashMap<>();
        HandshakeResponse handshakeResponse = () -> responseHeaders;

        configurator.modifyHandshake(perConnection, handshakeRequest, handshakeResponse);
        if (!responseHeaders.isEmpty()) {
            LOG.fine("[zeroz4j] Chappe writes a fixed 101 response; dropped handshake response headers "
                    + responseHeaders.keySet() + ". The browser-id cookie is set on the page instead.");
        }

        String id = UUID.randomUUID().toString();
        return new WebSocketUpgrade(new Connection(id, uri, parameters, perConnection), null, request);
    }

    /** The handler for one connection. */
    private final class Connection implements WebSocketHandler {
        private final String id;
        private final URI uri;
        private final Map<String, List<String>> parameters;
        private final ServerEndpointConfig config;
        private volatile ChappeSession chappe;

        Connection(String id, URI uri, Map<String, List<String>> parameters, ServerEndpointConfig config) {
            this.id = id;
            this.uri = uri;
            this.parameters = parameters;
            this.config = config;
        }

        @Override
        public void onOpen(WebSocket socket, Request request) {
            ChappeSession opened = new ChappeSession(socket, id, uri, parameters);
            chappe = opened;
            reaper.add(opened);
            engine.onOpen(opened.session(), config);
        }

        @Override
        public void onBinary(WebSocket socket, ByteBuffer data) {
            ChappeSession current = chappe;
            if (current == null) {
                return;
            }
            current.touch();
            int limit = current.maxBinaryBytes();
            if (data.remaining() > limit) {
                LOG.warning("[zeroz4j] A message of " + data.remaining() + " bytes is over the largest"
                        + " binary message accepted (" + limit + " bytes, zeroz.ws.maxBinaryMessageBytes)."
                        + " Closing connection " + id + " without an error response.");
                current.closeWith(ChappeSession.CLOSE_TOO_BIG, "Too big.");
                return;
            }
            engine.processIncomingBinaryPayload(data, current.session());
        }

        @Override
        public void onPing(WebSocket socket, ByteBuffer data) {
            ChappeSession current = chappe;
            if (current != null) {
                current.touch();
            }
        }

        @Override
        public void onClose(WebSocket socket, int code, String reason) {
            ChappeSession current = chappe;
            if (current == null) {
                return;
            }
            current.markClosed();
            reaper.remove(current);
            CloseReason closeReason;
            try {
                closeReason = new CloseReason(CloseReason.CloseCodes.getCloseCode(code), reason);
            } catch (RuntimeException unknownCode) {
                closeReason = null;
            }
            engine.onClose(current.session(), closeReason);
        }

        @Override
        public void onError(WebSocket socket, Throwable failure) {
            ChappeSession current = chappe;
            if (current != null) {
                try {
                    engine.onError(current.session(), failure);
                } catch (RuntimeException ex) {
                    LOG.log(Level.FINE, "[zeroz4j] The engine failed while handling a connection error", ex);
                }
            }
        }
    }
}
