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
import com.zeroz4j.api.GrowableBuffer;
import com.zeroz4j.api.RmiSecurityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.teavm.interop.AsyncCallback;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class WasmRmiClientTest {

    private FakeWebSocketChannel channel;

    static class FakeWebSocketChannel implements WasmWebSocketChannel {
        public List<byte[]> sentMessages = new ArrayList<>();
        public BinaryMessageHandler handler;

        @Override
        public void sendRawBytes(byte[] payload) {
            sentMessages.add(payload);
        }

        @Override
        public void registerBinaryMessageHandler(BinaryMessageHandler handler) {
            this.handler = handler;
        }

    }

    static class FakeAsyncCallback implements AsyncCallback<Object> {
        public Object result;
        public Throwable error;
        public boolean completed;

        @Override
        public void complete(Object result) {
            this.result = result;
            this.completed = true;
        }

        @Override
        public void error(Throwable e) {
            this.error = e;
            this.completed = true;
        }
    }

    @BeforeEach
    public void setup() {
        com.zeroz4j.signals.Signals.resetForTesting();
        channel = new FakeWebSocketChannel();
        WasmRmiClient.pendingRequests.clear();
        WasmRmiClient.pushListeners.clear();
        WasmRmiClient.messageIdGenerator.set(0);
        WasmRmiClient.initialize(channel);
    }

    @Test
    public void testInitializeRegistersHandler() {
        assertNotNull(channel.handler, "Handler should be registered");
    }

    @Test
    public void testExecuteCallSendsMessageAndAwaitsResponse() throws Exception {
        FakeAsyncCallback callback = new FakeAsyncCallback();
        Object[] args = new Object[]{"World"};

        WasmRmiClient.executeCall("MyService", "sayHello", args, callback);

        assertEquals(1, channel.sentMessages.size());
        assertEquals(1, WasmRmiClient.pendingRequests.size());

        byte[] sent = channel.sentMessages.get(0);
        ByteBuffer buf = ByteBuffer.wrap(sent);
        int msgId = buf.getInt(); // correlation ID

        // Validate sent format
        assertEquals("MyService", BinarySerializer.readString(buf));
        assertEquals("sayHello", BinarySerializer.readString(buf));
        assertEquals(1, buf.getInt()); // arg count
        assertEquals("World", BinarySerializer.readValue(buf, WasmRmiClient.MAPPER));

        // Simulate server SUCCESS response
        GrowableBuffer resp = new GrowableBuffer();
        resp.putInt(msgId);
        resp.put((byte) 0x01); // SUCCESS
        BinarySerializer.writeValue(resp, "Hello World", WasmRmiClient.MAPPER);

        WasmRmiClient.routeIncomingMessage(resp.toByteArray());

        assertTrue(callback.completed);
        assertEquals("Hello World", callback.result);
        assertNull(callback.error);
        assertEquals(0, WasmRmiClient.pendingRequests.size());
    }

    @Test
    public void testExecuteCallHandlesErrorResponse() throws Exception {
        FakeAsyncCallback callback = new FakeAsyncCallback();
        WasmRmiClient.executeCall("MyService", "throwError", null, callback);

        byte[] sent = channel.sentMessages.get(0);
        int msgId = ByteBuffer.wrap(sent).getInt();

        // Simulate server ERROR response
        GrowableBuffer resp = new GrowableBuffer();
        resp.putInt(msgId);
        resp.put((byte) 0x0F); // ERROR
        BinarySerializer.writeString(resp, "Server Error Occurred");

        WasmRmiClient.routeIncomingMessage(resp.toByteArray());

        assertTrue(callback.completed);
        assertNotNull(callback.error);
        assertEquals("Server Error Occurred", callback.error.getMessage());
    }

    /** Builds an AUTH frame the way the server does. */
    /**
     * An AUTH frame from a server that only knows version 2 - no catalog after the roles.
     *
     * <p>Deliberately still version 2: this is the older-server half of the protocol change, and a
     * client that read past the end of one of these would report a broken connection where the only
     * thing missing is a translation.</p>
     */
    private static byte[] authFrame(boolean authenticated, String username, String... roles) {
        GrowableBuffer frame = new GrowableBuffer();
        frame.putInt(0); // Correlation ID (0 for broadcast)
        frame.put((byte) 0x03); // AUTH frame
        frame.put((byte) 2); // Protocol version
        frame.put((byte) (authenticated ? 1 : 0));
        BinarySerializer.writeString(frame, username);
        frame.putInt(roles.length);
        for (String role : roles) {
            BinarySerializer.writeString(frame, role);
        }
        return frame.toByteArray();
    }

    @Test
    public void testRouteIncomingAuthFrame() throws Exception {
        WasmRmiClient.routeIncomingMessage(authFrame(true, "alice", "admin", "user"));

        assertTrue(RmiSecurityContext.isAuthenticated());
        assertTrue(RmiSecurityContext.isResolved());
        assertEquals("alice", RmiSecurityContext.getUsername());
        assertTrue(RmiSecurityContext.hasAnyRole("admin"));
        assertTrue(RmiSecurityContext.hasAnyRole("user"));
        assertFalse(RmiSecurityContext.hasAnyRole("guest"));
    }

    /**
     * A version-3 AUTH frame: the roles, then the language, the languages on offer, and the words.
     */
    private static byte[] authFrameWithCatalog(String language, String greeting) {
        GrowableBuffer frame = new GrowableBuffer();
        frame.putInt(0);
        frame.put((byte) 0x03);
        frame.put((byte) 3);
        frame.put((byte) 1);
        BinarySerializer.writeString(frame, "alice");
        frame.putInt(0);
        BinarySerializer.writeString(frame, language);
        frame.putInt(2);
        BinarySerializer.writeString(frame, "en");
        BinarySerializer.writeString(frame, "de");
        frame.putInt(1);
        BinarySerializer.writeString(frame, "i18n/app");
        frame.putInt(1);
        BinarySerializer.writeString(frame, "greet");
        BinarySerializer.writeString(frame, greeting);
        return frame.toByteArray();
    }

    /**
     * The words arrive with the frame that says the connection is ready, and they are in hand
     * <b>before</b> the application is told it may build a screen - which is the whole reason they
     * ride on this frame rather than being fetched.
     */
    @Test
    public void testAVersion3FrameCarriesTheWords() throws Exception {
        com.zeroz4j.api.i18n.ClientMessages.forgetForTesting();
        com.zeroz4j.api.i18n.ClientMessages.install();
        RmiSecurityContext.clear();
        String[] seenWhenTold = { null };
        RmiSecurityContext.onResolved(() ->
                seenWhenTold[0] = new com.zeroz4j.api.i18n.Message("i18n/app", "greet").text());

        WasmRmiClient.routeIncomingMessage(authFrameWithCatalog("de", "Guten Tag"));

        assertEquals("Guten Tag", seenWhenTold[0],
                "the words must already be there when the application mounts its first screen, or "
                        + "that screen is drawn in English and corrected a moment later");
        assertEquals(java.util.Arrays.asList("en", "de"),
                com.zeroz4j.api.i18n.ClientMessages.offeredLanguages(),
                "a language selector offers exactly what the server said it can answer in");
        com.zeroz4j.api.i18n.ClientMessages.forgetForTesting();
    }

    /**
     * The bug this pins: a refused connection used to arrive as an ordinary frame naming it
     * "anonymous", and the client set authenticated = true regardless — so a login gate hung off
     * onAuthenticated let anyone straight through.
     */
    @Test
    public void testARejectedConnectionIsNotAuthenticated() throws Exception {
        RmiSecurityContext.clear();
        boolean[] succeeded = { false };
        boolean[] failed = { false };
        RmiSecurityContext.onAuthenticated(() -> succeeded[0] = true);
        RmiSecurityContext.onAuthenticationFailed(() -> failed[0] = true);

        WasmRmiClient.routeIncomingMessage(authFrame(false, "anonymous"));

        assertFalse(RmiSecurityContext.isAuthenticated(),
                "the provider declined; isAuthenticated() must say so without a role check");
        assertTrue(RmiSecurityContext.isResolved(), "the decision has arrived");
        assertFalse(succeeded[0], "a login gate on onAuthenticated must not open");
        assertTrue(failed[0], "the application needs a positive signal to show a login error");
    }

    @Test
    public void testAnAuthenticatedUserWithNoRolesIsStillAuthenticated() throws Exception {
        RmiSecurityContext.clear();

        WasmRmiClient.routeIncomingMessage(authFrame(true, "roleless"));

        assertTrue(RmiSecurityContext.isAuthenticated(),
                "an empty role set is not a failed sign-in; checking roles cannot substitute");
        assertEquals("roleless", RmiSecurityContext.getUsername());
    }

    /**
     * An application with no login is anonymous by design. Making {@code onAuthenticated} honest
     * about that broke every such application, because they used it as a "connection ready" signal
     * and so mounted nothing at all — a blank page. {@code onResolved} is that signal.
     */
    @Test
    public void testAnAnonymousConnectionStillReportsThatTheServerAnswered() throws Exception {
        RmiSecurityContext.clear();
        boolean[] resolved = { false };
        boolean[] authenticated = { false };
        RmiSecurityContext.onResolved(() -> resolved[0] = true);
        RmiSecurityContext.onAuthenticated(() -> authenticated[0] = true);

        WasmRmiClient.routeIncomingMessage(authFrame(false, "anonymous"));

        assertTrue(resolved[0], "an open application must still be told the connection is usable");
        assertFalse(authenticated[0], "but it is not a sign-in, and must not be reported as one");
    }

    @Test
    public void testOnResolvedAlsoRunsForARealSignIn() throws Exception {
        RmiSecurityContext.clear();
        boolean[] resolved = { false };

        WasmRmiClient.routeIncomingMessage(authFrame(true, "alice", "user"));
        RmiSecurityContext.onResolved(() -> resolved[0] = true);

        assertTrue(resolved[0], "registering late must not miss a decision already taken");
    }

    @Test
    public void testOnResolvedRunsBeforeTheOutcomeSpecificCallback() throws Exception {
        RmiSecurityContext.clear();
        java.util.List<String> order = new java.util.ArrayList<>();
        RmiSecurityContext.onAuthenticated(() -> order.add("authenticated"));
        RmiSecurityContext.onResolved(() -> order.add("resolved"));

        WasmRmiClient.routeIncomingMessage(authFrame(true, "alice", "user"));

        // The UI is mounted from onResolved, so it must exist before anything reacts to identity.
        assertEquals(java.util.List.of("resolved", "authenticated"), order);
    }

    @Test
    public void testACallbackRegisteredAfterTheDecisionStillRuns() throws Exception {
        RmiSecurityContext.clear();
        WasmRmiClient.routeIncomingMessage(authFrame(false, "anonymous"));

        boolean[] failed = { false };
        boolean[] succeeded = { false };
        RmiSecurityContext.onAuthenticationFailed(() -> failed[0] = true);
        RmiSecurityContext.onAuthenticated(() -> succeeded[0] = true);

        assertTrue(failed[0], "a late listener must not miss a decision already taken");
        assertFalse(succeeded[0]);
    }

    @Test
    public void testPushListeners() throws Exception {
        List<Object> receivedPushes = new ArrayList<>();
        WasmRmiClient.registerPushListener("testTopic", receivedPushes::add);

        GrowableBuffer pushFrame = new GrowableBuffer();
        pushFrame.putInt(0);
        pushFrame.put((byte) 0x02); // PUSH
        BinarySerializer.writeString(pushFrame, "testTopic");
        BinarySerializer.writeValue(pushFrame, "pushPayload", WasmRmiClient.MAPPER);

        WasmRmiClient.routeIncomingMessage(pushFrame.toByteArray());

        assertEquals(1, receivedPushes.size());
        assertEquals("pushPayload", receivedPushes.get(0));

        // Test remove
        WasmRmiClient.clearPushListeners("testTopic");
        receivedPushes.clear();

        WasmRmiClient.routeIncomingMessage(pushFrame.toByteArray());
        assertEquals(0, receivedPushes.size(), "Should not receive pushes after clear");
    }

    @Test
    public void testRemoveSinglePushListener() throws Exception {
        List<Object> first = new ArrayList<>();
        List<Object> second = new ArrayList<>();
        WasmRmiClient.PushListener<Object> firstListener = first::add;
        WasmRmiClient.PushListener<Object> secondListener = second::add;

        WasmRmiClient.registerPushListener("removeTopic", firstListener);
        WasmRmiClient.registerPushListener("removeTopic", secondListener);

        GrowableBuffer pushFrame = new GrowableBuffer();
        pushFrame.putInt(0);
        pushFrame.put((byte) 0x02); // PUSH
        BinarySerializer.writeString(pushFrame, "removeTopic");
        BinarySerializer.writeValue(pushFrame, "payload", WasmRmiClient.MAPPER);

        WasmRmiClient.routeIncomingMessage(pushFrame.toByteArray());
        assertEquals(1, first.size());
        assertEquals(1, second.size());

        WasmRmiClient.removePushListener("removeTopic", firstListener);

        WasmRmiClient.routeIncomingMessage(pushFrame.toByteArray());
        assertEquals(1, first.size(), "Removed listener should not receive further pushes");
        assertEquals(2, second.size(), "Remaining listener should keep receiving pushes");

        WasmRmiClient.clearPushListeners("removeTopic");
    }

    @Test
    public void testServerEventsOnAndDispose() throws Exception {
        com.zeroz4j.api.EventTopic<String> topic =
                com.zeroz4j.api.EventTopic.of(String.class, "events.test");

        List<String> received = new ArrayList<>();
        com.zeroz4j.api.Disposable subscription =
                ServerEvents.on(topic, received::add);

        GrowableBuffer pushFrame = new GrowableBuffer();
        pushFrame.putInt(0);
        pushFrame.put((byte) 0x02); // PUSH
        BinarySerializer.writeString(pushFrame, topic.name());
        BinarySerializer.writeValue(pushFrame, "hello", WasmRmiClient.MAPPER);

        WasmRmiClient.routeIncomingMessage(pushFrame.toByteArray());
        assertEquals(List.of("hello"), received);

        subscription.dispose();
        WasmRmiClient.routeIncomingMessage(pushFrame.toByteArray());
        assertEquals(1, received.size(), "Disposed subscription should not receive pushes");
    }

    @Test
    public void testServerEventsLatest() throws Exception {
        com.zeroz4j.api.EventTopic<String> topic =
                com.zeroz4j.api.EventTopic.of(String.class, "events.latest");

        ServerEvents.LatestSignal<String> latest = ServerEvents.latest(topic, "none");
        assertEquals("none", latest.get());

        List<String> observed = new ArrayList<>();
        com.zeroz4j.api.Disposable effect =
                com.zeroz4j.signals.Effect.create(() -> observed.add(latest.get()));

        GrowableBuffer pushFrame = new GrowableBuffer();
        pushFrame.putInt(0);
        pushFrame.put((byte) 0x02); // PUSH
        BinarySerializer.writeString(pushFrame, topic.name());
        BinarySerializer.writeValue(pushFrame, "online", WasmRmiClient.MAPPER);

        WasmRmiClient.routeIncomingMessage(pushFrame.toByteArray());
        assertEquals("online", latest.get());
        assertEquals(List.of("none", "online"), observed, "Effect should re-run on push");

        effect.dispose();
        latest.dispose();
    }

    @Test
    public void testSharedSignalMirror() throws Exception {
        com.zeroz4j.signals.ValueSignal<String> mirror =
                com.zeroz4j.signals.Signals.shared("test.shared", "initial");

        // Declaring the signal must send a subscribe request for the retained value
        assertEquals(1, channel.sentMessages.size());
        ByteBuffer sent = ByteBuffer.wrap(channel.sentMessages.get(0));
        assertEquals(0, sent.getInt()); // fire-and-forget, no correlation
        assertEquals("zeroz4j.signals", BinarySerializer.readString(sent));
        assertEquals("subscribe", BinarySerializer.readString(sent));
        assertEquals(1, sent.getInt());
        assertEquals("test.shared", BinarySerializer.readValue(sent, WasmRmiClient.MAPPER));

        // A SIGNAL_UPDATE frame applies to the mirror and notifies effects
        List<String> observed = new ArrayList<>();
        com.zeroz4j.signals.Effect.create(() -> observed.add(mirror.get()));

        GrowableBuffer update = new GrowableBuffer();
        update.putInt(0);
        update.put((byte) 0x17); // SIGNAL_UPD
        BinarySerializer.writeString(update, "test.shared");
        BinarySerializer.writeValue(update, "from server", WasmRmiClient.MAPPER);
        WasmRmiClient.routeIncomingMessage(update.toByteArray());

        assertEquals("from server", mirror.get());
        assertEquals(List.of("initial", "from server"), observed);

        // The mirror is server-authoritative: local writes fail
        assertThrows(IllegalStateException.class, () -> mirror.set("local write"));
    }

    @Test
    public void testWritableSharedSignalSendsOptimisticWrite() throws Exception {
        com.zeroz4j.signals.ValueSignal<String> mirror =
                com.zeroz4j.signals.Signals.sharedWritable("test.theme", "dark");
        assertEquals(1, channel.sentMessages.size()); // subscribe on declaration

        mirror.set("light");
        assertEquals("light", mirror.get(), "Optimistic local apply");

        assertEquals(2, channel.sentMessages.size());
        ByteBuffer sent = ByteBuffer.wrap(channel.sentMessages.get(1));
        assertEquals(0, sent.getInt());
        assertEquals("zeroz4j.signals", BinarySerializer.readString(sent));
        assertEquals("set", BinarySerializer.readString(sent));
        assertEquals(2, sent.getInt());
        assertEquals("test.theme", BinarySerializer.readValue(sent, WasmRmiClient.MAPPER));
        assertEquals("light", BinarySerializer.readValue(sent, WasmRmiClient.MAPPER));

        // A corrective SIGNAL_UPD (rejection) snaps the mirror back to server truth
        GrowableBuffer corrective = new GrowableBuffer();
        corrective.putInt(0);
        corrective.put((byte) 0x17); // SIGNAL_UPD
        BinarySerializer.writeString(corrective, "test.theme");
        BinarySerializer.writeValue(corrective, "dark", WasmRmiClient.MAPPER);
        WasmRmiClient.routeIncomingMessage(corrective.toByteArray());
        assertEquals("dark", mirror.get());
        assertEquals(2, channel.sentMessages.size(), "Corrective apply must not re-send");
    }

    @Test
    public void testLiveMutationSendsMutateFrame() throws Exception {
        // initialize() installed the mutation listener; with no scheduler, flush is immediate.
        com.zeroz4j.api.LiveMutationTracker.fieldChanged("mutated-state");

        assertEquals(1, channel.sentMessages.size());
        ByteBuffer sent = ByteBuffer.wrap(channel.sentMessages.get(0));
        assertEquals(0, sent.getInt());
        assertEquals("zeroz4j.livesync", BinarySerializer.readString(sent));
        assertEquals("mutate", BinarySerializer.readString(sent));
        assertEquals(1, sent.getInt());
        assertEquals("mutated-state", BinarySerializer.readValue(sent, WasmRmiClient.MAPPER));
    }

    @Test
    public void testPendingRequestTimeout() throws Exception {
        WasmRmiClient.setRequestTimeout(1);
        try {
            FakeAsyncCallback callback = new FakeAsyncCallback();
            WasmRmiClient.executeCall("SlowService", "neverAnswers", new Object[0], callback);
            assertEquals(1, WasmRmiClient.pendingRequests.size());

            Thread.sleep(20);

            // Any incoming frame triggers the sweep; use a push on an unrelated topic.
            GrowableBuffer pushFrame = new GrowableBuffer();
            pushFrame.putInt(0);
            pushFrame.put((byte) 0x02); // PUSH
            BinarySerializer.writeString(pushFrame, "unrelated.topic");
            BinarySerializer.writeValue(pushFrame, "x", WasmRmiClient.MAPPER);
            WasmRmiClient.routeIncomingMessage(pushFrame.toByteArray());

            assertTrue(callback.completed, "Stale request should be completed with an error");
            assertNotNull(callback.error);
            assertTrue(callback.error.getMessage().contains("timed out"));
            assertEquals(0, WasmRmiClient.pendingRequests.size());
        } finally {
            WasmRmiClient.setRequestTimeout(30_000);
        }
    }
}
