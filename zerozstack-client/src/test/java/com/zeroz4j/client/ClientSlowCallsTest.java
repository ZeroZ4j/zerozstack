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
import com.zeroz4j.api.RmiClientExecutor;
import com.zeroz4j.api.SlowCall;
import com.zeroz4j.api.SlowCallBoard;
import com.zeroz4j.api.SlowCallStatus;
import com.zeroz4j.api.SyncFrameTypes;
import com.zeroz4j.signals.Effect;
import com.zeroz4j.signals.Signals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The browser half of a slow call, fed boards the way the signal transport feeds them.
 */
class ClientSlowCallsTest {

    private ReconnectRecoveryTest.FakeChannel channel;
    private final List<String> calls = new ArrayList<>();
    /** What the next collect does: a value to answer with, or a RuntimeException to throw. */
    private volatile Object collectAnswer = "the value";

    @BeforeEach
    void setup() {
        Signals.resetForTesting();
        ClientSignalTransport.resetForTesting();
        WasmRmiClient.pendingRequests.clear();
        channel = new ReconnectRecoveryTest.FakeChannel();
        WasmRmiClient.initialize(channel);
        RmiClientExecutor.setInstance((service, method, args) -> {
            synchronized (calls) {
                calls.add(method + ":" + args[0]);
            }
            if (collectAnswer instanceof RuntimeException failure) {
                throw failure;
            }
            return "collect".equals(method) ? collectAnswer : null;
        });
    }

    private static SlowCallBoard.Entry entry(String id, String key, SlowCallStatus status,
                                             int percent, String message) {
        return new SlowCallBoard.Entry(id, key, status, percent, null, message);
    }

