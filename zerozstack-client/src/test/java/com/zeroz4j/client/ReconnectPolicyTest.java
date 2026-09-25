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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When the client tries to connect again after a drop.
 *
 * <p>The case this was written for: a phone kept a tab open in the background, the browser closed
 * the connection a second or two after every open, and the client came back every 16 to 35 seconds
 * for hours. Every test here drives {@link ReconnectPolicy} with a clock, a page visibility and a
 * timer the test controls, so nothing depends on how long a step takes.</p>
 */
class ReconnectPolicyTest {

    /** The browser, as far as the policy can see it. */
    static final class FakeHost implements ReconnectPolicy.Host {
        long now = 1_000_000L;
        boolean hidden;
        int socketsOpened;
        final List<Integer> delays = new ArrayList<>();
        final List<Runnable> timers = new ArrayList<>();

        @Override public long now() { return now; }
        @Override public boolean pageHidden() { return hidden; }
        @Override public void schedule(int delayMillis, Runnable task) {
            delays.add(delayMillis);
            timers.add(task);
        }
        @Override public void openSocket() { socketsOpened++; }

        /** Runs the timer scheduled last, as the browser would once its delay had passed. */
        void fireLastTimer() {
            Runnable task = timers.get(timers.size() - 1);
            now += delays.get(delays.size() - 1);
            task.run();
        }
    }

    private FakeHost host;
    private ReconnectPolicy policy;

    @BeforeEach
    void setUp() {
        host = new FakeHost();
        policy = new ReconnectPolicy(host);
    }

    /** One connection that opens and is closed by the browser after the given time. */
    private ReconnectPolicy.Outcome openThenDropAfter(long millis) {
        policy.opened();
        host.now += millis;
        return policy.closed(1006, "");
    }

    @Test
    void aConnectionThatDropsSoonAfterOpeningDoesNotResetTheBackoff() {
        policy.opened();
        host.now += 60_000;
        assertEquals(ReconnectPolicy.Outcome.RETRY_SCHEDULED, policy.closed(1006, ""));

        // The production loop: open, dropped 0.1 to 2.3 seconds later, again and again. Before
        // 0.9.1 every open reset the backoff, so every attempt waited the first delay.
        for (int i = 0; i < 4; i++) {
            host.fireLastTimer();
            assertEquals(ReconnectPolicy.Outcome.RETRY_SCHEDULED, openThenDropAfter(1_500));
        }

        assertEquals(List.of(500, 1000, 2000, 4000, 8000), host.delays,
                "a connection that stays open for less than 30 seconds is a failure, "
                        + "and the delay keeps growing");
        assertEquals(5, policy.failures());
    }

    @Test
    void aConnectionThatStayedOpenLongEnoughStartsTheBackoffAgain() {
        for (int i = 0; i < 3; i++) {
            openThenDropAfter(1_000);
        }
        assertEquals(3, policy.failures());

        openThenDropAfter(ReconnectPolicy.STABLE_AFTER_MS);

        assertEquals(1, policy.failures(), "30 seconds open counts as a good connection");
        assertEquals(500, policy.lastDelay());
    }

    @Test
    void theDelayDoublesAndStopsAtFifteenSeconds() {
        assertEquals(500, ReconnectPolicy.delayFor(1));
        assertEquals(1000, ReconnectPolicy.delayFor(2));
        assertEquals(8000, ReconnectPolicy.delayFor(5));
        assertEquals(15_000, ReconnectPolicy.delayFor(6));
        assertEquals(15_000, ReconnectPolicy.delayFor(40));
    }

    @Test
    void nothingIsAttemptedWhileThePageIsHiddenAndTheAttemptIsImmediateWhenItIsShown() {
        policy.opened();
        host.now += 1_200;
        host.hidden = true;

        assertEquals(ReconnectPolicy.Outcome.WAITING_FOR_VISIBLE, policy.closed(1006, ""));
        assertTrue(host.timers.isEmpty(), "no timer is set for a hidden page");
        assertEquals(0, host.socketsOpened);

        // The network coming back does not wake a hidden page either.
        policy.networkBack();
        assertEquals(0, host.socketsOpened);

        host.hidden = false;
        policy.pageShown();

        assertEquals(1, host.socketsOpened, "shown again: connect at once, with no delay");
        assertTrue(host.timers.isEmpty());
        assertFalse(policy.isWaitingForVisible());

        policy.pageShown();
        assertEquals(1, host.socketsOpened, "a second visibility event does not open a second socket");
    }

