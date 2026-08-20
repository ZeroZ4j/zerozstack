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

    /** Key used to store the browser's client id in user properties map ("zeroz.clientId"). */
    public static final String CLIENT_KEY = "zeroz.clientId";

    /**
     * Key marking a handshake refused by {@link OriginPolicy}. The upgrade itself cannot be failed
     * from here in a container-independent way, so the flag is read by
     * {@link WasmRmiServerEngine#onOpen} which closes the connection immediately.
     */
    public static final String REJECTED_KEY = "zeroz.rejected";

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

        // Origin first: a browser attaches the client-id cookie to any connection to this origin,
        // including one opened by an attacker's page, so an unchecked Origin would hand that page
        // the victim's identity. Nothing else about the handshake matters if this fails.
        String origin = firstHeader(request, "Origin");
        String host = firstHeader(request, "Host");
        if (!OriginPolicy.isAllowed(origin, host)) {
            LOG.warning("[zeroz4j] Refused WebSocket handshake. "
                    + OriginPolicy.explainRefusal(origin, host));
            config.getUserProperties().put(REJECTED_KEY, Boolean.TRUE);
            // Which check refused decides the sentence the browser is closed with, and the two are
            // fixed in different settings: sending someone whose host name is not answered for to
            // read origin configuration wastes their afternoon.
            config.getUserProperties().put(WasmRmiServerEngine.REFUSED_BY_KEY,
                    OriginPolicy.isHostAllowed(host)
                            ? WasmRmiServerEngine.REFUSED_BY_ORIGIN
                            : WasmRmiServerEngine.REFUSED_BY_HOST);
            return;
        }

        resolveClientId(config, request, response);

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

    /**
     * Hands the container the CDI-managed engine rather than a bare {@code new}.
     *
     * <h2>The failure this exists to stop</h2>
     * {@link WasmRmiServerEngine} is an {@code @ApplicationScoped} bean with three injected
     * collaborators, and the container asks the configurator for the endpoint instance. The Jakarta
     * API's default implementation delegates to the container's default configurator, and whether
     * that one knows about CDI is entirely the container's business: WildFly's does, and
     * <b>Tomcat's does not</b> — {@code DefaultServerEndpointConfigurator.getEndpointInstance} is
     * literally {@code clazz.getConstructor().newInstance()}. On Tomcat the engine therefore came up
     * with three null fields and the very first connection died in {@code onOpen} with
     * {@code NullPointerException: ... "this.syncEngine" is null}, followed by an endless reconnect
     * loop from a client that had done nothing wrong.
     *
     * <p>Asking CDI first fixes that and changes nothing anywhere else: where the container already
     * resolves the bean, this returns the same one.
     *
     * <p>Falling back rather than failing matters too. An application may register endpoints that
     * are not beans, and CDI may not be running at all in a plain embedded test — in both cases the
     * container's own behaviour is the right answer.
     */
    @Override
    public <T> T getEndpointInstance(Class<T> endpointClass) throws InstantiationException {
        try {
            jakarta.enterprise.inject.Instance<T> resolvable =
                    jakarta.enterprise.inject.spi.CDI.current().select(endpointClass);
            if (!resolvable.isUnsatisfied() && !resolvable.isAmbiguous()) {
                return resolvable.get();
            }
        } catch (RuntimeException | LinkageError noCdi) {
            LOG.fine("[zeroz4j] No CDI instance for " + endpointClass.getName()
                    + "; letting the container construct it: " + noCdi);
        }
        return super.getEndpointInstance(endpointClass);
    }

    private static String firstParam(java.util.Map<String, java.util.List<String>> params, String name) {
        java.util.List<String> values = params != null ? params.get(name) : null;
        return values != null && !values.isEmpty() ? values.get(0) : null;
    }

    /**
     * Reads a handshake header case-insensitively. HTTP header names are case-insensitive and
     * containers disagree on how they key the map, so an exact lookup silently misses.
     */
    private static String firstHeader(HandshakeRequest request, String name) {
        java.util.Map<String, java.util.List<String>> headers = request.getHeaders();
        if (headers == null) {
            return null;
        }
        for (java.util.Map.Entry<String, java.util.List<String>> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) {
                java.util.List<String> values = entry.getValue();
                return values != null && !values.isEmpty() ? values.get(0) : null;
            }
        }
        return null;
    }

    /**
     * Establishes the browser's client id for this connection.
     *
     * <p>A valid signed cookie is accepted; anything else — absent, tampered with, or expired — mints
     * a fresh id and sets it on the handshake response. Minting here rather than only when the page
     * is served means an application whose assets come from somewhere else still gets a working
     * {@link com.zeroz4j.api.Scope#CLIENT}.</p>
     *
     * <p>A rejected token is deliberately indistinguishable from a first visit: the connection
     * simply becomes a new client, because reporting the difference would tell an attacker whether
     * a guessed id exists.</p>
     */
    private static void resolveClientId(ServerEndpointConfig config,
                                        HandshakeRequest request,
                                        HandshakeResponse response) {
        String presented = ClientIdentity.fromCookieHeader(firstHeader(request, "Cookie"));
        String clientId = ClientIdentity.verify(presented);

        if (clientId == null) {
            String issued = ClientIdentity.issue();
            clientId = ClientIdentity.verify(issued);
            try {
                java.util.Map<String, java.util.List<String>> headers = response.getHeaders();
                if (headers != null) {
                    java.net.URI uri = request.getRequestURI();
                    headers.put("Set-Cookie", java.util.Collections.singletonList(
                            ClientIdentity.cookieHeader(issued,
                                    ClientIdentity.secureFor(uri != null ? uri.getScheme() : null))));
                }
            } catch (RuntimeException ex) {
                // Some containers expose an immutable response header map. The id still works for
                // this connection; it just will not persist past it.
                LOG.fine("[zeroz4j] Could not set the client-id cookie: " + ex.getMessage());
            }
        }
        if (clientId != null) {
            config.getUserProperties().put(CLIENT_KEY, clientId);
        }
    }

}
