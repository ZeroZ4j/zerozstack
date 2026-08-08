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

/**
 * How {@link OidcAuthenticationProvider} reaches the identity provider and reads its claims.
 *
 * <p>Read from system properties by default, so a deployment configures authentication the same way
 * it configures everything else in this framework and no code changes between environments.</p>
 *
 * <table border="1">
 *   <caption>System properties</caption>
 *   <tr><th>Property</th><th>Meaning</th></tr>
 *   <tr><td>{@code zeroz.oidc.issuer}</td>
 *       <td><b>Required.</b> The realm's issuer URL, exactly as it appears in the token's
 *           {@code iss} claim — for Keycloak,
 *           {@code https://host/realms/<realm>}.</td></tr>
 *   <tr><td>{@code zeroz.oidc.jwksUri}</td>
 *       <td>Where the signing keys are published. Defaults to Keycloak's location under the
 *           issuer, {@code <issuer>/protocol/openid-connect/certs}.</td></tr>
 *   <tr><td>{@code zeroz.oidc.clientId}</td>
 *       <td>This application's client id in the provider. Used to read client roles out of
 *           Keycloak's {@code resource_access}, and to check who the token was minted for when no
 *           audience is configured.</td></tr>
 *   <tr><td>{@code zeroz.oidc.audience}</td>
 *       <td>The {@code aud} the token must carry. <b>Unset by default</b>, because a stock Keycloak
 *           access token carries {@code aud="account"} and names the client in {@code azp} instead —
 *           defaulting this to the client id would reject every token a normal realm issues. With it
 *           unset, a token is accepted when {@code aud} contains {@code clientId} <em>or</em>
 *           {@code azp} equals it. Set it explicitly once the realm has an audience mapper.
 *           {@code *} skips the check entirely.</td></tr>
 *   <tr><td>{@code zeroz.oidc.principalClaim}</td>
 *       <td>Which claim becomes the user name; default {@code preferred_username}, falling back to
 *           {@code sub} when absent.</td></tr>
 *   <tr><td>{@code zeroz.oidc.rolesClaim}</td>
 *       <td>A flat claim holding roles, for providers that publish them that way. Unset by default,
 *           in which case Keycloak's {@code realm_access} and {@code resource_access} are read.</td></tr>
 *   <tr><td>{@code zeroz.oidc.tenantClaim}</td>
 *       <td>Which claim carries the tenant. Unset by default, which means single-tenant: the
 *           principal reports no tenant and no {@code Scope.TENANT} push can reach it.</td></tr>
 *   <tr><td>{@code zeroz.oidc.tenantFromRealm}</td>
 *       <td>{@code true} to derive the tenant from the realm name at the end of the issuer URL,
 *           for deployments giving each customer its own realm. Ignored when
 *           {@code tenantClaim} is set.</td></tr>
 *   <tr><td>{@code zeroz.oidc.clockSkewSeconds}</td>
 *       <td>Tolerance for {@code exp} and {@code nbf} against clock drift; default 60.</td></tr>
 * </table>
 */
public final class OidcConfiguration {

    private static final String PREFIX = "zeroz.oidc.";
    /** Keycloak's JWKS path under a realm issuer. */
    private static final String KEYCLOAK_CERTS_PATH = "/protocol/openid-connect/certs";
    /** Accepts a token regardless of who it was minted for. */
    public static final String ANY_AUDIENCE = "*";

    private final String issuer;
    private final String jwksUri;
    private final String clientId;
    private final String audience;
    private final String principalClaim;
    private final String rolesClaim;
    private final String tenantClaim;
    private final boolean tenantFromRealm;
    private final int clockSkewSeconds;

