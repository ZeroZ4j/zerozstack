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

import com.zeroz4j.api.BinaryPackable;
import com.zeroz4j.api.BinarySerializer;
import com.zeroz4j.api.GrowableBuffer;
import com.zeroz4j.api.ObjectMapper;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.ByteBuffer;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.zeroz4j.api.BinaryRegistry;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;
import java.security.Principal;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Extension;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.WebSocketContainer;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LiveSyncEngineTest {

    private SyncEngine syncEngine;
    private ObjectMapper mapper;

    @BeforeEach
    public void setup() {
        BinaryRegistry.register(Node.class.getName(), Node::new);
        syncEngine = new SyncEngine();
        mapper = new ObjectMapper();
        syncEngine.mapper = mapper;
    }

    public static class Node implements BinaryPackable {
        public String name;
        public Node next;

        public Node() {}
        public Node(String name) { this.name = name; }

        @Override
        public void writeToBuffer(GrowableBuffer buffer, ObjectMapper mapper) {
            BinarySerializer.writeString(buffer, name);
            BinarySerializer.writeValue(buffer, next, mapper);
        }

        @Override
        public void readFromBuffer(ByteBuffer buffer, ObjectMapper mapper) {
            this.name = BinarySerializer.readString(buffer);
            this.next = (Node) BinarySerializer.readValue(buffer, mapper);
        }
    }

    static class FakeBasic implements RemoteEndpoint.Basic {
        public ByteBuffer lastBuffer;
        @Override public void sendText(String text) {}
        @Override public void sendBinary(ByteBuffer data) { lastBuffer = ByteBuffer.allocate(data.remaining()); lastBuffer.put(data); lastBuffer.flip(); }
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
        @Override public Map<String, Object> getUserProperties() { return null; }
        @Override public Principal getUserPrincipal() { return null; }
        @Override public Set<Session> getOpenSessions() { return null; }
        @Override public URI getRequestURI() { return null; }
    }

    @Test
    public void testCyclicObjectGraphAndMultiSessionSync() throws Exception {
        FakeSession session1 = new FakeSession("sess-1");
        FakeSession session2 = new FakeSession("sess-2");

        syncEngine.addSession(session1);
        syncEngine.addSession(session2);

        Node nodeA = new Node("A");
        Node nodeB = new Node("B");
        nodeA.next = nodeB;
        nodeB.next = nodeA;

        // Register with mapper so syncEngine knows about it
        mapper.register(nodeA);
        mapper.register(nodeB);

        syncEngine.notifyChanged(nodeA);

        ByteBuffer sentData1 = session1.basic.lastBuffer;
        ByteBuffer sentData2 = session2.basic.lastBuffer;

        assertNotNull(sentData1);
        assertNotNull(sentData2);

        assertEquals(sentData1, sentData2, "Both sessions should receive identical sync payloads");

        // Verify the payload contents via a fresh deserialization
        sentData1.rewind();
        int reqId = sentData1.getInt(); // 0
        byte type = sentData1.get(); // 0x10

        assertEquals(0, reqId);
        assertEquals(0x10, type);

        // Deserializing requires a fresh mapper on the receiving end
        ObjectMapper clientMapper = new ObjectMapper();
        Object received = BinarySerializer.readValue(sentData1, clientMapper);

        assertNotNull(received);
        assertEquals(Node.class, received.getClass());

        Node receivedA = (Node) received;
        assertEquals("A", receivedA.name);
        assertNotNull(receivedA.next);
        assertEquals("B", receivedA.next.name);
        
        // Check cyclic identity
        assertSame(receivedA, receivedA.next.next, "Cyclic reference should point back to node A");
    }

    @Test
    public void testRemoveSession() throws Exception {
        FakeSession session1 = new FakeSession("sess-1");
        syncEngine.addSession(session1);
        
        Node nodeA = new Node("A");
        mapper.register(nodeA);

        syncEngine.notifyChanged(nodeA);
        assertNotNull(session1.basic.lastBuffer);

        // Remove session and try again
        session1.basic.lastBuffer = null;
        syncEngine.removeSession("sess-1");
        
        syncEngine.notifyChanged(nodeA);
        assertNull(session1.basic.lastBuffer, "Removed session should not receive updates");
    }

    @Test
    public void testBroadcastFailure() throws Exception {
        FakeSession session1 = new FakeSession("sess-1");
        session1.basic = new FakeBasic() {
            @Override
            public void sendBinary(ByteBuffer data) {
                throw new RuntimeException("Simulated IO Exception");
            }
        };

        syncEngine.addSession(session1);
        
        Node nodeA = new Node("A");
        mapper.register(nodeA);

        // Should not throw an exception out of notifyChanged
        syncEngine.notifyChanged(nodeA);
        // If we get here, the exception was caught and handled
    }
}
