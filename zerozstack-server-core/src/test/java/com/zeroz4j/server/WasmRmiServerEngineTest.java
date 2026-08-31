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

import com.zeroz4j.api.BinarySerializer;
import com.zeroz4j.api.GrowableBuffer;
import com.zeroz4j.api.ObjectMapper;
import com.zeroz4j.api.RmiService;
import com.zeroz4j.api.RolesAllowed;
import com.zeroz4j.api.Secured;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import jakarta.websocket.RemoteEndpoint;
import org.jboss.weld.junit5.EnableWeld;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Decoder;
import jakarta.websocket.Encoder;
import jakarta.websocket.Extension;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.WebSocketContainer;

@EnableWeld
public class WasmRmiServerEngineTest {

    /**
     * What a connection actually received, for an assertion about how many frames it received.
     *
     * <p>"expected: 2 but was: 1" says nothing about <i>which</i> frame is missing, and the two
     * candidates - the AUTH frame the connection opens with, and the answer to the call - fail for
     * entirely different reasons. This prints each frame's correlation id and opcode, and whether
     * the connection still has a writer at all.</p>
     *
     * @param s the connection
     * @return one line naming every frame it has received
     */
    static String diag(FakeSession s) {
        StringBuilder sb = new StringBuilder("frames=");
        for (ByteBuffer frame : s.basic.sentBuffers()) {
            ByteBuffer reading = frame.duplicate();
            reading.position(0);
            sb.append("[id=").append(reading.getInt()).append(" op=")
              .append(reading.remaining() > 0 ? Integer.toHexString(reading.get() & 0xFF) : "-")
              .append(']');
        }
        return sb.append(" hasWriter=").append(WsWrites.hasWriter(s)).toString();
    }


    @RmiService
    public interface MyTestService {
        String sayHello(String name);
        
        @Secured
        String secretMethod();

        @RolesAllowed("admin")
        String adminMethod();

        void throwError();

        void refuseWithReason();

        String scopedCall();
    }

    /** Reproduces the per-tenant EmbeddedStorageManager producer pattern. */
    @jakarta.enterprise.context.RequestScoped
    public static class ScopedProbe {
        public String ping() {
            return "request-scope-active";
        }
    }

    @ApplicationScoped
    public static class MyTestServiceImpl implements MyTestService {
        @Inject
        ScopedProbe scopedProbe;

        @Override
        public String sayHello(String name) {
            return "Hello " + name;
        }

        @Override
        public String scopedCall() {
            return scopedProbe.ping();
        }

        @Override
        public String secretMethod() {
            return "secret";
        }

        @Override
        public String adminMethod() {
            return "admin";
        }

        @Override
        public void throwError() {
            throw new RuntimeException("Intentional Error");
        }

        @Override
        public void refuseWithReason() {
            throw new ClientVisibleException("That invoice was already approved.");
        }
    }

    @WeldSetup
    public WeldInitiator weld = WeldInitiator.of(
            ServerRuntime.class,
            WasmRmiServerEngine.class,
            SyncEngine.class,
            ObjectMapperProducer.class,
            LiveMutexManager.class,
            MyTestServiceImpl.class,
            ScopedProbe.class
    );

    @Inject
    WasmRmiServerEngine engine;

    @Inject
    ServerRuntime runtime;

    @Inject
    ObjectMapper mapper;

    static class FakeBasic implements RemoteEndpoint.Basic {
        /** The session this remote belongs to, so reads can wait for its writer. */
        Session owner;
        /** Written by the session's writer thread, read by test assertions. */
        private final List<ByteBuffer> recorded = new CopyOnWriteArrayList<>();
        public CountDownLatch latch;

        /**
         * Everything sent so far, once the connection's writer has caught up.
         *
         * <p>A method rather than a field because writes leave the calling thread: {@code WsWrites}
         * queues a frame and a per-connection writer thread puts it on the wire, so reading the
         * list straight after a call would race that thread.</p>
         */
        public List<ByteBuffer> sentBuffers() {
            WsWrites.awaitQuiet(owner);
            return recorded;
        }

