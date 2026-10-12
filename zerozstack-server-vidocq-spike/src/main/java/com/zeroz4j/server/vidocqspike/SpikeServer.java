package com.zeroz4j.server.vidocqspike;

import com.zeroz4j.api.BinaryRegistry;
import com.zeroz4j.server.RmiEndpointConfigurator;
import com.zeroz4j.server.StaticContent;
import com.zeroz4j.server.WasmRmiServerEngine;
import io.vidocq.chappe.api.Body;
import io.vidocq.chappe.api.Handler;
import io.vidocq.chappe.api.Headers;
import io.vidocq.chappe.api.Request;
import io.vidocq.chappe.api.Response;
import io.vidocq.chappe.api.Server;
import io.vidocq.chappe.api.StatusCode;
import io.vidocq.chappe.api.WebSocket;
import io.vidocq.chappe.api.WebSocketHandler;
import io.vidocq.chappe.api.WebSocketUpgrade;
import io.vidocq.vauban.core.container.VaubanContainer;
import jakarta.websocket.CloseReason;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;

import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
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

/**
 * SPIKE, throwaway. Runs the unmodified ZeroZ Stack engine on Vidocq 0.3.0: Chappe for HTTP and
 * raw WebSocket, Vauban for CDI. No Helidon, Weld or Tyrus on the classpath.
 *
 * <p>The adapter fakes just enough of jakarta.websocket (Session, HandshakeRequest,
 * HandshakeResponse, EndpointConfig) over a Chappe WebSocket to drive WasmRmiServerEngine and
 * RmiEndpointConfigurator as they are.</p>
 */
public final class SpikeServer {

    public static void main(String[] args) throws Exception {
        long t0 = System.nanoTime();
        int port = Integer.parseInt(System.getProperty("zeroz.port", "8194"));

        BinaryRegistry.init();

        VaubanContainer container = VaubanContainer.builder()
                .scanClasspath()
                .scanBeanArchivesFromClasspath()
                .build();
        long tCdi = System.nanoTime();

        CdiProbe.run(container);

        WasmRmiServerEngine engine = container.select(WasmRmiServerEngine.class);
        RmiEndpointConfigurator configurator = new RmiEndpointConfigurator();
        ServerEndpointConfig endpointConfig = ServerEndpointConfig.Builder
                .create(WasmRmiServerEngine.class, "/wasm-rmi").configurator(configurator).build();

        Handler handler = request -> {
            if ("/wasm-rmi".equals(request.path())) {
                return upgrade(request, engine, configurator, endpointConfig);
            }
            return serveStatic(request);
        };

        Server server = Server.builder().port(port).handler(handler).build();
        server.start();
        long tUp = System.nanoTime();
        System.out.println("[spike] Vidocq server listening on http://localhost:" + server.port()
                + "/ pid=" + ProcessHandle.current().pid()
                + " cdi=" + (tCdi - t0) / 1_000_000 + "ms total=" + (tUp - t0) / 1_000_000 + "ms"
                + " jvmUptime=" + java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime() + "ms");
        Thread.currentThread().join();
    }

    // ------------------------------------------------------------------ static content

    private static Response serveStatic(Request request) throws Exception {
        String path = request.path();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        String resolved = StaticContent.resolve(path);
        if (resolved == null) {
            return Response.of(StatusCode.NOT_FOUND);
        }
        byte[] bytes;
        if (StaticContent.SHELL.equals(resolved)) {
            bytes = StaticContent.shellBytes("/");
        } else {
            try (InputStream in = StaticContent.open(resolved)) {
                bytes = in == null ? null : in.readAllBytes();
            }
        }
        if (bytes == null) {
            return Response.of(StatusCode.NOT_FOUND);
        }
        String contentType = StaticContent.contentType(resolved);
        Response.Builder response = Response.builder().status(StatusCode.OK)
                .header("Content-Type", contentType).body(Body.of(bytes));
        // The signed browser-id cookie. Chappe 0.3.0 cannot put a header on the 101 upgrade
        // response, so the page response is the only place it can be set.
        String cookie = StaticContent.clientIdCookieFor(contentType,
                request.headers().firstOrNull("Cookie"), request.scheme());
        if (cookie != null) {
            response.header("Set-Cookie", cookie);
        }
        return response.build();
    }

    // ------------------------------------------------------------------ handshake

    private static Response upgrade(Request request, WasmRmiServerEngine engine,
                                    RmiEndpointConfigurator configurator,
                                    ServerEndpointConfig sharedConfig) {
        // Proof that a handshake can be refused with a plain HTTP answer before any upgrade.
        if (request.queryParam("spikeRefuse").isPresent()) {
            return Response.builder().status(StatusCode.FORBIDDEN).body("refused by spike").build();
        }

        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Headers.Entry entry : request.headers()) {
            headers.computeIfAbsent(entry.name(), k -> new ArrayList<>()).add(entry.value());
        }
        Map<String, List<String>> params = new HashMap<>();
        request.queryParams().forEach((k, v) -> params.put(k, List.of(v)));
        URI uri = URI.create((request.isSecure() ? "wss" : "ws") + "://"
                + request.header("Host").orElse("localhost") + request.path()
                + (request.query() == null || request.query().isEmpty() ? "" : "?" + request.query()));

