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
import com.zeroz4j.client.AppBase;
import com.zeroz4j.ui.component.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns URLs into views: matches the current path, loads what the matched route needs, and mounts
 * the result.
 *
 * <p>Start it once, pointing at the element the application lives in:</p>
 * <pre>{@code
 * Zeroz4jClient.connect(wsUrl, () -> Router.start("app-root"));
 * ...
 * Router.navigate("/tasks/42");     // or click an <a data-route href="/tasks/42">
 * }</pre>
 *
 * <h2>What happens on a navigation</h2>
 * <ol>
 *   <li>The path is matched against the route table, most specific pattern first.</li>
 *   <li>The layout chain is resolved outward from the matched route.</li>
 *   <li>Every {@code @RequiresRole} in that chain is checked. A failure never renders the view.</li>
 *   <li>Each level's {@code load} runs, outermost first, so a nested route can rely on data its
 *       layout fetched.</li>
 *   <li>Only then are the components built, innermost first, and each layout wraps its child.</li>
 * </ol>
 *
 * <p>Nothing reaches the screen until every loader has returned, so there is no intermediate state
 * to design around — and no view that mounts, discovers it needs data, and re-renders.</p>
 *
 * <h2>Deployed under a context path</h2>
 * <p>Route paths are written the way {@code @Route} declares them, and stay that way whether the
 * application is served from {@code /} or from {@code /coachapp}. The router translates in both
 * directions through {@link com.zeroz4j.client.AppBase}, so nothing in a route table, a
 * {@code navigate} call or a {@code RouteParams} ever carries a context path.</p>
 *
 * <p>An {@code href} is the exception, because it has to be a real URL for middle-click and "open in
 * new tab" to work — write those with {@code AppBase.location("/tasks/42")}. The router accepts
 * either form on the way back in.</p>
 *
 * <h2>Loaders run in sequence, not in parallel</h2>
 * <p>Client code runs on a single cooperative scheduler and cannot create threads, so a layout's
 * loader and its child's cannot overlap: two round trips are two round trips. The win over fetching
 * inside components is the ordering guarantee above, not concurrency. Loading data a layout and its
 * children both need <em>once, in the layout</em> is what avoids the repeated fetch.</p>
 */
public final class Router {

    /** Notified when a navigation finishes, for updating navigation highlighting and the like. */
    public interface NavigationListener {
        /**
         * Called after a navigation has completed and its view is on screen.
         *
         * @param params the parameters of the route now displayed
         */
        void onNavigated(RouteParams params);
    }

    /** Handles a navigation that could not be completed. */
    public interface ErrorHandler {
        /**
         * Called when a navigation could not be completed — usually a loader whose call failed. The
         * page is left as it was.
         *
         * @param path   the path that failed
         * @param reason what went wrong
         */
        void onError(String path, Throwable reason);
    }

    private static String containerId;
    private static ErrorHandler errorHandler;
    private static final List<NavigationListener> listeners = new ArrayList<>();
    private static String notFoundPath;
    private static String forbiddenPath;
    private static String currentPath;

    private Router() {}

    /**
     * Loads the route table, renders whatever the current URL points at, and starts listening for
     * navigation.
     *
     * @param containerElementId id of the element the router owns; its contents are replaced on
     *                           every navigation
     */
    public static void start(String containerElementId) {
        containerId = containerElementId;
        RouteRegistry.init();
        // Route paths are what the route table is written in and what @Route declares; browser
        // locations carry the deployment's context path in front of them. Every crossing between the
        // two goes through AppBase, so a route table never has to know where it was deployed.
        RouterBrowser.onPopState(path -> renderInCoroutine(AppBase.route(path)));
        RouterBrowser.interceptRouteLinks(Router::navigate);
        renderInCoroutine(AppBase.route(RouterBrowser.currentPath()));
    }

    /**
     * Navigates to a path, adding a history entry so Back returns where the user came from.
     *
     * @param path the path, e.g. {@code "/tasks/42"}
     */
    public static void navigate(String path) {
        // Either form is accepted: a route path as @Route declares it, or a full location as an
        // anchor written with AppBase.location carries it. Anchors are the reason -- an href has to
        // be a real URL for middle-click and "open in new tab" to land in the right application.
        if (path == null) {
            return;
        }
        String route = AppBase.route(path);
        if (route.equals(currentPath)) {
            return;
        }
        RouterBrowser.pushState(AppBase.location(route));
        renderInCoroutine(route);
    }

