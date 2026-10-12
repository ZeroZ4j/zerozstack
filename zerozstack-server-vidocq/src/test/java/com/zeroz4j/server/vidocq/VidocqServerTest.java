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

import com.zeroz4j.api.BinaryRegistry;
import com.zeroz4j.api.BinarySerializer;
import com.zeroz4j.api.FileUploadRpc;
import com.zeroz4j.api.GrowableBuffer;
import com.zeroz4j.api.ObjectMapper;
import com.zeroz4j.server.ServerRuntime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives a real {@link Zeroz4jVidocqServer} over real sockets: a Java HTTP client opens real
 * WebSocket connections and posts real uploads. Nothing is mocked between the client and the engine.
 */
class VidocqServerTest {

    private static final byte RPC_RESPONSE = 0x01;
    private static final byte AUTH = 0x03;
    private static final byte RPC_ERROR = 0x0F;

    private static Zeroz4jVidocqServer server;
    private static String host;
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeAll
    static void start() {
        System.setProperty("zeroz.security.mode", "dev");
        BinaryRegistry.init();
        server = Zeroz4jVidocqServer.start(0, "vidocq test");
        host = "localhost:" + server.port();
    }

    @AfterAll
    static void stop() {
        server.close();
        System.clearProperty("zeroz.security.mode");
    }

    @AfterEach
    void clearLimits() {
        System.clearProperty("zeroz.ws.maxBinaryMessageBytes");
        System.clearProperty("zeroz.upload.maxBytes");
    }

    // ------------------------------------------------------------------ (a) anonymous round trip

    @Test
    @DisplayName("an anonymous connection opens, is told it is anonymous, and makes a call")
    void anonymousRoundTrip() throws Exception {
        try (Client client = Client.connect("/wasm-rmi", "http://" + host, null)) {
            Auth auth = client.auth();
            assertFalse(auth.authenticated, "no credentials were sent");
            assertEquals("pong", client.call(PingService.class.getName(), "ping"));
            assertEquals("pong", client.call(PingService.class.getName(), "ping"),
                    "a second call on the same connection works");
        }
    }

    // ------------------------------------------------------------------ (b) dev login

    @Test
    @DisplayName("dev login is accepted and a wrong password is not")
    void devLogin() throws Exception {
        try (Client client = Client.connect("/wasm-rmi?user=demo&password=demo", "http://" + host, null)) {
            Auth auth = client.auth();
            assertTrue(auth.authenticated);
            assertEquals("demo", auth.user);
            assertEquals("demo", client.call(WhoService.class.getName(), "whoAmI"),
                    "the secured service sees who signed in");
        }

        try (Client client = Client.connect("/wasm-rmi?user=demo&password=wrong", "http://" + host, null)) {
            Auth auth = client.auth();
            assertFalse(auth.authenticated, "a wrong password leaves the connection unauthenticated");
            Object answer = client.call(WhoService.class.getName(), "whoAmI");
            assertTrue(answer instanceof RefusedCall, "a secured call is refused, got " + answer);
        }
    }

    // ------------------------------------------------------------------ (c) foreign origin

    @Test
    @DisplayName("a handshake from a page on another origin is closed before it is told anything")
    void foreignOriginIsRefused() throws Exception {
        try (Client client = Client.connect("/wasm-rmi", "http://evil.example", null)) {
            assertEquals(1008, client.closeCode(), "closed with 'violated policy'");
            assertNull(client.frames.poll(300, TimeUnit.MILLISECONDS), "no AUTH frame was sent");
        }
    }

    // ------------------------------------------------------------------ (d) message size