    @Test
    void aTimerThatFiresWhileThePageIsHiddenWaitsForItToBeShown() {
        openThenDropAfter(800);
        host.hidden = true;

        host.fireLastTimer();

        assertEquals(0, host.socketsOpened);
        assertTrue(policy.isWaitingForVisible());

        host.hidden = false;
        policy.pageShown();
        assertEquals(1, host.socketsOpened);
    }

    @Test
    void theNetworkComingBackConnectsAtOnceInsteadOfWaitingOutTheDelay() {
        for (int i = 0; i < 5; i++) {
            openThenDropAfter(500);
        }
        assertEquals(8000, policy.lastDelay());

        policy.networkBack();
        assertEquals(1, host.socketsOpened, "the browser says the network is back: try now");

        host.fireLastTimer();
        assertEquals(1, host.socketsOpened, "the timer it replaced does nothing when it fires");
    }

    @Test
    void afterTenFailuresInARowTheClientStopsAndSaysSo() {
        ReconnectPolicy.Outcome outcome = null;
        for (int i = 0; i < ReconnectPolicy.MAX_CONSECUTIVE_FAILURES; i++) {
            // Attempts that never open count as failures too.
            outcome = policy.closed(1006, "");
            if (i < ReconnectPolicy.MAX_CONSECUTIVE_FAILURES - 1) {
                assertEquals(ReconnectPolicy.Outcome.RETRY_SCHEDULED, outcome);
                host.fireLastTimer();
            }
        }

        assertEquals(ReconnectPolicy.Outcome.GAVE_UP, outcome);
        assertTrue(policy.hasGivenUp());
        int opened = host.socketsOpened;
        policy.pageShown();
        policy.networkBack();
        assertEquals(opened, host.socketsOpened, "nothing more is attempted by itself");

        int waited = 0;
        for (int i = 0; i < host.delays.size(); i++) {
            waited += host.delays.get(i);
        }
        assertEquals(75_500, waited, "long enough to wait out a server restart of about a minute");

        policy.reset();
        assertFalse(policy.hasGivenUp(), "an explicit reconnect starts again");
        assertEquals(0, policy.failures());
    }

    @Test
    void theChannelShowsTheReloadStateOnceItHasGivenUp() {
        // What the connection bar reads: CLOSED together with hasGivenUp(). A channel closed by
        // the application is CLOSED too, but has not given up, so the bar does not ask for a
        // reload there.
        for (int i = 0; i < ReconnectPolicy.MAX_CONSECUTIVE_FAILURES; i++) {
            policy.closed(1006, "");
        }
        assertTrue(policy.hasGivenUp());

        ReconnectPolicy stoppedByTheApplication = new ReconnectPolicy(new FakeHost());
        stoppedByTheApplication.stop();
        assertEquals(ReconnectPolicy.Outcome.STOPPED, stoppedByTheApplication.closed(1000, ""));
        assertFalse(stoppedByTheApplication.hasGivenUp());
    }

    @Test
    void theNextHandshakeCarriesHowThePreviousConnectionEnded() {
        assertEquals("wss://example.com/wasm-rmi", policy.withPreviousClose("wss://example.com/wasm-rmi"),
                "a first connection has nothing to report");

        policy.opened();
        host.now += 830;
        host.hidden = false;
        policy.closed(1006, "going away & gone");

        assertEquals("wss://example.com/wasm-rmi?user=demo&zerozCloseCode=1006&zerozCloseAfterMs=830"
                        + "&zerozCloseHidden=0&zerozAttempt=1&zerozCloseReason=going%20away%20%26%20gone",
                policy.withPreviousClose("wss://example.com/wasm-rmi?user=demo"));

        host.hidden = true;
        policy.closed(1001, "");
        assertEquals("ws://h/wasm-rmi?zerozCloseCode=1001&zerozCloseHidden=1&zerozAttempt=1",
                policy.withPreviousClose("ws://h/wasm-rmi"),
                "an attempt that never opened has no open time; a hidden page is said so");
    }
}
