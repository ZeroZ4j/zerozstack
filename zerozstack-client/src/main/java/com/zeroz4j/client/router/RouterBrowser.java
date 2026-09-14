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

import com.zeroz4j.client.AppBase;
import com.zeroz4j.ui.component.Component;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.dom.html.HTMLElement;

import java.util.function.Consumer;

/**
 * The browser APIs the {@link Router} drives: the history API, the address bar, and the container
 * element it swaps views into.
 *
 * <p>Framework-internal.</p>
 */
final class RouterBrowser implements RouterHost {

    RouterBrowser() {}

    @Override
    public String currentLocation() {
        return currentPath();
    }

    @Override
    public void pushState(String location) {
        pushLocation(location);
    }

    @Override
    public void replaceState(String location) {
        replaceLocation(location);
    }

    @Override
    public String toRoute(String location) {
        return AppBase.route(location);
    }

    @Override
    public String toLocation(String route) {
        return AppBase.location(route);
    }

    @Override
    public void onPopState(Consumer<String> listener) {
        listenForPopState(listener::accept);
    }

    @Override
    public void interceptRouteLinks(Consumer<String> listener) {
        listenForRouteLinks(listener::accept);
    }

    @Override
    public void mount(String containerId, Component view) {
        mountView(containerId, view);
    }

    @Override
    public void warn(String message) {
        consoleWarn(message);
    }

    /**
     * Runs a navigation on a green thread.
     *
     * <p>Navigation is triggered from browser callbacks - a click, a popstate, the frame that
     * reports authentication - and TeaVM cannot suspend a coroutine on a stack that started in
     * native JavaScript. A loader making an RMI call is exactly such a suspension, so calling it
     * directly from those callbacks fails with "suspension point reached from non-threading
     * context" and the navigation dies before rendering anything.</p>
     *
     * <p>Starting a thread re-enters TeaVM's own scheduler, which is what makes the loaders legal.
     * It is a green thread on the browser's event loop, not parallelism - nothing here runs at the
     * same time as anything else.</p>
     */
    @Override
    public void runNavigation(Runnable navigation) {
        new Thread(navigation).start();
    }

    /** Receives a path. */
    @JSFunctor
    interface PathCallback extends JSObject {
        void accept(String path);
    }

    /** @return the current path including its query string */
    @JSBody(params = {}, script = "return window.location.pathname + window.location.search;")
    private static native String currentPath();

    /** Adds a history entry, so Back returns to where the user came from. */
    @JSBody(params = { "path" }, script = "window.history.pushState({}, '', path);")
    private static native void pushLocation(String path);

    /** Replaces the current history entry, for a redirect that should not be re-enterable. */
    @JSBody(params = { "path" }, script = "window.history.replaceState({}, '', path);")
    private static native void replaceLocation(String path);

    /** Fires when the user presses Back or Forward. */
    @JSBody(params = { "callback" }, script =
        "window.addEventListener('popstate', function() {"
        + "  callback(window.location.pathname + window.location.search);"
        + "});")
    private static native void listenForPopState(PathCallback callback);

    /**
     * Routes clicks on in-application links without a page reload.
     *
     * <p>Deliberately opt-in, matching only anchors carrying {@code data-route}: taking over every
     * anchor on the page would swallow links to other sites and to server-rendered downloads.
     * Modified clicks — new tab, new window, download — are left to the browser, because a user
     * asking for a new tab means it.</p>
     */
    @JSBody(params = { "callback" }, script =
        "document.addEventListener('click', function(event) {"
        + "  if (event.defaultPrevented || event.button !== 0) { return; }"
        + "  if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) { return; }"
        + "  var node = event.target;"
        + "  while (node && node.tagName !== 'A') { node = node.parentElement; }"
        + "  if (!node || !node.hasAttribute('data-route')) { return; }"
        + "  if (node.hasAttribute('target') || node.hasAttribute('download')) { return; }"
        + "  var href = node.getAttribute('href');"
        + "  if (!href || href.indexOf('://') >= 0) { return; }"
        + "  event.preventDefault();"
        + "  callback(href);"
        + "});")
    private static native void listenForRouteLinks(PathCallback callback);

    /**
     * Replaces the container's contents with the rendered view, shutting the old one down.
     *
     * <p>Emptying the container by hand would leave the view the person just left running - its
     * timers, its effects, its subscriptions - so {@code Component.replaceContents} does it
     * instead, which runs {@code onDetach} on the old view and everything inside it.</p>
     */
    private static void mountView(String containerId, Component view) {
        HTMLElement container = elementById(containerId);
        if (container == null) {
            consoleWarn("[zeroz4j] Router cannot mount: no element with id '" + containerId + "'.");
            return;
        }
        Component.replaceContents(container, view);
    }

    @JSBody(params = { "id" }, script = "return document.getElementById(id);")
    static native HTMLElement elementById(String id);

    /** Writes a diagnostic line to the browser console. */
    @JSBody(params = { "message" }, script = "console.warn(message);")
    static native void consoleWarn(String message);
}
