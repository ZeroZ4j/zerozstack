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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A lazy handle is a capability: presenting it asks the server to load and return a subgraph. These
 * tests pin the rule that makes that safe — <b>a connection can only resolve handles it was itself
 * sent</b>. Without it, a handle would let any client read data it was never permitted to see.
 *
 * <p>Since 0.8.0 the registry belongs to one server rather than to the whole process, so the last
 * test here pins the other half of the rule: one server's handles are not the other's.</p>
 */
class LazyHandlesTest {

    private ServerRuntime server;

    @BeforeEach
    void freshServer() {
        server = new ServerRuntime();
    }

    /** Registers a reference the way a frame write does: inside a bracket naming server and connection. */
    private static String registerOn(ServerRuntime runtime, String sessionId, Object lazy) {
        try (LazyHandles.Write write = LazyHandles.writingTo(runtime, sessionId)) {
            return LazyHandles.register(lazy);
        }
    }

    private String register(String sessionId, Object lazy) {
        return registerOn(server, sessionId, lazy);
    }

    private Object resolve(String handle, String sessionId) {
        return server.lazyHandles().resolve(handle, sessionId);
    }

    private int handleCount(String sessionId) {
        return server.lazyHandles().handleCount(sessionId);
    }

    @Test
    void aHandleResolvesForTheSessionItWasDisclosedTo() {
        Object lazy = new Object();
        String handle = register("session-A", lazy);

        assertSame(lazy, resolve(handle, "session-A"));
    }

    @Test
    void anotherSessionCannotResolveIt() {
        String handle = register("session-A", new Object());

        assertNull(resolve(handle, "session-B"),
                "a handle disclosed to one connection must not be resolvable by another");
    }

    @Test
    void anUnknownHandleResolvesToNothing() {
        assertNull(resolve("made-up", "session-A"));
        assertNull(resolve(null, "session-A"));
        assertNull(resolve("anything", null));
    }

    @Test
    void registeringOutsideASessionIsRefused() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> LazyHandles.register(new Object()));
        assertTrue(error.getMessage().contains("not be bound to any connection"),
                "the refusal must explain why: " + error.getMessage());
    }

    @Test
    void theSameReferenceReusesItsHandle() {
        Object lazy = new Object();
        String first;
        String second;
        try (LazyHandles.Write write = LazyHandles.writingTo(server, "session-A")) {
            first = LazyHandles.register(lazy);
            second = LazyHandles.register(lazy);
        }

        assertEquals(first, second, "a repeatedly synced object must not grow the registry");
        assertEquals(1, handleCount("session-A"));
    }

    @Test
    void distinctReferencesGetDistinctHandles() {
        String first = register("session-A", new Object());
        String second = register("session-A", new Object());

        assertNotEquals(first, second);
        assertEquals(2, handleCount("session-A"));
    }

    @Test
    void twoSessionsGetSeparateHandlesForTheSameReference() {
        Object shared = new Object();

        String handleA = register("session-A", shared);
        String handleB = register("session-B", shared);

        assertNotEquals(handleA, handleB, "handles are per connection, not global");
        assertSame(shared, resolve(handleA, "session-A"));
        assertSame(shared, resolve(handleB, "session-B"));
        assertNull(resolve(handleA, "session-B"), "handles must not be interchangeable");
    }

    @Test
    void closingASessionReleasesItsHandles() {
        String handle = register("session-A", new Object());
        assertEquals(1, handleCount("session-A"));

        server.lazyHandles().sessionClosed("session-A");

        assertEquals(0, handleCount("session-A"));
        assertNull(resolve(handle, "session-A"),
                "handles must not outlive the connection, unlike ObjectMapper entries");
    }

    @Test
    void closingOneSessionLeavesOthersIntact() {
        register("session-A", new Object());
        String handleB = register("session-B", new Object());

        server.lazyHandles().sessionClosed("session-A");

        assertEquals(0, handleCount("session-A"));
        assertEquals(1, handleCount("session-B"));
        assertNull(resolve(handleB, "session-A"));
    }

    /**
     * The 0.8.0 rule. Two servers in one process used to share one registry keyed by connection id,
     * so a handle issued by one could be resolved on the other whenever the two happened to use the
     * same connection id — which in a test is almost always.
     */
    @Test
    @DisplayName("one server's handles are not another server's, even on the same connection id")
    void handlesDoNotCrossServers() {
        ServerRuntime other = new ServerRuntime();
        Object mine = new Object();

        String handle = register("session-A", mine);

        assertSame(mine, resolve(handle, "session-A"));
        assertNull(other.lazyHandles().resolve(handle, "session-A"),
                "a second server in the same process must not resolve the first server's handle");
        assertEquals(0, other.lazyHandles().handleCount("session-A"));
    }
}
