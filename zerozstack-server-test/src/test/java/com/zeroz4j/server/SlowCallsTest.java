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

import com.zeroz4j.api.RmiService;
import com.zeroz4j.api.RolesAllowed;
import com.zeroz4j.api.SlowCall;
import com.zeroz4j.api.SlowCallRpc;
import com.zeroz4j.api.SlowCallState;
import com.zeroz4j.api.SlowCallStatus;
import com.zeroz4j.server.test.TestConnection;
import com.zeroz4j.server.test.TestServer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slow calls driven through real frames on a real server.
 *
 * <p>In this package because expiry is counted in minutes, and {@link SlowCallRegistry#sweep(long)}
 * is how a test gets there without waiting for them.</p>
 */
class SlowCallsTest {

    private static final long WAIT = 5_000L;

    /** Holds work open until a test lets it end. */
    private static final Map<String, CountDownLatch> GATES = new ConcurrentHashMap<>();
    private static final AtomicBoolean INTERRUPTED = new AtomicBoolean();
    private static final AtomicBoolean SAW_CANCELED_FLAG = new AtomicBoolean();
    private static final AtomicInteger STARTED = new AtomicInteger();

    private static CountDownLatch gate(String name) {
        return GATES.computeIfAbsent(name, ignored -> new CountDownLatch(1));
    }

    @RmiService
    public interface ReportService {
        SlowCall<String> quick(String key, String value);

        SlowCall<String> gated(String key);

        SlowCall<String> caller();

        SlowCall<String> failsPlainly();

        SlowCall<String> failsWithSentence();

        @RolesAllowed("admin")
        SlowCall<String> adminOnly();

        SlowCall<String> blocksUntilCanceled();

        SlowCall<String> floodsProgress(int steps);

        void startsWithNoKey();
    }

    /** Proves the request scope is active inside the work. */
    @RequestScoped
    public static class RequestNote {
        public String value() {
            return "request-scope-active";
        }
    }

    @ApplicationScoped
    public static class ReportServiceImpl implements ReportService {

        @Inject
        SlowCalls slowCalls;

        @Inject
        RequestNote note;

        @Override
        public SlowCall<String> quick(String key, String value) {
            return slowCalls.start(key, job -> value);
        }

        @Override
        public SlowCall<String> gated(String key) {
            return slowCalls.start(key, job -> {
                STARTED.incrementAndGet();
                job.progress(40, "Halfway");
                gate(key).await();
                return "done " + key;
            });
        }

        @Override
        public SlowCall<String> caller() {
            return slowCalls.start("caller", job -> String.join("|",
                    RmiRequestContext.getPrincipal() == null
                            ? "nobody" : RmiRequestContext.getPrincipal().getName(),
                    String.valueOf(RmiRequestContext.getRoles().contains("agent")),
                    String.valueOf(RmiRequestContext.getTenantId()),
                    String.valueOf(RmiRequestContext.getClientId() != null),
                    String.valueOf(RmiRequestContext.getSessionId()),
                    note.value()));
        }

        @Override
        public SlowCall<String> failsPlainly() {
            return slowCalls.start("plain", job -> {
                throw new IllegalStateException("table REPORTS is locked");
            });
        }

        @Override
        public SlowCall<String> failsWithSentence() {
            return slowCalls.start("sentence", job -> {
                throw new ClientVisibleException("The model is not available.");
            });
        }

        @Override
        public SlowCall<String> adminOnly() {
            return slowCalls.start("admin", job -> {
                STARTED.incrementAndGet();
                return "secret";
            });
        }

        @Override
        public SlowCall<String> blocksUntilCanceled() {
            return slowCalls.start("blocking", job -> {
                STARTED.incrementAndGet();
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException stopped) {
                    INTERRUPTED.set(true);
                    SAW_CANCELED_FLAG.set(job.isCanceled());
                    throw stopped;
                }
                return "never";
            });
        }

        @Override
        public SlowCall<String> floodsProgress(int steps) {
            return slowCalls.start("flood", job -> {
                for (int i = 1; i <= steps; i++) {
                    job.progress(i * 100 / steps, "Step " + i);
                }
                gate("flood").await();
                return "flooded";
            });
        }

        @Override
        public void startsWithNoKey() {
            slowCalls.start("", job -> "x");
        }
    }

    @BeforeEach
    void reset() {
        GATES.clear();
        INTERRUPTED.set(false);
        SAW_CANCELED_FLAG.set(false);
        STARTED.set(0);
    }

    private static TestServer.Builder server() {
        return TestServer.builder().ignoringSystemProperties()
                .beans(ReportServiceImpl.class, RequestNote.class);
    }

    private static SlowCall<String> startGated(TestConnection browser, String key) {
        return browser.call(ReportService.class, "gated", key);
    }

    /** Waits until the work has said something, so a test is not racing the thread it started. */
    private static SlowCallState<String> awaitProgress(TestConnection browser,
                                                       SlowCall<String> call) {
        long deadline = System.currentTimeMillis() + WAIT;
        while (System.currentTimeMillis() < deadline) {
            SlowCallState<String> state = browser.slowCallState(call);
            if (state.percent() >= 0) {
                return state;
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("no progress from " + call);
    }

    // ------------------------------------------------------------------ the ordinary life

    @Test
    void theCallReturnsAtOnceAndTheValueComesLater() {
        try (TestServer server = server().start();
             TestConnection browser = server.connect("alice")) {

            SlowCall<String> call = startGated(browser, "report-1");

            assertEquals("report-1", call.key());
            assertEquals(SlowCallStatus.RUNNING, browser.slowCallState(call).status());

            gate("report-1").countDown();

            SlowCallState<String> end = browser.awaitSlowCall(call, WAIT);
            assertTrue(end.isFinished());
            assertEquals("done report-1", end.value());
        }
    }

    @Test
    void progressReachesTheBoard() {
        try (TestServer server = server().start();
             TestConnection browser = server.connect("alice")) {

            SlowCall<String> call = startGated(browser, "report-1");

            SlowCallState<String> state = awaitProgress(browser, call);

            assertEquals(40, state.percent());
            assertEquals("Halfway", state.progressText());
            gate("report-1").countDown();
        }
    }

    @Test
    void aFloodOfProgressStaysAFewFramesAndTheLastOneArrives() {
        try (TestServer server = server().start();
             TestConnection browser = server.connect("alice")) {

            browser.slowCallState(SlowCall.find("anything"));
            browser.clearFrames();

            SlowCall<String> call = browser.call(ReportService.class, "floodsProgress", 100_000);

            long deadline = System.currentTimeMillis() + WAIT;
            while (browser.slowCallState(call).percent() != 100
                    && System.currentTimeMillis() < deadline) {
                Thread.onSpinWait();
            }
            assertEquals(100, browser.slowCallState(call).percent());
            assertEquals("Step 100000", browser.slowCallState(call).progressText());
            assertTrue(browser.countOf(TestConnection.SIGNAL_UPDATE) < 40,
                    "progress is sent at most four times a second, sent "
                            + browser.countOf(TestConnection.SIGNAL_UPDATE));
            gate("flood").countDown();
        }
    }

    @Test
    void startingARunningKeyJoinsIt() {
        try (TestServer server = server().start();
             TestConnection browser = server.connect("alice")) {

            SlowCall<String> first = startGated(browser, "report-1");
            SlowCall<String> second = startGated(browser, "report-1");

            assertEquals(first.id(), second.id());
            gate("report-1").countDown();
            browser.awaitSlowCall(first, WAIT);
            assertEquals(1, STARTED.get(), "the work ran once");
        }
    }

    @Test
    void startingAnEndedKeyRunsItAgain() {
        try (TestServer server = server().start();
             TestConnection browser = server.connect("alice")) {

            SlowCall<String> first = browser.call(ReportService.class, "quick", "k", "one");
            assertEquals("one", browser.awaitSlowCall(first, WAIT).value());

            SlowCall<String> second = browser.call(ReportService.class, "quick", "k", "two");

            assertNotEquals(first.id(), second.id());
            assertEquals("two", browser.awaitSlowCall(second, WAIT).value());
        }
    }

    // ------------------------------------------------------------------ reconnect and reload

    @Test
    void aResultFinishedWhileTheConnectionWasDownIsCollectedAfterwards() throws Exception {
        try (TestServer server = server().start()) {
            TestConnection before = server.connect();
            SlowCall<String> call = startGated(before, "report-1");
            before.close();

            gate("report-1").countDown();
            while (server.runtime().slowCalls().runningCount() > 0) {
                Thread.sleep(1);
            }

            try (TestConnection after = server.reconnect(before)) {
                assertEquals(before.browserId(), after.browserId());
                assertNotEquals(before.id(), after.id());

                SlowCallState<String> end = after.awaitSlowCall(call, WAIT);

                assertEquals("done report-1", end.value());
            }
        }
    }

    @Test
    void aReloadedPageFindsItsRunningWorkByKey() {
        try (TestServer server = server().start()) {
            TestConnection before = server.connect();
            startGated(before, "report-1");
            before.close();

            try (TestConnection reloaded = server.reconnect(before)) {
                // A reloaded page has no handle left, only the key.
                SlowCall<String> found = SlowCall.find("report-1");

                assertTrue(reloaded.slowCallState(found).isRunning());

                gate("report-1").countDown();
                assertEquals("done report-1", reloaded.awaitSlowCall(found, WAIT).value());
            }
        }
    }

    @Test
    void aKeyWithNothingUnderItIsIdle() {
        try (TestServer server = server().start();
             TestConnection browser = server.connect()) {
            assertTrue(browser.slowCallState(SlowCall.find("never-started")).isIdle());
        }
    }

    @Test
    void aSignedInUserFollowsTheirWorkFromAnotherBrowser() {
        try (TestServer server = server().start();
             TestConnection laptop = server.connect("alice");
             TestConnection phone = server.connect("alice")) {

            startGated(laptop, "report-1");

            assertTrue(phone.slowCallState(SlowCall.find("report-1")).isRunning());
            gate("report-1").countDown();
        }
    }

    @Test
    void aSecondTabOfOneBrowserHearsWithoutAskingAgain() {
        try (TestServer server = server().start();
             TestConnection tab = server.connect("alice");
             TestConnection other = server.connect("alice")) {

            other.slowCallState(SlowCall.find("report-1"));
            other.clearFrames();

            startGated(tab, "report-1");

            assertTrue(other.countOf(TestConnection.SIGNAL_UPDATE) > 0, "the board was pushed");
            gate("report-1").countDown();
        }
    }

    @Test
    void anAppThatUsesNoSlowCallsIsSentNoBoard() {
        try (TestServer server = server().start();
             TestConnection subscribed = server.connect("alice");
             TestConnection silent = server.connect("bob")) {

            subscribed.slowCallState(SlowCall.find("x"));
            silent.clearFrames();

            startGated(silent, "report-1");

            assertEquals(0, silent.countOf(TestConnection.SIGNAL_UPDATE));
            gate("report-1").countDown();
        }
    }

    // ------------------------------------------------------------------ who may see what

    @Test
    void anotherBrowserCanNeitherSeeNorCollectNorCancel() {
        try (TestServer server = server().start();
             TestConnection owner = server.connect();
             TestConnection stranger = server.connect()) {

            SlowCall<String> call = startGated(owner, "report-1");
            gate("report-1").countDown();
            SlowCallState<String> end = owner.awaitSlowCall(call, WAIT);
            assertTrue(end.isFinished());

            assertTrue(stranger.slowCallState(SlowCall.find("report-1")).isIdle());
            IllegalStateException refused = assertThrows(IllegalStateException.class,
                    () -> stranger.call(SlowCallRpc.class, "collect", call.id()));
            assertEquals("The server no longer has this work. Start it again.",
                    refused.getMessage());
        }
    }

    @Test
    void aStrangerCannotCancelRunningWork() {
        try (TestServer server = server().start();
             TestConnection owner = server.connect("alice");
             TestConnection stranger = server.connect("mallory")) {

            SlowCall<String> call = startGated(owner, "report-1");

            stranger.cancelSlowCall(call);

            assertTrue(owner.slowCallState(call).isRunning());
            gate("report-1").countDown();
        }
    }

    @Test
    void theSameUserNameInAnotherTenantIsSomebodyElse() {
        try (TestServer server = server().start();
             TestConnection acme = server.connectAsTenant("admin", "acme");
             TestConnection globex = server.connectAsTenant("admin", "globex")) {

            SlowCall<String> call = startGated(acme, "report-1");

            assertTrue(globex.slowCallState(SlowCall.find("report-1")).isIdle());
            gate("report-1").countDown();
            acme.awaitSlowCall(call, WAIT);
            assertThrows(IllegalStateException.class,
                    () -> globex.call(SlowCallRpc.class, "collect", call.id()));
        }
    }

    @Test
    void rolesAreCheckedBeforeTheWorkStarts() {
        try (TestServer server = server().start();
             TestConnection browser = server.connect("alice", "user")) {

            assertThrows(IllegalStateException.class,
                    () -> browser.call(ReportService.class, "adminOnly"));

            assertEquals(0, STARTED.get());
        }
    }

    // ------------------------------------------------------------------ on the work's thread

    @Test
    void theWorkRunsWithTheCallersIdentityAndARequestScope() {
        try (TestServer server = server().start();
             TestConnection browser = server.connectAsTenant("alice", "acme", "agent")) {

            SlowCall<String> call = browser.call(ReportService.class, "caller");

            assertEquals("alice|true|acme|true|null|request-scope-active",
                    browser.awaitSlowCall(call, WAIT).value());
        }
    }

    // ------------------------------------------------------------------ failures

    @Test
    void anUnexpectedFailureReachesTheBrowserAsAReference() {
        try (TestServer server = server().start();
             TestConnection browser = server.connect("alice")) {

            SlowCall<String> call = browser.call(ReportService.class, "failsPlainly");

            SlowCallState<String> end = browser.awaitSlowCall(call, WAIT);

            assertTrue(end.isFailed());
            assertTrue(end.message().startsWith(
                    "The server could not complete this request. Reference: "), end.message());
            assertFalse(end.message().contains("REPORTS"), "the real message stays in the log");
        }
    }

    @Test
    void aClientVisibleFailureKeepsItsSentence() {
        try (TestServer server = server().start();
             TestConnection browser = server.connect("alice")) {

            SlowCall<String> call = browser.call(ReportService.class, "failsWithSentence");

            assertEquals("The model is not available.",
                    browser.awaitSlowCall(call, WAIT).message());
        }
    }

    // ------------------------------------------------------------------ canceling

    @Test
    void cancelInterruptsTheWork() throws Exception {
        try (TestServer server = server().start();
             TestConnection browser = server.connect("alice")) {

            SlowCall<String> call = browser.call(ReportService.class, "blocksUntilCanceled");
            while (STARTED.get() == 0) {
                Thread.sleep(1);
            }

            browser.cancelSlowCall(call);

            assertTrue(browser.awaitSlowCall(call, WAIT).isCanceled());
            while (server.runtime().slowCalls().runningCount() > 0) {
                Thread.sleep(1);
            }
            assertTrue(INTERRUPTED.get(), "the work saw the interrupt");
            assertTrue(SAW_CANCELED_FLAG.get(), "the work saw the flag");
        }
    }

    @Test
    void cancelingEndedWorkDoesNothing() {
        try (TestServer server = server().start();
             TestConnection browser = server.connect("alice")) {

            SlowCall<String> call = browser.call(ReportService.class, "quick", "k", "value");
            browser.awaitSlowCall(call, WAIT);

            browser.cancelSlowCall(call);

            assertEquals("value", browser.awaitSlowCall(call, WAIT).value());
        }
    }

    // ------------------------------------------------------------------ limits

    @Test
    void oneBrowserCanRunOnlySoMany() {
        try (TestServer server = server()
                     .set(ServerSettings.SLOW_CALL_MAX_RUNNING_PER_CLIENT, 2).start();
             TestConnection browser = server.connect("alice");
             TestConnection other = server.connect("bob")) {

            SlowCall<String> first = startGated(browser, "a");
            startGated(browser, "b");

            IllegalStateException refused = assertThrows(IllegalStateException.class,
                    () -> startGated(browser, "c"));
            assertEquals("Too many slow calls are running. Wait for one to finish.",
                    refused.getMessage());
            startGated(other, "x"); // Somebody else is not affected.

            gate("a").countDown();
            browser.awaitSlowCall(first, WAIT);
            startGated(browser, "c");

            gate("b").countDown();
            gate("c").countDown();
            gate("x").countDown();
        }
    }

    @Test
    void theWholeServerCanRunOnlySoMany() {
        try (TestServer server = server().set(ServerSettings.SLOW_CALL_MAX_RUNNING, 1).start();
             TestConnection alice = server.connect("alice");
             TestConnection bob = server.connect("bob")) {

            startGated(alice, "a");

            IllegalStateException refused = assertThrows(IllegalStateException.class,
                    () -> startGated(bob, "b"));
            assertEquals("The server is busy. Try again in a moment.", refused.getMessage());
            gate("a").countDown();
        }
    }

    @Test
    void onlySoManyResultsAreKeptAndTheOldestGoesFirst() {
        try (TestServer server = server()
                     .set(ServerSettings.SLOW_CALL_MAX_KEPT_PER_CLIENT, 2).start();
             TestConnection browser = server.connect("alice")) {

            for (String key : new String[] {"a", "b", "c"}) {
                SlowCall<String> call = browser.call(ReportService.class, "quick", key, key);
                browser.awaitSlowCall(call, WAIT);
            }

            assertTrue(browser.slowCallState(SlowCall.find("a")).isIdle());
            assertEquals("b", browser.slowCallState(SlowCall.find("b")).value());
            assertEquals("c", browser.slowCallState(SlowCall.find("c")).value());
        }
    }

    @Test
    void aResultExpires() {
        try (TestServer server = server().set(ServerSettings.SLOW_CALL_KEEP_MINUTES, 10).start();
             TestConnection browser = server.connect("alice")) {

            SlowCall<String> call = browser.call(ReportService.class, "quick", "k", "value");
            browser.awaitSlowCall(call, WAIT);

            SlowCallRegistry registry = server.runtime().slowCalls();
            assertEquals(0, registry.sweep(System.currentTimeMillis() + 9 * 60_000L));
            assertEquals("value", browser.slowCallState(call).value());

            assertEquals(1, registry.sweep(System.currentTimeMillis() + 11 * 60_000L));

            assertTrue(browser.slowCallState(call).isFailed());
            assertThrows(IllegalStateException.class,
                    () -> browser.call(SlowCallRpc.class, "collect", call.id()));
        }
    }

    // ------------------------------------------------------------------ restart and shutdown

    @Test
    void aServerRestartLosesTheWorkAndTheBrowserIsTold() {
        SlowCall<String> call;
        try (TestServer before = server().start();
             TestConnection browser = before.connect("alice")) {
            call = startGated(browser, "report-1");
        }

        try (TestServer after = server().start();
             TestConnection browser = after.connect("alice")) {

            SlowCallState<String> state = browser.slowCallState(call);

            assertTrue(state.isFailed());
            assertEquals("The server no longer has this work. Start it again.", state.message());
        }
    }

    @Test
    void shuttingDownInterruptsRunningWork() throws Exception {
        try (TestServer server = server().start();
             TestConnection browser = server.connect("alice")) {
            browser.call(ReportService.class, "blocksUntilCanceled");
            while (STARTED.get() == 0) {
                Thread.sleep(1);
            }
        }
        long deadline = System.currentTimeMillis() + WAIT;
        while (!INTERRUPTED.get() && System.currentTimeMillis() < deadline) {
            TimeUnit.MILLISECONDS.sleep(1);
        }
        assertTrue(INTERRUPTED.get());
    }

    // ------------------------------------------------------------------ mistakes

    @Test
    void anEmptyKeyIsRefused() {
        try (TestServer server = server().start();
             TestConnection browser = server.connect("alice")) {

            assertThrows(IllegalStateException.class,
                    () -> browser.call(ReportService.class, "startsWithNoKey"));
        }
    }

    @Test
    void startingOutsideAServiceCallSaysSo() {
        try (TestServer server = server().start()) {
            IllegalStateException thrown = assertThrows(IllegalStateException.class,
                    () -> server.bean(SlowCalls.class).start("k", job -> "x"));
            assertTrue(thrown.getMessage().contains("has no owner"), thrown.getMessage());
        }
    }
}
