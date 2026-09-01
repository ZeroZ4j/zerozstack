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
import com.zeroz4j.api.LiveMutationTracker;
import com.zeroz4j.api.SyncFrameTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.teavm.interop.AsyncCallback;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Typing sends one message per pause, not one per character - and nothing is lost doing it.
 *
 * <p>The browser's own clock and timer are replaced here so time can be moved a millisecond at a
 * time. The measurement that matters is the browser proof run, which counts the messages the
 * server actually received; these tests hold the two ways a debounce loses somebody's work:
 * a person who never stops typing must still have their work sent, and a call made straight after
 * typing must not reach the server before the typing does.</p>
 */
public class LiveMutationDebounceTest {

    /** A channel whose openness the test controls. */
    static class FakeChannel implements WasmWebSocketChannel {
        final List<byte[]> sent = new ArrayList<>();
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

    /** Time the test moves by hand, plus the callbacks waiting for it. */
    static class FakeTime implements LiveMutations.Clock, LiveMutations.Delayer {
        long now = 1_000_000L;
        private final List<long[]> dueAt = new ArrayList<>();
        private final List<Runnable> tasks = new ArrayList<>();

        @Override
        public long nowMillis() {
            return now;
        }

        @Override
        public boolean after(int millis, Runnable task) {
            dueAt.add(new long[]{now + millis});
            tasks.add(task);
            return true;
        }

        /** Moves the clock forward one step, running whatever falls due on the way. */
        void advance(long millis) {
            long target = now + millis;
            while (true) {
                int next = -1;
                for (int i = 0; i < tasks.size(); i++) {
                    if (dueAt.get(i)[0] <= target
                            && (next < 0 || dueAt.get(i)[0] < dueAt.get(next)[0])) {
                        next = i;
                    }
                }
                if (next < 0) {
                    now = target;
                    return;
                }
                now = dueAt.get(next)[0];
                Runnable task = tasks.remove(next);
                dueAt.remove(next);
                task.run();
            }
        }
    }

    /** One decoded fire-and-forget framework frame. */
    record Frame(String iface, String method, List<Object> args) {
    }

    private FakeChannel channel;
    private FakeTime time;

    @BeforeEach
    public void setup() {
        LiveMutations.resetForTesting();
        WasmRmiClient.pendingRequests.clear();
        WasmRmiClient.MAPPER.clear();
        channel = new FakeChannel();
        WasmRmiClient.initialize(channel);
        WasmRmiClient.resetConnectionStateForTesting();
        channel.sent.clear();
        time = new FakeTime();
        LiveMutations.useForTesting(time, time);
    }

    @AfterEach
    public void teardown() {
        LiveMutations.resetForTesting();
    }

    private List<Frame> framesSent() {
        List<Frame> frames = new ArrayList<>();
        for (byte[] raw : channel.sent) {
            ByteBuffer buffer = ByteBuffer.wrap(raw);
            buffer.getInt();
            String iface = BinarySerializer.readString(buffer);
            String method = BinarySerializer.readString(buffer);
            int count = buffer.getInt();
            List<Object> args = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                args.add(BinarySerializer.readValue(buffer, WasmRmiClient.MAPPER));
            }
            frames.add(new Frame(iface, method, args));
        }
        return frames;
    }

    private List<Frame> mutationsSent() {
        List<Frame> mutations = new ArrayList<>();
        for (Frame frame : framesSent()) {
            if (SyncFrameTypes.LIVESYNC_SERVICE.equals(frame.iface())) {
                mutations.add(frame);
            }
        }
        return mutations;
    }

    // -------------------------------------------------------------- one message per pause

    @Test
    @DisplayName("a burst of keystrokes is one message, sent after the typing stops")
    public void aBurstOfKeystrokesIsOneMessage() {
        List<String> typed = new ArrayList<>();

        // Twelve characters at 40 ms apart - a fast typist, well inside the quiet period.
        for (int i = 0; i < 12; i++) {
            typed.add(String.valueOf((char) ('a' + i)));
            LiveMutationTracker.fieldChanged(typed);
            time.advance(40);
        }
        assertTrue(mutationsSent().isEmpty(), "nothing goes out while the person is still typing");

        time.advance(LiveMutations.DEFAULT_QUIET_MILLIS);

        List<Frame> mutations = mutationsSent();
        assertEquals(1, mutations.size(), "twelve characters, one message");
        assertEquals(typed, mutations.get(0).args().get(0),
                "the whole typed text is in it, last character included");
    }

    @Test
    @DisplayName("nothing is sent again once the burst has gone")
    public void anIdleTimerSendsNothing() {
        LiveMutationTracker.fieldChanged(new ArrayList<>(List.of("x")));
        time.advance(5_000);

        assertEquals(1, mutationsSent().size());
    }