        @Override public void sendText(String text) {}
        @Override public void sendBinary(ByteBuffer data) {
            ByteBuffer copy = ByteBuffer.allocate(data.remaining());
            copy.put(data);
            copy.flip();
            recorded.add(copy);
            if (latch != null) latch.countDown();
        }
        @Override public void sendText(String partialMessage, boolean isLast) {}
        @Override public void sendBinary(ByteBuffer partialByte, boolean isLast) {}
        @Override public OutputStream getSendStream() { return null; }
        @Override public Writer getSendWriter() { return null; }
        @Override public void sendObject(Object data) {}
        @Override public void setBatchingAllowed(boolean allowed) {}
        @Override public boolean getBatchingAllowed() { return false; }
        @Override public void flushBatch() {}
        @Override public void sendPing(ByteBuffer applicationData) {}
        @Override public void sendPong(ByteBuffer applicationData) {}
    }

    static class FakeSession implements Session {
        private String id;
        public FakeBasic basic = new FakeBasic();
        private Map<String, Object> props = new HashMap<>();
        public FakeSession(String id) { this.id = id; this.basic.owner = this; }
        @Override public WebSocketContainer getContainer() { return null; }
        @Override public void addMessageHandler(MessageHandler handler) {}
        @Override public <T> void addMessageHandler(Class<T> clazz, MessageHandler.Whole<T> handler) {}
        @Override public <T> void addMessageHandler(Class<T> clazz, MessageHandler.Partial<T> handler) {}
        @Override public Set<MessageHandler> getMessageHandlers() { return null; }
        @Override public void removeMessageHandler(MessageHandler handler) {}
        @Override public String getProtocolVersion() { return null; }
        @Override public String getNegotiatedSubprotocol() { return null; }
        @Override public List<Extension> getNegotiatedExtensions() { return null; }
        @Override public boolean isSecure() { return false; }
        @Override public boolean isOpen() { return !closed; }
        /** Recorded rather than ignored, so the configured limits can be asserted. */
        public long maxIdleTimeout;
        public int maxBinaryBufferSize;
        /** Recorded so a disconnect can be asserted on: which sessions closed, and why. */
        public boolean closed;
        public CloseReason closeReason;
        @Override public long getMaxIdleTimeout() { return maxIdleTimeout; }
        @Override public void setMaxIdleTimeout(long milliseconds) { maxIdleTimeout = milliseconds; }
        @Override public void setMaxBinaryMessageBufferSize(int length) { maxBinaryBufferSize = length; }
        @Override public int getMaxBinaryMessageBufferSize() { return maxBinaryBufferSize; }
        @Override public void setMaxTextMessageBufferSize(int length) {}
        @Override public int getMaxTextMessageBufferSize() { return 0; }
        @Override public RemoteEndpoint.Async getAsyncRemote() { return null; }
        @Override public RemoteEndpoint.Basic getBasicRemote() { return basic; }
        @Override public String getId() { return id; }
        @Override public void close() { closed = true; }
        @Override public void close(CloseReason reason) { closed = true; closeReason = reason; }
        @Override public Map<String, List<String>> getRequestParameterMap() { return null; }
        @Override public String getQueryString() { return null; }
        @Override public Map<String, String> getPathParameters() { return null; }
        @Override public Map<String, Object> getUserProperties() { return props; }
        @Override public Principal getUserPrincipal() { return null; }
        @Override public Set<Session> getOpenSessions() { return null; }
        @Override public URI getRequestURI() { return null; }
    }

    static class FakeEndpointConfig implements EndpointConfig {
        private Map<String, Object> props = new HashMap<>();

        @Override public List<Class<? extends Decoder>> getDecoders() { return null; }
        @Override public List<Class<? extends Encoder>> getEncoders() { return null; }
        @Override public Map<String, Object> getUserProperties() { return props; }
    }

    private FakeSession fakeSession;

    @BeforeEach
    public void setup() {
        fakeSession = new FakeSession("sess-1");
        engine.scanServiceRegistry();
    }
    
