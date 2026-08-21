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
package com.zeroz4j.server.oidc;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.zeroz4j.server.AuthenticatedPrincipal;
import com.zeroz4j.server.AuthenticationProvider;
import com.zeroz4j.server.HandshakeCredentials;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Authenticates a WebSocket handshake against an OpenID Connect provider such as Keycloak.
 *
 * <p>The browser performs the login itself — an authorization-code flow with PKCE, which the client
 * runtime implements — and arrives holding an access token. This provider's job is the other half:
 * verify that token really came from the configured issuer, has not expired, and was minted for this
 * application, then turn its claims into the identity everything downstream reads.</p>
 *
 * <p>Register it the way any provider is registered, in
 * {@code META-INF/services/com.zeroz4j.server.AuthenticationProvider}:</p>
 * <pre>
 * com.zeroz4j.server.oidc.OidcAuthenticationProvider
 * </pre>
 * <p>Registering any provider disables the {@code demo}/{@code admin} development fallback entirely.
 * See {@link OidcConfiguration} for the system properties this reads.</p>
 *
 * <h2>How claims become an identity</h2>
 * <ul>
 *   <li><b>Name</b> — {@code preferred_username} by default, falling back to {@code sub}. The
 *       fallback matters: a provider configured without a username scope still yields a stable
 *       identity rather than a failed login.</li>
 *   <li><b>Roles</b> — Keycloak publishes them in {@code realm_access.roles} and
 *       {@code resource_access.<clientId>.roles} rather than a flat claim, and both are read and
 *       merged. Providers that do use a flat claim are handled by setting
 *       {@code zeroz.oidc.rolesClaim}.</li>
 *   <li><b>Tenant</b> — from a configured claim, or from the realm name in the issuer URL when each
 *       customer has its own realm. Neither configured means single-tenant, and the principal
 *       reports no tenant, so no tenant-scoped push can reach it.</li>
 * </ul>
 *
 * <h2>Verification is not optional</h2>
 * <p>A token is a bearer credential: whoever holds it is treated as its subject. Every check below
 * therefore fails closed — a token that cannot be verified is refused rather than partially trusted.
 * Signature, issuer, audience and expiry are all checked, against keys fetched from the provider's
 * published JWKS and cached between handshakes.</p>
 */
public final class OidcAuthenticationProvider implements AuthenticationProvider {

    private static final Logger LOG = Logger.getLogger(OidcAuthenticationProvider.class.getName());

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String KEYCLOAK_REALM_ACCESS = "realm_access";
    private static final String KEYCLOAK_RESOURCE_ACCESS = "resource_access";
    private static final String KEYCLOAK_ROLES = "roles";

    private final OidcConfiguration config;
    private final DefaultJWTProcessor<SecurityContext> processor;

    /**
     * Builds a provider from system properties. This is the constructor {@link java.util.ServiceLoader}
     * uses, so an application registers the class and configures it entirely from the outside.
     */
    public OidcAuthenticationProvider() {
        this(OidcConfiguration.fromSystemProperties());
    }

    /**
     * Builds a provider that fetches signing keys from the configured JWKS endpoint.
     *
     * @param config the configuration
     */
    public OidcAuthenticationProvider(OidcConfiguration config) {
        this(config, defaultKeySource(config));
    }

    /**
     * Builds a provider against a supplied key source.
     *
     * @param config    the configuration
     * @param keySource where signing keys come from; an in-memory set in tests, the provider's
     *                  published JWKS in a deployment
     */
    public OidcAuthenticationProvider(OidcConfiguration config, JWKSource<SecurityContext> keySource) {
        this.config = config;
        this.processor = new DefaultJWTProcessor<>();
        this.processor.setJWSKeySelector(
                new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource));

        Set<String> requiredClaims = new LinkedHashSet<>(Arrays.asList("sub", "exp"));
        String expectedAudience = OidcConfiguration.ANY_AUDIENCE.equals(config.audience())
                ? null : config.audience();
        DefaultJWTClaimsVerifier<SecurityContext> verifier = new DefaultJWTClaimsVerifier<>(
                expectedAudience,
                new JWTClaimsSet.Builder().issuer(config.issuer()).build(),
                requiredClaims);
        verifier.setMaxClockSkew(config.clockSkewSeconds());
        this.processor.setJWTClaimsSetVerifier(verifier);

