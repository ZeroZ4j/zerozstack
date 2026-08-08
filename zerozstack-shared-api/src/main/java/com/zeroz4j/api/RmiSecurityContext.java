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
package com.zeroz4j.api;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Who the server says this connection is, as reported by the AUTH frame (0x03) it sends once the
 * WebSocket opens.
 *
 * <p>The server decides; this is a mirror of that decision for the client's own use — showing a name,
 * hiding a menu item, gating a route. It is never a security boundary: every {@code @Secured} and
 * {@code @RolesAllowed} check is made again server-side, on every call.</p>
 *
 * <h2>Authenticated, anonymous, and not yet known</h2>
 * <p>These are three different states and the API keeps them apart. A connection whose credentials
 * the application's {@code AuthenticationProvider} declined is <b>anonymous</b>, not authenticated —
 * {@link #isAuthenticated()} stays false and {@link #onAuthenticated(Runnable)} never fires, so a
 * login gate hung off that callback holds. Before the server has answered at all, {@link #isResolved()}
 * is false and neither callback has run.</p>
 */
public final class RmiSecurityContext {

    private static final Logger LOG = Logger.getLogger(RmiSecurityContext.class.getName());
    private static volatile String username;
    private static volatile Set<String> roles = Collections.emptySet();
    private static volatile boolean authenticated;
    private static volatile boolean resolved;
    private static final List<Runnable> authCallbacks = new CopyOnWriteArrayList<>();
    private static final List<Runnable> failureCallbacks = new CopyOnWriteArrayList<>();
    private static final List<Runnable> resolvedCallbacks = new CopyOnWriteArrayList<>();

    private RmiSecurityContext() {}

    /**
     * Records the server's decision about this connection.
     *
     * <p>Called by the client runtime when an AUTH frame arrives; applications never call it.</p>
     *
     * @param user          the user name, or the anonymous sentinel when not authenticated
     * @param roles         the granted roles; empty for an anonymous connection
     * @param authenticated whether the server actually accepted an identity. <b>Not</b> inferred from
     *                      the other two arguments: a rejected connection still carries a name, and a
     *                      genuinely authenticated user may hold no application roles at all, so
     *                      guessing from either one gets a real case wrong.
     */
    public static void populate(String user, Set<String> roles, boolean authenticated) {
        RmiSecurityContext.username = user;
        RmiSecurityContext.roles = Collections.unmodifiableSet(new LinkedHashSet<>(roles));
        RmiSecurityContext.authenticated = authenticated;
        RmiSecurityContext.resolved = true;
        // Resolved first: an application that mounts its UI here should have done so before the
        // outcome-specific callbacks run and start reacting to the identity.
        run(resolvedCallbacks);
        run(authenticated ? authCallbacks : failureCallbacks);
    }

    private static void run(List<Runnable> callbacks) {
        for (Runnable callback : callbacks) {
            try {
                callback.run();
            } catch (Exception e) {
                LOG.warning("[RmiSecurityContext] Auth callback error: " + e.getMessage());
            }
        }
    }

    /**
     * Whether the server accepted an identity for this connection.
     *
     * <p>False for a connection the application's {@code AuthenticationProvider} declined, and false
     * before the server has answered. It means what its name says: no additional role check is needed
     * to tell a real sign-in from a refused one.</p>
     *
     * @return true only when authentication succeeded
     */
    public static boolean isAuthenticated() {
        return authenticated;
    }

    /**
     * Whether the server has reported its decision yet.
     *
     * <p>Lets a caller tell "declined" from "still waiting", which {@link #isAuthenticated()} alone
     * cannot — both are false.</p>
     *
     * @return true once an AUTH frame has arrived
     */
    public static boolean isResolved() {
        return resolved;
    }

    /**
     * Returns the authenticated username.
     *
     * @return username string, or {@code null} if unauthenticated
     */
    public static String getUsername() {
        return username;
    }

    /**
     * Returns the unmodifiable set of granted security roles.
     *
     * @return set of role names
     */
    public static Set<String> getRoles() {
        return roles;
    }

    /**
     * Evaluates whether the authenticated user possesses at least one of the specified roles.
     *
     * @param checkRoles varargs array of role strings to check
     * @return true if user is authenticated and possesses any of the specified roles
     *
     * <p><b>Under the hood:</b> Returns false if unauthenticated or roles set is empty. Iterates through {@code checkRoles}
     * and returns true on first match in {@code roles.contains(role)}.</p>
     */
    public static boolean hasAnyRole(String... checkRoles) {
        if (!authenticated || roles.isEmpty()) return false;
        for (String role : checkRoles) {
            if (roles.contains(role)) return true;
        }
        return false;
    }

    /**
     * Registers a listener for a successful sign-in, run immediately if one has already happened.
     *
     * <p>Fires <b>only</b> when the server accepted an identity, which is what makes it safe to gate
     * a protected view on. A connection the provider declined does not reach it.</p>
     *
     * <p><b>Not a "connected" signal.</b> An application with no login is anonymous by design and
     * never reaches this callback, so mounting a UI from it renders nothing at all. Use
     * {@link #onResolved(Runnable)} for readiness and keep this one for identity.</p>
     *
     * @param callback the {@link Runnable} to execute
     */
    public static void onAuthenticated(Runnable callback) {
        authCallbacks.add(callback);
        if (authenticated) {
            callback.run();
        }
    }

    /**
     * Registers a listener for the server declining this connection, run immediately if that has
     * already happened.
     *
     * <p>The counterpart to {@link #onAuthenticated(Runnable)}: an application with a login screen
     * needs a positive signal that the credentials were refused, otherwise a wrong password is
     * indistinguishable from a slow network and the screen waits forever.</p>
     *
     * @param callback the {@link Runnable} to execute
     */
    public static void onAuthenticationFailed(Runnable callback) {
        failureCallbacks.add(callback);
        if (resolved && !authenticated) {
            callback.run();
        }
    }

    /**
     * Registers a listener for the server having answered at all, run immediately if it already has.
     *
     * <p><b>This is the "connection is ready" hook, and the one an application with no login wants.</b>
     * It fires whether the connection ended up authenticated or anonymous — the point is that the
     * server has reported, so identity is now known and the application can build its UI.</p>
     *
     * <p>Do not use {@link #onAuthenticated(Runnable)} for this. That one is a statement about
     * <em>identity</em>, not about readiness, and it deliberately never fires for an anonymous
     * connection — so an open application that mounts from it renders nothing at all.</p>
     *
     * <pre>{@code
     * RmiSecurityContext.onResolved(() -> mountUi());              // open app: always runs
     * RmiSecurityContext.onAuthenticated(() -> showAdminMenu());   // only for a real sign-in
     * RmiSecurityContext.onAuthenticationFailed(() -> showError()); // credentials refused
     * }</pre>
     *
     * @param callback the {@link Runnable} to execute
     */
    public static void onResolved(Runnable callback) {
        resolvedCallbacks.add(callback);
        if (resolved) {
            callback.run();
        }
    }

    /**
     * Clears all identity data from the security context (e.g. upon disconnect or logout).
     *
     * <p><b>Under the hood:</b> Resets static fields {@code username = null}, {@code roles = emptySet()}, and {@code authenticated = false}.</p>
     */
    public static void clear() {
        username = null;
        roles = Collections.emptySet();
        authenticated = false;
        resolved = false;
    }
}