        // Per-connection user properties, as a real container gives each handshake.
        Map<String, Object> userProperties = new ConcurrentHashMap<>();
        ServerEndpointConfig perConnection = (ServerEndpointConfig) Proxy.newProxyInstance(
                SpikeServer.class.getClassLoader(), new Class<?>[] {ServerEndpointConfig.class},
                (proxy, method, a) -> "getUserProperties".equals(method.getName())
                        ? userProperties : method.invoke(sharedConfig, a));

        HandshakeRequest handshakeRequest = new HandshakeRequest() {
            @Override public Map<String, List<String>> getHeaders() { return headers; }
            @Override public Principal getUserPrincipal() { return null; }
            @Override public URI getRequestURI() { return uri; }
            @Override public boolean isUserInRole(String role) { return false; }
            @Override public Object getHttpSession() { return null; }
            @Override public Map<String, List<String>> getParameterMap() { return params; }
            @Override public String getQueryString() { return request.query(); }
        };
        Map<String, List<String>> responseHeaders = new HashMap<>();
        HandshakeResponse handshakeResponse = () -> responseHeaders;

        configurator.modifyHandshake(perConnection, handshakeRequest, handshakeResponse);
        if (!responseHeaders.isEmpty()) {
            System.out.println("[spike] DROPPED handshake response headers (Chappe 0.3.0 writes a fixed 101): "
                    + responseHeaders.keySet());
        }

        String id = UUID.randomUUID().toString();
        return new WebSocketUpgrade(new WebSocketHandler() {
            private volatile Session session;

            @Override
            public void onOpen(WebSocket ws, Request req) {
                session = sessionOver(ws, id, uri, params);
                engine.onOpen(session, perConnection);
            }

            @Override
            public void onBinary(WebSocket ws, ByteBuffer data) {
                engine.processIncomingBinaryPayload(data, session);
            }

            @Override
            public void onClose(WebSocket ws, int code, String reason) {
                CloseReason closeReason;
                try {
                    closeReason = new CloseReason(CloseReason.CloseCodes.getCloseCode(code), reason);
                } catch (RuntimeException unknownCode) {
                    closeReason = null;
                }
                if (session != null) {
                    engine.onClose(session, closeReason);
                }
            }

            @Override
            public void onError(WebSocket ws, Throwable failure) {
                if (session != null) {
                    engine.onError(session, failure);
                }
            }
        }, null, request);
    }

    // ------------------------------------------------------------------ Session over a Chappe WebSocket

    private static Session sessionOver(WebSocket ws, String id, URI uri, Map<String, List<String>> params) {
        Map<String, Object> properties = new ConcurrentHashMap<>();
        RemoteEndpoint.Basic remote = (RemoteEndpoint.Basic) Proxy.newProxyInstance(
                SpikeServer.class.getClassLoader(), new Class<?>[] {RemoteEndpoint.Basic.class},
                (proxy, method, a) -> {
                    if ("sendBinary".equals(method.getName()) && a.length == 1) {
                        ws.sendBinary((ByteBuffer) a[0]);
                        return null;
                    }
                    return defaultFor(proxy, method, a);
                });
        InvocationHandler handler = (proxy, method, a) -> {
            switch (method.getName()) {
                case "getId": return id;
                case "getUserProperties": return properties;
                case "isOpen": return ws.isOpen();
                case "getBasicRemote": return remote;
                case "getRequestParameterMap": return params;
                case "getRequestURI": return uri;
                case "getUserPrincipal": return properties.get(RmiEndpointConfigurator.PRINCIPAL_KEY);
                case "close":
                    if (a != null && a.length == 1 && a[0] instanceof CloseReason reason) {
                        ws.close(reason.getCloseCode().getCode(), reason.getReasonPhrase());
                    } else {
                        ws.close();
                    }
                    return null;
                default: return defaultFor(proxy, method, a);
            }
        };
        return (Session) Proxy.newProxyInstance(SpikeServer.class.getClassLoader(),
                new Class<?>[] {Session.class}, handler);
    }

    /** equals/hashCode/toString by identity; everything else answers zero, false or null. */
    private static Object defaultFor(Object proxy, java.lang.reflect.Method method, Object[] a) {
        switch (method.getName()) {
            case "equals": return proxy == a[0];
            case "hashCode": return System.identityHashCode(proxy);
            case "toString": return "SpikeProxy@" + System.identityHashCode(proxy);
            default:
        }
        Class<?> type = method.getReturnType();
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }

    private SpikeServer() { }
}