    /**
     * Navigates without adding a history entry, replacing the current one.
     *
     * <p>For a redirect the user should not be able to go Back into — a landing path that resolves
     * elsewhere, or a route they were bounced off.</p>
     *
     * @param path the path
     */
    public static void replace(String path) {
        String route = AppBase.route(path);
        RouterBrowser.replaceState(AppBase.location(route));
        renderInCoroutine(route);
    }

    /**
     * Where to send a navigation that matches no route. Without one, an unmatched path reports
     * through the error handler and leaves the page as it was.
     *
     * @param path the fallback path
     */
    public static void notFoundRoute(String path) {
        notFoundPath = path;
    }

    /**
     * Where to send a navigation the user's roles do not permit.
     *
     * <p>Client-side role checks are for showing the right thing, never for protection — the server
     * re-checks every call. Skipping this only means the user reaches a view that fails.</p>
     *
     * @param path the fallback path
     */
    public static void forbiddenRoute(String path) {
        forbiddenPath = path;
    }

    /**
     * Handles a navigation that threw — usually a loader whose call failed.
     *
     * @param handler the handler
     */
    public static void onError(ErrorHandler handler) {
        errorHandler = handler;
    }

    /**
     * Adds a listener notified after each successful navigation.
     *
     * @param listener the listener
     */
    public static void addNavigationListener(NavigationListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /**
     * The path whose view is on screen. Updated only once a navigation has fully succeeded, so it
     * never reports a route whose loader failed.
     *
     * @return the current path, or null before the first navigation
     */
    public static String currentPath() {
        return currentPath;
    }

    // ---------------------------------------------------------------- rendering

    /**
     * Runs a navigation where its loaders are allowed to suspend.
     *
     * <p>Navigation is triggered from browser callbacks — a click, a popstate, the frame that
     * reports authentication — and TeaVM cannot suspend a coroutine on a stack that started in
     * native JavaScript. A loader making an RMI call is exactly such a suspension, so calling it
     * directly from those callbacks fails with "suspension point reached from non-threading
     * context" and the navigation dies before rendering anything.</p>
     *
     * <p>Starting a thread re-enters TeaVM's own scheduler, which is what makes the loaders legal.
     * It is a green thread on the browser's event loop, not parallelism — nothing here runs at the
     * same time as anything else.</p>
     */
    private static void renderInCoroutine(String fullPath) {
        new Thread(() -> render(fullPath)).start();
    }

    private static void render(String fullPath) {
        String path = stripQuery(fullPath);
        RouteRegistry.RouteMatch match = RouteRegistry.match(path);

        if (match == null) {
            if (notFoundPath != null && !notFoundPath.equals(path)) {
                replace(notFoundPath);
            } else {
                fail(path, new IllegalStateException("No route matches '" + path
                        + "'. Declare one with @Route(\"" + path + "\"), or set a not-found route."));
            }
            return;
        }

        List<RouteDefinition> chain = layoutChain(match.definition());

        for (RouteDefinition definition : chain) {
            if (!isPermitted(definition)) {
                if (forbiddenPath != null && !forbiddenPath.equals(path)) {
                    replace(forbiddenPath);
                } else {
                    fail(path, new SecurityException("'" + path + "' requires one of "
                            + definition.requiredRoles() + "; this user has "
                            + RmiSecurityContext.getRoles() + "."));
                }
                return;
            }
        }

        RouteParams params = new RouteParams(path, match.pathParams(), queryParams(fullPath));

        try {
            // Outermost first, so a nested route can rely on what its layout fetched. Everything is
            // loaded before anything is built -- the whole point of putting the fetch on the route.
            List<Object> instances = new ArrayList<>(chain.size());
            List<Object> data = new ArrayList<>(chain.size());
            for (RouteDefinition definition : chain) {
                Object instance = definition.newInstance();
                instances.add(instance);
                data.add(load(instance, params));
            }

            // Then build inward-out: the matched view first, each layout wrapping what it contains.
            int last = chain.size() - 1;
            Component rendered = renderView(instances.get(last), data.get(last), params,
                    chain.get(last));
            for (int i = last - 1; i >= 0; i--) {
                rendered = renderLayout(instances.get(i), data.get(i), params, rendered,
                        chain.get(i));
            }

            RouterBrowser.mount(containerId, rendered);
            currentPath = path;
            for (NavigationListener listener : listeners) {
                listener.onNavigated(params);
            }
        } catch (RuntimeException ex) {
            // The page is left as it was: replacing a working view with a blank one because a fetch
            // failed loses whatever the user was doing.
            fail(path, ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object load(Object instance, RouteParams params) {
        if (instance instanceof RouteView) {
            return ((RouteView<Object>) instance).load(params);
        }
        return ((RouteLayout<Object>) instance).load(params);
    }

    @SuppressWarnings("unchecked")
    private static Component renderView(Object instance, Object data, RouteParams params,
                                        RouteDefinition definition) {
        if (!(instance instanceof RouteView)) {
            throw new IllegalStateException(definition.targetClassName()
                    + " is annotated @Route but does not implement RouteView. A class implementing "
                    + "RouteLayout can only be reached through a child route's layout attribute.");
        }
        return ((RouteView<Object>) instance).render(data, params);
    }

    @SuppressWarnings("unchecked")
    private static Component renderLayout(Object instance, Object data, RouteParams params,
                                          Component child, RouteDefinition definition) {
        if (!(instance instanceof RouteLayout)) {
            throw new IllegalStateException(definition.targetClassName()
                    + " is used as a layout but does not implement RouteLayout.");
        }
        return ((RouteLayout<Object>) instance).render(data, params, child);
    }

    /**
     * Resolves a route's layout chain, outermost first, ending with the route itself.
     */
    private static List<RouteDefinition> layoutChain(RouteDefinition route) {
        List<RouteDefinition> chain = new ArrayList<>();
        chain.add(route);

        RouteDefinition current = route;
        while (current.layoutClassName() != null) {
            RouteDefinition parent = RouteRegistry.byClassName(current.layoutClassName());
            if (parent == null) {
                throw new IllegalStateException(current.targetClassName() + " names "
                        + current.layoutClassName() + " as its layout, but that class carries no "
                        + "@Route annotation, so it is not in the route table.");
            }
            if (chain.contains(parent)) {
                throw new IllegalStateException("Layouts form a cycle at " + parent.targetClassName()
                        + "; a layout cannot contain itself, directly or through its own layout.");
            }
            chain.add(0, parent);
            current = parent;
        }
        return chain;
    }

    private static boolean isPermitted(RouteDefinition definition) {
        if (definition.requiredRoles().isEmpty()) {
            return true;
        }
        return RmiSecurityContext.hasAnyRole(
                definition.requiredRoles().toArray(new String[0]));
    }

    private static void fail(String path, Throwable reason) {
        if (errorHandler != null) {
            errorHandler.onError(path, reason);
            return;
        }
        // Without a handler this would vanish, and a navigation that silently does nothing is the
        // hardest kind of bug to notice.
        RouterBrowser.warn("[zeroz4j] Navigation to '" + path + "' failed: " + reason.getMessage());
    }

    private static String stripQuery(String fullPath) {
        int question = fullPath.indexOf('?');
        String path = question >= 0 ? fullPath.substring(0, question) : fullPath;
        int hash = path.indexOf('#');
        return hash >= 0 ? path.substring(0, hash) : path;
    }

    private static Map<String, String> queryParams(String fullPath) {
        Map<String, String> params = new LinkedHashMap<>();
        int question = fullPath.indexOf('?');
        if (question < 0) {
            return params;
        }
        String query = fullPath.substring(question + 1);
        int hash = query.indexOf('#');
        if (hash >= 0) {
            query = query.substring(0, hash);
        }
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int equals = pair.indexOf('=');
            if (equals < 0) {
                params.put(pair, "");
            } else {
                params.put(pair.substring(0, equals), pair.substring(equals + 1));
            }
        }
        return params;
    }
}
