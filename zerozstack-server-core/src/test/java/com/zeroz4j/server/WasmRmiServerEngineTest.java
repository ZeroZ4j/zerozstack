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

    @RmiService
    public interface MyTestService {
        String sayHello(String name);
        
        @Secured
        String secretMethod();

        @RolesAllowed("admin")
        String adminMethod();

        void throwError();

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
    }

    @WeldSetup
    public WeldInitiator weld = WeldInitiator.of(
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
    ObjectMapper mapper;

    static class FakeBasic implements RemoteEndpoint.Basic {
        public List<ByteBuffer> sentBuffers = new ArrayList<>();
        public CountDownLatch latch;
        @Override public void sendText(String text) {}
        @Override public void sendBinary(ByteBuffer data) {
            ByteBuffer copy = ByteBuffer.allocate(data.remaining());
            copy.put(data);
            copy.flip();
            sentBuffers.add(copy);
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
        public FakeSession(String id) { this.id = id; }
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
        @Override public boolean isOpen() { return true; }
        @Override public long getMaxIdleTimeout() { return 0; }
        @Override public void setMaxIdleTimeout(long milliseconds) {}
        @Override public void setMaxBinaryMessageBufferSize(int length) {}
        @Override public int getMaxBinaryMessageBufferSize() { return 0; }
        @Override public void setMaxTextMessageBufferSize(int length) {}
        @Override public int getMaxTextMessageBufferSize() { return 0; }
        @Override public RemoteEndpoint.Async getAsyncRemote() { return null; }
        @Override public RemoteEndpoint.Basic getBasicRemote() { return basic; }
        @Override public String getId() { return id; }
        @Override public void close() {}
        @Override public void close(CloseReason closeReason) {}
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

        assertEquals(1, fakeSession.basic.sentBuffers.size());
        
        ByteBuffer buf = fakeSession.basic.sentBuffers.get(0);
        assertEquals(0, buf.getInt());
        assertEquals((byte) 0x03, buf.get());
        assertEquals((byte) 1, buf.get());
        assertEquals("testUser", BinarySerializer.readString(buf));
        int numRoles = buf.getInt();
        assertEquals(1, numRoles);
        assertEquals("user", BinarySerializer.readString(buf));
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

        ByteBuffer response = fakeSession.basic.sentBuffers.get(1);
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

        assertEquals(2, fakeSession.basic.sentBuffers.size());
        
        ByteBuffer response = fakeSession.basic.sentBuffers.get(1);
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

        assertEquals(2, fakeSession.basic.sentBuffers.size());
        
        ByteBuffer response = fakeSession.basic.sentBuffers.get(1);
        assertEquals(101, response.getInt());
        assertEquals((byte) 0x0F, response.get());
        String errorMsg = BinarySerializer.readString(response);
        assertTrue(errorMsg.contains("Authentication required"));
    }

    @Test
    public void testProcessIncomingCallExceptionHandling() throws Exception {
        engine.onOpen(fakeSession, new FakeEndpointConfig()); 
        
        GrowableBuffer buffer = new GrowableBuffer();
        buffer.putInt(102);
        BinarySerializer.writeString(buffer, MyTestService.class.getName());
        BinarySerializer.writeString(buffer, "throwError");
        buffer.putInt(0); 

        fakeSession.basic.latch = new CountDownLatch(1);

        engine.processIncomingBinaryPayload(ByteBuffer.wrap(buffer.toByteArray()), fakeSession);
        
        assertTrue(fakeSession.basic.latch.await(2, TimeUnit.SECONDS));

        assertEquals(2, fakeSession.basic.sentBuffers.size());
        
        ByteBuffer response = fakeSession.basic.sentBuffers.get(1);
        assertEquals(102, response.getInt());
        assertEquals((byte) 0x0F, response.get()); 
        assertEquals("Intentional Error", BinarySerializer.readString(response));
    }
}