    @AfterEach
    public void cleanup() {
        engine.onClose(fakeSession);
    }

    @Test
    public void testOnOpenSendsAuthFrame() throws Exception {
        FakeEndpointConfig config = new FakeEndpointConfig();
        config.getUserProperties().put(RmiEndpointConfigurator.PRINCIPAL_KEY, (Principal) () -> "testUser");
        config.getUserProperties().put(RmiEndpointConfigurator.ROLES_KEY, Set.of("user"));

        engine.onOpen(fakeSession, config);

        assertEquals(1, fakeSession.basic.sentBuffers().size());
        
        ByteBuffer buf = fakeSession.basic.sentBuffers().get(0);
        assertEquals(0, buf.getInt());
        assertEquals((byte) 0x03, buf.get());
        assertEquals((byte) 2, buf.get(), "AUTH protocol version");
        assertEquals((byte) 1, buf.get(), "authenticated flag");
        assertEquals("testUser", BinarySerializer.readString(buf));
        int numRoles = buf.getInt();
        assertEquals(1, numRoles);
        assertEquals("user", BinarySerializer.readString(buf));
    }

    /**
     * A connection whose credentials the application's provider declined has no principal. Reporting
     * it as an ordinary connection named "anonymous" made it indistinguishable from a real sign-in,
     * which silently defeated login gates built the documented way.
     */
    @Test
    public void testOnOpenMarksARejectedConnectionUnauthenticated() throws Exception {
        FakeEndpointConfig config = new FakeEndpointConfig();
        config.getUserProperties().put(RmiEndpointConfigurator.ROLES_KEY, Set.of());

        engine.onOpen(fakeSession, config);

        assertEquals(1, fakeSession.basic.sentBuffers().size(),
                "the frame is still sent: silence cannot tell a refusal from a slow network");

        ByteBuffer buf = fakeSession.basic.sentBuffers().get(0);
        assertEquals(0, buf.getInt());
        assertEquals((byte) 0x03, buf.get());
        assertEquals((byte) 2, buf.get(), "AUTH protocol version");
        assertEquals((byte) 0, buf.get(),
                "a declined connection must be marked unauthenticated, not merely role-less");
        assertEquals("anonymous", BinarySerializer.readString(buf));
        assertEquals(0, buf.getInt(), "no roles");
    }

    /**
     * The narrower case the roles alone cannot express: a real user who happens to hold no
     * application roles is authenticated, and must not be lumped in with a refused connection.
     */
    @Test
    public void testAnAuthenticatedUserWithNoRolesIsStillAuthenticated() throws Exception {
        FakeEndpointConfig config = new FakeEndpointConfig();
        config.getUserProperties().put(RmiEndpointConfigurator.PRINCIPAL_KEY,
                (Principal) () -> "roleless");
        config.getUserProperties().put(RmiEndpointConfigurator.ROLES_KEY, Set.of());

        engine.onOpen(fakeSession, config);

        ByteBuffer buf = fakeSession.basic.sentBuffers().get(0);
        buf.getInt();
        buf.get();
        buf.get();
        assertEquals((byte) 1, buf.get(), "an empty role set is not a failed authentication");
        assertEquals("roleless", BinarySerializer.readString(buf));
    }

    @Test
    public void testRequestScopedBeansResolveDuringRmiCalls() throws Exception {
        engine.onOpen(fakeSession, new FakeEndpointConfig());

        GrowableBuffer buffer = new GrowableBuffer();
        buffer.putInt(300);
        BinarySerializer.writeString(buffer, MyTestService.class.getName());
        BinarySerializer.writeString(buffer, "scopedCall");
        buffer.putInt(0);

        fakeSession.basic.latch = new CountDownLatch(1);
        engine.processIncomingBinaryPayload(ByteBuffer.wrap(buffer.toByteArray()), fakeSession);
        assertTrue(fakeSession.basic.latch.await(2, TimeUnit.SECONDS));

        ByteBuffer response = fakeSession.basic.sentBuffers().get(1);
        assertEquals(300, response.getInt());
        assertEquals((byte) 0x01, response.get(), "Must be a success frame, not ContextNotActiveException");
        assertEquals("request-scope-active", BinarySerializer.readValue(response, mapper));
    }

