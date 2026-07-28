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

import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;
import java.security.Principal;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Server endpoint configurator that intercepts HTTP -> WebSocket upgrade handshakes and propagates security principals and roles.
 *
 * <p>Reads the authenticated HTTP principal and role set during the WebSocket upgrade handshake and copies them
 * into {@code ServerEndpointConfig.getUserProperties()} under {@link #PRINCIPAL_KEY} and {@link #ROLES_KEY}.</p>
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>Handshake Interception:</b> Executed during HTTP GET upgrade to WebSocket before {@code @OnOpen}.</li>
 *   <li><b>Role Population:</b> Evaluates {@code HandshakeRequest.isUserInRole(role)} for each role registered in {@code knownRoles}.</li>
 * </ul>
 */
public class RmiEndpointConfigurator extends ServerEndpointConfig.Configurator {

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(RmiEndpointConfigurator.class.getName());

    /** Key used to store the Principal in user properties map ("zeroz.principal"). */
    public static final String PRINCIPAL_KEY = "zeroz.principal";
    /** Key used to store the role set in user properties map ("zeroz.roles"). */
    public static final String ROLES_KEY = "zeroz.roles";

    /** Key used to store the tenant id in user properties map ("zeroz.tenant"). */
    public static final String TENANT_KEY = "zeroz.tenant";

    /**
     * The application's authentication provider, discovered once via {@link java.util.ServiceLoader}.
     * Resolved lazily because the handshake can run before anything else has touched this class.
     */
    private static volatile AuthenticationProvider provider;
    private static volatile boolean providerResolved;

    /**
     * @return the application's provider, or null when none is registered
     */
    static AuthenticationProvider provider() {
        if (!providerResolved) {
            synchronized (RmiEndpointConfigurator.class) {
                if (!providerResolved) {
                    provider = loadProvider();
                    providerResolved = true;
                }
            }
        }
        return provider;
    }

    private static AuthenticationProvider loadProvider() {
        java.util.List<AuthenticationProvider> found = new java.util.ArrayList<>();
        try {
            for (AuthenticationProvider p
                    : java.util.ServiceLoader.load(AuthenticationProvider.class)) {
                found.add(p);
            }
        } catch (Throwable t) {
            LOG.log(java.util.logging.Level.WARNING,
                    "[zeroz4j] Failed to load AuthenticationProvider: " + t.getMessage(), t);
            return null;
        }
        if (found.isEmpty()) {
            return null;
        }
        if (found.size() > 1) {
            // Ambiguous authentication is refused rather than resolved arbitrarily: picking one at
            // random decides who can log in.
            throw new IllegalStateException(
                    "Multiple AuthenticationProvider implementations found: " + found
                    + ". Exactly one must be registered in META-INF/services.");
        }
        LOG.info("[zeroz4j] Authentication provider: " + found.get(0).getClass().getName());
        return found.get(0);
    }

    /** Test support: forces the provider to be looked up again. */
    static void resetProviderForTesting() {
        synchronized (RmiEndpointConfigurator.class) {
            provider = null;
            providerResolved = false;
        }
    }

    /** Roles to check during handshake - populated by WasmRmiServerEngine at startup. */
    static final Set<String> knownRoles = new LinkedHashSet<>();

    /**
     * Modifies the WebSocket handshake request to store the caller's security principal and roles in user properties.
     *
     * @param config   the server endpoint configuration instance
     * @param request  the handshake HTTP request
     * @param response the handshake HTTP response
     *
     * <p><b>Under the hood:</b> Extracts {@code request.getUserPrincipal()}. Checks {@code request.isUserInRole(role)} against
     * {@code knownRoles}. Puts principal and userRoles set into {@code config.getUserProperties()}.</p>
     */
    @Override
    public void modifyHandshake(ServerEndpointConfig config,
                                HandshakeRequest request,
                                HandshakeResponse response) {
        super.modifyHandshake(config, request, response);
        Principal principal = request.getUserPrincipal();
        Set<String> userRoles = new LinkedHashSet<>();
        String tenantId = null;

        // An application-supplied provider decides identity, roles and tenant. It sees the container
        // principal too, so a deployment behind container-managed security can enrich rather than
        // replace it.
        AuthenticationProvider auth = provider();
        if (auth != null) {
            try {
                AuthenticatedPrincipal authenticated = auth.authenticate(new HandshakeCredentials(
                        request.getParameterMap(), request.getHeaders(), principal));
                if (authenticated != null) {
                    final String name = authenticated.name();
                    principal = () -> name;
                    userRoles.addAll(authenticated.roles());
                    tenantId = authenticated.tenantId();
                } else {
                    principal = null;   // the provider declined: stay anonymous
                }
            } catch (RuntimeException ex) {
                // Refusing the upgrade would give the client no way to report why, so the connection
                // proceeds anonymously and every @Secured call on it fails.
                LOG.log(java.util.logging.Level.WARNING,
                        "[zeroz4j] Authentication provider rejected the handshake: "
                        + ex.getMessage(), ex);
                principal = null;
                userRoles.clear();
                tenantId = null;
            }
        } else if (principal == null && DevAuth.isDevMode()) {
            // Development fallback, used only when no provider is registered: credentials arrive as
            // user/password query parameters and are checked against DevAuth's demo users.
            java.util.Map<String, java.util.List<String>> params = request.getParameterMap();
            String user = firstParam(params, "user");
            String password = firstParam(params, "password");
            Set<String> devRoles = DevAuth.authenticate(user, password);
            if (devRoles != null) {
                principal = () -> user;
                userRoles.addAll(devRoles);
            }
        }

        // Anonymous connections have no principal; user-properties maps may reject null
        // values (Tomcat/TomEE use a ConcurrentHashMap and NPE on put(key, null)).
        if (principal != null) {
            config.getUserProperties().put(PRINCIPAL_KEY, principal);
        }

        if (principal != null && userRoles.isEmpty()) {
            for (String role : knownRoles) {
                if (request.isUserInRole(role)) {
                    userRoles.add(role);
                }
            }
        }
        config.getUserProperties().put(ROLES_KEY, userRoles);
        if (tenantId != null) {
            config.getUserProperties().put(TENANT_KEY, tenantId);
        }
    }

    private static String firstParam(java.util.Map<String, java.util.List<String>> params, String name) {
        java.util.List<String> values = params != null ? params.get(name) : null;
        return values != null && !values.isEmpty() ? values.get(0) : null;
    }
}
