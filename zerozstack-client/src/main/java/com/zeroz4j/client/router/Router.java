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

import com.zeroz4j.api.Disposable;
import com.zeroz4j.api.RmiSecurityContext;
import com.zeroz4j.ui.component.Component;

import java.util.ArrayDeque;
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
 * <h2>Showing that something is happening</h2>
 * <pre>{@code
 * Router.showBusyIndicator(true);   // a bar, a spinner and a wait cursor once a page takes 300 ms
 * Router.showFailureMessage(true);  // "We could not open this page..." with a Retry button
 * }</pre>
 *
 * <p>Both are off unless switched on, and both work with every way a navigation can start - links,
 * {@code navigate}, {@code replace}, Back and Forward - with nothing else to wire. An application
 * drawing its own uses {@link #addLifecycleListener(LifecycleListener)}, which is what those two
 * are built on.</p>
 *
 * <h2>Navigations that overlap</h2>
 * <p>Every navigation carries a sequence number. Start a second one while the first is still
 * loading and the first is abandoned: whatever it produces later - a view or a failure - is thrown
 * away. The screen and the address bar always end up at the navigation started last. See
 * {@link Navigation}.</p>
 *
 * <h2>When a navigation fails</h2>
 * <p>The page is left as it was, and the address bar keeps the address that failed, so reloading
 * the page tries it again. The failure goes to every error listener and every lifecycle listener.
 * When the cause was the connection, the framework reconnects the socket by itself, but it never
 * runs the navigation again by itself - a navigation is loader calls, and RMI calls are never
 * replayed. {@link #retry()} runs it again.</p>
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

    /**
     * Told when a navigation starts, and when it finishes or fails.
     *
     * <h2>What is promised</h2>
     * <ul>
     *   <li>Every way a navigation can begin raises {@code onNavigationStarted}: a
     *       {@code data-route} link, {@link #navigate}, {@link #replace}, Back and Forward, the
     *       first render in {@link #start}, a redirect to the not-found or forbidden route, and
     *       {@link #retry()}.</li>
     *   <li>The navigation started last always ends in exactly one {@code onNavigationFinished} or
     *       {@code onNavigationFailed}. An older one that was still loading when a newer one started
     *       ends in neither: it is superseded, and its result is thrown away. The newer navigation
     *       names it in {@link Navigation#supersededId()}.</li>
     *   <li>A redirect supersedes the navigation that caused it and then ends like any other; see
     *       {@link Navigation#redirectedFrom()}.</li>
     *   <li>A link or {@link #navigate} to the route already on the screen, or already loading,
     *       starts nothing and raises nothing - unless the last navigation to it failed, in which
     *       case it runs again. {@link #replace}, Back and Forward always start a navigation.</li>
     *   <li>Events arrive in the order they happened, one at a time. A listener that starts a
     *       navigation from inside a callback does not interrupt the event being delivered: every
     *       listener hears about it first, then about the new navigation.</li>
     * </ul>
     *
     * <p>So a loading indicator is two lines: on at started, off at finished or failed.</p>
     */
    public interface LifecycleListener {
        /**
         * A navigation has begun. Nothing has been loaded yet.
         *
         * @param navigation the navigation
         */
        default void onNavigationStarted(Navigation navigation) {
        }

        /**
         * The navigation started last has its view on the screen.
         *
         * @param navigation the navigation
         * @param params     the parameters of the route now displayed
         */
        default void onNavigationFinished(Navigation navigation, RouteParams params) {
        }

        /**
         * The navigation started last could not be completed. The page was left as it was.
         *
         * @param navigation the navigation
         * @param reason     what went wrong; also {@link Navigation#failure()}
         */
        default void onNavigationFailed(Navigation navigation, Throwable reason) {
        }
    }

    static RouterHost host = new RouterBrowser();

    private static String containerId;
    private static final List<ErrorHandler> errorHandlers = new ArrayList<>();
    private static final List<NavigationListener> listeners = new ArrayList<>();
    private static final List<LifecycleListener> lifecycleListeners = new ArrayList<>();
    private static String notFoundPath;
    private static String forbiddenPath;
    private static String currentPath;
    private static boolean listening;

    /** The sequence number the next navigation takes. */
    private static long sequence;

    /** The navigation started last, whatever became of it; null before the first. */
    private static Navigation latest;

    /** Events waiting to be delivered, so one is never delivered from inside another. */
    private static final ArrayDeque<Runnable> events = new ArrayDeque<>();
    private static boolean delivering;

    private Router() {}

    /**
     * Loads the route table, renders whatever the current URL points at, and starts listening for
     * navigation.
     *
     * <p>Calling it again renders the current URL into the given container again; it does not add a
     * second set of click and Back/Forward listeners.</p>
     *
     * @param containerElementId id of the element the router owns; its contents are replaced on
     *                           every navigation
     */
    public static void start(String containerElementId) {
        containerId = containerElementId;
        RouteRegistry.init();
        if (!listening) {
            listening = true;
            // Route paths are what the route table is written in and what @Route declares; browser
            // locations carry the deployment's context path in front of them. Every crossing between
            // the two goes through the host, so a route table never has to know where it was
            // deployed.
            host.onPopState(location -> begin(host.toRoute(location), Navigation.Trigger.HISTORY));
            host.interceptRouteLinks(href -> navigate(href, Navigation.Trigger.LINK));
        }
        begin(host.toRoute(host.currentLocation()), Navigation.Trigger.INITIAL);
    }

    /**
     * Navigates to a path, adding a history entry so Back returns where the user came from.
     *
     * <p>Does nothing, and raises no event, when the path is the route already on the screen or the
     * route already loading. The comparison includes the query string, so {@code /projects?sort=name}
     * from {@code /projects} is a navigation.</p>
     *
     * @param path the path, e.g. {@code "/tasks/42"}
     */
    public static void navigate(String path) {
        navigate(path, Navigation.Trigger.NAVIGATE);
    }

    private static void navigate(String path, Navigation.Trigger trigger) {
        // Either form is accepted: a route path as @Route declares it, or a full location as an
        // anchor written with AppBase.location carries it. Anchors are the reason -- an href has to
        // be a real URL for middle-click and "open in new tab" to land in the right application.
        if (path == null) {
            return;
        }
        String route = host.toRoute(path);
        // Compared with the navigation started last, not with the view on screen. Comparing with
        // the screen alone is how a pending navigation used to win against a later click: with
        // /tasks still loading, a click on the link for the screen still showing matched the
        // screen, did nothing, and /tasks then arrived on top of what the person had just asked
        // for. A failed navigation is the exception, because asking again for a page that failed
        // is asking to try it again.
        if (latest != null && route.equals(latest.path())
                && latest.outcome() != Navigation.Outcome.FAILED) {
            return;
        }
        host.pushState(host.toLocation(route));
        begin(route, trigger);
    }

    /**
     * Navigates without adding a history entry, replacing the current one.
     *
     * <p>For a redirect the user should not be able to go Back into — a landing path that resolves
     * elsewhere, or a route they were bounced off. Always starts a navigation, even to the route
     * already on the screen, so it also serves to load the current page again.</p>
     *
     * @param path the path
     */
    public static void replace(String path) {
        String route = host.toRoute(path);
        host.replaceState(host.toLocation(route));
        begin(route, Navigation.Trigger.REPLACE);
    }

    /**
     * Runs the last navigation again, if it failed.
     *
     * <p>The framework reconnects a dropped connection by itself but never repeats a failed
     * navigation by itself, because a navigation is loader calls and RMI calls are never replayed:
     * the framework cannot know a call is safe to repeat. This is the explicit "try again". It is
     * what the Retry button on {@link #showFailureMessage(boolean) the failure message} does.</p>
     *
     * <p>While the connection is still down a retry fails straight away, with a
     * {@code DisconnectedException}, so it is only useful once the connection is back.</p>
     *
     * @return true when a navigation was started; false when the last navigation did not fail
     */
    public static boolean retry() {
        Navigation failed = latest;
        if (failed == null || failed.outcome() != Navigation.Outcome.FAILED) {
            return false;
        }
        host.replaceState(host.toLocation(failed.path()));
        begin(failed.path(), Navigation.Trigger.RETRY);
        return true;
    }

    /**
     * Where to send a navigation that matches no route. Without one, an unmatched path fails with a
     * {@link RouteNotFoundException} and leaves the page as it was.
     *
     * @param path the fallback path
     */
    public static void notFoundRoute(String path) {
        notFoundPath = path;
    }

    /**
     * Where to send a navigation the user's roles do not permit. Without one, it fails with a
     * {@link RouteForbiddenException}.
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
     * Adds a handler for a navigation that failed — usually a loader whose call failed.
     *
     * <p>The same as {@link #addErrorListener(ErrorHandler)}. Up to and including 0.9.0 this <em>replaced</em>
     * the previous handler, so only the last one registered was ever called; it now adds.</p>
     *
     * @param handler the handler
     */
    public static void onError(ErrorHandler handler) {
        addErrorListener(handler);
    }

    /**
     * Adds a handler for a navigation that failed. Every handler added is called, in the order they
     * were added.
     *
     * <p>Only the navigation started last reports a failure; one that was overtaken by a newer
     * navigation reports nothing. With no handler at all, a failure is written to the browser
     * console rather than vanishing.</p>
     *
     * @param handler the handler
     * @return removes the handler again
     */
    public static Disposable addErrorListener(ErrorHandler handler) {
        if (handler == null) {
            return () -> { };
        }
        errorHandlers.add(handler);
        return () -> errorHandlers.remove(handler);
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
     * Adds a listener told when each navigation starts, finishes and fails. See
     * {@link LifecycleListener} for exactly what is promised.
     *
     * @param listener the listener
     * @return removes the listener again
     */
    public static Disposable addLifecycleListener(LifecycleListener listener) {
        if (listener == null) {
            return () -> { };
        }
        lifecycleListeners.add(listener);
        return () -> lifecycleListeners.remove(listener);
    }

    /**
     * Turns the router's built-in busy indicator on or off. Off unless switched on.
     *
     * <p>Once a navigation has been running for 300 milliseconds it shows a thin bar across the top
     * of the page, a spinner on a small card in the middle, and a wait cursor over the whole page.
     * It hides the moment the navigation started last finishes or fails. It has no timeout of its
     * own: it stays for exactly as long as the navigation is really running. A navigation that
     * cannot complete does end - a dropped or silent connection fails its calls, and a call the
     * server never answers fails at the request timeout - and the indicator goes with it.</p>
     *
     * <p>It covers nothing and takes no clicks. A screen reader hears "Loading". Under
     * {@code prefers-reduced-motion} it pulses instead of moving. Its look is set with CSS custom
     * properties - color, bar height, spinner size, card background, and an offset to center it in
     * a content area beside a side menu; the names are in {@code docs/ROUTING.md}.</p>
     *
     * @param enabled true to show it
     */
    public static void showBusyIndicator(boolean enabled) {
        NavigationFeedback.busyIndicator(enabled);
    }

    /**
     * Turns the router's built-in failure message on or off. Off unless switched on.
     *
     * <p>When the navigation started last fails, it shows a short message near the top of the page
     * with a Retry button and a Dismiss button. For a dropped connection or a call the server did
     * not answer in time: "We could not open this page. Check your connection and try again." While
     * the connection is still down it adds that Retry will work once it is back, and Retry does
     * nothing until then. For a loader the server refused: "We could not open this page. Something
     * went wrong while loading it." For an address with no route, or a route the person may not
     * see, it says so and offers no Retry, because trying again cannot help.</p>
     *
     * <p>It goes away when the next navigation starts, including the one Retry starts, and when
     * Dismiss is pressed. It does not replace error listeners; they are still called.</p>
     *
     * @param enabled true to show it
     */
    public static void showFailureMessage(boolean enabled) {
        NavigationFeedback.failureMessage(enabled);
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

    /**
     * The navigation started last, whether it is still loading, finished or failed.
     *
     * @return the navigation, or null before the first
     */
    public static Navigation latestNavigation() {
        return latest;
    }

    // ---------------------------------------------------------------- the navigation lifecycle

    /**
     * Starts a navigation: supersedes whatever was still loading, announces it, and runs it where
     * its loaders may suspend.
     */
    private static Navigation begin(String route, Navigation.Trigger trigger) {
        Navigation navigation = open(route, trigger, null);
        host.runNavigation(() -> run(navigation));
        return navigation;
    }

    private static Navigation open(String route, Navigation.Trigger trigger, Navigation redirectedFrom) {
        Navigation previous = latest;
        long superseded = 0;
        if (previous != null && previous.outcome() == Navigation.Outcome.PENDING) {
            previous.markSuperseded();
            superseded = previous.id();
        }
        Navigation navigation = new Navigation(++sequence, route, trigger, redirectedFrom, superseded);
        latest = navigation;
        deliver(() -> {
            for (LifecycleListener listener : new ArrayList<>(lifecycleListeners)) {
                try {
                    listener.onNavigationStarted(navigation);
                } catch (RuntimeException ex) {
                    listenerFailed("onNavigationStarted", ex);
                }
            }
        });
        return navigation;
    }

    /** Whether a navigation is still the one whose result counts. */
    private static boolean isLatest(Navigation navigation) {
        return navigation == latest && navigation.outcome() == Navigation.Outcome.PENDING;
    }

    private static void run(Navigation navigation) {
        if (!isLatest(navigation)) {
            return;
        }
        String fullPath = navigation.path();
        String path = stripQuery(fullPath);
        try {
            RouteRegistry.RouteMatch match = RouteRegistry.match(path);

            if (match == null) {
                if (notFoundPath != null && !notFoundPath.equals(path)) {
                    redirect(navigation, notFoundPath);
                } else {
                    fail(navigation, new RouteNotFoundException(path));
                }
                return;
            }

            List<RouteDefinition> chain = layoutChain(match.definition());

            for (RouteDefinition definition : chain) {
                if (!isPermitted(definition)) {
                    if (forbiddenPath != null && !forbiddenPath.equals(path)) {
                        redirect(navigation, forbiddenPath);
                    } else {
                        fail(navigation, new RouteForbiddenException(path, "'" + path
                                + "' requires one of " + definition.requiredRoles()
                                + "; this user has " + RmiSecurityContext.getRoles() + "."));
                    }
                    return;
                }
            }

            RouteParams params = new RouteParams(path, match.pathParams(), queryParams(fullPath));

            // Outermost first, so a nested route can rely on what its layout fetched. Everything is
            // loaded before anything is built -- the whole point of putting the fetch on the route.
            List<Object> instances = new ArrayList<>(chain.size());
            List<Object> data = new ArrayList<>(chain.size());
            for (RouteDefinition definition : chain) {
                Object instance = definition.newInstance();
                instances.add(instance);
                data.add(load(instance, params));
                // A loader is where a navigation waits, so it is where a newer one can have started.
                // Stop here rather than running the remaining loaders for a page nobody will see.
                if (!isLatest(navigation)) {
                    return;
                }
            }

            // Then build inward-out: the matched view first, each layout wrapping what it contains.
            int last = chain.size() - 1;
            Component rendered = renderView(instances.get(last), data.get(last), params,
                    chain.get(last));
            for (int i = last - 1; i >= 0; i--) {
                rendered = renderLayout(instances.get(i), data.get(i), params, rendered,
                        chain.get(i));
            }
            if (!isLatest(navigation)) {
                return;
            }

            host.mount(containerId, rendered);
            finish(navigation, params);
        } catch (Throwable ex) {
            // The page is left as it was: replacing a working view with a blank one because a fetch
            // failed loses whatever the user was doing. Throwable rather than RuntimeException,
            // because a navigation that ended by throwing something else would otherwise never end
            // at all, and a busy indicator waiting for it would wait for ever.
            fail(navigation, ex);
        }
    }

    /**
     * Sends a navigation somewhere else. The redirect is a navigation of its own that supersedes the
     * one it came from, runs on the same green thread, and replaces the history entry so Back does
     * not walk into the address that was refused.
     */
    private static void redirect(Navigation from, String target) {
        String route = host.toRoute(target);
        host.replaceState(host.toLocation(route));
        Navigation redirect = open(route, Navigation.Trigger.REDIRECT, from);
        run(redirect);
    }

    private static void finish(Navigation navigation, RouteParams params) {
        if (!isLatest(navigation)) {
            return;
        }
        navigation.markFinished();
        currentPath = params.path();
        deliver(() -> {
            for (LifecycleListener listener : new ArrayList<>(lifecycleListeners)) {
                try {
                    listener.onNavigationFinished(navigation, params);
                } catch (RuntimeException ex) {
                    listenerFailed("onNavigationFinished", ex);
                }
            }
            for (NavigationListener listener : new ArrayList<>(listeners)) {
                try {
                    listener.onNavigated(params);
                } catch (RuntimeException ex) {
                    listenerFailed("onNavigated", ex);
                }
            }
        });
    }

    private static void fail(Navigation navigation, Throwable reason) {
        if (!isLatest(navigation)) {
            // Overtaken while it was loading. Its failure is about a page nobody is waiting for any
            // more, and reporting it would put an error message over the page they did ask for.
            return;
        }
        navigation.markFailed(reason);
        String path = stripQuery(navigation.path());
        deliver(() -> {
            for (LifecycleListener listener : new ArrayList<>(lifecycleListeners)) {
                try {
                    listener.onNavigationFailed(navigation, reason);
                } catch (RuntimeException ex) {
                    listenerFailed("onNavigationFailed", ex);
                }
            }
            if (errorHandlers.isEmpty()) {
                // Without a handler this would vanish, and a navigation that silently does nothing
                // is the hardest kind of bug to notice.
                host.warn("[zeroz4j] Navigation to '" + path + "' failed: " + reason.getMessage());
                return;
            }
            for (ErrorHandler handler : new ArrayList<>(errorHandlers)) {
                try {
                    handler.onError(path, reason);
                } catch (RuntimeException ex) {
                    listenerFailed("onError", ex);
                }
            }
        });
    }

    /**
     * Delivers events one at a time, in the order they were raised. An event raised while another
     * is being delivered - a listener that navigates, say - waits until every listener has had the
     * one before it.
     */
    private static void deliver(Runnable event) {
        events.add(event);
        if (delivering) {
            return;
        }
        delivering = true;
        try {
            Runnable next;
            while ((next = events.poll()) != null) {
                next.run();
            }
        } finally {
            delivering = false;
        }
    }

    private static void listenerFailed(String callback, RuntimeException ex) {
        // One listener throwing must not stop the others hearing about the navigation, or a busy
        // indicator registered after it would never be told to hide.
        host.warn("[zeroz4j] A router listener threw from " + callback + ": " + ex);
    }

    /** Test support only: forgets every listener, every navigation and every setting. */
    static void resetForTesting(RouterHost testHost) {
        host = testHost;
        containerId = null;
        errorHandlers.clear();
        listeners.clear();
        lifecycleListeners.clear();
        notFoundPath = null;
        forbiddenPath = null;
        currentPath = null;
        listening = false;
        sequence = 0;
        latest = null;
        events.clear();
        delivering = false;
    }

    // ---------------------------------------------------------------- rendering

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