    @Test
    public void testProcessIncomingCallSuccess() throws Exception {
        engine.onOpen(fakeSession, new FakeEndpointConfig());
        
        GrowableBuffer buffer = new GrowableBuffer();
        buffer.putInt(100);
        BinarySerializer.writeString(buffer, MyTestService.class.getName());
        BinarySerializer.writeString(buffer, "sayHello");
        buffer.putInt(1);
        BinarySerializer.writeValue(buffer, "World", mapper);

        fakeSession.basic.latch = new CountDownLatch(1);
        
        engine.processIncomingBinaryPayload(ByteBuffer.wrap(buffer.toByteArray()), fakeSession);
        
        assertTrue(fakeSession.basic.latch.await(2, TimeUnit.SECONDS));

        assertEquals(2, fakeSession.basic.sentBuffers().size(), diag(fakeSession));
        
        ByteBuffer response = fakeSession.basic.sentBuffers().get(1);
        assertEquals(100, response.getInt());
        assertEquals((byte) 0x01, response.get()); 
        assertEquals("Hello World", BinarySerializer.readValue(response, mapper));
    }

    @Test
    public void testProcessIncomingCallSecurityDenied() throws Exception {
        FakeEndpointConfig config = new FakeEndpointConfig();
        engine.onOpen(fakeSession, config); 
        
        GrowableBuffer buffer = new GrowableBuffer();
        buffer.putInt(101);
        BinarySerializer.writeString(buffer, MyTestService.class.getName());
        BinarySerializer.writeString(buffer, "adminMethod");
        buffer.putInt(0); 

        fakeSession.basic.latch = new CountDownLatch(1);

        engine.processIncomingBinaryPayload(ByteBuffer.wrap(buffer.toByteArray()), fakeSession);
        
        assertTrue(fakeSession.basic.latch.await(2, TimeUnit.SECONDS));

        assertEquals(2, fakeSession.basic.sentBuffers().size(), diag(fakeSession));
        
        ByteBuffer response = fakeSession.basic.sentBuffers().get(1);
        assertEquals(101, response.getInt());
        assertEquals((byte) 0x0F, response.get());
        String errorMsg = BinarySerializer.readString(response);
        assertTrue(errorMsg.contains("Authentication required"));
    }

    /**
     * An exception the application did not plan for describes the machinery - class names, field
     * names, container internals - and an anonymous caller can provoke one on purpose to map the
     * system. The caller gets one sentence and a code that also appears in the server log.
     */
    @Test
    public void testAnUnexpectedFailureIsNotDescribedToTheCaller() throws Exception {
        engine.onOpen(fakeSession, new FakeEndpointConfig());

        GrowableBuffer buffer = new GrowableBuffer();
        buffer.putInt(102);
        BinarySerializer.writeString(buffer, MyTestService.class.getName());
        BinarySerializer.writeString(buffer, "throwError");
        buffer.putInt(0);

        fakeSession.basic.latch = new CountDownLatch(1);

        engine.processIncomingBinaryPayload(ByteBuffer.wrap(buffer.toByteArray()), fakeSession);

        assertTrue(fakeSession.basic.latch.await(2, TimeUnit.SECONDS));

        assertEquals(2, fakeSession.basic.sentBuffers().size(), diag(fakeSession));

        ByteBuffer response = fakeSession.basic.sentBuffers().get(1);
        assertEquals(102, response.getInt());
        assertEquals((byte) 0x0F, response.get());
        String message = BinarySerializer.readString(response);
        assertFalse(message.contains("Intentional Error"),
                "the internal message must not travel: " + message);
        assertTrue(message.startsWith("The server could not complete this request. Reference: "),
                "the caller gets one generic sentence: " + message);
        assertTrue(message.length() > "The server could not complete this request. Reference: ".length(),
                "and a reference code to quote to support: " + message);
    }