    @Test
    @DisplayName("a message over zeroz.ws.maxBinaryMessageBytes closes that connection and no other")
    void oversizedMessageClosesTheConnection() throws Exception {
        System.setProperty("zeroz.ws.maxBinaryMessageBytes", "1024");
        ServerRuntime runtime = server.container().select(ServerRuntime.class);
        try (Client bystander = Client.connect("/wasm-rmi", "http://" + host, null);
             Client sender = Client.connect("/wasm-rmi", "http://" + host, null)) {
            bystander.auth();
            sender.auth();
            int before = runtime.sessionCount();

            sender.socket.sendBinary(ByteBuffer.wrap(new byte[2048]), true).get(5, TimeUnit.SECONDS);

            assertEquals(1009, sender.closeCode(), "closed with 'message too big'");
            assertEquals("pong", bystander.call(PingService.class.getName(), "ping"),
                    "another connection is not affected");
            awaitSessions(runtime, before - 1);
        }
    }

    // ------------------------------------------------------------------ (e) upload

    @Test
    @DisplayName("an upload is accepted, and one over zeroz.upload.maxBytes is refused")
    void upload() throws Exception {
        String cookie = browserCookie();

        // Accepted: the pass is minted over the live connection, then the bytes go over HTTP.
        try (Client client = Client.connect("/wasm-rmi", "http://" + host, cookie)) {
            client.auth();
            Object pass = client.call(FileUploadRpc.class.getName(), "requestUploadPass",
                    "note.txt", "text/plain", 5L);
            assertTrue(pass instanceof String, "a pass was issued, got " + pass);
            HttpResponse<String> response = post((String) pass, cookie, new byte[] {'h', 'e', 'l', 'l', 'o'});
            assertEquals(200, response.statusCode(), response.body());
            assertEquals("Kept note.txt", response.body());
            assertTrue(RecordingUploadHandler.RECEIVED.contains("note.txt:hello"),
                    "the application's handler received the file, got " + RecordingUploadHandler.RECEIVED);
        }

        // Refused: the pass was for 10 bytes, the body is 2000 and the limit is 1024.
        System.setProperty("zeroz.upload.maxBytes", "1024");
        try (Client client = Client.connect("/wasm-rmi", "http://" + host, cookie)) {
            client.auth();
            Object pass = client.call(FileUploadRpc.class.getName(), "requestUploadPass",
                    "big.bin", "application/octet-stream", 10L);
            assertTrue(pass instanceof String, "a pass was issued, got " + pass);
            HttpResponse<String> response = post((String) pass, cookie, new byte[2000]);
            assertEquals(413, response.statusCode(), response.body());
            assertTrue(response.body().contains("too big"), response.body());
            assertFalse(RecordingUploadHandler.RECEIVED.stream().anyMatch(s -> s.startsWith("big.bin")));
        }

        // Refused: no pass at all.
        HttpResponse<String> noPass = post("not-a-pass", cookie, new byte[] {1});
        assertTrue(noPass.statusCode() >= 400, "a made-up pass is refused, got " + noPass.statusCode());
    }

    // ------------------------------------------------------------------ static content

    @Test
    @DisplayName("the page carries the browser-id cookie; assets and client routes are served; unknown files are 404")
    void staticContent() throws Exception {
        HttpResponse<String> page = get("/");
        assertEquals(200, page.statusCode());
        assertTrue(page.headers().firstValue("Content-Type").orElse("").startsWith("text/html"));
        assertTrue(page.headers().firstValue("Set-Cookie").isPresent(), "the page sets the browser-id cookie");
        assertTrue(page.body().contains("shell"));
        assertTrue(page.body().contains("<base"), "the shell carries a base href");

        HttpResponse<String> route = get("/some/client/route");
        assertEquals(200, route.statusCode(), "a client route gets the shell");
        assertTrue(route.body().contains("shell"));

        HttpResponse<String> asset = get("/hello.js");
        assertEquals(200, asset.statusCode());
        assertTrue(asset.headers().firstValue("Content-Type").orElse("").contains("javascript"));
        assertTrue(asset.headers().firstValue("Set-Cookie").isEmpty(), "only the page sets the cookie");

        assertEquals(404, get("/missing.js").statusCode());
        assertEquals(404, get("/../secret.txt").statusCode());
    }

    // ------------------------------------------------------------------ application REST resources

