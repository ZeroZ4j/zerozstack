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

import com.zeroz4j.api.BinarySerializer;
import com.zeroz4j.api.GrowableBuffer;
import com.zeroz4j.api.ObjectMapper;
import com.zeroz4j.api.RmiService;
import com.zeroz4j.api.SyncFrameTypes;
import jakarta.enterprise.context.ApplicationScoped;
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
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Starts the dev server, loads the shell over HTTP, and makes one call over a real WebSocket. */
class Zeroz4jDevServerTest {

    /** The service the test application offers. */
    @RmiService
    public interface Greeter {
        String greet(String name);
    }

    /** Its implementation, a bean like any other. */
    @ApplicationScoped
    public static class GreeterImpl implements Greeter {
        @Override
        public String greet(String name) {
            return "Hello, " + name;
        }
    }

    @Test
    void runsAnApplicationWithoutScanning() throws Exception {
        try (Zeroz4jDevServer server = Zeroz4jDevServer.builder()
                .port(0).contextPath("/app").beans(GreeterImpl.class).start()) {

            HttpResponse<String> shell = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(server.baseUrl())).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, shell.statusCode());
            assertTrue(shell.body().contains("app-root"));
            assertTrue(shell.body().contains("<base href=\"/app/\""), shell.body());

            int messageId = 424242;
            ObjectMapper mapper = new ObjectMapper();
            GrowableBuffer call = new GrowableBuffer();
            call.putInt(messageId);
            BinarySerializer.writeString(call, Greeter.class.getName());
            BinarySerializer.writeString(call, "greet");
            call.putInt(1);
            BinarySerializer.writeValue(call, "Anna", mapper);

            CompletableFuture<ByteBuffer> answer = new CompletableFuture<>();
            ByteArrayOutputStream partial = new ByteArrayOutputStream();
            WebSocket socket = HttpClient.newHttpClient().newWebSocketBuilder()
                    .buildAsync(URI.create(server.webSocketUrl()), new WebSocket.Listener() {
                        @Override
                        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data,
                                                           boolean last) {
                            byte[] chunk = new byte[data.remaining()];
                            data.get(chunk);
                            partial.write(chunk, 0, chunk.length);
                            if (last) {
                                ByteBuffer frame = ByteBuffer.wrap(partial.toByteArray());
                                partial.reset();
                                if (frame.remaining() > 4 && frame.getInt(0) == messageId) {
                                    answer.complete(frame);
                                }
                            }
                            webSocket.request(1);
                            return null;
                        }
                    }).get(10, TimeUnit.SECONDS);
            socket.sendBinary(ByteBuffer.wrap(call.toByteArray()), true).get(10, TimeUnit.SECONDS);

            ByteBuffer frame = answer.get(10, TimeUnit.SECONDS);
            frame.getInt();
            assertEquals(SyncFrameTypes.RPC_RESPONSE, frame.get());
            assertEquals("Hello, Anna", BinarySerializer.readValue(frame, mapper));
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
        }
    }
}