    /**
     * The opposite case: a refusal the application wrote for the caller to read travels word for
     * word. This is the escape hatch that makes sanitizing everything else safe.
     */
    @Test
    public void testAnApplicationRefusalReachesTheCallerUnchanged() throws Exception {
        engine.onOpen(fakeSession, new FakeEndpointConfig());

        GrowableBuffer buffer = new GrowableBuffer();
        buffer.putInt(103);
        BinarySerializer.writeString(buffer, MyTestService.class.getName());
        BinarySerializer.writeString(buffer, "refuseWithReason");
        buffer.putInt(0);

        fakeSession.basic.latch = new CountDownLatch(1);
        engine.processIncomingBinaryPayload(ByteBuffer.wrap(buffer.toByteArray()), fakeSession);
        assertTrue(fakeSession.basic.latch.await(2, TimeUnit.SECONDS));

        ByteBuffer response = fakeSession.basic.sentBuffers().get(1);
        assertEquals(103, response.getInt());
        assertEquals((byte) 0x0F, response.get());
        assertEquals("That invoice was already approved.", BinarySerializer.readString(response));
    }

    // ------------------------------------------------------- refused handshakes

    /**
     * Two different checks refuse a handshake and they are fixed in two different places, so the
     * one sentence the browser is given has to say which. Telling somebody whose host name is not
     * answered for that their origin was rejected sends them to read configuration that was never
     * the problem.
     */
    @Test
    public void testARefusedOriginSaysSo() {
        FakeEndpointConfig config = new FakeEndpointConfig();
        config.getUserProperties().put(RmiEndpointConfigurator.REJECTED_KEY, Boolean.TRUE);
        config.getUserProperties().put(WasmRmiServerEngine.REFUSED_BY_KEY,
                WasmRmiServerEngine.REFUSED_BY_ORIGIN);

        engine.onOpen(fakeSession, config);

        assertTrue(fakeSession.closed, "a refused handshake is closed at once");
        assertEquals(WasmRmiServerEngine.ORIGIN_REFUSED_REASON,
                fakeSession.closeReason.getReasonPhrase());
        assertTrue(fakeSession.closeReason.getReasonPhrase().contains("zeroz.origins"),
                "the developer meets this with no stack trace, so it must name what to look at");
    }

    @Test
    public void testARefusedHostSaysSoInstead() {
        FakeEndpointConfig config = new FakeEndpointConfig();
        config.getUserProperties().put(RmiEndpointConfigurator.REJECTED_KEY, Boolean.TRUE);
        config.getUserProperties().put(WasmRmiServerEngine.REFUSED_BY_KEY,
                WasmRmiServerEngine.REFUSED_BY_HOST);

        engine.onOpen(fakeSession, config);

        String reason = fakeSession.closeReason.getReasonPhrase();
        assertEquals(WasmRmiServerEngine.HOST_REFUSED_REASON, reason);
        assertFalse(reason.toLowerCase().contains("origin"),
                "sending a host-name refusal to the origin settings costs an hour: " + reason);
    }

    @Test
    public void testAnUnexplainedRefusalNamesBothChecks() {
        FakeEndpointConfig config = new FakeEndpointConfig();
        config.getUserProperties().put(RmiEndpointConfigurator.REJECTED_KEY, Boolean.TRUE);

        engine.onOpen(fakeSession, config);

        assertEquals(WasmRmiServerEngine.HANDSHAKE_REFUSED_REASON,
                fakeSession.closeReason.getReasonPhrase());
    }

    /** The protocol allows 123 bytes, and a container throws rather than truncating. */
    @Test
    public void testEveryRefusalReasonFitsTheProtocolLimit() {
        for (String reason : new String[] {
                WasmRmiServerEngine.ORIGIN_REFUSED_REASON,
                WasmRmiServerEngine.HOST_REFUSED_REASON,
                WasmRmiServerEngine.HANDSHAKE_REFUSED_REASON }) {
            assertTrue(reason.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 123,
                    "too long for a close frame: " + reason);
        }
    }

