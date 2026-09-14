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
package com.zeroz4j.client.router;

import com.zeroz4j.api.DisconnectedException;
import com.zeroz4j.api.Disposable;
import com.zeroz4j.api.RmiSecurityContext;
import com.zeroz4j.ui.component.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The order of navigation events, on every path a navigation can take.
 *
 * <p>The promise under test is the one a loading indicator depends on: every way of starting a
 * navigation says so, the navigation started last always ends in exactly one "finished" or
 * "failed", and an older one that was overtaken says nothing more at all - not even when its loader
 * comes back afterwards, successfully or not.</p>
 *
 * <p>Runs on the JVM against a stand-in for the browser. A navigation that has to be "still
 * loading" runs on a real thread and waits at a gate in its loader until the test opens it; the
 * test then waits for that thread to end before doing anything else, so only one thread is ever
 * inside the router at a time and nothing here depends on how long anything takes.</p>
 */
class RouterLifecycleTest {

    // ---------------------------------------------------------------- the stand-in browser

    static final class FakeHost implements RouterHost {
        String location = "/";
        final List<String> history = new CopyOnWriteArrayList<>();
        final List<String> warnings = new CopyOnWriteArrayList<>();
        final List<String> mounted = new CopyOnWriteArrayList<>();
        final List<Thread> threads = new CopyOnWriteArrayList<>();
        Consumer<String> popState;
        Consumer<String> links;
        boolean threaded;

        @Override public String currentLocation() { return location; }
        @Override public void pushState(String loc) { history.add("push " + loc); location = loc; }
        @Override public void replaceState(String loc) { history.add("replace " + loc); location = loc; }
        @Override public String toRoute(String loc) { return loc; }
        @Override public String toLocation(String route) { return route; }
        @Override public void onPopState(Consumer<String> listener) { popState = listener; }
        @Override public void interceptRouteLinks(Consumer<String> listener) { links = listener; }
        @Override public void mount(String containerId, Component view) { mounted.add(lastRendered); }
        @Override public void warn(String message) { warnings.add(message); }

        @Override
        public void runNavigation(Runnable navigation) {
            if (!threaded) {
                navigation.run();
                return;
            }
            Thread thread = new Thread(navigation, "navigation");
            threads.add(thread);
            thread.start();
        }

        void back(String loc) {
            location = loc;
            popState.accept(loc);
        }

        void click(String href) {
            links.accept(href);
        }

        /** Waits for every navigation except the one standing at the given gate. */
        void awaitNavigationsExcept(Gate held) throws InterruptedException {
            for (Thread thread : threads) {
                if (thread != held.waiter) {
                    thread.join(TimeUnit.SECONDS.toMillis(10));
                    assertFalse(thread.isAlive(), "a navigation thread did not end");
                }
            }
        }

        void awaitNavigations() throws InterruptedException {
            for (Thread thread : threads) {
                thread.join(TimeUnit.SECONDS.toMillis(10));
                assertFalse(thread.isAlive(), "a navigation thread did not end");
            }
        }
    }

    /** Where a loader waits, so a navigation can be kept "still loading". */
    static final class Gate {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch opened = new CountDownLatch(1);
        volatile RuntimeException failWith;
        volatile Thread waiter;

        void awaitEntered() throws InterruptedException {
            assertTrue(entered.await(10, TimeUnit.SECONDS), "the loader was never reached");
        }

        void open() {
            opened.countDown();
        }

        void openFailing(RuntimeException reason) {
            failWith = reason;
            opened.countDown();
        }
    }

    static final Map<String, Gate> gates = new ConcurrentHashMap<>();
    static final Map<String, RuntimeException> failingLoaders = new ConcurrentHashMap<>();
    static final List<String> loaded = new CopyOnWriteArrayList<>();
    static volatile String lastRendered;

    /** A route whose loader passes through its gate, if one is set, and whose view is its path. */
    static final class TestView implements RouteView<String> {
        @Override
        public String load(RouteParams params) {
            return pass(params.path());
        }

        @Override
        public Component render(String data, RouteParams params) {
            lastRendered = params.path();
            return null;
        }
    }

    /** A layout, gated under the key "layout:" + path. */
    static final class TestLayout implements RouteLayout<String> {
        @Override
        public String load(RouteParams params) {
            return pass("layout:" + params.path());
        }

        @Override
        public Component render(String data, RouteParams params, Component child) {
            return child;
        }
    }

