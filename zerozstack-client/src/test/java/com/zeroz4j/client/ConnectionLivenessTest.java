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
import com.zeroz4j.api.RequestTimeoutException;
import com.zeroz4j.api.SyncFrameTypes;
import com.zeroz4j.signals.Signals;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A call on a connection that has gone silent must end, and must end saying why.
 *
 * <p>Two mechanisms, both driven here with a clock the test moves by hand so nothing depends on how
 * long a step takes: the request timer, which fails a call at its deadline with nothing else
 * arriving; and the liveness check, which gives up on a connection whose ping went unanswered, so
 * the calls on it fail as disconnected long before their deadline.</p>
 *
 * <p>What the JVM cannot show is the browser half - that closing the socket really starts a
 * reconnect, and that {@code setInterval} really ticks. The routing tour's browser test covers
 * that, against a proxy that stops forwarding without closing anything.</p>
 */
class ConnectionLivenessTest {

    /** A channel that records what it sends, and behaves like a real one when given up on. */
    static final class FakeChannel implements WasmWebSocketChannel {
        final List<byte[]> sent = new ArrayList<>();
        final List<String> dropped = new ArrayList<>();
        boolean open = true;

        @Override
        public void registerBinaryMessageHandler(BinaryMessageHandler handler) {
        }

        @Override
        public void sendRawBytes(byte[] bytes) {
            sent.add(bytes);
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void dropUnresponsive(String reason) {
            dropped.add(reason);
            open = false;
            WasmRmiClient.onStateChange(WasmRmiClientChannel.State.RECONNECTING);
        }

        int pings() {
            int count = 0;
            for (byte[] frame : sent) {
                ByteBuffer buffer = ByteBuffer.wrap(frame);
                buffer.getInt();
                if (SyncFrameTypes.KEEPALIVE_SERVICE.equals(BinarySerializer.readString(buffer))) {
                    count++;
                }
            }
            return count;
        }
    }

    static final class Callback implements org.teavm.interop.AsyncCallback<Object> {
        Object result;
        Throwable error;

        @Override
        public void complete(Object value) {
            result = value;
        }

        @Override
        public void error(Throwable e) {
            error = e;
        }
    }

    private FakeChannel channel;
    private double start;

    @BeforeEach
    void setUp() {
        Signals.resetForTesting();
        ClientSignalTransport.resetForTesting();
        LiveMutations.resetForTesting();
        ClientLiveMutexProvider.resetForTesting();
        WasmRmiClient.pendingRequests.clear();
        channel = new FakeChannel();
        WasmRmiClient.initialize(channel);
        WasmRmiClient.resetConnectionStateForTesting();
        WasmRmiClient.onStateChange(WasmRmiClientChannel.State.CONNECTED);
        start = System.currentTimeMillis();
        Keepalive.resetForTesting(start);
        channel.sent.clear();
    }

    @AfterEach
    void tearDown() {
        WasmRmiClient.setRequestTimeout(30_000);
        WasmRmiClient.pendingRequests.clear();
        Keepalive.resetForTesting(System.currentTimeMillis());
    }

    private Callback call() {
        Callback callback = new Callback();
        WasmRmiClient.executeCall("SlowService", "neverAnswers", new Object[0], callback);
        return callback;
    }

    private long sentAt() {
        return WasmRmiClient.pendingRequests.values().iterator().next().createdAtMs;
    }

    // ---------------------------------------------------------------- the request timer

    @Test
    void theTimerFailsAnOverdueCallWithNothingElseArriving() {
        WasmRmiClient.setRequestTimeout(3_000);
        Callback callback = call();
        long sent = sentAt();

        WasmRmiClient.timeoutTick(sent + 2_999);
        assertNull(callback.error, "not due yet");

        WasmRmiClient.timeoutTick(sent + 3_000);
        assertNull(callback.error, "the first look that finds it overdue only notes it");

        WasmRmiClient.timeoutTick(sent + 4_000);
        assertInstanceOf(RequestTimeoutException.class, callback.error,
                "a timeout is its own type, so it can be told from a refusal");
        assertTrue(callback.error.getMessage().contains("SlowService#neverAnswers"),
                callback.error.getMessage());
        assertTrue(WasmRmiClient.pendingRequests.isEmpty());
    }

    @Test
    void anAnswerArrivingBeforeTheSecondLookStillWins() {
        WasmRmiClient.setRequestTimeout(3_000);
        Callback callback = call();
        long sent = sentAt();
        WasmRmiClient.timeoutTick(sent + 60_000);   // a tab waking from a long sleep

        // The answer was sitting in the queue behind the timer.
        WasmRmiClient.pendingRequests.values().iterator().next().callback.complete("answer");
        WasmRmiClient.pendingRequests.clear();
        WasmRmiClient.timeoutTick(sent + 61_000);

        assertEquals("answer", callback.result);
        assertNull(callback.error);
    }

