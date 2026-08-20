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

import com.zeroz4j.api.EventTopic;
import com.zeroz4j.api.Scope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Server events used to reach every connected session with no principal filter, so anything
 * belonging to one user could only be published by leaking it to everyone. These tests pin the
 * scoped alternative.
 */
class ScopedPublishTest {

    private static final EventTopic<String> NOTICE = EventTopic.of(String.class, "test.notice");

    private WasmRmiServerEngine engine;

    @BeforeEach
    void setUp() throws Exception {
        engine = new WasmRmiServerEngine();
        engine.mapper = new com.zeroz4j.api.ObjectMapper();
        WasmRmiServerEngine.clearActiveSessionsForTesting();
    }

    private WasmRmiServerEngineTest.FakeSession session(String id, String user) {
        return session(id, user, null);
    }

    private WasmRmiServerEngineTest.FakeSession session(String id, String user, String tenant) {
        WasmRmiServerEngineTest.FakeSession s = new WasmRmiServerEngineTest.FakeSession(id);
        if (user != null) {
            Principal principal = () -> user;
            s.getUserProperties().put(RmiEndpointConfigurator.PRINCIPAL_KEY, principal);
        }
        if (tenant != null) {
            s.getUserProperties().put(RmiEndpointConfigurator.TENANT_KEY, tenant);
        }
        s.getUserProperties().put(RmiEndpointConfigurator.ROLES_KEY, Set.of());
        WasmRmiServerEngine.addActiveSessionForTesting(s);
        return s;
    }

    private static int frames(WasmRmiServerEngineTest.FakeSession s) {
        return s.basic.sentBuffers().size();
    }

    @Test
    void globalStillReachesEveryone() {
        WasmRmiServerEngineTest.FakeSession alice = session("s1", "alice");
        WasmRmiServerEngineTest.FakeSession bob = session("s2", "bob");

        engine.publish(NOTICE, "public news");

        assertEquals(1, frames(alice));
        assertEquals(1, frames(bob));
    }

    @Test
    void publishToUserReachesOnlyThatUser() {
        WasmRmiServerEngineTest.FakeSession alice = session("s1", "alice");
        WasmRmiServerEngineTest.FakeSession bob = session("s2", "bob");

        engine.publishToUser(NOTICE, "your balance changed", "alice");

        assertEquals(1, frames(alice));
        assertEquals(0, frames(bob), "another user must not receive it");
    }

    @Test
    void publishToUserReachesAllOfThatUsersSessions() {
        // The same person in two tabs, or on two devices.
        WasmRmiServerEngineTest.FakeSession tabOne = session("s1", "alice");
        WasmRmiServerEngineTest.FakeSession tabTwo = session("s2", "alice");
        WasmRmiServerEngineTest.FakeSession bob = session("s3", "bob");

        engine.publishToUser(NOTICE, "your balance changed", "alice");

        assertEquals(1, frames(tabOne));
        assertEquals(1, frames(tabTwo), "every session of that user receives it");
        assertEquals(0, frames(bob));
    }

    @Test
    void publishToSessionReachesOneTabOnly() {
        WasmRmiServerEngineTest.FakeSession tabOne = session("s1", "alice");
        WasmRmiServerEngineTest.FakeSession tabTwo = session("s2", "alice");

        engine.publishToSession(NOTICE, "this tab only", "s1");

        assertEquals(1, frames(tabOne));
        assertEquals(0, frames(tabTwo), "the same user's other tab must not receive it");
    }

    @Test
    void anonymousSessionsAreNotReachedByUserScope() {
        WasmRmiServerEngineTest.FakeSession anonymous = session("s1", null);

        engine.publishToUser(NOTICE, "private", "alice");

        assertEquals(0, frames(anonymous),
                "a session with no principal must never match a user-scoped push");
    }

    @Test
    void aMissingTargetIsRefused() {
        session("s1", "alice");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> engine.publish(NOTICE, "x", Scope.USER, null));
        assertTrue(error.getMessage().contains("needs a target"),
                "silently reaching nobody would be worse: " + error.getMessage());

        assertThrows(IllegalArgumentException.class,
                () -> engine.publish(NOTICE, "x", Scope.SESSION, ""));
    }

    @Test
    void anUnserializablePayloadFailsBeforeAnySessionIsTouched() {
        WasmRmiServerEngineTest.FakeSession alice = session("s1", "alice");
        EventTopic<Object> bad = EventTopic.of(Object.class, "test.bad");

        assertThrows(IllegalArgumentException.class,
                () -> engine.publish(bad, new java.io.ByteArrayOutputStream(), Scope.USER, "alice"));
        assertEquals(0, frames(alice), "nothing should be sent when the payload cannot be written");
    }

    @Test
    void tenantScopeReachesOnlyThatTenant() {
        WasmRmiServerEngineTest.FakeSession acmeOne = session("s1", "alice", "acme");
        WasmRmiServerEngineTest.FakeSession acmeTwo = session("s2", "bob", "acme");
        WasmRmiServerEngineTest.FakeSession other = session("s3", "carol", "globex");

        engine.publish(NOTICE, "acme maintenance window", Scope.TENANT, "acme");

        assertEquals(1, frames(acmeOne));
        assertEquals(1, frames(acmeTwo), "every session of the tenant receives it");
        assertEquals(0, frames(other), "another tenant must never receive it");
    }

    @Test
    void aSessionWithoutATenantNeverMatchesTenantScope() {
        // Anonymous connections, and providers that report no tenant, must not receive tenant pushes.
        WasmRmiServerEngineTest.FakeSession noTenant = session("s1", "alice", null);
        WasmRmiServerEngineTest.FakeSession anonymous = session("s2", null, null);

        engine.publish(NOTICE, "acme only", Scope.TENANT, "acme");

        assertEquals(0, frames(noTenant));
        assertEquals(0, frames(anonymous));
    }
}
