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
import com.zeroz4j.api.DisconnectedException;
import com.zeroz4j.api.LiveMutationTracker;
import com.zeroz4j.api.LiveMutex;
import com.zeroz4j.api.RmiClientExecutor;
import com.zeroz4j.api.SyncFrameTypes;
import com.zeroz4j.signals.Signals;
import com.zeroz4j.signals.ValueSignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.teavm.interop.AsyncCallback;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The disconnect-and-recover contract, driven through state transitions on a fake channel:
 * calls fail fast rather than hang, nothing the user did while offline is lost, and
 * reconnecting restores signals and requests a re-sync without application involvement.
 */
public class ReconnectRecoveryTest {

    private FakeChannel channel;

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

    /** One decoded fire-and-forget framework frame. */
    record Frame(String iface, String method, List<Object> args) {
    }

    private Frame decode(byte[] raw) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(raw);
        buffer.getInt(); // correlation id
        String iface = BinarySerializer.readString(buffer);
        String method = BinarySerializer.readString(buffer);
        int count = buffer.getInt();
        List<Object> args = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            args.add(BinarySerializer.readValue(buffer, WasmRmiClient.MAPPER));
        }
        return new Frame(iface, method, args);
    }

    private List<Frame> decodeAll() throws Exception {
        List<Frame> frames = new ArrayList<>();
        for (byte[] raw : channel.sent) {
            frames.add(decode(raw));
        }
        return frames;
    }

    static class RecordingCallback implements AsyncCallback<Object> {
        Object result;
        Throwable error;

        @Override
        public void complete(Object result) {
            this.result = result;
        }

        @Override
        public void error(Throwable e) {
            this.error = e;
        }
    }

    @BeforeEach
    public void setup() {
        Signals.resetForTesting();
        ClientSignalTransport.resetForTesting();
        LiveMutations.resetForTesting();
        ClientLiveMutexProvider.resetForTesting();
        WasmRmiClient.pendingRequests.clear();
        WasmRmiClient.MAPPER.clear();
        channel = new FakeChannel();
        WasmRmiClient.initialize(channel);
        WasmRmiClient.resetConnectionStateForTesting();
    }

    private void drop() {
        channel.open = false;
        WasmRmiClient.onStateChange(WasmRmiClientChannel.State.RECONNECTING);
    }

    private void restore() {
        channel.open = true;
        WasmRmiClient.onStateChange(WasmRmiClientChannel.State.CONNECTED);
    }

    // ------------------------------------------------------------------ fail fast

    @Test
    public void callWhileDisconnectedFailsImmediately() {
        channel.open = false;

        RecordingCallback callback = new RecordingCallback();
        WasmRmiClient.executeCall("MyService", "doWork", null, callback);

        assertNotNull(callback.error, "the call must fail, not hang");
        assertInstanceOf(DisconnectedException.class, callback.error);
        assertTrue(channel.sent.isEmpty(), "nothing may be written into a dead socket");
        assertTrue(WasmRmiClient.pendingRequests.isEmpty(), "a refused call must not linger as pending");
    }

    @Test
    public void dropFailsInFlightCallsImmediately() {
        RecordingCallback callback = new RecordingCallback();
        WasmRmiClient.executeCall("MyService", "doWork", null, callback);
        assertEquals(1, WasmRmiClient.pendingRequests.size());
        assertNull(callback.error, "still in flight before the drop");

        drop();

        assertInstanceOf(DisconnectedException.class, callback.error,
                "the suspended call must resume with an error the moment the drop is known");
        assertTrue(WasmRmiClient.pendingRequests.isEmpty());
    }

    @Test
    public void connectionStateSignalTracksTransitions() {
        assertEquals(WasmRmiClientChannel.State.CONNECTING, WasmRmiClient.connectionState().get());
        WasmRmiClient.onStateChange(WasmRmiClientChannel.State.CONNECTED);
        assertEquals(WasmRmiClientChannel.State.CONNECTED, WasmRmiClient.connectionState().get());
        drop();
        assertEquals(WasmRmiClientChannel.State.RECONNECTING, WasmRmiClient.connectionState().get());
    }

    // ------------------------------------------------------------------ recovery

    @Test
    public void reconnectResubscribesEverySharedSignal() throws Exception {
        Signals.shared("recovery.counter", "initial");
        assertEquals(1, channel.sent.size(), "declaring the signal subscribes it");
        channel.sent.clear();

        drop();
        restore();

        List<Frame> frames = decodeAll();
        assertEquals(1, frames.size());
        assertEquals(SyncFrameTypes.SIGNALS_SERVICE, frames.get(0).iface());
        assertEquals("subscribe", frames.get(0).method());
        assertEquals("recovery.counter", frames.get(0).args().get(0));
    }

    @Test
    public void reconnectRequestsResyncForEveryHeldObject() throws Exception {
        WasmRmiClient.MAPPER.registerWithId("handle-1", new Object());
        WasmRmiClient.MAPPER.registerWithId("handle-2", new Object());

        drop();
        restore();

        List<Frame> frames = decodeAll();
        assertEquals(1, frames.size());
        assertEquals(SyncFrameTypes.RESYNC_SERVICE, frames.get(0).iface());
        assertEquals("sync", frames.get(0).method());
        @SuppressWarnings("unchecked")
        List<Object> handles = (List<Object>) frames.get(0).args().get(0);
        assertTrue(handles.contains("handle-1") && handles.contains("handle-2"));
    }

    @Test
    public void reconnectWithNothingToRecoverSendsNothing() throws Exception {
        drop();
        restore();
        assertTrue(channel.sent.isEmpty(), "no signals, no objects: no traffic");
    }

    // ------------------------------------------------------------------ offline activity

    @Test
    public void offlineSignalWritesFlushOnceWithTheLatestValue() throws Exception {
        ValueSignal<String> note = Signals.sharedWritable("recovery.note", "a");
        channel.sent.clear();

        drop();
        note.set("b");
        note.set("c");
        assertTrue(channel.sent.isEmpty(), "writes while offline must not hit the socket");
        assertEquals("c", note.get(), "the optimistic local value stands");

        restore();

        List<Frame> frames = decodeAll();
        // The queued write must go out first, so the re-subscription's retained value
        // already reflects it; then the re-subscribe.
        assertEquals(2, frames.size());
        assertEquals("set", frames.get(0).method());
        assertEquals("recovery.note", frames.get(0).args().get(0));
        assertEquals("c", frames.get(0).args().get(1));
        assertEquals("subscribe", frames.get(1).method());
    }

    @Test
    public void offlineEditsToLiveObjectsAreRetainedAndFlushedOnReconnect() throws Exception {
        drop();
        LiveMutationTracker.fieldChanged("edited-while-offline");
        assertTrue(channel.sent.isEmpty(), "the edit is retained, not written into a dead socket");

        restore();

        List<Frame> frames = decodeAll();
        assertEquals(1, frames.size());
        assertEquals(SyncFrameTypes.LIVESYNC_SERVICE, frames.get(0).iface());
        assertEquals("mutate", frames.get(0).method());
        assertEquals("edited-while-offline", frames.get(0).args().get(0));
    }

    // ------------------------------------------------------------------ mutex loss

    @Test
    public void heldMutexReportsItselfLostOnDrop() {
        RmiClientExecutor.setInstance((iface, method, args) -> null); // lock() succeeds silently
        List<String> events = new ArrayList<>();

        LiveMutex mutex = new ClientLiveMutexProvider().create(new Object());
        mutex.setLostListener(() -> events.add("lost"));
        mutex.lock();

        drop();

        assertEquals(List.of("lost"), events);

        // A second drop must not re-fire: the loss was already reported.
        WasmRmiClient.onStateChange(WasmRmiClientChannel.State.CLOSED);
        assertEquals(List.of("lost"), events);
    }

    @Test
    public void unlockedMutexDoesNotReportLoss() {
        RmiClientExecutor.setInstance((iface, method, args) -> null);
        List<String> events = new ArrayList<>();

        LiveMutex mutex = new ClientLiveMutexProvider().create(new Object());
        mutex.setLostListener(() -> events.add("lost"));
        mutex.lock();
        mutex.unlock();

        drop();

        assertTrue(events.isEmpty(), "a lock released before the drop was not lost");
    }
}
