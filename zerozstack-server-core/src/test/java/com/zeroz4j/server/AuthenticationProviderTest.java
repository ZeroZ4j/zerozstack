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

import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The authentication SPI and the tenant identity it establishes. Before this existed the only way to
 * authenticate was {@code DevAuth}'s hardcoded demo users, and a session had no tenant at all — which
 * is why {@link com.zeroz4j.api.Scope#TENANT} could not exist.
 */
class AuthenticationProviderTest {

    private static HandshakeCredentials credentials(String... paramPairs) {
        Map<String, List<String>> params = new java.util.LinkedHashMap<>();
        for (int i = 0; i < paramPairs.length; i += 2) {
            params.put(paramPairs[i], List.of(paramPairs[i + 1]));
        }
        return new HandshakeCredentials(params, Collections.emptyMap(), null);
    }

    @Test
    void aProviderReportsNameRolesAndTenant() {
        AuthenticationProvider provider = c -> new AuthenticatedPrincipal(
                c.parameter("user"), Set.of("editor"), "acme");

        AuthenticatedPrincipal result = provider.authenticate(credentials("user", "alice"));

        assertEquals("alice", result.name());
        assertEquals(Set.of("editor"), result.roles());
        assertEquals("acme", result.tenantId());
    }

    @Test
    void decliningLeavesTheConnectionAnonymous() {
        AuthenticationProvider provider = c -> null;
        assertNull(provider.authenticate(credentials("user", "nobody")));
    }

    @Test
    void aPrincipalMustHaveAName() {
        assertThrows(IllegalArgumentException.class,
                () -> new AuthenticatedPrincipal(null, Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new AuthenticatedPrincipal("  ", Set.of()));
    }

    @Test
    void rolesAndTenantDefaultSafely() {
        AuthenticatedPrincipal p = new AuthenticatedPrincipal("alice", null);

        assertTrue(p.roles().isEmpty(), "null roles must mean no roles, not a NullPointerException");
        assertNull(p.tenantId(), "single-tenant applications report no tenant");
    }

    @Test
    void rolesAreNotModifiableThroughTheReturnedSet() {
        AuthenticatedPrincipal p = new AuthenticatedPrincipal("alice", Set.of("user"), "acme");
        assertThrows(UnsupportedOperationException.class, () -> p.roles().add("admin"),
                "roles decide authorization and must not be mutable after authentication");
    }

    @Test
    void credentialsExposeParametersHeadersAndTheContainerPrincipal() {
        Principal container = () -> "from-container";
        HandshakeCredentials c = new HandshakeCredentials(
                Map.of("token", List.of("abc")),
                Map.of("X-Tenant", List.of("acme")),
                container);

        assertEquals("abc", c.parameter("token"));
        assertEquals("acme", c.header("X-Tenant"));
        assertEquals(container, c.containerPrincipal());
        assertNull(c.parameter("absent"));
        assertNull(c.header("absent"));
    }

    @Test
    void credentialsTolerateMissingMaps() {
        HandshakeCredentials c = new HandshakeCredentials(null, null, null);
        assertNull(c.parameter("anything"));
        assertTrue(c.parameters().isEmpty());
        assertTrue(c.headers().isEmpty());
    }

    @Test
    void aProviderMayEnrichTheContainerPrincipal() {
        // A deployment behind container-managed security keeps its identity and adds roles/tenant.
        AuthenticationProvider provider = c -> c.containerPrincipal() == null
                ? null
                : new AuthenticatedPrincipal(c.containerPrincipal().getName(), Set.of("user"), "acme");

        HandshakeCredentials c = new HandshakeCredentials(
                Collections.emptyMap(), Collections.emptyMap(), () -> "alice");

        AuthenticatedPrincipal result = provider.authenticate(c);
        assertEquals("alice", result.name());
        assertEquals("acme", result.tenantId());
    }
}
