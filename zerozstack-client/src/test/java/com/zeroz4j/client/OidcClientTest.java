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
package com.zeroz4j.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The login flow itself only runs in a browser, but the parts that build URLs are plain Java — and
 * they are where a mistake turns into a redirect that silently does not match what the provider has
 * registered, or a token pasted into the wrong query parameter.
 */
class OidcClientTest {

    @Test
    void aRedirectUriSurvivesEncodingIntact() {
        assertEquals("https%3A%2F%2Fapp.example.com%2Fdashboard",
                OidcClient.encode("https://app.example.com/dashboard"));
    }

    @Test
    void aSpaceBecomesPercentTwentyNotPlus() {
        // Scopes are space-delimited. Form encoding would write '+', which a provider reads
        // literally inside a redirect URI and then refuses to match.
        assertEquals("openid%20profile", OidcClient.encode("openid profile"));
    }

    @Test
    void unreservedCharactersAreLeftAlone() {
        assertEquals("abcXYZ019-_.~", OidcClient.encode("abcXYZ019-_.~"));
    }

    @Test
    void nonAsciiTravelsAsUtf8Bytes() {
        assertEquals("Sch%C3%B6ning", OidcClient.encode("Schöning"));
    }

    @Test
    void nullEncodesToEmptyRatherThanTheStringNull() {
        assertEquals("", OidcClient.encode(null));
    }

    @Test
    void aConfigWithoutAnIssuerOrClientIdIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new OidcClient.Config(null, "app"));
        assertThrows(IllegalArgumentException.class, () -> new OidcClient.Config("", "app"));
        assertThrows(IllegalArgumentException.class,
                () -> new OidcClient.Config("https://keycloak.example.com/realms/acme", null));
    }

    @Test
    void keycloakEndpointsAreDerivedFromTheIssuer() {
        OidcClient.Config config =
                new OidcClient.Config("https://keycloak.example.com/realms/acme/", "zeroz-app");

        assertEquals("https://keycloak.example.com/realms/acme/protocol/openid-connect/auth",
                config.authorizeEndpoint());
        assertEquals("https://keycloak.example.com/realms/acme/protocol/openid-connect/token",
                config.tokenEndpoint());
        assertEquals("https://keycloak.example.com/realms/acme/protocol/openid-connect/logout",
                config.logoutEndpoint());
    }

    @Test
    void endpointsCanBeOverriddenForOtherProviders() {
        OidcClient.Config config =
                new OidcClient.Config("https://accounts.example.com", "zeroz-app")
                        .endpoints("https://accounts.example.com/authorize",
                                "https://accounts.example.com/oauth/token",
                                "https://accounts.example.com/v2/logout");

        assertEquals("https://accounts.example.com/authorize", config.authorizeEndpoint());
        assertEquals("https://accounts.example.com/oauth/token", config.tokenEndpoint());
    }
}
