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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A client id is the only thing standing between one anonymous browser's state and another's, so
 * these tests pin the properties that make it worth anything: it cannot be forged, it cannot be
 * altered, and a rejected one is indistinguishable from never having had one.
 */
class ClientIdentityTest {

    @BeforeEach
    @AfterEach
    void resetSigningKey() {
        System.clearProperty("zeroz.clientId.secret");
        System.clearProperty("zeroz.clientId.ttlDays");
        System.clearProperty("zeroz.clientId.secureCookie");
        ClientIdentity.resetForTesting();
    }

    @Test
    void anIssuedTokenVerifies() {
        String token = ClientIdentity.issue();

        String id = ClientIdentity.verify(token);

        assertTrue(id != null && !id.isEmpty(), "a freshly issued token must verify");
    }

    @Test
    void everyIssuedIdIsDistinct() {
        assertNotEquals(ClientIdentity.verify(ClientIdentity.issue()),
                ClientIdentity.verify(ClientIdentity.issue()),
                "two browsers must never be handed the same id");
    }

    @Test
    void aTamperedIdIsRefused() {
        System.setProperty("zeroz.clientId.secret", "test-secret");
        ClientIdentity.resetForTesting();
        String token = ClientIdentity.issue();
        String realId = ClientIdentity.verify(token);
        String signature = token.substring(token.lastIndexOf('.') + 1);
        String issuedAt = token.substring(realId.length() + 1, token.lastIndexOf('.'));

        // Someone swaps in the id they want while keeping a signature that was genuinely issued.
        String forged = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" + "." + issuedAt + "." + signature;

        assertNull(ClientIdentity.verify(forged),
                "editing the id must invalidate the signature, or anyone can become anyone");
    }

    @Test
    void aFabricatedTokenIsRefused() {
        assertNull(ClientIdentity.verify("made-up-id.1700000000000.made-up-signature"));
        assertNull(ClientIdentity.verify("no-dots-at-all"));
        assertNull(ClientIdentity.verify(""));
        assertNull(ClientIdentity.verify(null));
    }

    @Test
    void aTokenSignedWithAnotherKeyIsRefused() {
        System.setProperty("zeroz.clientId.secret", "key-one");
        ClientIdentity.resetForTesting();
        String issuedElsewhere = ClientIdentity.issue();

        System.setProperty("zeroz.clientId.secret", "key-two");
        ClientIdentity.resetForTesting();

        assertNull(ClientIdentity.verify(issuedElsewhere),
                "a node must not accept ids it did not issue when keys differ");
    }

    @Test
    void anExpiredTokenIsRefused() {
        System.setProperty("zeroz.clientId.secret", "test-secret");
        System.setProperty("zeroz.clientId.ttlDays", "0");
        ClientIdentity.resetForTesting();

        // With a zero-day lifetime the token is already at its allowance the moment it exists, so
        // it is refused however fast the check follows the issue. This used to read "older than",
        // which was true only while the first HMAC in the JVM was still slow enough to push the
        // clock past a millisecond: the test passed when it ran early in a run and failed when it
        // ran late.
        assertNull(ClientIdentity.verify(ClientIdentity.issue()));

        // Repeated on a JVM that is now warm, where issuing and checking land in the same
        // millisecond. This is the run that used to accept the token.
        for (int attempt = 0; attempt < 50; attempt++) {
            assertNull(ClientIdentity.verify(ClientIdentity.issue()),
                    "refusal must not depend on how long the machine took between issuing the id "
                            + "and checking it");
        }
    }

    @Test
    void theCookieCannotBeReadByScript() {
        String cookie = ClientIdentity.cookieHeader(ClientIdentity.issue(), true);

        assertTrue(cookie.contains("HttpOnly"),
                "without HttpOnly a single XSS bug steals every client id");
        assertTrue(cookie.contains("SameSite=Strict"));
        assertTrue(cookie.contains("Secure"));
        assertTrue(cookie.startsWith(ClientIdentity.COOKIE_NAME + "="));
    }

    @Test
    void thePlainHttpCookieOmitsSecureSoLocalDevelopmentWorks() {
        // A Secure cookie on http:// is dropped by the browser, which would look like the feature
        // silently not working rather than a configuration problem.
        assertFalse(ClientIdentity.cookieHeader(ClientIdentity.issue(), false).contains("Secure"));
        assertFalse(ClientIdentity.secureFor("http"));
        assertTrue(ClientIdentity.secureFor("https"));
        assertTrue(ClientIdentity.secureFor("wss"));
    }

    @Test
    void theSecureFlagCanBeForcedBehindATerminatingProxy() {
        System.setProperty("zeroz.clientId.secureCookie", "true");

        assertTrue(ClientIdentity.secureFor("http"),
                "an app behind TLS termination only ever sees plain http");
    }

    @Test
    void theCookieIsFoundAmongOthers() {
        String token = ClientIdentity.issue();

        assertEquals(token, ClientIdentity.fromCookieHeader(
                "theme=dark; " + ClientIdentity.COOKIE_NAME + "=" + token + "; lang=en"));
        assertNull(ClientIdentity.fromCookieHeader("theme=dark; lang=en"));
        assertNull(ClientIdentity.fromCookieHeader(null));
    }
}
