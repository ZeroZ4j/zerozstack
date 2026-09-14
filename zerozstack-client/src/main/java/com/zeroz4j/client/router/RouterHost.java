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

import com.zeroz4j.ui.component.Component;

import java.util.function.Consumer;

/**
 * Everything the {@link Router} needs from the page it runs in.
 *
 * <p>Framework-internal. In a browser it is {@link RouterBrowser}; the router's own tests put a
 * recording stand-in here, which is what makes the order of navigation events testable on the JVM
 * at all - the history API, the address bar and green threads do not exist there.</p>
 */
interface RouterHost {

    /** @return the address bar's path and query string */
    String currentLocation();

    /** Adds a history entry. */
    void pushState(String location);

    /** Replaces the current history entry. */
    void replaceState(String location);

    /** A browser location turned into a route, with the deployment's context path removed. */
    String toRoute(String location);

    /** A route turned into a browser location, with the deployment's context path put back. */
    String toLocation(String route);

    /** Calls back with the new location every time Back or Forward is pressed. */
    void onPopState(Consumer<String> listener);

    /** Calls back with the {@code href} of every ordinary click on an {@code <a data-route>}. */
    void interceptRouteLinks(Consumer<String> listener);

    /** Replaces the container's contents with a view, shutting the old one down. */
    void mount(String containerId, Component view);

    /** Writes a line to the console. */
    void warn(String message);

    /** Runs a navigation where its loaders may suspend: a green thread in the browser. */
    void runNavigation(Runnable navigation);
}
