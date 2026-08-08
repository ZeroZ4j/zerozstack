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

/**
 * {@code @OnMessage} takes a whole message — there is no partial-message handling — so a response
 * larger than the container's binary buffer does not raise an error, it closes the socket. These
 * tests pin that the limit can be raised, and that leaving it unset does not quietly override
 * whatever the container was configured with.
 */
class WebSocketLimitsTest {

    private WasmRmiServerEngine engine;

    @BeforeEach
    void setUp() {
        clearProperties();
        com.zeroz4j.api.ObjectMapper mapper = new com.zeroz4j.api.ObjectMapper();
        engine = new WasmRmiServerEngine();
        engine.mapper = mapper;
        engine.syncEngine = new SyncEngine();
        engine.syncEngine.mapper = mapper;
        WasmRmiServerEngine.clearActiveSessionsForTesting();
    }

    @AfterEach
    void tearDown() {
        clearProperties();
        WasmRmiServerEngine.clearActiveSessionsForTesting();
    }

    private static void clearProperties() {
        System.clearProperty(WasmRmiServerEngine.MAX_BINARY_BYTES_PROPERTY);
        System.clearProperty(WasmRmiServerEngine.IDLE_TIMEOUT_MINUTES_PROPERTY);
    }

    private WasmRmiServerEngineTest.FakeSession open() {
        WasmRmiServerEngineTest.FakeSession session =
                new WasmRmiServerEngineTest.FakeSession("limits-" + System.nanoTime());
        WasmRmiServerEngineTest.FakeEndpointConfig config =
                new WasmRmiServerEngineTest.FakeEndpointConfig();
        config.getUserProperties().put(RmiEndpointConfigurator.ROLES_KEY, java.util.Set.of());
        engine.onOpen(session, config);
        return session;
    }

    @Test
    void unsetPropertiesLeaveTheContainersOwnLimits() {
        WasmRmiServerEngineTest.FakeSession session = open();

        assertEquals(0, session.maxBinaryBufferSize,
                "an unset property must not impose a framework default over the deployment's tuning");
        assertEquals(0L, session.maxIdleTimeout);
    }

    @Test
    void aConfiguredBinaryLimitIsApplied() {
        System.setProperty(WasmRmiServerEngine.MAX_BINARY_BYTES_PROPERTY, "8388608");

        assertEquals(8 * 1024 * 1024, open().maxBinaryBufferSize);
    }

    @Test
    void aConfiguredIdleTimeoutIsAppliedInMinutes() {
        System.setProperty(WasmRmiServerEngine.IDLE_TIMEOUT_MINUTES_PROPERTY, "1");

        assertEquals(60_000L, open().maxIdleTimeout,
                "an abandoned tab should not hold a session and its resources indefinitely");
    }

    @Test
    void anUnusableValueIsIgnoredRatherThanApplied() {
        // Zero and negative mean different things to different containers, and "banana" means
        // nothing anywhere; either way, leaving the container's value alone is the safe answer.
        System.setProperty(WasmRmiServerEngine.MAX_BINARY_BYTES_PROPERTY, "0");
        System.setProperty(WasmRmiServerEngine.IDLE_TIMEOUT_MINUTES_PROPERTY, "banana");

        WasmRmiServerEngineTest.FakeSession session = open();

        assertEquals(0, session.maxBinaryBufferSize);
        assertEquals(0L, session.maxIdleTimeout);
    }
}
