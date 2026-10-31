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
package com.zeroz4j.server.test;

import com.zeroz4j.api.BinarySerializer;
import com.zeroz4j.api.GrowableBuffer;
import com.zeroz4j.api.ObjectMapper;
import com.zeroz4j.api.SlowCall;
import com.zeroz4j.api.SlowCallBoard;
import com.zeroz4j.api.SlowCallRpc;
import com.zeroz4j.api.SlowCallState;
import com.zeroz4j.api.SlowCallStatus;
import com.zeroz4j.api.SyncFrameTypes;
import com.zeroz4j.api.i18n.FrameworkText;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Decoder;
import jakarta.websocket.Encoder;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Extension;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;

import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;
import java.nio.ByteBuffer;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One browser talking to a {@link TestServer}, without a browser.
 *
 * <p>It is a real {@link Session} as far as the framework is concerned — the server opens it,
 * writes to it and closes it exactly as it would a socket — but the bytes land in a list this class
 * hands back, instead of on a network. It is the same stand-in the framework's own tests have always
 * used, made supported so an application can use it too.</p>
 *
 * <p>Get one from {@link TestServer#connect()}. Do not build one directly: a connection has to be
 * <em>opened on</em> a server, and one that was not belongs to nobody.</p>
 *
 * <pre>{@code
 * try (TestServer server = TestServer.builder().beans(Greeter.class).start();
 *      TestConnection browser = server.connect("alice", "admin")) {
 *
 *     server.events().publish(NOTICES, "hello");
 *
 *     assertEquals(1, browser.pushCount());
 * }
 * }</pre>
 *
 * @since 0.8.0
 */
public final class TestConnection implements Session, AutoCloseable {

    /** The opcode byte of a server push (an event). */
    public static final byte PUSH = 0x02;
    /** The opcode byte of the frame that says who the server decided you are. */
    public static final byte AUTH = 0x03;
    /**
     * The opcode byte of the frame carrying translated words.
     *
     * <p>Written only when somebody switches language on a connection that is already open, and
     * always immediately before the {@link #SIGNAL_UPDATE} that puts the new language on screen.</p>
     */
    public static final byte CATALOG = 0x04;
    /** The opcode byte of a shared-signal value. */
    public static final byte SIGNAL_UPDATE = 0x17;
    /** The opcode byte of a LiveSync object update. */
    public static final byte OBJECT_UPDATE = 0x10;

    /** How long {@link #call} waits for an answer. A service method under test should be quick. */
    private static final long CALL_WAIT_MILLIS = 10_000L;

    private final String id;
    private final TestServer server;
    private final Map<String, Object> properties = new HashMap<>();
    private final Recorder recorder = new Recorder();

    private volatile boolean closed;
    private volatile CloseReason closeReason;
    private int maxBinaryBufferSize;
    private long maxIdleTimeout;

    /** Message numbers for {@link #call}. High, so they stand apart from a test's own frames. */
    private final AtomicInteger callSerial = new AtomicInteger(1_000_000);
    /** What a browser keeps: its own table of the objects it was sent. */
    private final ObjectMapper browserMapper = new ObjectMapper();
    private volatile boolean subscribedToSlowCalls;

    TestConnection(TestServer server, String id) {
        this.server = server;
        this.id = id;
    }

    // ------------------------------------------------------------------ what a test asks

    /**
     * @return this connection's id, the same one the server knows it by
     */
    public String id() {
        return id;
    }

    /**
     * @return the server this connection was opened on
     */
    public TestServer server() {
        return server;
    }

    /**
     * @return the browser id this connection carries, or null when it carries none
     */
    public String browserId() {
        return (String) properties.get("zeroz.clientId");
    }

    /** @return the user name this connection signed in with, or null */
    String user() {
        Principal principal = getUserPrincipal();
        return principal == null ? null : principal.getName();
    }

    /** @return the tenant this connection belongs to, or null */
    String tenant() {
        return (String) properties.get("zeroz.tenant");
    }

    /** @return the roles this connection signed in with */
    @SuppressWarnings("unchecked")
    String[] roles() {
        Set<String> roles = (Set<String>) properties.get("zeroz.roles");
        return roles == null ? new String[0] : roles.toArray(new String[0]);
    }

    /**
     * @return the language this connection reads, or null when it never said
     * @since 0.9.0
     */
    public String language() {
        return (String) properties.get("zeroz.locale");
    }

    /**
     * Everything the server has written to this connection so far.
     *
     * <p>Waits for the server's outbound writer to catch up first. Writing leaves the calling
     * thread — a queue and one writer thread per connection — so reading the list straight after a
     * call would race that thread and see nothing.</p>
     *
     * @return the frames, oldest first; each is a fresh copy
     */
    public List<byte[]> frames() {
        server.awaitDelivered(this);
        List<byte[]> copy = new ArrayList<>(recorder.recorded.size());
        for (byte[] frame : recorder.recorded) {
            copy.add(frame.clone());
        }
        return Collections.unmodifiableList(copy);
    }

    /**
     * @return how many frames the server has written to this connection
     */
    public int frameCount() {
        return frames().size();
    }

    /**
     * One frame the server wrote.
     *
     * @param index which one, counting from zero
     * @return the raw bytes
     */
    public byte[] frame(int index) {
        return frames().get(index);
    }

    /**
     * What kind of frame the server wrote.
     *
     * <p>The opcode is the fifth byte: four bytes of correlation id come first. Compare it with
     * {@link #PUSH}, {@link #AUTH}, {@link #CATALOG}, {@link #SIGNAL_UPDATE} or
     * {@link #OBJECT_UPDATE}.</p>
     *
     * @param index which frame, counting from zero
     * @return the opcode byte
     */
    public byte opcode(int index) {
        return frame(index)[4];
    }

    /**
     * How many frames of one kind the server wrote.
     *
     * @param opcode the kind, for example {@link #PUSH}
     * @return the count
     */
    public int countOf(byte opcode) {
        int found = 0;
        for (byte[] frame : frames()) {
            if (frame.length > 4 && frame[4] == opcode) {
                found++;
            }
        }
        return found;
    }

    /**
     * How many events this connection was pushed.
     *
     * <p>The AUTH frame every connection is sent when it opens is not one, so this counts what the
     * application actually published.</p>
     *
     * @return the number of push frames
     */
    public int pushCount() {
        return countOf(PUSH);
    }

    /**
     * Forgets every frame recorded so far, so the next assertion starts from nothing.
     */
    public void clearFrames() {
        server.awaitDelivered(this);
        recorder.recorded.clear();
    }

    /**
     * Sends a frame to the server, the way a browser would.
     *
     * @param frame the raw bytes
     */
    public void send(byte[] frame) {
        server.deliverToServer(this, ByteBuffer.wrap(frame));
    }

    /**
     * Calls a service method the way a browser does, and waits for the answer.
     *
     * <p>A real call frame goes through the real engine, so {@code @Secured}, {@code @RolesAllowed},
     * validation and the caller's identity all apply.</p>
     *
     * @param <R>     what the method returns
     * @param service the {@code @RmiService} interface
     * @param method  the method name
     * @param args    the arguments
     * @return the answer
     * @throws IllegalStateException with the server's sentence when it answered with an error, and
     *                               when it has not answered within ten seconds
     * @since 0.11.0
     */
    @SuppressWarnings("unchecked")
    public <R> R call(Class<?> service, String method, Object... args) {
        int messageId = callSerial.incrementAndGet();
        GrowableBuffer buffer = new GrowableBuffer();
        buffer.putInt(messageId);
        BinarySerializer.writeString(buffer, service.getName());
        BinarySerializer.writeString(buffer, method);
        buffer.putInt(args.length);
        for (Object arg : args) {
            BinarySerializer.writeValue(buffer, arg, browserMapper);
        }
        send(buffer.toByteArray());

        long deadline = System.currentTimeMillis() + CALL_WAIT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            for (byte[] frame : frames()) {
                ByteBuffer answer = ByteBuffer.wrap(frame);
                if (answer.getInt() != messageId) {
                    continue;
                }
                if (answer.get() == SyncFrameTypes.RPC_ERROR) {
                    throw new IllegalStateException(BinarySerializer.readString(answer));
                }
                return (R) BinarySerializer.readValue(answer, browserMapper);
            }
            pause();
        }
        throw new IllegalStateException(service.getSimpleName() + "." + method
                + " was not answered within " + CALL_WAIT_MILLIS + " ms.");
    }

    /**
     * Where a slow call stands, as a browser would see it now.
     *
     * <p>Reads the board the server sends this connection and, for finished work, fetches the value.
     * A key the board does not have is {@link SlowCallStatus#IDLE} for a handle from
     * {@code SlowCall.find}, and failed with the sentence a screen gets for a handle that had a
     * run, which is what a browser shows after a server restart.</p>
     *
     * @param <T>  what the work returns
     * @param call the handle
     * @return the state
     * @since 0.11.0
     */
    public <T> SlowCallState<T> slowCallState(SlowCall<T> call) {
        SlowCallBoard board = latestBoard();
        SlowCallBoard.Entry entry = board.find(call.key());
        if (entry == null) {
            return call.id() == null ? SlowCallState.idle()
                    : SlowCallState.failed(com.zeroz4j.server.ServerMessages.inEnglish(FrameworkText.slowCallLost()));
        }
        if (entry.status() == SlowCallStatus.RUNNING) {
            return SlowCallState.running(entry.percent(), entry.progressText());
        }
        if (entry.status() == SlowCallStatus.FAILED) {
            return SlowCallState.failed(entry.message());
        }
        if (entry.status() == SlowCallStatus.CANCELED) {
            return SlowCallState.canceled();
        }
        T value = call(SlowCallRpc.class, "collect", entry.id());
        return SlowCallState.finished(value);
    }

    /**
     * Waits until a slow call has ended, and answers how.
     *
     * @param <T>           what the work returns
     * @param call          the handle
     * @param timeoutMillis how long to wait
     * @return the final state
     * @throws IllegalStateException when the work is still running at the end of the wait
     * @since 0.11.0
     */
    public <T> SlowCallState<T> awaitSlowCall(SlowCall<T> call, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (true) {
            SlowCallState<T> state = slowCallState(call);
            if (!state.isRunning()) {
                return state;
            }
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("Slow call '" + call.key() + "' is still running "
                        + "after " + timeoutMillis + " ms: " + state);
            }
            pause();
        }
    }

    /**
     * Asks a slow call to stop, the way {@code SlowCall.cancel()} does in a browser.
     *
     * @param call the handle
     * @since 0.11.0
     */
    public void cancelSlowCall(SlowCall<?> call) {
        call(SlowCallRpc.class, "cancel", call.key());
    }

    /** The newest board this connection was sent, subscribing first if it has not. */
    private SlowCallBoard latestBoard() {
        if (!subscribedToSlowCalls) {
            subscribedToSlowCalls = true;
            GrowableBuffer buffer = new GrowableBuffer();
            buffer.putInt(0);
            BinarySerializer.writeString(buffer, SyncFrameTypes.SIGNALS_SERVICE);
            BinarySerializer.writeString(buffer, "subscribe");
            buffer.putInt(1);
            BinarySerializer.writeValue(buffer, SlowCallBoard.SIGNAL_NAME, browserMapper);
            send(buffer.toByteArray());
        }
        long deadline = System.currentTimeMillis() + CALL_WAIT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            List<byte[]> written = frames();
            for (int i = written.size() - 1; i >= 0; i--) {
                ByteBuffer frame = ByteBuffer.wrap(written.get(i));
                frame.getInt();
                if (frame.get() == SIGNAL_UPDATE
                        && SlowCallBoard.SIGNAL_NAME.equals(BinarySerializer.readString(frame))) {
                    return (SlowCallBoard) BinarySerializer.readValue(frame, browserMapper);
                }
            }
            pause();
        }
        throw new IllegalStateException("The server did not answer the slow call subscription "
                + "within " + CALL_WAIT_MILLIS + " ms.");
    }

    private static void pause() {
        try {
            Thread.sleep(2);
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the server.", stopped);
        }
    }

    /**
     * @return why the server closed this connection, or null when it did not
     */
    public String closedBecause() {
        return closeReason == null ? null : closeReason.getReasonPhrase();
    }

    /** Closes this connection on the server, the way a browser going away does. */
    @Override
    public void close() {
        if (!closed) {
            server.closeConnection(this);
        }
    }

    /** Marks the connection shut without telling the server; used by the server's own close. */
    void markClosed(CloseReason reason) {
        this.closed = true;
        this.closeReason = reason;
    }

    // ------------------------------------------------------------------ the Session contract

    /** The endpoint configuration this connection is opened with. */
    EndpointConfig openingConfig(Principal principal, Set<String> roles, String tenant,
                                 String browserId, String language) {
        Map<String, Object> handshake = new HashMap<>();
        if (language != null) {
            handshake.put("zeroz.locale", language);
        }
        if (principal != null) {
            handshake.put("zeroz.principal", principal);
        }
        handshake.put("zeroz.roles", roles);
        if (tenant != null) {
            handshake.put("zeroz.tenant", tenant);
        }
        if (browserId != null) {
            handshake.put("zeroz.clientId", browserId);
        }
        return new EndpointConfig() {
            @Override public List<Class<? extends Encoder>> getEncoders() {
                return Collections.emptyList();
            }
            @Override public List<Class<? extends Decoder>> getDecoders() {
                return Collections.emptyList();
            }
            @Override public Map<String, Object> getUserProperties() {
                return handshake;
            }
        };
    }

    @Override public WebSocketContainer getContainer() { return null; }
    @Override public void addMessageHandler(MessageHandler handler) { }
    @Override public <T> void addMessageHandler(Class<T> type, MessageHandler.Whole<T> handler) { }
    @Override public <T> void addMessageHandler(Class<T> type, MessageHandler.Partial<T> handler) { }
    @Override public Set<MessageHandler> getMessageHandlers() { return Collections.emptySet(); }
    @Override public void removeMessageHandler(MessageHandler handler) { }
    @Override public String getProtocolVersion() { return "13"; }
    @Override public String getNegotiatedSubprotocol() { return ""; }
    @Override public List<Extension> getNegotiatedExtensions() { return Collections.emptyList(); }
    @Override public boolean isSecure() { return false; }
    @Override public boolean isOpen() { return !closed; }
    @Override public long getMaxIdleTimeout() { return maxIdleTimeout; }
    @Override public void setMaxIdleTimeout(long milliseconds) { maxIdleTimeout = milliseconds; }
    @Override public void setMaxBinaryMessageBufferSize(int length) { maxBinaryBufferSize = length; }
    @Override public int getMaxBinaryMessageBufferSize() { return maxBinaryBufferSize; }
    @Override public void setMaxTextMessageBufferSize(int length) { }
    @Override public int getMaxTextMessageBufferSize() { return 0; }
    @Override public RemoteEndpoint.Async getAsyncRemote() { return null; }
    @Override public RemoteEndpoint.Basic getBasicRemote() { return recorder; }
    @Override public String getId() { return id; }
    @Override public void close(CloseReason reason) { markClosed(reason); }
    @Override public Map<String, List<String>> getRequestParameterMap() { return Collections.emptyMap(); }
    @Override public String getQueryString() { return ""; }
    @Override public Map<String, String> getPathParameters() { return Collections.emptyMap(); }
    @Override public Map<String, Object> getUserProperties() { return properties; }
    @Override public Principal getUserPrincipal() { return (Principal) properties.get("zeroz.principal"); }
    @Override public Set<Session> getOpenSessions() { return Collections.emptySet(); }
    @Override public URI getRequestURI() { return URI.create("ws://test/wasm-rmi"); }

    /**
     * The far end of the wire: it keeps what was written instead of sending it.
     *
     * <p>The largest limit a server sets on a connection is enforced here too, so a test can prove
     * that two servers really do apply different limits.</p>
     */
    private final class Recorder implements RemoteEndpoint.Basic {

        private final List<byte[]> recorded = new CopyOnWriteArrayList<>();

        @Override
        public void sendBinary(ByteBuffer data) {
            byte[] frame = new byte[data.remaining()];
            data.get(frame);
            recorded.add(frame);
        }

        @Override public void sendText(String text) { }
        @Override public void sendText(String partial, boolean isLast) { }
        @Override public void sendBinary(ByteBuffer partial, boolean isLast) { }
        @Override public OutputStream getSendStream() { return null; }
        @Override public Writer getSendWriter() { return null; }
        @Override public void sendObject(Object data) { }
        @Override public void setBatchingAllowed(boolean allowed) { }
        @Override public boolean getBatchingAllowed() { return false; }
        @Override public void flushBatch() { }
        @Override public void sendPing(ByteBuffer applicationData) { }
        @Override public void sendPong(ByteBuffer applicationData) { }
    }
}
