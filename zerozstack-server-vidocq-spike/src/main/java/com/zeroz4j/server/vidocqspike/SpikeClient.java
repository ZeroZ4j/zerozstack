package com.zeroz4j.server.vidocqspike;

import com.zeroz4j.api.BinaryRegistry;
import com.zeroz4j.api.BinarySerializer;
import com.zeroz4j.api.GrowableBuffer;
import com.zeroz4j.api.ObjectMapper;
import com.zeroz4j.example.api.TaskService;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** SPIKE. A real socket client: fetches the page, then makes one RMI call over the WebSocket. */
public final class SpikeClient {

    public static void main(String[] args) throws Exception {
        String host = "localhost:" + System.getProperty("zeroz.port", "8194");
        HttpClient http = HttpClient.newHttpClient();
        BinaryRegistry.init();

        // 1. the page and the bundle
        HttpResponse<String> page = http.send(HttpRequest.newBuilder(URI.create("http://" + host + "/")).build(),
                HttpResponse.BodyHandlers.ofString());
        System.out.println("[client] GET / -> " + page.statusCode() + " " + page.headers().firstValue("Content-Type").orElse("?")
                + " bytes=" + page.body().length() + " set-cookie=" + page.headers().firstValue("Set-Cookie").isPresent());
        java.util.regex.Matcher script = java.util.regex.Pattern.compile("src=\"([^\"]+[.]js)\"").matcher(page.body());
        while (script.find()) {
            HttpResponse<byte[]> js = http.send(HttpRequest.newBuilder(URI.create("http://" + host + "/").resolve(script.group(1))).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            System.out.println("[client] GET " + script.group(1) + " -> " + js.statusCode() + " bytes=" + js.body().length);
        }
        HttpResponse<String> deep = http.send(HttpRequest.newBuilder(URI.create("http://" + host + "/some/client/route")).build(),
                HttpResponse.BodyHandlers.ofString());
        System.out.println("[client] GET /some/client/route (shell fallback) -> " + deep.statusCode() + " bytes=" + deep.body().length());

        // 2. the RMI round trip
        Listener listener = new Listener();
        WebSocket ws = http.newWebSocketBuilder().header("Origin", "http://" + host)
                .buildAsync(URI.create("ws://" + host + "/wasm-rmi"), listener).get(10, TimeUnit.SECONDS);
        byte[] first = listener.frames.poll(10, TimeUnit.SECONDS);
        System.out.println("[client] first frame from server: " + describe(first));

        ObjectMapper mapper = new ObjectMapper();
        for (int messageId = 1; messageId <= 2; messageId++) {
            GrowableBuffer request = new GrowableBuffer();
            request.putInt(messageId);
            BinarySerializer.writeString(request, TaskService.class.getName());
            BinarySerializer.writeString(request, "getTasks");
            request.putInt(0);
            long sent = System.nanoTime();
            ws.sendBinary(ByteBuffer.wrap(request.toByteArray()), true).get(10, TimeUnit.SECONDS);
            byte[] answer;
            while (true) {
                answer = listener.frames.poll(20, TimeUnit.SECONDS);
                if (answer == null) throw new IllegalStateException("no answer to RMI call " + messageId);
                if (answer.length >= 5 && ByteBuffer.wrap(answer).getInt() == messageId) break;
                System.out.println("[client] other frame: " + describe(answer));
            }
            ByteBuffer buffer = ByteBuffer.wrap(answer);
            buffer.getInt();
            byte opcode = buffer.get();
            if (opcode == 0x0F) {
                System.out.println("[client] RMI REFUSED: " + BinarySerializer.readString(buffer));
            } else {
                Object value = BinarySerializer.readValue(buffer, mapper);
                StringBuilder shown = new StringBuilder();
                if (value instanceof List<?> list) {
                    for (Object task : list) {
                        shown.append(task.getClass().getSimpleName()).append('(')
                                .append(task.getClass().getMethod("getTitle").invoke(task)).append(") ");
                    }
                }
                System.out.println("[client] RMI getTasks #" + messageId + " opcode=0x0" + opcode + " in "
                        + (System.nanoTime() - sent) / 1_000_000 + "ms -> " + shown);
            }
        }
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS);

        // 3. a handshake from a foreign page: the engine's own origin check must close it
        Listener foreign = new Listener();
        try {
            http.newWebSocketBuilder().header("Origin", "http://evil.example")
                    .buildAsync(URI.create("ws://" + host + "/wasm-rmi"), foreign).get(10, TimeUnit.SECONDS);
            System.out.println("[client] foreign origin: upgraded, then closed with " + foreign.closed.get(10, TimeUnit.SECONDS));
        } catch (Exception e) {
            System.out.println("[client] foreign origin: handshake failed " + e);
        }

        // 4. a handshake refused with plain HTTP before any upgrade
        try {
            http.newWebSocketBuilder().buildAsync(URI.create("ws://" + host + "/wasm-rmi?spikeRefuse=1"), new Listener())
                    .get(10, TimeUnit.SECONDS);
            System.out.println("[client] spikeRefuse: UNEXPECTEDLY upgraded");
        } catch (Exception e) {
            System.out.println("[client] spikeRefuse: handshake refused -> " + e.getCause());
        }
        System.exit(0);
    }

    private static String describe(byte[] frame) {
        if (frame == null) return "none";
        return frame.length + " bytes, opcode byte[4]=0x" + (frame.length > 4 ? Integer.toHexString(frame[4] & 0xff) : "?");
    }

    static final class Listener implements WebSocket.Listener {
        final LinkedBlockingQueue<byte[]> frames = new LinkedBlockingQueue<>();
        final CompletableFuture<String> closed = new CompletableFuture<>();
        private final ByteArrayOutputStream partial = new ByteArrayOutputStream();

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
            closed.complete(statusCode + " \"" + reason + "\"");
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            closed.complete("error " + error);
        }
    }

    private SpikeClient() { }
}