    private static void board(SlowCallBoard.Entry... entries) {
        ClientSignalTransport.handleUpdate(SlowCallBoard.SIGNAL_NAME,
                new SlowCallBoard(Arrays.asList(entries)));
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(1);
        }
        assertTrue(condition.getAsBoolean(), "timed out");
    }

    private int collects() {
        synchronized (calls) {
            return (int) calls.stream().filter(call -> call.startsWith("collect:")).count();
        }
    }

    @Test
    void nothingIsSentUntilAScreenTouchesASlowCall() throws Exception {
        assertEquals(0, channel.sent.size());

        SlowCall<String> call = SlowCall.find("report");
        assertEquals(0, channel.sent.size(), "making a handle asks nothing");

        call.state();

        assertEquals(1, channel.sent.size());
        ByteBuffer frame = ByteBuffer.wrap(channel.sent.get(0));
        frame.getInt();
        assertEquals(SyncFrameTypes.SIGNALS_SERVICE, BinarySerializer.readString(frame));
        assertEquals("subscribe", BinarySerializer.readString(frame));
    }

    @Test
    void aFoundKeyGoesFromCheckingToIdle() {
        SlowCall<String> call = SlowCall.find("report");
        assertEquals(SlowCallStatus.CHECKING, call.state().status());

        board();

        assertEquals(SlowCallStatus.IDLE, call.state().status());
    }

    @Test
    void anEffectFollowsTheWholeLife() throws Exception {
        SlowCall<String> call = new SlowCall<>("report", "run-1");
        List<SlowCallStatus> seen = new ArrayList<>();
        List<Integer> percents = new ArrayList<>();

        Effect.create(() -> {
            seen.add(call.state().status());
            percents.add(call.state().percent());
        });
        assertEquals(List.of(SlowCallStatus.RUNNING), seen, "a handle from a call starts running");

        board(entry("run-1", "report", SlowCallStatus.RUNNING, 40, null));
        assertEquals(40, percents.get(percents.size() - 1));

        board(entry("run-1", "report", SlowCallStatus.FINISHED, 100, null));
        await(() -> seen.get(seen.size() - 1) == SlowCallStatus.FINISHED);

        assertEquals("the value", call.state().value());
        assertEquals(1, collects());
    }

    @Test
    void aBoardRepeatedAfterTheValueArrivedDoesNotFetchAgain() throws Exception {
        SlowCall<String> call = new SlowCall<>("report", "run-1");
        call.state();
        board(entry("run-1", "report", SlowCallStatus.FINISHED, 100, null));
        await(() -> call.state().isFinished());

        board(entry("run-1", "report", SlowCallStatus.FINISHED, 100, null));

        assertEquals(1, collects());
    }

    @Test
    void aReloadedScreenCollectsWorkThatFinishedMeanwhile() throws Exception {
        SlowCall<String> found = SlowCall.find("report");
        found.state();

        board(entry("run-1", "report", SlowCallStatus.FINISHED, 100, null));

        await(() -> found.state().isFinished());
        assertEquals("the value", found.state().value());
    }

    @Test
    void aDropDuringTheFetchIsRetriedByTheBoardAfterTheReconnect() throws Exception {
        collectAnswer = new DisconnectedException("Connection lost.");
        SlowCall<String> call = new SlowCall<>("report", "run-1");
        call.state();
        board(entry("run-1", "report", SlowCallStatus.FINISHED, 100, null));
        await(() -> collects() == 1);
        Thread.sleep(20);
        assertTrue(call.state().isRunning(), "still waiting, not failed");

        collectAnswer = "the value";
        board(entry("run-1", "report", SlowCallStatus.FINISHED, 100, null));

        await(() -> call.state().isFinished());
        assertEquals(2, collects());
    }

    @Test
    void workMissingFromTheBoardAfterARestartBecomesASentence() {
        SlowCall<String> call = new SlowCall<>("report", "run-1");
        call.state();
        board(entry("run-1", "report", SlowCallStatus.RUNNING, 40, null));

        board(); // What the first board after a server restart looks like.

        assertTrue(call.state().isFailed());
        assertEquals("The server no longer has this work. Start it again.",
                call.state().message());
    }

    @Test
    void aValueInHandSurvivesItsExpiryOnTheServer() throws Exception {
        SlowCall<String> call = new SlowCall<>("report", "run-1");
        call.state();
        board(entry("run-1", "report", SlowCallStatus.FINISHED, 100, null));
        await(() -> call.state().isFinished());

        board();

        assertEquals("the value", call.state().value());
    }

    @Test
    void failedAndCanceledComeFromTheBoard() {
        SlowCall<String> failing = new SlowCall<>("a", "run-1");
        SlowCall<String> stopped = new SlowCall<>("b", "run-2");
        failing.state();
        stopped.state();

        board(entry("run-1", "a", SlowCallStatus.FAILED, -1, "The model is not available."),
                entry("run-2", "b", SlowCallStatus.CANCELED, -1, null));

        assertEquals("The model is not available.", failing.state().message());
        assertTrue(stopped.state().isCanceled());
    }

    @Test
    void aFetchRefusedByTheServerBecomesItsSentence() throws Exception {
        collectAnswer = new RuntimeException("The server no longer has this work. Start it again.");
        SlowCall<String> call = new SlowCall<>("report", "run-1");
        call.state();

        board(entry("run-1", "report", SlowCallStatus.FINISHED, 100, null));

        await(() -> call.state().isFailed());
    }

    @Test
    void aRestartOfTheKeyFromAnotherTabIsFollowed() throws Exception {
        SlowCall<String> call = new SlowCall<>("report", "run-1");
        call.state();
        board(entry("run-1", "report", SlowCallStatus.FINISHED, 100, null));
        await(() -> call.state().isFinished());

        board(entry("run-2", "report", SlowCallStatus.RUNNING, -1, null));
        assertTrue(call.state().isRunning());

        collectAnswer = "the second value";
        board(entry("run-2", "report", SlowCallStatus.FINISHED, 100, null));
        await(() -> "the second value".equals(call.state().value()));
    }

    @Test
    void anEffectStartingToFollowANewKeyDuringABoardIsSafe() {
        SlowCall<String> first = new SlowCall<>("a", "run-1");
        AtomicInteger runs = new AtomicInteger();
        Effect.create(() -> {
            first.state();
            if (runs.incrementAndGet() > 1) {
                SlowCall.find("b").state();
            }
        });

        board(entry("run-1", "a", SlowCallStatus.RUNNING, 10, null));

        assertEquals(2, runs.get());
    }

    @Test
    void cancelAsksTheServerByKey() {
        SlowCall.find("report").cancel();

        assertEquals(List.of("cancel:report"), calls);
    }
}
