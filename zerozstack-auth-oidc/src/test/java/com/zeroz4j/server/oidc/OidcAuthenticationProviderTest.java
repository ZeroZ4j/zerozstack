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

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.zeroz4j.server.AuthenticatedPrincipal;
import com.zeroz4j.server.HandshakeCredentials;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A token is a bearer credential: whoever holds it is treated as its subject. These tests mint
 * tokens locally — no network, no live identity provider — and pin that every way of presenting a
 * bad one is refused rather than partially trusted.
 */
class OidcAuthenticationProviderTest {

    private static final String ISSUER = "https://keycloak.example.com/realms/acme";
    private static final String CLIENT_ID = "zeroz-app";

    private static RSAKey signingKey;
    private static JWKSource<SecurityContext> keySource;

    @BeforeAll
    static void generateSigningKey() throws Exception {
        signingKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        keySource = new ImmutableJWKSet<>(new JWKSet(signingKey.toPublicJWK()));
    }

    private static OidcConfiguration config() {
        return new OidcConfiguration(ISSUER, null, CLIENT_ID, null, null, null, null, false, 60);
    }

    private static OidcAuthenticationProvider provider() {
        return new OidcAuthenticationProvider(config(), keySource);
    }

    private static OidcAuthenticationProvider provider(OidcConfiguration config) {
        return new OidcAuthenticationProvider(config, keySource);
    }