    // ---------------------------------------------------------------- diagnose()

    /** Not annotated at all: the developer really does need to add @DataModel. */
    static class PlainPojo {
        public String text;
    }

    /**
     * Annotated but with no generated serializer alongside it — which is what a skipped annotation
     * processor looks like from the outside. Nothing named PlainAnnotated_Serializer exists.
     */
    @com.zeroz4j.api.DataModel
    static class PlainAnnotated {
        public String text;
    }

    /** Annotated, and a class named exactly as the processor would name its serializer. */
    @com.zeroz4j.api.DataModel
    static class WithSerializer {
        public String text;
    }

    /** Stands in for generated output; diagnose() only checks that this name resolves. */
    static class WithSerializer_Serializer {
    }

    @Test
    void diagnoseTellsAnUnannotatedTypeToAnnotateItself() {
        String message = WasmRmiServerEngine.diagnose(PlainPojo.class);
        assertTrue(message.contains("Annotate"), message);
        assertTrue(message.contains(PlainPojo.class.getName()), message);
        assertFalse(message.contains("annotation processor did not run"), message);
    }

    @Test
    void diagnosePointsAtTheProcessorWhenTheTypeIsAnnotatedButHasNoSerializer() {
        String message = WasmRmiServerEngine.diagnose(PlainAnnotated.class);
        // The old message said "annotate the type @DataModel" here, sending people to a class that
        // was already annotated. It must now name the real cause instead.
        assertTrue(message.contains("IS annotated @DataModel"), message);
        assertTrue(message.contains("annotation processor did not run"), message);
        assertTrue(message.contains("JEP 470"), message);
        assertTrue(message.contains("annotationProcessorPaths"), message);
    }

    @Test
    void diagnoseBlamesTheValueWhenTheSerializerWasGenerated() {
        // diagnose() decides purely on whether <Type>_Serializer resolves, so a companion class
        // with exactly that name stands in for generated output.
        String message = WasmRmiServerEngine.diagnose(WithSerializer.class);
        assertTrue(message.contains("problem is inside the value"), message);
        assertFalse(message.contains("annotation processor did not run"), message);
    }

    /**
     * A reconnected client presents the handles it holds; the server re-sends current state for
     * the ones it knows as 0x10 frames and skips the ones it does not (it restarted since they
     * were fetched) without failing the rest of the batch.
     */
    @Test
    public void testResyncResendsKnownHandlesAndSkipsUnknown() throws Exception {
        engine.onOpen(fakeSession, new FakeEndpointConfig());
        mapper.registerWithId("known-1", "current-state");
        // Re-sync answers only for objects this client was actually sent. Normally the record is
        // written as a side effect of sending the object; here the object is planted directly in
        // the registry, so the record is planted with it.
        runtime.disclosures().record(fakeSession.getId(), "known-1");

        GrowableBuffer buffer = new GrowableBuffer();
        buffer.putInt(0); // fire-and-forget
        BinarySerializer.writeString(buffer, com.zeroz4j.api.SyncFrameTypes.RESYNC_SERVICE);
        BinarySerializer.writeString(buffer, "sync");
        buffer.putInt(1);
        BinarySerializer.writeValue(buffer, List.of("known-1", "gone-since-restart"), mapper);

        fakeSession.basic.latch = new CountDownLatch(1);
        engine.processIncomingBinaryPayload(ByteBuffer.wrap(buffer.toByteArray()), fakeSession);
        assertTrue(fakeSession.basic.latch.await(2, TimeUnit.SECONDS));

        // Frame 0 is the auth frame from onOpen; the resync answer is the only other one:
        // one frame for the known handle, none for the unknown.
        assertEquals(2, fakeSession.basic.sentBuffers().size(), diag(fakeSession));
        ByteBuffer frame = fakeSession.basic.sentBuffers().get(1);
        assertEquals(0, frame.getInt());
        assertEquals(com.zeroz4j.api.SyncFrameTypes.SUBSCRIBE, frame.get());
        assertEquals("current-state", BinarySerializer.readValue(frame, mapper));
    }
}
