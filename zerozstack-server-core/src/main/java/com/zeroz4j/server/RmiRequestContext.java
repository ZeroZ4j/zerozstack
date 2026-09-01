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
import java.util.Locale;
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
    private static final ThreadLocal<String> clientIdHolder = new ThreadLocal<>();
    private static final ThreadLocal<Locale> localeHolder = new ThreadLocal<>();

    /**
     * What language to answer in when no call is in progress — a scheduled job writing a message,
     * say. Set from {@code zeroz.i18n.defaultLocale} when a server starts.
     */
    private static volatile Locale deploymentDefaultLocale = Locale.ENGLISH;

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
        setContext(principal, roles, sessionId, tenantId, null);
    }

    /**
     * Binds the caller's identity, roles, session, tenant and client id to the current thread.
     *
     * @param principal the authenticated principal, or null for an anonymous connection
     * @param roles     the granted roles
     * @param sessionId the WebSocket session id
     * @param tenantId  the tenant reported by the {@link AuthenticationProvider}, or null
     * @param clientId  the browser's client id, or null when the handshake carried none
     */
    public static void setContext(Principal principal, Set<String> roles, String sessionId,
                                  String tenantId, String clientId) {
        setContext(principal, roles, sessionId, tenantId, clientId, null);
    }

    /**
     * Binds the caller's identity, roles, session, tenant, client id and language to the current
     * thread.
     *
     * @param principal the authenticated principal, or null for an anonymous connection
     * @param roles     the granted roles
     * @param sessionId the WebSocket session id
     * @param tenantId  the tenant reported by the {@link AuthenticationProvider}, or null
     * @param clientId  the browser's client id, or null when the handshake carried none
     * @param locale    the language this connection reads, or null to use the deployment's own
     * @since 0.9.0
     */
    public static void setContext(Principal principal, Set<String> roles, String sessionId,
                                  String tenantId, String clientId, Locale locale) {
        tenantIdHolder.set(tenantId);
        clientIdHolder.set(clientId);
        localeHolder.set(locale);
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

    /**
     * The browser this call came from, as issued by {@link ClientIdentity}.
     *
     * <p>Present whether or not the connection is authenticated, which is what makes
     * {@link com.zeroz4j.api.Scope#CLIENT} the scope for an application with no login. Null only
     * when the handshake carried no client id and none could be issued.</p>
     *
     * <p><b>Identifies a browser, not a person.</b> Never use it to decide whether the caller may
     * see something that belongs to a particular user — that is what {@link #getPrincipal()} and
     * {@link #getRoles()} are for.</p>
     *
     * @return the client id, or null
     */
    public static String getClientId() {
        return clientIdHolder.get();
    }

    public static String getSessionId() {
        return sessionIdHolder.get();
    }

    /**
     * The language this call's caller reads.
     *
     * <p><b>Never null</b>, so nothing has to check. When no call is in progress it is the
     * deployment's own language, from {@code zeroz.i18n.defaultLocale} — deliberately not the
     * machine's, because a server in Frankfurt has a German JVM locale that has nothing to do with
     * whoever is calling it.</p>
     *
     * <p>Resolved once, when the connection was opened, from what the browser sent. Take it from
     * here and never from a method argument, for the same reason identity is taken from here: an
     * argument is something the caller can lie about and the framework cannot default.</p>
     *
     * @return the caller's locale
     * @since 0.9.0
     */
    public static Locale getLocale() {
        Locale bound = localeHolder.get();
        return bound != null ? bound : deploymentDefaultLocale;
    }

    /**
     * Sets what language to answer in when no call is in progress. Called when a server starts,
     * from its own {@code zeroz.i18n.defaultLocale}.
     *
     * @param locale the deployment's own language, or null for English
     * @since 0.9.0
     */
    public static void setDeploymentDefaultLocale(Locale locale) {
        deploymentDefaultLocale = locale != null ? locale : Locale.ENGLISH;
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
        clientIdHolder.remove();
        localeHolder.remove();
    }
}
