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

import com.zeroz4j.api.RmiSecurityContext;
import com.zeroz4j.ui.component.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Starting the router again - which an application does after every reconnect when it starts it
 * from {@code RmiSecurityContext.onAuthenticated} - must not add listeners again, and must not
 * stack one render per start.
 *
 * <p>Before 0.9.1 each start added another click listener and another Back/Forward listener. After
 * a morning of reconnects one click loaded the page dozens of times.</p>
 */
class RouterStartTest {

    /** The browser, as far as the router can see it. Navigations run when the test says so. */
    static final class FakeHost implements RouterHost {
        String location = "/";
        final List<Consumer<String>> popStateListeners = new ArrayList<>();
        final List<Consumer<String>> linkListeners = new ArrayList<>();
        final List<Runnable> waiting = new ArrayList<>();
        final List<String> mounted = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        boolean runAtOnce = true;

        @Override public String currentLocation() { return location; }
        @Override public void pushState(String loc) { location = loc; }
        @Override public void replaceState(String loc) { location = loc; }
        @Override public String toRoute(String loc) { return loc; }
        @Override public String toLocation(String route) { return route; }
        @Override public void onPopState(Consumer<String> listener) { popStateListeners.add(listener); }
        @Override public void interceptRouteLinks(Consumer<String> listener) { linkListeners.add(listener); }
        @Override public void mount(String containerId, Component view) { mounted.add(lastRendered); }
        @Override public void warn(String message) { warnings.add(message); }

        @Override
        public void runNavigation(Runnable navigation) {
            if (runAtOnce) {
                navigation.run();
            } else {
                waiting.add(navigation);
            }
        }
    }

    static final List<String> loads = new ArrayList<>();
    static String lastRendered;

    /** A route whose view is its path, and which counts its loads. */
    static final class CountingView implements RouteView<String> {
        @Override
        public String load(RouteParams params) {
            loads.add(params.path());
            return params.path();
        }

        @Override
        public Component render(String data, RouteParams params) {
            lastRendered = params.path();
            return null;
        }
    }

    private FakeHost host;

    @BeforeEach
    void setUp() {
        RouteRegistry.resetForTesting();
        RmiSecurityContext.clear();
        loads.clear();
        lastRendered = null;
        host = new FakeHost();
        Router.resetForTesting(host);
        for (String path : new String[] { "/", "/a" }) {
            RouteRegistry.register(new RouteDefinition(path, "View" + path, null,
                    Collections.emptySet(), CountingView::new, false, path, 1));
        }
    }

    @AfterEach
    void tearDown() {
        RouteRegistry.resetForTesting();
        RmiSecurityContext.clear();
        Router.resetForTesting(new RouterBrowser());
    }

    @Test
    void startingTwiceAddsTheListenersOnce() {
        host.location = "/a";
        Router.start("app-root");
        Router.start("app-root");
        Router.start("app-root");

        assertEquals(1, host.popStateListeners.size(), "one Back/Forward listener, however often started");
        assertEquals(1, host.linkListeners.size(), "one click listener, however often started");

        // One Back press loads the page once, not once per start.
        loads.clear();
        host.location = "/";
        host.popStateListeners.get(0).accept("/");
        assertEquals(List.of("/"), loads);
    }

    @Test
    void eachStartRefreshesTheCurrentViewOnce() {
        host.location = "/a";
        Router.start("app-root");
        Router.start("app-root");

        assertEquals(List.of("/a", "/a"), loads, "a restart may load the current page's data again, once");
        assertEquals(List.of("/a", "/a"), host.mounted);
    }

    @Test
    void aBurstOfStartsEndsInOneRender() {
        // Three reconnects in a row, each starting the router while the previous render is still
        // loading. Only the last one reaches the screen.
        host.runAtOnce = false;
        host.location = "/a";
        Router.start("app-root");
        Router.start("app-root");
        Router.start("app-root");

        for (Runnable navigation : new ArrayList<>(host.waiting)) {
            navigation.run();
        }

        assertEquals(List.of("/a"), host.mounted, "the overtaken renders are thrown away");
        assertEquals("/a", Router.currentPath());
        assertTrue(host.warnings.isEmpty(), "and report nothing: " + host.warnings);
    }
}
