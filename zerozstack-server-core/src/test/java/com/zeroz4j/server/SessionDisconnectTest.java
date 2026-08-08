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

import jakarta.websocket.CloseReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Identity is fixed for the life of a connection, so an application had no way to make a revoked
 * account, a removed role or an ended session take effect on a socket that was already open. These
 * tests pin the answer: closing the connection, which the client's own reconnect then turns into a
 * fresh authentication.
 */
class SessionDisconnectTest {

    private WasmRmiServerEngine engine;

    @BeforeEach
    void setUp() {
        engine = new WasmRmiServerEngine();
        engine.mapper = new com.zeroz4j.api.ObjectMapper();
        WasmRmiServerEngine.clearActiveSessionsForTesting();
    }

    private WasmRmiServerEngineTest.FakeSession session(String id, String user) {
        WasmRmiServerEngineTest.FakeSession s = new WasmRmiServerEngineTest.FakeSession(id);
        if (user != null) {
            Principal principal = () -> user;
            s.getUserProperties().put(RmiEndpointConfigurator.PRINCIPAL_KEY, principal);
        }
        s.getUserProperties().put(RmiEndpointConfigurator.ROLES_KEY, Set.of());
        WasmRmiServerEngine.addActiveSessionForTesting(s);
        return s;
    }

    @Test
    void disconnectClosesEverySessionOfThatUserAndNobodyElses() {
        // The same person in two tabs is the case that matters: closing one of them and leaving the
        // other open would revoke nothing at all.
        WasmRmiServerEngineTest.FakeSession tabOne = session("s1", "alice");
        WasmRmiServerEngineTest.FakeSession tabTwo = session("s2", "alice");
        WasmRmiServerEngineTest.FakeSession bob = session("s3", "bob");

        int closed = engine.disconnect("alice", "Your portal access was withdrawn.");

        assertEquals(2, closed);
        assertTrue(tabOne.closed);
        assertTrue(tabTwo.closed, "every session of that user must close");
        assertFalse(bob.closed, "another user's connection must be untouched");
    }

    @Test
    void theCloseCarriesThePolicyCodeAndTheReason() {
        // A policy close is what tells the client this was a decision rather than a dropped network,
        // which is the difference between showing a sign-in page and retrying for ever.
        WasmRmiServerEngineTest.FakeSession alice = session("s1", "alice");

        engine.disconnect("alice", "Session expired.");

        assertEquals(CloseReason.CloseCodes.VIOLATED_POLICY, alice.closeReason.getCloseCode());
        assertEquals("Session expired.", alice.closeReason.getReasonPhrase());
    }

    @Test
    void anAnonymousConnectionIsNeverMatched() {
        WasmRmiServerEngineTest.FakeSession anonymous = session("s1", null);

        assertEquals(0, engine.disconnect("alice", "gone"));
        assertFalse(anonymous.closed);
    }

    @Test
    void aBlankPrincipalClosesNothing() {
        // An application computing a principal that came back null would otherwise sign out every
        // user it has.
        WasmRmiServerEngineTest.FakeSession alice = session("s1", "alice");

        assertEquals(0, engine.disconnect(null, "oops"));
        assertEquals(0, engine.disconnect("  ", "oops"));
        assertFalse(alice.closed);
    }

    @Test
    void disconnectSessionClosesOneTabOnly() {
        WasmRmiServerEngineTest.FakeSession tabOne = session("s1", "alice");
        WasmRmiServerEngineTest.FakeSession tabTwo = session("s2", "alice");

        assertTrue(engine.disconnectSession("s1", "This connection has been open too long."));

        assertTrue(tabOne.closed);
        assertFalse(tabTwo.closed, "the same user's other tab must stay up");
    }

    @Test
    void disconnectingAnUnknownSessionIsNotAnError() {
        assertFalse(engine.disconnectSession("no-such-session", "gone"));
        assertFalse(engine.disconnectSession(null, "gone"));
    }

    @Test
    void anOverLongReasonIsTrimmedRatherThanThrown() {
        // Containers reject a reason over 123 bytes by throwing, which would turn "revoke this
        // account" into "revoke nothing" over a message nobody reads.
        String tooLong = "x".repeat(500);

        assertEquals(123, WasmRmiServerEngine.truncate(tooLong).length());
        assertEquals("", WasmRmiServerEngine.truncate(null));
        assertEquals("short", WasmRmiServerEngine.truncate("short"));
    }

    @Test
    void truncationNeverSplitsACharacter() {
        // Measured in bytes, so a multi-byte character straddling the limit must be dropped whole
        // rather than leaving half of it in the frame.
        String reason = "é".repeat(100);   // 200 bytes

        String truncated = WasmRmiServerEngine.truncate(reason);

        assertEquals(61, truncated.length(), "61 whole characters fit in 123 bytes");
        assertTrue(truncated.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 123);
    }
}