    @Test
    @DisplayName("a POST with a query parameter and a text body reaches the resource, which answers with a status")
    void restPost() throws Exception {
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(
                        URI.create("http://" + host + "/rest-test/client-log?level=warn"))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("boom")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(204, response.statusCode());
        assertTrue(RestTestResource.LOGGED.contains("warn:boom"), RestTestResource.LOGGED.toString());
    }

    @Test
    @DisplayName("a GET with a path parameter returns the string with its declared content type")
    void restGetPathParam() throws Exception {
        HttpResponse<String> response = get("/rest-test/echo/anna");
        assertEquals(200, response.statusCode());
        assertEquals("echo anna", response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/plain"));
    }

    @Test
    @DisplayName("a resource can inject an application-scoped bean")
    void restInjection() throws Exception {
        assertEquals("hello bob", get("/rest-test/greet/bob").body());
        assertEquals("hello bob", get("/rest-test/greet/bob").body(), "and again on the next request");
    }

    @Test
    @DisplayName("@Context HttpHeaders and UriInfo are filled in")
    void restContext() throws Exception {
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(
                        URI.create("http://" + host + "/rest-test/context?q=7"))
                .header("X-Probe", "yes").build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("yes|rest-test/context|7", response.body());
    }

    @Test
    @DisplayName("a resource's own 404 is returned as is; a wrong method is 405; unclaimed paths still get the shell")
    void restFallthrough() throws Exception {
        HttpResponse<String> own = get("/rest-test/missing");
        assertEquals(404, own.statusCode());
        assertEquals("resource says no", own.body(), "not the shell page");

        HttpResponse<String> wrong = HTTP.send(HttpRequest.newBuilder(
                        URI.create("http://" + host + "/rest-test/echo/anna"))
                .POST(HttpRequest.BodyPublishers.ofString("x")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(405, wrong.statusCode());

        HttpResponse<String> unclaimed = get("/rest-test/nothing/here");
        assertEquals(200, unclaimed.statusCode());
        assertTrue(unclaimed.body().contains("shell"), "no resource claims it, so the shell answers");
        assertEquals(200, get("/hello.js").statusCode(), "static files are still served");
        try (Client client = Client.connect("/wasm-rmi", "http://" + host, null)) {
            assertFalse(client.auth().authenticated, "the live connection is still the framework's");
        }
    }

    // ------------------------------------------------------------------ idle timeout (no sockets)

    @Test
    @DisplayName("a connection silent past its idle limit is closed; one with no limit is left alone")
    void idleReaper() {
        try (IdleReaper reaper = new IdleReaper()) {
            FakeSocket quietSocket = new FakeSocket();
            ChappeSession quiet = new ChappeSession(quietSocket.proxy(), "quiet", URI.create("ws://x/"), java.util.Map.of());
            quiet.session().setMaxIdleTimeout(60_000L);
            FakeSocket unlimitedSocket = new FakeSocket();
            ChappeSession unlimited = new ChappeSession(unlimitedSocket.proxy(), "unlimited", URI.create("ws://x/"), java.util.Map.of());
            reaper.add(quiet);
            reaper.add(unlimited);

            reaper.sweep(System.currentTimeMillis() + 30_000L);
            assertEquals(0, quietSocket.closedWith, "30 seconds is inside the limit");

            reaper.sweep(System.currentTimeMillis() + 61_000L);
            assertEquals(1001, quietSocket.closedWith, "61 seconds is past it");
            assertEquals(0, unlimitedSocket.closedWith, "no limit, never reaped");
        }
    }

    // ================================================================== helpers

    private static void awaitSessions(ServerRuntime runtime, int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (runtime.sessionCount() != expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(25);
        }
        assertEquals(expected, runtime.sessionCount(),
                "the engine was told the connection is gone and forgot it");
    }

    private static HttpResponse<String> get(String path) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://" + host + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** What a browser holds after loading the page: the signed browser-id cookie. */
    private static String browserCookie() throws Exception {
        String setCookie = get("/").headers().firstValue("Set-Cookie").orElseThrow();
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private static HttpResponse<String> post(String pass, String cookie, byte[] body) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://" + host + "/zeroz4j-upload"))
                        .header("Origin", "http://" + host)
                        .header("Cookie", cookie)
                        .header("X-Zeroz4j-Upload-Pass", pass)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** What the AUTH frame said. */
    private record Auth(boolean authenticated, String user) { }

    /** An RMI call the server answered with an error. */
    private record RefusedCall(String message) { }

    /** A WebSocket client speaking the framework's binary protocol. */
    private static final class Client implements AutoCloseable, WebSocket.Listener {
        final LinkedBlockingQueue<byte[]> frames = new LinkedBlockingQueue<>();
        final CompletableFuture<Integer> closed = new CompletableFuture<>();
        private final ByteArrayOutputStream partial = new ByteArrayOutputStream();
        WebSocket socket;
        private int nextId = 1;

        static Client connect(String path, String origin, String cookie) throws Exception {
            Client client = new Client();
            WebSocket.Builder builder = HTTP.newWebSocketBuilder().header("Origin", origin);
            if (cookie != null) {
                builder.header("Cookie", cookie);
            }
            client.socket = builder.buildAsync(URI.create("ws://" + host + path), client).get(10, TimeUnit.SECONDS);
            return client;
        }

        /** Reads the AUTH frame the server sends first. */
        Auth auth() throws Exception {
            byte[] frame = frames.poll(10, TimeUnit.SECONDS);
            assertNotNull(frame, "the server sent an AUTH frame");
            ByteBuffer buffer = ByteBuffer.wrap(frame);
            assertEquals(0, buffer.getInt());
            assertEquals(AUTH, buffer.get());
            buffer.get(); // protocol version
            boolean authenticated = buffer.get() == 1;
            return new Auth(authenticated, BinarySerializer.readString(buffer));
        }

        /** Calls a service method; returns its value, or a {@link RefusedCall}. */
        Object call(String service, String method, Object... arguments) throws Exception {
            int id = nextId++;
            GrowableBuffer request = new GrowableBuffer();
            request.putInt(id);
            BinarySerializer.writeString(request, service);
            BinarySerializer.writeString(request, method);
            request.putInt(arguments.length);
            for (Object argument : arguments) {
                BinarySerializer.writeValue(request, argument, MAPPER);
            }
            socket.sendBinary(ByteBuffer.wrap(request.toByteArray()), true).get(10, TimeUnit.SECONDS);
            while (true) {
                byte[] answer = frames.poll(10, TimeUnit.SECONDS);
                assertNotNull(answer, "no answer to " + method);
                ByteBuffer buffer = ByteBuffer.wrap(answer);
                if (buffer.getInt() != id) {
                    continue;
                }
                byte opcode = buffer.get();
                if (opcode == RPC_ERROR) {
                    return new RefusedCall(BinarySerializer.readString(buffer));
                }
                assertEquals(RPC_RESPONSE, opcode);
                return BinarySerializer.readValue(buffer, MAPPER);
            }
        }

        int closeCode() throws Exception {
            return closed.get(10, TimeUnit.SECONDS);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] chunk = new byte[data.remaining()];
            data.get(chunk);
            partial.writeBytes(chunk);
            if (last) {
                frames.add(partial.toByteArray());
                partial.reset();
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closed.complete(statusCode);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            closed.complete(-1);
        }

        @Override
        public void close() {
            socket.abort();
        }
    }

    /** A Chappe WebSocket that only remembers how it was closed. */
    private static final class FakeSocket {
        volatile int closedWith;

        io.vidocq.chappe.api.WebSocket proxy() {
            return (io.vidocq.chappe.api.WebSocket) java.lang.reflect.Proxy.newProxyInstance(
                    VidocqServerTest.class.getClassLoader(),
                    new Class<?>[] {io.vidocq.chappe.api.WebSocket.class},
                    (proxy, method, args) -> {
                        if ("close".equals(method.getName()) && args != null && args.length == 2) {
                            closedWith = (Integer) args[0];
                        }
                        return method.getReturnType() == boolean.class ? (Object) true : null;
                    });
        }
    }
}
