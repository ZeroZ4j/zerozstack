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
import io.vidocq.chappe.api.WebSocket;
import jakarta.websocket.CloseReason;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * One connection, seen through the part of {@code jakarta.websocket.Session} the engine uses.
 *
 * <p>The engine in {@code zerozstack-server-core} is written against the Jakarta WebSocket API.
 * Chappe has its own, smaller one. This class wraps a Chappe {@link WebSocket} in a
 * {@code Session} so the engine runs unchanged: identity, the user-property map, the write side,
 * {@code close}, and the two limits the engine sets on a connection when it opens.</p>
 *
 * <p><b>The limits live here.</b> Chappe has no per-connection message-size or idle-timeout setting,
 * so the engine's calls to {@code setMaxBinaryMessageBufferSize} and {@code setMaxIdleTimeout} are
 * recorded, and {@link WebSocketBridge} and {@link IdleReaper} enforce them. The engine reads the
 * settings, so the same properties apply here as under Helidon.</p>
 *
 * <p>Every method of {@code Session} the engine does not call answers zero, false or null.</p>
 */
final class ChappeSession implements InvocationHandler {

    private static final Logger LOG = Logger.getLogger(ChappeSession.class.getName());

    /**
     * What applies until the engine sets a limit: the framework's own default, 4 MB, so a
     * connection is never unbounded in the moment between opening and the engine's first call.
     */
    static final int FALLBACK_MAX_BINARY_BYTES = 4 * 1024 * 1024;

    /** WebSocket close code 1009, what a message past the limit is closed with (Tyrus does the same). */
    static final int CLOSE_TOO_BIG = 1009;

    /** WebSocket close code 1001, what an idle connection is closed with. */
    static final int CLOSE_GOING_AWAY = 1001;

    private final WebSocket socket;
    private final String id;
    private final URI uri;
    private final Map<String, List<String>> parameters;
    private final Map<String, Object> userProperties = new ConcurrentHashMap<>();
    private final AtomicBoolean closing = new AtomicBoolean();
    private final Session session;
    private final RemoteEndpoint.Basic remote;

    private volatile int maxBinaryBytes = FALLBACK_MAX_BINARY_BYTES;
    private volatile long maxIdleMillis;
    private volatile long lastActivityMillis = System.currentTimeMillis();

    ChappeSession(WebSocket socket, String id, URI uri, Map<String, List<String>> parameters) {
        this.socket = socket;
        this.id = id;
        this.uri = uri;
        this.parameters = parameters;
        ClassLoader loader = ChappeSession.class.getClassLoader();
        this.remote = (RemoteEndpoint.Basic) Proxy.newProxyInstance(loader,
                new Class<?>[] {RemoteEndpoint.Basic.class}, this::invokeRemote);
        this.session = (Session) Proxy.newProxyInstance(loader, new Class<?>[] {Session.class}, this);
    }

    /** @return the {@code Session} to hand to the engine */
    Session session() {
        return session;
    }

    int maxBinaryBytes() {
        return maxBinaryBytes;
    }

    long maxIdleMillis() {
        return maxIdleMillis;
    }

    long lastActivityMillis() {
        return lastActivityMillis;
    }

    /** Records that something moved on this connection, for the idle timeout. */
    void touch() {
        lastActivityMillis = System.currentTimeMillis();
    }

    boolean isOpen() {
        return !closing.get() && socket.isOpen();
    }

    /**
     * Closes the connection once. A second call, from another thread or after the peer has gone,
     * does nothing.
     */
    void closeWith(int code, String reason) {
        if (!closing.compareAndSet(false, true)) {
            return;
        }
        try {
            socket.close(code, reason == null ? "" : reason);
        } catch (IOException | RuntimeException ex) {
            LOG.fine("[zeroz4j] Closing connection " + id + " failed: " + ex.getMessage());
        }
    }

    /** Called when the peer's close arrives, so a later {@link #closeWith} stays quiet. */
    void markClosed() {
        closing.set(true);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        switch (method.getName()) {
            case "getId": return id;
            case "getUserProperties": return userProperties;
            case "isOpen": return isOpen();
            case "getBasicRemote": return remote;
            case "getRequestParameterMap": return parameters;
            case "getRequestURI": return uri;
            case "getUserPrincipal": return userProperties.get(RmiEndpointConfigurator.PRINCIPAL_KEY);
            case "setMaxBinaryMessageBufferSize":
                maxBinaryBytes = (Integer) args[0];
                return null;
            case "getMaxBinaryMessageBufferSize": return maxBinaryBytes;
            case "setMaxIdleTimeout":
                maxIdleMillis = (Long) args[0];
                return null;
            case "getMaxIdleTimeout": return maxIdleMillis;
            case "close":
                if (args != null && args.length == 1 && args[0] instanceof CloseReason reason) {
                    closeWith(reason.getCloseCode().getCode(), reason.getReasonPhrase());
                } else {
                    closeWith(1000, "");
                }
                return null;
            default: return defaultAnswer(proxy, method, args, "ChappeSession");
        }
    }

    private Object invokeRemote(Object proxy, Method method, Object[] args) throws IOException {
        if ("sendBinary".equals(method.getName()) && args != null && args.length == 1) {
            touch();
            socket.sendBinary((ByteBuffer) args[0]);
            return null;
        }
        return defaultAnswer(proxy, method, args, "ChappeRemote");
    }

    /** equals, hashCode and toString by identity; everything else answers zero, false or null. */
    private Object defaultAnswer(Object proxy, Method method, Object[] args, String label) {
        switch (method.getName()) {
            case "equals": return proxy == args[0];
            case "hashCode": return System.identityHashCode(proxy);
            case "toString": return label + "[" + id + "]";
            default:
        }
        Class<?> type = method.getReturnType();
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }
}