    /**
     * Builds a configuration explicitly, for a test or an application that reads its settings from
     * somewhere other than system properties.
     *
     * @param issuer           the expected {@code iss}; required
     * @param jwksUri          where signing keys live; null derives Keycloak's location
     * @param clientId         this application's client id, or null
     * @param audience         the expected {@code aud}; null falls back to checking {@code azp}
     *                         against {@code clientId}, which is how a stock Keycloak token names
     *                         the client it was issued to
     * @param principalClaim   claim to use as the user name; null means {@code preferred_username}
     * @param rolesClaim       flat roles claim; null reads Keycloak's role structure instead
     * @param tenantClaim      claim carrying the tenant, or null
     * @param tenantFromRealm  derive the tenant from the realm in the issuer URL
     * @param clockSkewSeconds tolerance for expiry checks
     */
    public OidcConfiguration(String issuer, String jwksUri, String clientId, String audience,
                             String principalClaim, String rolesClaim, String tenantClaim,
                             boolean tenantFromRealm, int clockSkewSeconds) {
        if (issuer == null || issuer.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "OIDC authentication needs an issuer. Set " + PREFIX + "issuer to the realm URL "
                    + "that appears in your tokens' iss claim, e.g. "
                    + "https://keycloak.example.com/realms/acme");
        }
        this.issuer = stripTrailingSlash(issuer.trim());
        this.jwksUri = jwksUri != null && !jwksUri.trim().isEmpty()
                ? jwksUri.trim()
                : this.issuer + KEYCLOAK_CERTS_PATH;
        this.clientId = emptyToNull(clientId);
        // Deliberately NOT defaulting to clientId. Keycloak's access tokens carry aud="account" by
        // default and name the client in azp instead, so requiring aud == clientId rejects every
        // token a stock realm issues. When no audience is configured the check falls back to azp;
        // see OidcAuthenticationProvider.
        this.audience = emptyToNull(audience);
        this.principalClaim = emptyToNull(principalClaim) != null
                ? principalClaim.trim() : "preferred_username";
        this.rolesClaim = emptyToNull(rolesClaim);
        this.tenantClaim = emptyToNull(tenantClaim);
        this.tenantFromRealm = tenantFromRealm;
        this.clockSkewSeconds = clockSkewSeconds;
    }

    /**
     * Reads the configuration from system properties.
     *
     * @return the configuration
     * @throws IllegalArgumentException when the issuer is missing
     */
    public static OidcConfiguration fromSystemProperties() {
        return new OidcConfiguration(
                System.getProperty(PREFIX + "issuer"),
                System.getProperty(PREFIX + "jwksUri"),
                System.getProperty(PREFIX + "clientId"),
                System.getProperty(PREFIX + "audience"),
                System.getProperty(PREFIX + "principalClaim"),
                System.getProperty(PREFIX + "rolesClaim"),
                System.getProperty(PREFIX + "tenantClaim"),
                Boolean.parseBoolean(System.getProperty(PREFIX + "tenantFromRealm", "false")),
                Integer.parseInt(System.getProperty(PREFIX + "clockSkewSeconds", "60")));
    }

    /**
     * The realm a token must have come from, matched against its {@code iss} claim exactly.
     *
     * @return the expected issuer
     */
    public String issuer() {
        return issuer;
    }

    /**
     * Where the provider publishes the public keys its tokens are signed with. Fetched once and
     * cached, so a handshake never becomes a round trip to the identity provider.
     *
     * @return the JWKS endpoint
     */
    public String jwksUri() {
        return jwksUri;
    }

    /**
     * This application's registration in the provider. Used to pick its roles out of Keycloak's
     * {@code resource_access}, and to recognise tokens minted for it.
     *
     * @return the client id, or null when none is configured
     */
    public String clientId() {
        return clientId;
    }

    /**
     * The audience a token must carry, when one is configured at all. Usually it is not: see the
     * class documentation for why defaulting this to the client id rejects every stock Keycloak
     * token.
     *
     * @return the configured audience, {@link #ANY_AUDIENCE} to skip the check, or null to fall back
     *         to comparing {@code azp} with {@link #clientId()}
     */
    public String audience() {
        return audience;
    }

    /**
     * Which claim becomes the user name that {@code RmiRequestContext.getPrincipal()} reports.
     *
     * @return the principal claim; {@code sub} is used when it is absent from a token
     */
    public String principalClaim() {
        return principalClaim;
    }

    /**
     * A single claim holding a flat list of roles, for providers that publish them that way.
     *
     * @return the roles claim, or null to read Keycloak's nested {@code realm_access} and
     *         {@code resource_access} instead
     */
    public String rolesClaim() {
        return rolesClaim;
    }

    /**
     * Which claim carries the tenant, for a deployment putting every customer in one realm.
     *
     * @return the tenant claim, or null when the application is single-tenant
     */
    public String tenantClaim() {
        return tenantClaim;
    }

    /**
     * Whether to treat the realm itself as the tenant, for a deployment giving each customer its own
     * realm. Ignored when {@link #tenantClaim()} is set.
     *
     * @return true when the tenant is the realm name from the issuer URL
     */
    public boolean tenantFromRealm() {
        return tenantFromRealm;
    }

    /**
     * How much clock drift to tolerate when checking {@code exp} and {@code nbf}, so a server a few
     * seconds out of step with the provider does not reject valid tokens.
     *
     * @return the tolerance in seconds
     */
    public int clockSkewSeconds() {
        return clockSkewSeconds;
    }

    /**
     * The realm name at the end of a Keycloak issuer URL, for the realm-per-tenant deployment.
     *
     * @return the realm name, or null when the issuer does not look like a Keycloak realm URL
     */
    public String realmName() {
        int marker = issuer.lastIndexOf("/realms/");
        if (marker < 0) {
            return null;
        }
        String realm = issuer.substring(marker + "/realms/".length());
        return realm.isEmpty() ? null : realm;
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