        LOG.info("[zeroz4j] OIDC authentication against " + config.issuer()
                + " (keys: " + config.jwksUri() + ")");
    }

    private static JWKSource<SecurityContext> defaultKeySource(OidcConfiguration config) {
        try {
            // Cached and rate-limited: a handshake must not become a round trip to the identity
            // provider, and a burst of reconnects must not become a burst of JWKS fetches.
            return JWKSourceBuilder.create(URI.create(config.jwksUri()).toURL())
                    .retrying(true)
                    .build();
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Cannot read OIDC signing keys from " + config.jwksUri() + ": " + ex.getMessage(), ex);
        }
    }

    @Override
    public AuthenticatedPrincipal authenticate(HandshakeCredentials credentials) {
        String token = tokenFrom(credentials);
        if (token == null) {
            return null;   // no credentials offered: the connection stays anonymous
        }

        JWTClaimsSet claims;
        try {
            claims = processor.process(token, null);
        } catch (Exception ex) {
            // Refused, not accepted-with-less-trust. The message deliberately does not include the
            // token, which would put a live credential in the log file.
            throw new IllegalStateException("Rejected OIDC token: " + ex.getMessage(), ex);
        }

        requireMintedForThisApplication(claims);

        String name = principalNameFrom(claims);
        if (name == null) {
            throw new IllegalStateException("Rejected OIDC token: no '" + config.principalClaim()
                    + "' or 'sub' claim to identify the user by.");
        }
        return new AuthenticatedPrincipal(name, rolesFrom(claims), tenantFrom(claims));
    }

    /**
     * Reads the access token from the handshake.
     *
     * <p>A browser cannot set arbitrary headers on a WebSocket upgrade, so the token normally
     * arrives as a query parameter. The {@code Authorization} header is accepted too, for native and
     * server-to-server clients that can send one.</p>
     */
    private String tokenFrom(HandshakeCredentials credentials) {
        String parameter = credentials.parameter("token");
        if (parameter != null && !parameter.isEmpty()) {
            return parameter;
        }
        String authorization = credentials.header("Authorization");
        if (authorization != null && authorization.regionMatches(true, 0, BEARER_PREFIX, 0,
                BEARER_PREFIX.length())) {
            String token = authorization.substring(BEARER_PREFIX.length()).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }

    /**
     * Refuses a token that was not minted for this application.
     *
     * <p>Only runs when no explicit audience is configured — nimbus has already enforced that one if
     * there is. A stock Keycloak access token carries {@code aud="account"} and names the client it
     * was issued to in {@code azp}, so checking {@code aud} alone against the client id rejects every
     * normal token, while checking nothing at all would accept a token minted for a different
     * application on the same realm. Either claim naming this client is accepted.</p>
     */
    private void requireMintedForThisApplication(JWTClaimsSet claims) {
        if (config.audience() != null || config.clientId() == null) {
            return;   // explicitly configured (already verified), or nothing to check against
        }
        String clientId = config.clientId();
        List<String> audiences = claims.getAudience();
        if (audiences != null && audiences.contains(clientId)) {
            return;
        }
        Object authorizedParty = claims.getClaim("azp");
        if (clientId.equals(authorizedParty)) {
            return;
        }
        throw new IllegalStateException("Rejected OIDC token: it was minted for " + audiences
                + " (azp=" + authorizedParty + "), not for this application (" + clientId + ").");
    }

    private String principalNameFrom(JWTClaimsSet claims) {
        Object configured = claims.getClaim(config.principalClaim());
        if (configured instanceof String && !((String) configured).isEmpty()) {
            return (String) configured;
        }
        // Falling back to sub rather than failing: a realm without the profile scope still gives a
        // stable, unique identity, and refusing the login would be a confusing way to report a
        // missing optional claim.
        return claims.getSubject();
    }

    /**
     * Collects roles, handling Keycloak's nested structure as well as a flat claim.
     */
    @SuppressWarnings("unchecked")
    private Set<String> rolesFrom(JWTClaimsSet claims) {
        Set<String> roles = new LinkedHashSet<>();

        if (config.rolesClaim() != null) {
            addRoles(roles, claims.getClaim(config.rolesClaim()));
            return roles;
        }

        Object realmAccess = claims.getClaim(KEYCLOAK_REALM_ACCESS);
        if (realmAccess instanceof Map) {
            addRoles(roles, ((Map<String, Object>) realmAccess).get(KEYCLOAK_ROLES));
        }

        Object resourceAccess = claims.getClaim(KEYCLOAK_RESOURCE_ACCESS);
        if (resourceAccess instanceof Map && config.clientId() != null) {
            Object forThisClient = ((Map<String, Object>) resourceAccess).get(config.clientId());
            if (forThisClient instanceof Map) {
                addRoles(roles, ((Map<String, Object>) forThisClient).get(KEYCLOAK_ROLES));
            }
        }
        return roles;
    }

    /** Accepts either a list of strings or a single space-delimited string, as providers differ. */
    private static void addRoles(Set<String> target, Object claimValue) {
        if (claimValue instanceof List) {
            for (Object role : (List<?>) claimValue) {
                if (role != null) {
                    target.add(String.valueOf(role));
                }
            }
        } else if (claimValue instanceof String) {
            for (String role : ((String) claimValue).trim().split("\\s+")) {
                if (!role.isEmpty()) {
                    target.add(role);
                }
            }
        }
    }

    private String tenantFrom(JWTClaimsSet claims) {
        if (config.tenantClaim() != null) {
            Object value = claims.getClaim(config.tenantClaim());
            return value != null ? String.valueOf(value) : null;
        }
        if (config.tenantFromRealm()) {
            return config.realmName();
        }
        return null;   // single-tenant: no Scope.TENANT push can reach this session
    }

    /**
     * The settings this provider resolved at startup — useful for logging what a deployment actually
     * picked up when a login is failing for reasons the token itself does not explain.
     *
     * @return the configuration in use
     */
    public OidcConfiguration configuration() {
        return config;
    }
}
