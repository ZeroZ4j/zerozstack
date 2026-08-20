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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The browser attaches the client-id cookie to any connection to this origin, including one opened
 * by a page the user is merely visiting. These tests pin the check that stops that page being handed
 * the victim's identity.
 */
class OriginPolicyTest {

    @BeforeEach
    @AfterEach
    void clearConfiguration() {
        System.clearProperty("zeroz.origins");
        System.clearProperty("zeroz.hosts");
    }

    @Test
    void aPageFromThisServerIsAllowed() {
        assertTrue(OriginPolicy.isAllowed("https://app.example.com", "app.example.com"));
        assertTrue(OriginPolicy.isAllowed("http://localhost:8080", "localhost:8080"));
    }

    @Test
    void anAttackersPageIsRefused() {
        assertFalse(OriginPolicy.isAllowed("https://evil.example.net", "app.example.com"),
                "this is the whole point: the cookie would otherwise be attached for them");
    }

    @Test
    void aLookalikeHostIsRefused() {
        assertFalse(OriginPolicy.isAllowed("https://app.example.com.evil.net", "app.example.com"));
        assertFalse(OriginPolicy.isAllowed("https://app.example.com:9999", "app.example.com"),
                "a different port is a different origin");
    }

    @Test
    void aConfiguredAllowlistReplacesTheSameOriginRule() {
        System.setProperty("zeroz.origins", "https://app.example.com, https://admin.example.com");

        assertTrue(OriginPolicy.isAllowed("https://app.example.com", "sockets.example.com"));
        assertTrue(OriginPolicy.isAllowed("https://admin.example.com", "sockets.example.com"));
        assertFalse(OriginPolicy.isAllowed("https://other.example.com", "sockets.example.com"));
    }

    @Test
    void theCheckCanBeDisabledDeliberately() {
        System.setProperty("zeroz.origins", "*");

        assertTrue(OriginPolicy.isAllowed("https://anything.example.net", "app.example.com"));
    }

    @Test
    void aRequestWithNoOriginIsAllowed() {
        // Browsers always send Origin on a WebSocket handshake, so its absence means a native or
        // test client -- which has no ambient cookies to abuse, and refusing it would break them
        // for nothing.
        assertTrue(OriginPolicy.isAllowed(null, "app.example.com"));
        assertTrue(OriginPolicy.isAllowed("", "app.example.com"));
    }

    @Test
    void aMissingHostIsRefusedRatherThanAssumedSafe() {
        assertFalse(OriginPolicy.isAllowed("https://app.example.com", null));
    }

    @Test
    void withNoHostAllowlistNothingChanges() {
        // The allowlist is opt-in on purpose: setting it wrong locks a deployment out, so an
        // upgrade must not start enforcing one nobody wrote.
        assertTrue(OriginPolicy.isAllowed("http://evil.example.net:8080", "evil.example.net:8080"),
                "same-origin still passes when both headers agree, rebound or not");
    }

    @Test
    void aHostAllowlistRefusesARebouncedName() {
        // DNS rebinding: the attacker's page points evil.example.net at this server, so Origin and
        // Host agree and the same-origin rule lets it through. Naming the hosts we answer for is
        // what stops it.
        System.setProperty("zeroz.hosts", "app.example.com");

        assertFalse(OriginPolicy.isAllowed("http://evil.example.net:8080", "evil.example.net:8080"));
        assertTrue(OriginPolicy.isAllowed("https://app.example.com", "app.example.com"));
    }

    @Test
    void aHostEntryWithNoPortAcceptsAnyPort() {
        System.setProperty("zeroz.hosts", "app.example.com");

        assertTrue(OriginPolicy.isAllowed("http://app.example.com:8080", "app.example.com:8080"));
        assertTrue(OriginPolicy.isAllowed(null, "APP.EXAMPLE.COM:9443"));
    }

    @Test
    void aHostEntryWithAPortPinsThatPort() {
        System.setProperty("zeroz.hosts", "app.example.com:8443");

        assertTrue(OriginPolicy.isAllowed(null, "app.example.com:8443"));
        assertFalse(OriginPolicy.isAllowed(null, "app.example.com:8080"));
        assertFalse(OriginPolicy.isAllowed(null, "app.example.com"));
    }

    @Test
    void theHostAllowlistAppliesToClientsThatSendNoOrigin() {
        System.setProperty("zeroz.hosts", "app.example.com");

        assertFalse(OriginPolicy.isAllowed(null, "evil.example.net"),
                "the question is which name the request was addressed to, not which page sent it");
        assertFalse(OriginPolicy.isAllowed(null, null));
    }

    @Test
    void theHostAllowlistIsNotDisabledByAllowingEveryOrigin() {
        System.setProperty("zeroz.origins", "*");
        System.setProperty("zeroz.hosts", "app.example.com");

        assertTrue(OriginPolicy.isAllowed("https://anything.example.net", "app.example.com"));
        assertFalse(OriginPolicy.isAllowed("https://anything.example.net", "evil.example.net"));
    }

    @Test
    void theHostCheckCanBeTurnedOffDeliberately() {
        System.setProperty("zeroz.hosts", "*");

        assertTrue(OriginPolicy.isAllowed(null, "evil.example.net"));
        assertTrue(OriginPolicy.isHostAllowed("anything.example.net"));
    }

    @Test
    void aRefusedHostIsExplainedByName() {
        System.setProperty("zeroz.hosts", "app.example.com");

        String explanation = OriginPolicy.explainRefusal("https://evil.example.net", "evil.example.net");
        assertTrue(explanation.contains("zeroz.hosts"), explanation);
        assertTrue(explanation.contains("app.example.com"), explanation);
    }
}
