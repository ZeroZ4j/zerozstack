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
package com.zeroz4j.server;

import java.security.Principal;
import java.util.Collections;
import java.util.Set;

/**
 * Thread-local context managing the security principal, granted roles, and session ID for the current RMI execution thread.
 * Populated by {@link WasmRmiServerEngine} before invoking service methods on virtual threads.
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>ThreadLocal Lifecycle:</b> Uses 3 {@link ThreadLocal} variables ({@code principalHolder}, {@code rolesHolder}, {@code sessionIdHolder}).</li>
 *   <li><b>Cleanup Guarantee:</b> {@code WasmRmiServerEngine} wraps RMI invocation in a {@code try-finally} block ensuring {@link #clear()} is executed.</li>
 * </ul>
 */
public final class RmiRequestContext {
    private static final ThreadLocal<Principal> principalHolder = new ThreadLocal<>();
    private static final ThreadLocal<Set<String>> rolesHolder = new ThreadLocal<>();
    private static final ThreadLocal<String> sessionIdHolder = new ThreadLocal<>();
    private static final ThreadLocal<String> tenantIdHolder = new ThreadLocal<>();

    private RmiRequestContext() {}

    /**
     * Sets the security principal, role set, and session ID for the current thread context.
     *
     * @param principal caller security principal
     * @param roles     set of role strings
     * @param sessionId WebSocket session ID
     *
     * <p><b>Under the hood:</b> Sets the 3 internal {@code ThreadLocal} holders.</p>
     */
    public static void setContext(Principal principal, Set<String> roles, String sessionId) {
        setContext(principal, roles, sessionId, null);
    }

    /**
     * Binds the caller's identity, roles, session and tenant to the current thread.
     *
     * @param principal the authenticated principal, or null for an anonymous connection
     * @param roles     the granted roles
     * @param sessionId the WebSocket session id
     * @param tenantId  the tenant reported by the {@link AuthenticationProvider}, or null
     */
    public static void setContext(Principal principal, Set<String> roles, String sessionId,
                                  String tenantId) {
        tenantIdHolder.set(tenantId);
        principalHolder.set(principal);
        rolesHolder.set(roles != null ? roles : Collections.emptySet());
        sessionIdHolder.set(sessionId);
    }

    /**
     * Retrieves the security {@link Principal} for the current call context.
     *
     * @return principal object, or {@code null} if unauthenticated/unpopulated
     */
    public static Principal getPrincipal() {
        return principalHolder.get();
    }

    /**
     * Retrieves the set of role names granted to the caller.
     *
     * @return set of role names (returns empty set if unpopulated)
     */
    public static Set<String> getRoles() {
        Set<String> roles = rolesHolder.get();
        return roles != null ? roles : Collections.emptySet();
    }

    /**
     * Retrieves the WebSocket session ID of the caller.
     *
     * @return session ID string
     */
    /**
     * The tenant this call belongs to, as reported by the {@link AuthenticationProvider}.
     *
     * <p>Use it to scope a push with {@link com.zeroz4j.api.Scope#TENANT}, and to partition data in a
     * multi-tenant application. Null in a single-tenant application, or when the connection is
     * anonymous.</p>
     *
     * @return the tenant id, or null
     */
    public static String getTenantId() {
        return tenantIdHolder.get();
    }

    public static String getSessionId() {
        return sessionIdHolder.get();
    }

    /**
     * Clears all thread-local context variables to prevent memory leaks in pooled threads.
     *
     * <p><b>Under the hood:</b> Invokes {@code remove()} on all ThreadLocal holders.</p>
     */
    public static void clear() {
        principalHolder.remove();
        rolesHolder.remove();
        sessionIdHolder.remove();
        tenantIdHolder.remove();
    }
}
