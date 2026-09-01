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
package com.zeroz4j.client;

import com.zeroz4j.api.BinarySerializer;
import com.zeroz4j.api.Disposable;
import com.zeroz4j.api.GrowableBuffer;
import com.zeroz4j.api.LiveMutationRefusals;
import com.zeroz4j.api.LiveMutationTracker;
import com.zeroz4j.api.SyncFrameTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An edit that never reached the server is said out loud, and the screen is put back.
 *
 * <p>The failure this guards against is the one that hid the broken up direction of LiveSync for a
 * whole version: the write threw, the client caught it, printed one console line and dropped the
 * edit. The person kept looking at a value the server had never heard of, and no code anywhere
 * could find out. Two ways an edit can fail to land, one story for both.</p>
 */
public class LiveMutationRefusalTest {

    /** A channel whose openness the test controls. */
    static class FakeChannel implements WasmWebSocketChannel {
        List<byte[]> sent = new ArrayList<>();
        boolean open = true;

        @Override
        public void sendRawBytes(byte[] payload) {
            sent.add(payload);
        }

        @Override
        public void registerBinaryMessageHandler(BinaryMessageHandler handler) {
        }

        @Override
        public boolean isOpen() {
            return open;
        }
    }

    private FakeChannel channel;
    private final List<String> refusals = new ArrayList<>();
    private Disposable subscription;

    @BeforeEach
    public void setup() {
        LiveMutations.resetForTesting();
        LiveMutationRefusals.resetForTesting();
        refusals.clear();
        WasmRmiClient.pendingRequests.clear();
        WasmRmiClient.MAPPER.clear();
        channel = new FakeChannel();
        WasmRmiClient.initialize(channel);
        WasmRmiClient.resetConnectionStateForTesting();
        channel.sent.clear();
        subscription = LiveMutationRefusals.onRefused(
                (model, reason) -> refusals.add(model + " | " + reason));
    }

    @AfterEach
    public void teardown() {
        subscription.dispose();
        LiveMutationRefusals.resetForTesting();
    }

    /** Decodes one fire-and-forget framework frame into its service name and arguments. */
    private List<Object> argumentsOf(byte[] raw, String expectedService, String expectedMethod) {
        ByteBuffer buffer = ByteBuffer.wrap(raw);
        buffer.getInt();
        assertEquals(expectedService, BinarySerializer.readString(buffer));
        assertEquals(expectedMethod, BinarySerializer.readString(buffer));
        int count = buffer.getInt();
        List<Object> args = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            args.add(BinarySerializer.readValue(buffer, WasmRmiClient.MAPPER));
        }
        return args;
    }

    @Test
    @DisplayName("an edit that cannot be sent is reported, and the object is asked for again")
    public void anUnsendableEditIsReportedAndReverted() {
        // A value the serializer has no idea how to write, standing in for anything the write path
        // refuses. It is registered, so the client knows the name to ask the server for.
        Object held = new Object();
        WasmRmiClient.MAPPER.registerWithId("handle-42", held);

        LiveMutationTracker.fieldChanged(held);

        assertEquals(1, refusals.size(), "the edit must not be dropped in silence");
        assertTrue(refusals.get(0).contains("java.lang.Object"), refusals.get(0));
        assertTrue(refusals.get(0).contains("put back"),
                "the person is told the value is being corrected: " + refusals.get(0));

        assertEquals(1, channel.sent.size(), "one request, asking the server for the truth");
        List<Object> args = argumentsOf(channel.sent.get(0), SyncFrameTypes.RESYNC_SERVICE, "sync");
        assertEquals(List.of("handle-42"), args.get(0));
    }

    @Test
    @DisplayName("an edit with nothing to revert to still says the change did not happen")
    public void anUnsendableEditOnAnUnknownObjectIsStillReported() {
        LiveMutationTracker.fieldChanged(new Object());

        assertEquals(1, refusals.size());
        assertTrue(refusals.get(0).contains("does not have it"), refusals.get(0));
        assertTrue(channel.sent.isEmpty(), "nothing to ask for: the object has no handle");
    }

    @Test
    @DisplayName("the server's refusal reaches the application instead of being an unknown frame")
    public void aServerRefusalReachesTheApplication() {
        GrowableBuffer frame = new GrowableBuffer(64);
        frame.putInt(0);
        frame.put(SyncFrameTypes.REJECT);
        BinarySerializer.writeString(frame, "com.example.TeamProfile");
        BinarySerializer.writeString(frame, "Requires one of the roles [editor]");

        WasmRmiClient.routeIncomingMessage(frame.toByteArray());

        assertEquals(List.of("com.example.TeamProfile | Requires one of the roles [editor]"),
                refusals);
    }
}