    static String pass(String key) {
        loaded.add(key);
        Gate gate = gates.get(key);
        if (gate != null) {
            gate.waiter = Thread.currentThread();
            gate.entered.countDown();
            try {
                if (!gate.opened.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("gate for " + key + " was never opened");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            if (gate.failWith != null) {
                throw gate.failWith;
            }
        }
        RuntimeException failure = failingLoaders.get(key);
        if (failure != null) {
            throw failure;
        }
        return key;
    }

    /** Writes down every event, one line each. */
    static final class Recorder implements Router.LifecycleListener {
        final List<String> events = new CopyOnWriteArrayList<>();
        final List<Navigation> started = new CopyOnWriteArrayList<>();

        @Override
        public void onNavigationStarted(Navigation navigation) {
            started.add(navigation);
            events.add("started " + navigation.id() + " " + navigation.trigger() + " " + navigation.path());
        }

        @Override
        public void onNavigationFinished(Navigation navigation, RouteParams params) {
            events.add("finished " + navigation.id() + " " + params.path());
        }

        @Override
        public void onNavigationFailed(Navigation navigation, Throwable reason) {
            events.add("failed " + navigation.id() + " " + reason.getClass().getSimpleName());
        }
    }

    private FakeHost host;
    private Recorder recorder;

    @BeforeEach
    void setUp() {
        RouteRegistry.resetForTesting();
        RmiSecurityContext.clear();
        gates.clear();
        failingLoaders.clear();
        loaded.clear();
        lastRendered = null;
        host = new FakeHost();
        Router.resetForTesting(host);
        recorder = new Recorder();
        Router.addLifecycleListener(recorder);

        view("/");
        view("/a");
        view("/b");
        view("/slow");
        view("/not-found");
        view("/forbidden");
        RouteRegistry.register(new RouteDefinition("/admin", "AdminView", null, Set.of("admin"),
                TestView::new, false, "Admin", 1));
        RouteRegistry.register(new RouteDefinition("/shell", "Shell", null, Collections.emptySet(),
                TestLayout::new, true, "Shell", 1));
        RouteRegistry.register(new RouteDefinition("/inside", "InsideView", "Shell",
                Collections.emptySet(), TestView::new, false, "Inside", 1));
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        for (Gate gate : gates.values()) {
            gate.open();
        }
        host.awaitNavigations();
        RouteRegistry.resetForTesting();
        RmiSecurityContext.clear();
        Router.resetForTesting(new RouterBrowser());
    }

    private static void view(String path) {
        RouteRegistry.register(new RouteDefinition(path, "View" + path, null, Collections.emptySet(),
                TestView::new, false, path, 1));
    }

    private Gate gate(String key) {
        Gate gate = new Gate();
        gates.put(key, gate);
        return gate;
    }

    private void startAt(String location) {
        host.location = location;
        Router.start("app-root");
    }

    /** The started-last navigation ended in exactly one finished or failed, and nothing is pending. */
    private void assertLatestEndedOnce() {
        Navigation latest = Router.latestNavigation();
        long endings = recorder.events.stream()
                .filter(line -> line.startsWith("finished " + latest.id() + " ")
                        || line.startsWith("failed " + latest.id() + " "))
                .count();
        assertEquals(1, endings, "the latest navigation must end exactly once: " + recorder.events);
        assertTrue(latest.outcome() == Navigation.Outcome.FINISHED
                || latest.outcome() == Navigation.Outcome.FAILED);
    }

    // ---------------------------------------------------------------- every way in

    @Test
    void theFirstRenderInStartIsANavigation() {
        startAt("/a");

        assertEquals(List.of("started 1 INITIAL /a", "finished 1 /a"), recorder.events);
        assertEquals("/a", Router.currentPath());
        assertEquals(List.of("/a"), host.mounted);
    }

    @Test
    void aLinkClickStartsAndFinishes() {
        startAt("/");
        recorder.events.clear();

        host.click("/a");

        assertEquals(List.of("started 2 LINK /a", "finished 2 /a"), recorder.events);
        assertEquals("push /a", host.history.get(host.history.size() - 1));
    }

    @Test
    void navigateStartsAndFinishes() {
        startAt("/");
        recorder.events.clear();

        Router.navigate("/a");

        assertEquals(List.of("started 2 NAVIGATE /a", "finished 2 /a"), recorder.events);
    }

    @Test
    void replaceAlwaysStartsEvenForTheRouteOnScreen() {
        startAt("/a");
        recorder.events.clear();

        Router.replace("/a");

        assertEquals(List.of("started 2 REPLACE /a", "finished 2 /a"), recorder.events);
        assertEquals("replace /a", host.history.get(host.history.size() - 1));
    }

    @Test
    void backAndForwardStartAndFinish() {
        startAt("/");
        Router.navigate("/a");
        recorder.events.clear();

        host.back("/");
        host.back("/a");

        assertEquals(List.of("started 3 HISTORY /", "finished 3 /",
                "started 4 HISTORY /a", "finished 4 /a"), recorder.events);
        assertEquals("/a", Router.currentPath());
    }

    @Test
    void startingTwiceDoesNotListenTwice() {
        startAt("/");
        Consumer<String> firstLinks = host.links;
        Router.start("app-root");

        assertSame(firstLinks, host.links, "the click listener must be installed once");
    }

    // ---------------------------------------------------------------- nothing to do

    @Test
    void theRouteOnScreenRaisesNothing() {
        startAt("/a");
        recorder.events.clear();
        int historyBefore = host.history.size();

        host.click("/a");
        Router.navigate("/a");

        assertTrue(recorder.events.isEmpty(), "nothing started, so nothing may be announced");
        assertEquals(historyBefore, host.history.size(), "and no history entry is added");
    }

    @Test
    void aDifferentQueryStringIsANavigation() {
        startAt("/a");
        recorder.events.clear();

        Router.navigate("/a?sort=name");
        Router.navigate("/a?sort=name");

        assertEquals(List.of("started 2 NAVIGATE /a?sort=name", "finished 2 /a"), recorder.events);
    }

    @Test
    void theRouteAlreadyLoadingRaisesNothing() throws Exception {
        startAt("/");
        host.threaded = true;
        Gate slow = gate("/slow");
        Router.navigate("/slow");
        slow.awaitEntered();

        host.click("/slow");

        slow.open();
        host.awaitNavigations();
        assertEquals(List.of("started 1 INITIAL /", "finished 1 /",
                "started 2 NAVIGATE /slow", "finished 2 /slow"), recorder.events);
    }

    // ---------------------------------------------------------------- redirects

    @Test
    void aRedirectSupersedesTheNavigationThatCausedItAndEndsOnce() {
        Router.notFoundRoute("/not-found");
        startAt("/");
        recorder.events.clear();

        Router.navigate("/nowhere");

        assertEquals(List.of("started 2 NAVIGATE /nowhere", "started 3 REDIRECT /not-found",
                "finished 3 /not-found"), recorder.events);
        Navigation redirect = recorder.started.get(recorder.started.size() - 1);
        assertEquals(2, redirect.redirectedFrom().id());
        assertEquals(2, redirect.supersededId());
        assertEquals(Navigation.Outcome.SUPERSEDED, redirect.redirectedFrom().outcome());
        assertEquals("replace /not-found", host.history.get(host.history.size() - 1));
        assertLatestEndedOnce();
    }

    @Test
    void anUnmatchedPathWithNoFallbackFailsWithRouteNotFound() {
        startAt("/");
        recorder.events.clear();
        List<Throwable> reported = new ArrayList<>();
        Router.addErrorListener((path, reason) -> reported.add(reason));

        Router.navigate("/nowhere");

        assertEquals(List.of("started 2 NAVIGATE /nowhere", "failed 2 RouteNotFoundException"),
                recorder.events);
        assertInstanceOf(IllegalStateException.class, reported.get(0),
                "still an IllegalStateException, as before the type existed");
        assertEquals("/", Router.currentPath(), "the page is left as it was");
    }

    @Test
    void aForbiddenRouteRedirectsWhenAFallbackIsSet() {
        RmiSecurityContext.populate("demo", Set.of("user"), true);
        Router.forbiddenRoute("/forbidden");
        startAt("/");
        recorder.events.clear();

        host.click("/admin");

        assertEquals(List.of("started 2 LINK /admin", "started 3 REDIRECT /forbidden",
                "finished 3 /forbidden"), recorder.events);
    }

    @Test
    void aForbiddenRouteFailsWhenNoFallbackIsSet() {
        RmiSecurityContext.populate("demo", Set.of("user"), true);
        startAt("/");
        recorder.events.clear();

        host.click("/admin");

        assertEquals(List.of("started 2 LINK /admin", "failed 2 RouteForbiddenException"),
                recorder.events);
    }

    // ---------------------------------------------------------------- failures and listeners

    @Test
    void aFailedLoaderReportsOnceAndLeavesThePageAlone() {
        startAt("/");
        recorder.events.clear();
        failingLoaders.put("/a", new DisconnectedException("gone"));

        Router.navigate("/a");

        assertEquals(List.of("started 2 NAVIGATE /a", "failed 2 DisconnectedException"),
                recorder.events);
        assertEquals("/", Router.currentPath());
        assertEquals(List.of("/"), host.mounted, "nothing new was mounted");
        assertTrue(Router.latestNavigation().isConnectionProblem());
        assertLatestEndedOnce();
    }

    @Test
    void everyErrorListenerIsCalledAndOnErrorAddsRatherThanReplaces() {
        startAt("/");
        List<String> calls = new ArrayList<>();
        Router.onError((path, reason) -> calls.add("first " + path));
        Router.onError((path, reason) -> calls.add("second " + path));
        Disposable third = Router.addErrorListener((path, reason) -> calls.add("third " + path));
        failingLoaders.put("/a", new IllegalStateException("refused"));

        Router.navigate("/a");
        assertEquals(List.of("first /a", "second /a", "third /a"), calls);
        assertTrue(host.warnings.isEmpty(), "with a listener there is no console warning");

        calls.clear();
        third.dispose();
        Router.navigate("/a");
        assertEquals(List.of("first /a", "second /a"), calls);
    }

    @Test
    void withNoErrorListenerAFailureIsWrittenToTheConsole() {
        startAt("/");
        failingLoaders.put("/a", new IllegalStateException("refused"));

        Router.navigate("/a");

        assertEquals(1, host.warnings.size());
        assertTrue(host.warnings.get(0).contains("'/a' failed: refused"), host.warnings.get(0));
    }

    @Test
    void aThrowingListenerDoesNotStopTheOthersHearing() {
        Recorder second = new Recorder();
        Router.resetForTesting(host);
        Router.addLifecycleListener(new Router.LifecycleListener() {
            @Override
            public void onNavigationFinished(Navigation navigation, RouteParams params) {
                throw new IllegalStateException("listener bug");
            }
        });
        Router.addLifecycleListener(second);

        startAt("/a");

        assertEquals(List.of("started 1 INITIAL /a", "finished 1 /a"), second.events);
        assertEquals(1, host.warnings.size());
    }

    @Test
    void aFailedRouteAskedForAgainRunsAgain() {
        startAt("/");
        failingLoaders.put("/a", new IllegalStateException("refused"));
        Router.navigate("/a");
        failingLoaders.clear();
        recorder.events.clear();

        host.click("/a");

        assertEquals(List.of("started 3 LINK /a", "finished 3 /a"), recorder.events);
    }

    @Test
    void retryRunsTheFailedNavigationAgainAndOnlyThat() {
        startAt("/");
        assertFalse(Router.retry(), "nothing failed, so there is nothing to retry");

        failingLoaders.put("/a", new DisconnectedException("gone"));
        Router.navigate("/a");
        failingLoaders.clear();
        recorder.events.clear();

        assertTrue(Router.retry());

        assertEquals(List.of("started 3 RETRY /a", "finished 3 /a"), recorder.events);
        assertEquals("replace /a", host.history.get(host.history.size() - 1));
        assertFalse(Router.retry(), "the retry succeeded, so there is nothing left to retry");
    }

    @Test
    void aListenerThatNavigatesDoesNotInterruptTheEventBeingDelivered() {
        Router.resetForTesting(host);
        List<String> order = new ArrayList<>();
        Router.addLifecycleListener(new Router.LifecycleListener() {
            @Override
            public void onNavigationFinished(Navigation navigation, RouteParams params) {
                order.add("first heard finished " + params.path());
                if (params.path().equals("/a")) {
                    Router.navigate("/b");
                }
            }
        });
        Router.addLifecycleListener(new Router.LifecycleListener() {
            @Override
            public void onNavigationStarted(Navigation navigation) {
                order.add("second heard started " + navigation.path());
            }

            @Override
            public void onNavigationFinished(Navigation navigation, RouteParams params) {
                order.add("second heard finished " + params.path());
            }
        });

        startAt("/a");

        assertEquals(List.of(
                "second heard started /a",
                "first heard finished /a",
                "second heard finished /a",
                "second heard started /b",
                "first heard finished /b",
                "second heard finished /b"), order);
    }

    // ---------------------------------------------------------------- overlapping navigations

    @Test
    void anOlderNavigationThatFinishesLastIsThrownAway() throws Exception {
        startAt("/");
        host.threaded = true;
        Gate slow = gate("/slow");

        host.click("/slow");
        slow.awaitEntered();
        host.click("/a");
        host.awaitNavigationsExcept(slow);

        slow.open();
        host.awaitNavigations();

        assertEquals(List.of("started 1 INITIAL /", "finished 1 /",
                "started 2 LINK /slow", "started 3 LINK /a", "finished 3 /a"), recorder.events);
        assertEquals("/a", Router.currentPath(), "the screen stays on the page asked for last");
        assertEquals("/a", host.location, "and so does the address bar");
        assertEquals(List.of("/", "/a"), host.mounted, "the slow page was never mounted");
        assertEquals(3, recorder.started.get(2).id());
        assertEquals(2, recorder.started.get(2).supersededId());
        assertEquals(Navigation.Outcome.SUPERSEDED, recorder.started.get(1).outcome());
    }

    @Test
    void anOlderNavigationThatFailsLastReportsNothing() throws Exception {
        startAt("/");
        host.threaded = true;
        List<String> errors = new ArrayList<>();
        Router.addErrorListener((path, reason) -> errors.add(path));
        Gate slow = gate("/slow");

        Router.navigate("/slow");
        slow.awaitEntered();
        Router.navigate("/a");
        host.awaitNavigationsExcept(slow);

        slow.openFailing(new DisconnectedException("gone"));
        host.awaitNavigations();

        assertEquals(List.of("started 1 INITIAL /", "finished 1 /",
                "started 2 NAVIGATE /slow", "started 3 NAVIGATE /a", "finished 3 /a"), recorder.events);
        assertTrue(errors.isEmpty(), "a failure nobody is waiting for is not reported");
        assertLatestEndedOnce();
    }

    @Test
    void clickingTheScreenStillShowingWhileAnotherPageLoadsWins() throws Exception {
        startAt("/");
        host.threaded = true;
        Gate slow = gate("/slow");

        host.click("/slow");
        slow.awaitEntered();
        // The address already says /slow and the screen still shows /. Clicking the link for / is
        // not "nothing to do": it has to beat /slow.
        host.click("/");
        host.awaitNavigationsExcept(slow);

        slow.open();
        host.awaitNavigations();

        assertEquals(List.of("started 1 INITIAL /", "finished 1 /",
                "started 2 LINK /slow", "started 3 LINK /", "finished 3 /"), recorder.events);
        assertEquals("/", Router.currentPath());
        assertEquals("/", host.location, "the address matches the screen");
        assertLatestEndedOnce();
    }

    @Test
    void anOvertakenNavigationStopsBeforeItsRemainingLoaders() throws Exception {
        startAt("/");
        host.threaded = true;
        Gate layout = gate("layout:/inside");

        Router.navigate("/inside");
        layout.awaitEntered();
        Router.navigate("/a");
        host.awaitNavigationsExcept(layout);

        layout.open();
        host.awaitNavigations();

        assertTrue(loaded.contains("layout:/inside"));
        assertFalse(loaded.contains("/inside"),
                "the view's loader must not run for a page nobody will see: " + loaded);
    }

    @Test
    void backWhileAPageIsLoadingWins() throws Exception {
        startAt("/");
        host.threaded = true;
        Gate slow = gate("/slow");

        host.click("/slow");
        slow.awaitEntered();
        host.back("/");
        host.awaitNavigationsExcept(slow);

        slow.open();
        host.awaitNavigations();

        assertEquals("/", Router.currentPath());
        assertEquals(List.of("started 1 INITIAL /", "finished 1 /",
                "started 2 LINK /slow", "started 3 HISTORY /", "finished 3 /"), recorder.events);
    }

    @Test
    void theLatestNavigationIsNeverLeftWithoutAnEnding() throws Exception {
        Router.notFoundRoute("/not-found");
        startAt("/");
        host.threaded = true;
        Gate slow = gate("/slow");

        host.click("/slow");
        slow.awaitEntered();
        host.click("/nowhere");
        host.awaitNavigationsExcept(slow);
        slow.openFailing(new IllegalStateException("late"));
        host.awaitNavigations();

        assertLatestEndedOnce();
        assertNull(Router.latestNavigation().failure());
        assertEquals("/not-found", Router.currentPath());
    }

    @Test
    void theHarnessItselfNoticesANavigationThatNeverEnds() {
        // Guards the guard: if assertLatestEndedOnce could pass with no ending, every test above
        // that relies on it would be proving nothing.
        startAt("/");
        recorder.events.clear();
        try {
            assertLatestEndedOnce();
        } catch (AssertionError expected) {
            return;
        }
        fail("assertLatestEndedOnce passed with no ending recorded");
    }
}