    @Test
    void aTimeoutOfZeroTurnsTheTimerOff() {
        WasmRmiClient.setRequestTimeout(0);
        Callback callback = call();
        long sent = sentAt();

        WasmRmiClient.timeoutTick(sent + 3_600_000);
        WasmRmiClient.timeoutTick(sent + 7_200_000);

        assertNull(callback.error);
    }

    // ---------------------------------------------------------------- liveness

    @Test
    void aCallWaitingInSilenceAsksWhetherTheConnectionIsAlive() {
        call();
        long sent = sentAt();
        channel.sent.clear();

        Keepalive.tickAt(sent + 4_000);
        assertEquals(0, channel.pings(), "not yet: a call may legitimately take a few seconds");

        Keepalive.tickAt(sent + Keepalive.PROBE_AFTER_SECONDS * 1000 + 1_000);
        assertEquals(1, channel.pings(), "a call waiting with nothing arriving sends a ping");
    }

    @Test
    void anUnansweredPingDropsTheConnectionAndFailsTheCallAsDisconnected() {
        WasmRmiClient.setRequestTimeout(30_000);
        Callback callback = call();
        long sent = sentAt();
        double pingAt = sent + 6_000;

        Keepalive.tickAt(pingAt);
        assertEquals(1, channel.pings());

        Keepalive.tickAt(pingAt + 1_000);
        Keepalive.tickAt(pingAt + 9_000);
        assertTrue(channel.dropped.isEmpty(), "still inside the liveness period");

        Keepalive.tickAt(pingAt + Keepalive.DEFAULT_LIVENESS_SECONDS * 1000);
        assertEquals(1, channel.dropped.size(), "no answer in time: the connection is given up on");
        assertInstanceOf(DisconnectedException.class, callback.error,
                "and the call fails at once as disconnected, long before its 30-second deadline");
    }

    @Test
    void anythingArrivingSettlesThePing() {
        call();
        long sent = sentAt();
        double pingAt = sent + 6_000;
        Keepalive.tickAt(pingAt);

        Keepalive.noteReceived();   // the PONG, or any other frame
        Keepalive.tickAt(pingAt + 30_000);
        Keepalive.tickAt(pingAt + 31_000);

        assertTrue(channel.dropped.isEmpty());
    }

    @Test
    void anIdlePingIsAlsoExpectedToBeAnswered() {
        double idleAt = start + Keepalive.DEFAULT_SECONDS * 1000 + 1_000;

        Keepalive.tickAt(idleAt);
        assertEquals(1, channel.pings(), "the ordinary keepalive ping after silence");

        Keepalive.tickAt(idleAt + 1_000);
        Keepalive.tickAt(idleAt + Keepalive.DEFAULT_LIVENESS_SECONDS * 1000);
        assertEquals(1, channel.dropped.size());
    }

    @Test
    void aTabWakingUpIsNotJudgedOnItsFirstTick() {
        call();
        long sent = sentAt();
        double pingAt = sent + 6_000;
        Keepalive.tickAt(pingAt);

        Keepalive.tickAt(pingAt + 600_000);   // ten minutes asleep; the PONG may be queued behind this
        assertTrue(channel.dropped.isEmpty(), "one look after waking is not enough to judge");

        Keepalive.tickAt(pingAt + 601_000);
        assertEquals(1, channel.dropped.size());
    }

    @Test
    void livenessCanBeTurnedOff() {
        Keepalive.configureLiveness(0);
        Callback callback = call();
        long sent = sentAt();

        for (int second = 1; second <= 20; second++) {
            Keepalive.tickAt(sent + second * 1000L);
        }
        assertEquals(0, channel.pings(), "no probe pings when the check is off");

        // The ordinary idle keepalive still pings after 25 seconds, and nothing waits for its answer.
        for (int second = 21; second <= 120; second++) {
            Keepalive.tickAt(sent + second * 1000L);
        }
        assertTrue(channel.pings() > 0, "the idle keepalive is unaffected");
        assertTrue(channel.dropped.isEmpty());
        assertNull(callback.error);
    }

    @Test
    void aClosedChannelIsNeitherPingedNorDropped() {
        call();
        long sent = sentAt();
        Keepalive.tickAt(sent + 6_000);
        channel.open = false;

        Keepalive.tickAt(sent + 20_000);
        Keepalive.tickAt(sent + 30_000);

        assertTrue(channel.dropped.isEmpty());
    }
}