    // -------------------------------------------------------------- the ceiling

    @Test
    @DisplayName("somebody who never stops typing still has their work sent, about once a second")
    public void continuousTypingIsSentPeriodically() {
        List<String> typed = new ArrayList<>();

        // Ten seconds of typing at 40 ms a character: 250 characters, never a pause long enough
        // for the quiet period. Without a ceiling this would send nothing at all for ten seconds.
        for (int i = 0; i < 250; i++) {
            typed.add("x");
            LiveMutationTracker.fieldChanged(typed);
            time.advance(40);
        }

        int sentWhileTyping = mutationsSent().size();
        assertTrue(sentWhileTyping >= 9 && sentWhileTyping <= 11,
                "ten seconds of unbroken typing should send about ten messages, not 250 and not"
                        + " none - sent " + sentWhileTyping);

        time.advance(LiveMutations.DEFAULT_QUIET_MILLIS);
        List<Frame> mutations = mutationsSent();
        assertEquals(typed, mutations.get(mutations.size() - 1).args().get(0),
                "and the last message carries every character typed");
    }

    @Test
    @DisplayName("turning the waiting off restores a message per setter call")
    public void aQuietPeriodOfZeroSendsImmediately() {
        LiveMutations.configure(0, 0);

        List<String> typed = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            typed.add("x");
            LiveMutationTracker.fieldChanged(typed);
        }

        assertEquals(3, mutationsSent().size());
    }

    // -------------------------------------------------------------- nothing overtakes an edit

    @Test
    @DisplayName("a service call cannot reach the server before the edit the person just typed")
    public void aServiceCallDoesNotOvertakeAWaitingEdit() {
        List<String> typed = new ArrayList<>(List.of("the new topic"));
        LiveMutationTracker.fieldChanged(typed);
        assertTrue(mutationsSent().isEmpty(), "the edit is still waiting out its quiet period");

        // The person presses a button before that period is over.
        WasmRmiClient.executeCall("com.example.ChatService", "post", new Object[]{"hello"},
                new AsyncCallback<>() {
                    @Override
                    public void complete(Object result) {
                    }

                    @Override
                    public void error(Throwable e) {
                    }
                });

        List<Frame> frames = framesSent();
        assertEquals(2, frames.size(), "both went out");
        assertEquals(SyncFrameTypes.LIVESYNC_SERVICE, frames.get(0).iface(),
                "the edit is written to the socket first, so the server reads it first");
        assertEquals(typed, frames.get(0).args().get(0));
        assertEquals("com.example.ChatService", frames.get(1).iface());
    }

    @Test
    @DisplayName("a call with nothing waiting sends only itself")
    public void aCallWithNoWaitingEditSendsOnlyItself() {
        WasmRmiClient.executeCall("com.example.ChatService", "post", new Object[]{"hello"},
                new AsyncCallback<>() {
                    @Override
                    public void complete(Object result) {
                    }

                    @Override
                    public void error(Throwable e) {
                    }
                });

        assertEquals(1, framesSent().size());
        assertTrue(mutationsSent().isEmpty());
    }

    // -------------------------------------------------------------- what was already true

    @Test
    @DisplayName("an edit made while the connection is down is kept, not written into a dead socket")
    public void anEditMadeWhileDisconnectedIsKept() {
        channel.open = false;

        List<String> typed = new ArrayList<>(List.of("written during an outage"));
        LiveMutationTracker.fieldChanged(typed);
        time.advance(5_000);

        assertTrue(channel.sent.isEmpty(), "nothing may be written into a socket that is down");

        channel.open = true;
        LiveMutations.flushPending();

        List<Frame> mutations = mutationsSent();
        assertEquals(1, mutations.size());
        assertEquals(typed, mutations.get(0).args().get(0));
    }

    @Test
    @DisplayName("two different objects that are equal are two edits, not one")
    public void twoEqualButDifferentObjectsAreBothSent() {
        List<String> first = new ArrayList<>(List.of("same text"));
        List<String> second = new ArrayList<>(List.of("same text"));

        LiveMutationTracker.fieldChanged(first);
        LiveMutationTracker.fieldChanged(second);
        time.advance(LiveMutations.DEFAULT_QUIET_MILLIS);

        assertEquals(2, mutationsSent().size(),
                "one of the two people's edits would otherwise be thrown away");
    }

    @Test
    @DisplayName("editing one object many times is still one message")
    public void repeatedEditsToOneObjectAreOneMessage() {
        List<String> typed = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            typed.add("x");
            LiveMutationTracker.fieldChanged(typed);
        }
        time.advance(LiveMutations.DEFAULT_QUIET_MILLIS);

        assertEquals(1, mutationsSent().size());
    }
}
