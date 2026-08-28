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
 * {@code @OnMessage} takes a whole message — there is no partial-message handling — so a message
 * larger than the session's binary buffer does not raise an error, it closes the socket.
 *
 * <p>These tests pin the two halves of that: a deployment's own setting is applied unchanged, and
 * an unset property gets the framework's 4 MB default rather than the container's, which on Tyrus
 * (what Helidon embeds) is {@code Integer.MAX_VALUE} and would let a client make the server
 * assemble roughly 2 GB.</p>
 */
class WebSocketLimitsTest {

    private WasmRmiServerEngine engine;

    @BeforeEach
    void setUp() {
        clearProperties();
        com.zeroz4j.api.ObjectMapper mapper = new com.zeroz4j.api.ObjectMapper();
        engine = new WasmRmiServerEngine();
        engine.injectedRuntime = new ServerRuntime();
        engine.mapper = mapper;
        engine.syncEngine = new SyncEngine();
        engine.syncEngine.mapper = mapper;
        engine.syncEngine.runtime = engine.injectedRuntime;
    }

    @AfterEach
    void tearDown() {
        clearProperties();
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
    void anUnsetBinaryLimitGetsTheFrameworkDefaultOfFourMegabytes() {
        WasmRmiServerEngineTest.FakeSession session = open();

        assertEquals(4 * 1024 * 1024, session.maxBinaryBufferSize,
                "Tyrus defaults this to Integer.MAX_VALUE, so leaving it alone lets a client make"
                        + " the server assemble roughly 2 GB from a fragmented message");
        assertEquals(4 * 1024 * 1024, WasmRmiServerEngine.DEFAULT_MAX_BINARY_BYTES);
    }

    @Test
    void anUnsetIdleTimeoutStillLeavesTheContainersOwn() {
        // An abandoned connection costs a session, not memory, and containers disagree on what a
        // sensible value is — so this one is not defaulted.
        assertEquals(0L, open().maxIdleTimeout);
    }

    @Test
    void aConfiguredBinaryLimitIsApplied() {
        System.setProperty(WasmRmiServerEngine.MAX_BINARY_BYTES_PROPERTY, "8388608");

        assertEquals(8 * 1024 * 1024, open().maxBinaryBufferSize,
                "a deployment that has already tuned this keeps its number");
    }

    @Test
    void aConfiguredBinaryLimitSmallerThanTheDefaultIsAlsoApplied() {
        // "Explicit wins" has to mean downwards too, or a deployment on a constrained host cannot
        // tighten the limit.
        System.setProperty(WasmRmiServerEngine.MAX_BINARY_BYTES_PROPERTY, "65536");

        assertEquals(65536, open().maxBinaryBufferSize);
    }

    @Test
    void aConfiguredIdleTimeoutIsAppliedInMinutes() {
        System.setProperty(WasmRmiServerEngine.IDLE_TIMEOUT_MINUTES_PROPERTY, "1");

        assertEquals(60_000L, open().maxIdleTimeout,
                "an abandoned tab should not hold a session and its resources indefinitely");
    }

    @Test
    void anUnusableValueFallsBackToWhatAnUnsetPropertyWouldDo() {
        // Zero and negative mean different things to different containers, and "banana" means
        // nothing anywhere. An unusable size setting is not a reason to leave the server exposed,
        // so it lands on the framework default; an unusable idle setting leaves the container's.
        System.setProperty(WasmRmiServerEngine.MAX_BINARY_BYTES_PROPERTY, "0");
        System.setProperty(WasmRmiServerEngine.IDLE_TIMEOUT_MINUTES_PROPERTY, "banana");

        WasmRmiServerEngineTest.FakeSession session = open();

        assertEquals(4 * 1024 * 1024, session.maxBinaryBufferSize);
        assertEquals(0L, session.maxIdleTimeout);
    }
}
