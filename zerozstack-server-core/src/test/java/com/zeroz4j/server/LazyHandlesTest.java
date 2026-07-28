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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A lazy handle is a capability: presenting it asks the server to load and return a subgraph. These
 * tests pin the rule that makes that safe — <b>a session can only resolve handles it was itself
 * sent</b>. Without it, a handle would let any client read data it was never permitted to see.
 */
class LazyHandlesTest {

    @AfterEach
    void reset() {
        LazyHandles.resetForTesting();
    }

    @Test
    void aHandleResolvesForTheSessionItWasDisclosedTo() {
        Object lazy = new Object();
        LazyHandles.setCurrentSession("session-A");
        String handle = LazyHandles.register(lazy);
        LazyHandles.setCurrentSession(null);

        assertSame(lazy, LazyHandles.resolve(handle, "session-A"));
    }

    @Test
    void anotherSessionCannotResolveIt() {
        LazyHandles.setCurrentSession("session-A");
        String handle = LazyHandles.register(new Object());
        LazyHandles.setCurrentSession(null);

        assertNull(LazyHandles.resolve(handle, "session-B"),
                "a handle disclosed to one session must not be resolvable by another");
    }

    @Test
    void anUnknownHandleResolvesToNothing() {
        assertNull(LazyHandles.resolve("made-up", "session-A"));
        assertNull(LazyHandles.resolve(null, "session-A"));
        assertNull(LazyHandles.resolve("anything", null));
    }

    @Test
    void registeringOutsideASessionIsRefused() {
        LazyHandles.setCurrentSession(null);
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> LazyHandles.register(new Object()));
        assertEquals(true, error.getMessage().contains("not be bound to any session"),
                "the refusal must explain why: " + error.getMessage());
    }

    @Test
    void theSameReferenceReusesItsHandle() {
        Object lazy = new Object();
        LazyHandles.setCurrentSession("session-A");
        String first = LazyHandles.register(lazy);
        String second = LazyHandles.register(lazy);
        LazyHandles.setCurrentSession(null);

        assertEquals(first, second, "a repeatedly synced object must not grow the registry");
        assertEquals(1, LazyHandles.handleCount("session-A"));
    }

    @Test
    void distinctReferencesGetDistinctHandles() {
        LazyHandles.setCurrentSession("session-A");
        String first = LazyHandles.register(new Object());
        String second = LazyHandles.register(new Object());
        LazyHandles.setCurrentSession(null);

        assertNotEquals(first, second);
        assertEquals(2, LazyHandles.handleCount("session-A"));
    }

    @Test
    void twoSessionsGetSeparateHandlesForTheSameReference() {
        Object shared = new Object();

        LazyHandles.setCurrentSession("session-A");
        String handleA = LazyHandles.register(shared);
        LazyHandles.setCurrentSession("session-B");
        String handleB = LazyHandles.register(shared);
        LazyHandles.setCurrentSession(null);

        assertNotEquals(handleA, handleB, "handles are per session, not global");
        assertSame(shared, LazyHandles.resolve(handleA, "session-A"));
        assertSame(shared, LazyHandles.resolve(handleB, "session-B"));
        assertNull(LazyHandles.resolve(handleA, "session-B"), "handles must not be interchangeable");
    }

    @Test
    void closingASessionReleasesItsHandles() {
        LazyHandles.setCurrentSession("session-A");
        String handle = LazyHandles.register(new Object());
        LazyHandles.setCurrentSession(null);
        assertEquals(1, LazyHandles.handleCount("session-A"));

        LazyHandles.sessionClosed("session-A");

        assertEquals(0, LazyHandles.handleCount("session-A"));
        assertNull(LazyHandles.resolve(handle, "session-A"),
                "handles must not outlive the session, unlike ObjectMapper entries");
    }

    @Test
    void closingOneSessionLeavesOthersIntact() {
        LazyHandles.setCurrentSession("session-A");
        LazyHandles.register(new Object());
        LazyHandles.setCurrentSession("session-B");
        String handleB = LazyHandles.register(new Object());
        LazyHandles.setCurrentSession(null);

        LazyHandles.sessionClosed("session-A");

        assertEquals(0, LazyHandles.handleCount("session-A"));
        assertEquals(1, LazyHandles.handleCount("session-B"));
        assertNull(LazyHandles.resolve(handleB, "session-A"));
    }
}
