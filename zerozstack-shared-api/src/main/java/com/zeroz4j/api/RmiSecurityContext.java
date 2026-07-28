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
 * Security context populated from the AUTH frame (0x03) received from the server following WebSocket connection.
 * Provides the authenticated username and granted role set to client views and application code.
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>State Mutations:</b> Mutates static fields {@code username}, {@code roles}, and {@code authenticated}.</li>
 *   <li><b>Callbacks:</b> Stores post-authentication listeners in {@code authCallbacks} list and executes them on population.</li>
 * </ul>
 */
public final class RmiSecurityContext {

    private static final Logger LOG = Logger.getLogger(RmiSecurityContext.class.getName());
    private static volatile String username;
    private static volatile Set<String> roles = Collections.emptySet();
    private static volatile boolean authenticated;
    private static final List<Runnable> authCallbacks = new CopyOnWriteArrayList<>();

    private RmiSecurityContext() {}

    /**
     * Populates the security context with identity details received from the server.
     *
     * @param user  the authenticated username string
     * @param roles the set of granted role names
     *
     * <p><b>Under the hood:</b> Sets username and unmodifiable roles set, sets {@code authenticated = true},
     * and executes all registered callbacks in {@code authCallbacks}.</p>
     */
    public static void populate(String user, Set<String> roles) {
        RmiSecurityContext.username = user;
        RmiSecurityContext.roles = Collections.unmodifiableSet(new LinkedHashSet<>(roles));
        RmiSecurityContext.authenticated = true;
        for (Runnable callback : authCallbacks) {
            try {
                callback.run();
            } catch (Exception e) {
                LOG.warning("[RmiSecurityContext] Auth callback error: " + e.getMessage());
            }
        }
    }

    /**
     * Returns true if an AUTH frame has been received from the server.
     *
     * @return boolean authentication status
     *
     * <p><b>Under the hood:</b> Evaluates volatile boolean {@code authenticated}.</p>
     */
    public static boolean isAuthenticated() {
        return authenticated;
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
     * Registers a listener callback to be executed upon authentication completion.
     * If already authenticated at registration time, executes callback immediately.
     *
     * @param callback the {@link Runnable} to execute
     *
     * <p><b>Under the hood:</b> Appends callback to thread-safe {@code authCallbacks} list. Checks {@code authenticated} boolean;
     * if true, executes {@code callback.run()} inline.</p>
     */
    public static void onAuthenticated(Runnable callback) {
        authCallbacks.add(callback);
        if (authenticated) {
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
    }
}
