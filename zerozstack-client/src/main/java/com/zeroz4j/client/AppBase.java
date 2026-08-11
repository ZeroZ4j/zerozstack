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

import org.teavm.jso.JSBody;

/**
 * Where this application lives, when it does not live at the site root.
 *
 * <p>A WAR is usually deployed under a context path — {@code /coachapp}, {@code /clientportal} — and
 * everything the client computes for itself has to account for that. Four things do: the route the
 * URL means, the URL a navigation writes into the address bar, the WebSocket endpoint, and the
 * service worker's path. Each of them is a leading slash away from being wrong, and each of them
 * fails only once the application is deployed somewhere other than where it was developed.</p>
 *
 * <p>The base is read from {@code document.baseURI}, which the server fills in: the shell is served
 * with a {@code <base href>} for its own context path (see {@code StaticContent.shellBytes}). That
 * makes it the same value the browser already uses to resolve every relative URL on the page, so the
 * bundle, the manifest, the icons and this class cannot disagree about where the application is.</p>
 *
 * <h2>Using it</h2>
 * <pre>{@code
 * Zeroz4jClient.connect(AppBase.webSocketUrl(), () -> Router.start("app-root"));
 * ...
 * anchor.setAttribute("href", AppBase.location("/messages/42"));   // /coachapp/messages/42
 * }</pre>
 *
 * <p>{@link com.zeroz4j.client.router.Router} and {@link Pwa} use it by themselves; an application
 * only needs it for the socket URL and for anchors it writes by hand.</p>
 */
public final class AppBase {

    /** Cached because it cannot change without a page load, and it is read on every navigation. */
    private static String base;

    private AppBase() {}

    /**
     * The application's root path: always starts with a slash, always ends with one.
     *
     * @return {@code "/coachapp/"} under a context path, {@code "/"} at the site root
     */
    public static String path() {
        if (base == null) {
            base = normalize(baseUriPath());
        }
        return base;
    }

    /**
     * Resolves a path inside the application.
     *
     * @param relative a path relative to the application root, with or without a leading slash
     * @return the absolute path from the site root
     */
    public static String url(String relative) {
        return location(relative);
    }

    /**
     * The framework's WebSocket endpoint for this deployment.
     *
     * <p>Correct on a deep link and correct under a context path, which hand-written variants of this
     * usually are not: deriving the base by stripping the last segment off
     * {@code window.location.pathname} gives the right answer on {@code /coachapp/} and the wrong one
     * on {@code /coachapp} and on {@code /coachapp/messages/42}.</p>
     *
     * @return e.g. {@code "wss://example.com/coachapp/wasm-rmi"}
     */
    public static String webSocketUrl() {
        return webSocketScheme() + host() + path() + "wasm-rmi";
    }

    /**
     * The route a browser location means, with the application's base removed.
     *
     * @param locationPath {@code window.location.pathname}, optionally with its query string
     * @return the path the route table is matched against, e.g. {@code "/messages/42"}
     */
    public static String route(String locationPath) {
        return toRoute(path(), locationPath);
    }

    /**
     * The browser location a route means, with the application's base put back on.
     *
     * @param routePath a route path, e.g. {@code "/messages/42"}
     * @return the path to push into the address bar, e.g. {@code "/coachapp/messages/42"}
     */
    public static String location(String routePath) {
        return toLocation(path(), routePath);
    }

    // ------------------------------------------------------------------ the rules, without a browser

    /**
     * Strips a base from a location path. Package-private and taking the base as an argument so the
     * rule is testable off the browser, which is where its edge cases are.
     *
     * @param base         the application root, as {@link #path()} returns it
     * @param locationPath the location path, possibly with a query string
     * @return the route path, always starting with a slash
     */
    static String toRoute(String base, String locationPath) {
        if (locationPath == null || locationPath.isEmpty()) {
            return "/";
        }
        int query = locationPath.indexOf('?');
        String path = query >= 0 ? locationPath.substring(0, query) : locationPath;
        String rest = query >= 0 ? locationPath.substring(query) : "";

        if ("/".equals(base)) {
            return (path.isEmpty() ? "/" : path) + rest;
        }
        String prefix = base.substring(0, base.length() - 1);      // "/coachapp"
        if (path.equals(prefix)) {
            return "/" + rest;                                     // the bare context path
        }
        if (path.startsWith(base)) {
            return "/" + path.substring(base.length()) + rest;
        }
        // Outside the application. Nothing sensible to strip, and inventing a route from it would
        // mean a URL that is not ours matching a route that is.
        return path + rest;
    }

    /**
     * Puts the base back on a route path.
     *
     * @param base      the application root, as {@link #path()} returns it
     * @param routePath the route path
     * @return the location path
     */
    static String toLocation(String base, String routePath) {
        if (routePath == null) {
            return base;
        }
        if ("/".equals(base)) {
            return routePath.startsWith("/") ? routePath : "/" + routePath;
        }
        if (routePath.startsWith(base)) {
            return routePath;                                      // already absolute in this app
        }
        String relative = routePath.startsWith("/") ? routePath.substring(1) : routePath;
        return base + relative;
    }

    /**
     * Turns a {@code document.baseURI} pathname into a base: absolute, ending in a slash, and never
     * a file. A shell served for {@code /coachapp/messages/42} with no {@code <base>} reports that
     * whole path, so the last segment is dropped unless it is already a directory.
     *
     * @param baseUriPath the pathname of {@code document.baseURI}
     * @return the application root
     */
    static String normalize(String baseUriPath) {
        if (baseUriPath == null || baseUriPath.isEmpty()) {
            return "/";
        }
        String path = baseUriPath.startsWith("/") ? baseUriPath : "/" + baseUriPath;
        if (path.endsWith("/")) {
            return path;
        }
        return path.substring(0, path.lastIndexOf('/') + 1);
    }

    // ------------------------------------------------------------------ browser

    @JSBody(params = {}, script = "return new URL(document.baseURI).pathname;")
    private static native String baseUriPath();

    @JSBody(params = {}, script =
        "return window.location.protocol === 'https:' ? 'wss://' : 'ws://';")
    private static native String webSocketScheme();

    @JSBody(params = {}, script = "return window.location.host;")
    private static native String host();
}
