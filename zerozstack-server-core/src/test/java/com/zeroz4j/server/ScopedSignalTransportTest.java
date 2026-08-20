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

import com.zeroz4j.api.BinarySerializer;
import com.zeroz4j.api.ObjectMapper;
import com.zeroz4j.api.Scope;
import com.zeroz4j.api.SyncFrameTypes;
import com.zeroz4j.signals.ScopedSignal;
import com.zeroz4j.signals.Signals;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.security.Principal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scoped signals exist so that one browser's or one tenant's state never reaches another's. These
 * tests pin that on the server side, where the decision is actually made: the target comes from the
 * handshake, never from anything the client sends.
 */
class ScopedSignalTransportTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        Signals.resetForTesting();
        WasmRmiServerEngine.clearActiveSessionsForTesting();
        ServerSignalTransport.install(mapper);
    }

    @AfterEach
    void tearDown() {
        Signals.resetForTesting();
        WasmRmiServerEngine.clearActiveSessionsForTesting();
    }

    /** A connected session carrying whichever identity the handshake established. */
    private WasmRmiServerEngineTest.FakeSession session(String id, String clientId,
                                                        String user, String tenant, String... roles) {
        WasmRmiServerEngineTest.FakeSession s = new WasmRmiServerEngineTest.FakeSession(id);
        if (clientId != null) {
            s.getUserProperties().put(RmiEndpointConfigurator.CLIENT_KEY, clientId);
        }
        if (user != null) {
            Principal principal = () -> user;
            s.getUserProperties().put(RmiEndpointConfigurator.PRINCIPAL_KEY, principal);
        }
        if (tenant != null) {
            s.getUserProperties().put(RmiEndpointConfigurator.TENANT_KEY, tenant);
        }
        s.getUserProperties().put(RmiEndpointConfigurator.ROLES_KEY, Set.of(roles));
        WasmRmiServerEngine.addActiveSessionForTesting(s);
        return s;
    }

    /** Reads the last signal frame a session received: name plus value. */
    private String[] lastSignalFrame(WasmRmiServerEngineTest.FakeSession s) {
        ByteBuffer frame = s.basic.sentBuffers().get(s.basic.sentBuffers().size() - 1).duplicate();
        frame.getInt();
        assertEquals(SyncFrameTypes.SIGNAL_UPD, frame.get(), "expected a signal update frame");
        String name = BinarySerializer.readString(frame);
        Object value = BinarySerializer.readValue(frame, mapper);
        return new String[] { name, String.valueOf(value) };
    }

    private static int frames(WasmRmiServerEngineTest.FakeSession s) {
        return s.basic.sentBuffers().size();
    }

    @Test
    void aSubscriberIsSentItsOwnTargetsValue() {
        ScopedSignal<String> basket = Signals.scoped("shop.basket", "empty", Scope.CLIENT);
        basket.forTarget("browser-a").set("one apple");
        basket.forTarget("browser-b").set("two pears");

        WasmRmiServerEngineTest.FakeSession a = session("s1", "browser-a", null, null);
        WasmRmiServerEngineTest.FakeSession b = session("s2", "browser-b", null, null);

        ServerSignalTransport.handleSubscribe("shop.basket", a);
        ServerSignalTransport.handleSubscribe("shop.basket", b);

        assertEquals("one apple", lastSignalFrame(a)[1]);
        assertEquals("two pears", lastSignalFrame(b)[1]);
    }

    @Test
    void theFrameCarriesTheFamilyNameSoNoClientLearnsItsTarget() {
        Signals.scoped("shop.basket", "empty", Scope.CLIENT);
        WasmRmiServerEngineTest.FakeSession a = session("s1", "browser-a", null, null);

        ServerSignalTransport.handleSubscribe("shop.basket", a);

        assertEquals("shop.basket", lastSignalFrame(a)[0],
                "a per-target wire name would tell the client that other targets exist");
    }

    @Test
    void aSetReachesOnlyTheSessionsOfThatTarget() {
        ScopedSignal<String> basket = Signals.scoped("shop.basket", "empty", Scope.CLIENT);
        WasmRmiServerEngineTest.FakeSession tabOne = session("s1", "browser-a", null, null);
        WasmRmiServerEngineTest.FakeSession tabTwo = session("s2", "browser-a", null, null);
        WasmRmiServerEngineTest.FakeSession other = session("s3", "browser-b", null, null);

        basket.forTarget("browser-a").set("one apple");

        assertEquals(1, frames(tabOne));
        assertEquals(1, frames(tabTwo), "the same browser's other tab receives it");
        assertEquals(0, frames(other), "another browser must never receive it");
    }

    @Test
    void anAnonymousSessionIsSentNothingForATenantScopedSignal() {
        Signals.scoped("billing.plan", "free", Scope.TENANT);
        WasmRmiServerEngineTest.FakeSession anonymous = session("s1", "browser-a", null, null);

        ServerSignalTransport.handleSubscribe("billing.plan", anonymous);

        assertEquals(0, frames(anonymous),
                "with no tenant there is no value to send; guessing one would be a leak");
    }

    @Test
    void tenantScopeIsolatesTenants() {
        ScopedSignal<String> plan = Signals.scoped("billing.plan", "free", Scope.TENANT);
        WasmRmiServerEngineTest.FakeSession acme = session("s1", "b1", "alice", "acme");
        WasmRmiServerEngineTest.FakeSession globex = session("s2", "b2", "carol", "globex");

        plan.forTarget("acme").set("enterprise");

        assertEquals(1, frames(acme));
        assertEquals(0, frames(globex));
    }

    @Test
    void userScopeReachesEveryDeviceOfThatPerson() {
        ScopedSignal<String> inbox = Signals.scoped("mail.unread", "0", Scope.USER);
        WasmRmiServerEngineTest.FakeSession laptop = session("s1", "b1", "alice", null);
        WasmRmiServerEngineTest.FakeSession phone = session("s2", "b2", "alice", null);
        WasmRmiServerEngineTest.FakeSession bob = session("s3", "b3", "bob", null);

        inbox.forTarget("alice").set("3");

        assertEquals(1, frames(laptop));
        assertEquals(1, frames(phone));
        assertEquals(0, frames(bob));
    }

    @Test
    void aClientWriteLandsOnTheWritersOwnTarget() {
        ScopedSignal<String> basket =
                Signals.scopedWritable("shop.basket", "empty", Scope.CLIENT);
        WasmRmiServerEngineTest.FakeSession a = session("s1", "browser-a", null, null);
        session("s2", "browser-b", null, null);

        ServerSignalTransport.handleClientSet("shop.basket", "one apple", a);

        assertEquals("one apple", basket.forTarget("browser-a").get());
        assertEquals("empty", basket.forTarget("browser-b").get(),
                "a client cannot name a target, so it can only ever write its own");
    }

    @Test
    void aClientWriteWithoutTheRequiredRoleIsRefusedAndCorrected() {
        ScopedSignal<String> plan =
                Signals.scopedWritable("billing.plan", "free", Scope.TENANT, "admin");
        WasmRmiServerEngineTest.FakeSession user = session("s1", "b1", "alice", "acme", "user");

        ServerSignalTransport.handleClientSet("billing.plan", "enterprise", user);

        assertEquals("free", plan.forTarget("acme").get(), "the write must not be applied");
        assertEquals(1, frames(user), "the writer is snapped back to server truth");
        assertEquals("free", lastSignalFrame(user)[1]);
    }

    @Test
    void aClientWriteToAReadOnlyScopedSignalIsRefused() {
        ScopedSignal<String> plan = Signals.scoped("billing.plan", "free", Scope.TENANT);
        WasmRmiServerEngineTest.FakeSession user = session("s1", "b1", "alice", "acme");

        ServerSignalTransport.handleClientSet("billing.plan", "enterprise", user);

        assertEquals("free", plan.forTarget("acme").get());
    }

    @Test
    void aSubscriptionArrivingBeforeTheDeclarationIsAnsweredWhenItLoads() {
        WasmRmiServerEngineTest.FakeSession a = session("s1", "browser-a", null, null);

        // The declaring class has not been loaded yet, exactly as when a client connects during
        // startup. The subscription parks rather than being lost.
        ServerSignalTransport.handleSubscribe("shop.basket", a);
        assertEquals(0, frames(a));

        ScopedSignal<String> basket = Signals.scoped("shop.basket", "empty", Scope.CLIENT);

        assertEquals(1, frames(a), "the parked subscription should flush on declaration");
        assertEquals("empty", lastSignalFrame(a)[1]);
        assertTrue(basket.knownTargets().contains("browser-a"));
    }

    @Test
    void theServerCannotReadAScopedSignalWithoutNamingATarget() {
        ScopedSignal<String> basket = Signals.scoped("shop.basket", "empty", Scope.CLIENT);

        IllegalStateException error = assertThrows(IllegalStateException.class, basket::mine);

        assertTrue(error.getMessage().contains("forTarget"),
                "the message should name the method to use instead: " + error.getMessage());
    }
}
