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

import com.zeroz4j.api.SyncFrameTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The keepalive's server half.
 *
 * <h2>What this protects</h2>
 * An idle WebSocket is closed by whichever proxy in the path has the shortest timeout - nginx
 * defaults to 60 seconds, Cloudflare cuts at 100. Measured in a real deployment: every socket
 * authenticated and died at exactly 60 seconds, for ever. The answer has to come from the transport,
 * because a browser cannot send a WebSocket ping frame from page script.
 *
 * <p>Two properties decide whether it works, and both are asserted here: the server ANSWERS (so
 * traffic flows in the direction a proxy reads as well as the one it writes), and answering costs
 * nothing - no service lookup, no security check, no request context.
 */
class KeepaliveFrameTest {

    private WasmRmiServerEngine engine;

    @BeforeEach
    void setUp() {
        engine = new WasmRmiServerEngine();
        engine.mapper = new com.zeroz4j.api.ObjectMapper();
        WasmRmiServerEngine.clearActiveSessionsForTesting();
    }

    @Test
    @DisplayName("a ping is answered with one empty pong")
    void aPingIsAnswered() {
        WasmRmiServerEngineTest.FakeSession session = new WasmRmiServerEngineTest.FakeSession("s1");

        WasmRmiServerEngine.sendPong(session);

        assertEquals(1, session.basic.sentBuffers.size(), "the server must answer, or the proxy's read timer "
                + "never resets and the socket dies in the server-to-client direction");
        ByteBuffer answer = session.basic.sentBuffers.get(0);
        assertEquals(0, answer.getInt(), "a pong correlates with nothing");
        assertEquals(SyncFrameTypes.PONG, answer.get());
        assertFalse(answer.hasRemaining(), "a pong carries no payload; it is five bytes");
    }

    @Test
    @DisplayName("a closed session is not written to, and is forgotten")
    void aClosedSessionIsDropped() {
        WasmRmiServerEngineTest.FakeSession session = new WasmRmiServerEngineTest.FakeSession("s2");
        session.close();

        WasmRmiServerEngine.sendPong(session);

        assertTrue(session.basic.sentBuffers.isEmpty(), "writing to a closed session throws and would be logged "
                + "as a keepalive failure every twenty-five seconds");
    }

    /**
     * The frame the client sends, decoded exactly as the engine decodes it. If the shapes ever
     * differ the keepalive silently stops and the 60-second death returns, so the shape is pinned
     * rather than described.
     */
    @Test
    @DisplayName("the ping frame is a fire-and-forget call to the keepalive service")
    void thePingFrameShape() {
        com.zeroz4j.api.GrowableBuffer buffer = new com.zeroz4j.api.GrowableBuffer();
        buffer.putInt(0);
        com.zeroz4j.api.BinarySerializer.writeString(buffer, SyncFrameTypes.KEEPALIVE_SERVICE);
        com.zeroz4j.api.BinarySerializer.writeString(buffer, "ping");
        buffer.putInt(0);

        ByteBuffer frame = ByteBuffer.wrap(buffer.toByteArray());
        assertEquals(0, frame.getInt(), "correlation id 0 means nothing is waiting for an answer");
        assertEquals(SyncFrameTypes.KEEPALIVE_SERVICE,
                com.zeroz4j.api.BinarySerializer.readString(frame));
        assertEquals("ping", com.zeroz4j.api.BinarySerializer.readString(frame));
        assertEquals(0, frame.getInt(), "no arguments");
        assertFalse(frame.hasRemaining());
    }

    @Test
    @DisplayName("the keepalive service name is not something an application could register")
    void theServiceNameIsReserved() {
        assertTrue(SyncFrameTypes.KEEPALIVE_SERVICE.startsWith("zeroz4j."),
                "framework-internal service names are namespaced so an application interface can "
                        + "never collide with one");
    }
}