    /** Mints a token the way Keycloak would, so tests exercise the real claim shapes. */
    private static String token(JWTClaimsSet.Builder claims) throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(signingKey.getKeyID())
                        .type(JOSEObjectType.JWT)
                        .build(),
                claims.build());
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }

    private static JWTClaimsSet.Builder validClaims() {
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject("6f1a-user-uuid")
                .audience(CLIENT_ID)
                .claim("preferred_username", "alice")
                .expirationTime(new Date(System.currentTimeMillis() + 300_000));
    }

    private static HandshakeCredentials withToken(String token) {
        return new HandshakeCredentials(
                Collections.singletonMap("token", List.of(token)), Collections.emptyMap(), null);
    }

    // ---------------------------------------------------------------- accepted

    @Test
    void aValidTokenAuthenticates() throws Exception {
        AuthenticatedPrincipal principal = provider().authenticate(withToken(token(validClaims())));

        assertEquals("alice", principal.name());
        assertNull(principal.tenantId(), "no tenant claim configured means single-tenant");
    }

    @Test
    void keycloakRealmAndClientRolesAreBothRead() throws Exception {
        String token = token(validClaims()
                .claim("realm_access", Map.of("roles", List.of("user", "offline_access")))
                .claim("resource_access", Map.of(CLIENT_ID, Map.of("roles", List.of("editor")))));

        Set<String> roles = provider().authenticate(withToken(token)).roles();

        assertTrue(roles.containsAll(Set.of("user", "offline_access", "editor")),
                "Keycloak splits roles across two claims; missing either silently under-authorizes: "
                        + roles);
    }

    @Test
    void anotherClientsRolesAreNotGranted() throws Exception {
        String token = token(validClaims().claim("resource_access",
                Map.of("some-other-app", Map.of("roles", List.of("admin")))));

        assertTrue(provider().authenticate(withToken(token)).roles().isEmpty(),
                "roles granted for a different client must not apply here");
    }

    @Test
    void aFlatRolesClaimIsSupportedForOtherProviders() throws Exception {
        OidcConfiguration flat = new OidcConfiguration(
                ISSUER, null, CLIENT_ID, null, null, "roles", null, false, 60);
        String token = token(validClaims().claim("roles", List.of("user", "editor")));

        assertEquals(Set.of("user", "editor"), provider(flat).authenticate(withToken(token)).roles());
    }

    @Test
    void aSpaceDelimitedRolesClaimIsSupported() throws Exception {
        OidcConfiguration flat = new OidcConfiguration(
                ISSUER, null, CLIENT_ID, null, null, "scope", null, false, 60);
        String token = token(validClaims().claim("scope", "user editor"));

        assertEquals(Set.of("user", "editor"), provider(flat).authenticate(withToken(token)).roles());
    }

    @Test
    void theSubjectIsUsedWhenTheUsernameClaimIsAbsent() throws Exception {
        String token = token(new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject("6f1a-user-uuid")
                .audience(CLIENT_ID)
                .expirationTime(new Date(System.currentTimeMillis() + 300_000)));

        assertEquals("6f1a-user-uuid", provider().authenticate(withToken(token)).name(),
                "a realm without the profile scope should still log in, not fail");
    }

    @Test
    void aTenantClaimBecomesTheSessionTenant() throws Exception {
        OidcConfiguration multiTenant = new OidcConfiguration(
                ISSUER, null, CLIENT_ID, null, null, null, "tenant", false, 60);
        String token = token(validClaims().claim("tenant", "acme"));

        assertEquals("acme", provider(multiTenant).authenticate(withToken(token)).tenantId());
    }

    @Test
    void theRealmCanBeTheTenantForRealmPerCustomerDeployments() throws Exception {
        OidcConfiguration realmPerTenant = new OidcConfiguration(
                ISSUER, null, CLIENT_ID, null, null, null, null, true, 60);

        assertEquals("acme",
                provider(realmPerTenant).authenticate(withToken(token(validClaims()))).tenantId());
    }

    @Test
    void aBearerHeaderIsAcceptedForNonBrowserClients() throws Exception {
        HandshakeCredentials credentials = new HandshakeCredentials(
                Collections.emptyMap(),
                Collections.singletonMap("Authorization", List.of("Bearer " + token(validClaims()))),
                null);

        assertEquals("alice", provider().authenticate(credentials).name());
    }

    // ---------------------------------------------------------------- refused

    @Test
    void noTokenLeavesTheConnectionAnonymous() {
        HandshakeCredentials empty =
                new HandshakeCredentials(Collections.emptyMap(), Collections.emptyMap(), null);

        assertNull(provider().authenticate(empty),
                "an open application must still be able to connect without logging in");
    }

    @Test
    void anExpiredTokenIsRefused() throws Exception {
        String token = token(validClaims()
                .expirationTime(new Date(System.currentTimeMillis() - 600_000)));

        assertThrows(IllegalStateException.class, () -> provider().authenticate(withToken(token)));
    }

    @Test
    void aTokenFromAnotherIssuerIsRefused() throws Exception {
        String token = token(validClaims().issuer("https://evil.example.net/realms/acme"));

        assertThrows(IllegalStateException.class, () -> provider().authenticate(withToken(token)));
    }

    @Test
    void aTokenMintedForAnotherApplicationIsRefused() throws Exception {
        String token = token(validClaims().audience("some-other-app"));

        assertThrows(IllegalStateException.class, () -> provider().authenticate(withToken(token)),
                "accepting any audience lets a token issued for another app in through this door");
    }

    @Test
    void aTokenSignedByAnotherKeyIsRefused() throws Exception {
        RSAKey attackerKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(),
                validClaims().build());
        jwt.sign(new RSASSASigner(attackerKey));

        assertThrows(IllegalStateException.class,
                () -> provider().authenticate(withToken(jwt.serialize())),
                "matching the key id must not be enough; the signature has to verify");
    }

    @Test
    void aTamperedPayloadIsRefused() throws Exception {
        String[] parts = token(validClaims()).split("\\.");
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                validClaims().claim("preferred_username", "admin").build()
                        .toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));

        String tampered = parts[0] + "." + forgedPayload + "." + parts[2];

        assertThrows(IllegalStateException.class,
                () -> provider().authenticate(withToken(tampered)));
    }

    @Test
    void aGarbageTokenIsRefused() {
        assertThrows(IllegalStateException.class,
                () -> provider().authenticate(withToken("not-a-jwt")));
    }

    @Test
    void anUnsignedTokenIsRefused() throws Exception {
        // The classic "alg: none" attack: a token with a valid-looking payload and no signature.
        com.nimbusds.jwt.PlainJWT plain = new com.nimbusds.jwt.PlainJWT(validClaims().build());

        assertThrows(IllegalStateException.class,
                () -> provider().authenticate(withToken(plain.serialize())));
    }

    @Test
    void theRejectionMessageDoesNotContainTheToken() throws Exception {
        String token = token(validClaims().issuer("https://evil.example.net/realms/acme"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> provider().authenticate(withToken(token)));

        assertTrue(!error.getMessage().contains(token),
                "the message is logged, and a logged token is a live credential in a log file");
    }

    // ---------------------------------------------------------------- configuration

    @Test
    void aMissingIssuerIsReportedClearly() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new OidcConfiguration(null, null, null, null, null, null, null, false, 60));

        assertTrue(error.getMessage().contains("zeroz.oidc.issuer"),
                "the message should name the property to set: " + error.getMessage());
    }

    @Test
    void theJwksLocationDefaultsToKeycloaks() {
        OidcConfiguration defaults = config();

        assertEquals(ISSUER + "/protocol/openid-connect/certs", defaults.jwksUri());
        assertNull(defaults.audience(),
                "defaulting the audience to the client id would reject every stock Keycloak token");
        assertEquals("acme", defaults.realmName());
    }

    // ------------------------------------------------- what Keycloak actually issues

    /**
     * A stock Keycloak access token carries {@code aud="account"} and names the client in
     * {@code azp}. Requiring {@code aud == clientId} rejected every one of them — a defect the
     * hand-written tests missed because they minted the audience the implementation expected.
     */
    @Test
    void aStockKeycloakTokenIsAccepted() throws Exception {
        String token = token(new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject("6f1a-user-uuid")
                .audience("account")
                .claim("azp", CLIENT_ID)
                .claim("preferred_username", "ada")
                .expirationTime(new Date(System.currentTimeMillis() + 300_000)));

        assertEquals("ada", provider().authenticate(withToken(token)).name());
    }

    @Test
    void aTokenIssuedToAnotherClientOnTheSameRealmIsRefused() throws Exception {
        String token = token(new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject("6f1a-user-uuid")
                .audience("account")
                .claim("azp", "some-other-app")
                .expirationTime(new Date(System.currentTimeMillis() + 300_000)));

        assertThrows(IllegalStateException.class, () -> provider().authenticate(withToken(token)),
                "same realm, same signing key, different application — this must not get in");
    }

    @Test
    void anExplicitAudienceStillTakesPrecedence() throws Exception {
        OidcConfiguration withAudience = new OidcConfiguration(
                ISSUER, null, CLIENT_ID, "my-api", null, null, null, false, 60);
        String correct = token(validClaims().audience("my-api"));
        String wrong = token(validClaims().audience("account").claim("azp", CLIENT_ID));

        assertEquals("alice", provider(withAudience).authenticate(withToken(correct)).name());
        assertThrows(IllegalStateException.class,
                () -> provider(withAudience).authenticate(withToken(wrong)),
                "once an audience is configured it is the check, not a fallback");
    }

    @Test
    void anyAudienceCanBeAcceptedDeliberately() throws Exception {
        OidcConfiguration anyAudience = new OidcConfiguration(
                ISSUER, null, CLIENT_ID, OidcConfiguration.ANY_AUDIENCE, null, null, null, false, 60);
        String token = token(validClaims().audience("some-other-app"));

        assertEquals("alice", provider(anyAudience).authenticate(withToken(token)).name());
    }
}
