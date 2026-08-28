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
package com.zeroz4j.store;

import com.zeroz4j.api.BinarySerializer;
import com.zeroz4j.api.GrowableBuffer;
import com.zeroz4j.api.ObjectMapper;
import org.eclipse.serializer.reference.Lazy;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the behaviour of EclipseStore's {@link Lazy} on the wire.
 *
 * <p>The contract: a lazy field serializes to a <b>session-scoped handle</b> and never to its
 * contents, so a deferred subgraph stays deferred across the network. It is resolved only when a
 * client asks for it, and only by the session it was disclosed to.</p>
 *
 * <p>A tier with no {@code LazyAdapter} installed — anything without EclipseStore — rejects a lazy
 * reference loudly rather than guessing.</p>
 */
class LazyFieldSerializationTest {

    /**
     * The server the frames in this test are written on behalf of.
     *
     * <p>Since 0.8.0 a lazy handle belongs to one server as well as to one connection, so a write
     * is bracketed with both. Two servers in one process no longer share a handle registry.</p>
     */
    private com.zeroz4j.server.ServerRuntime server;

    @org.junit.jupiter.api.BeforeEach
    void freshServer() {
        server = new com.zeroz4j.server.ServerRuntime();
    }

    @org.junit.jupiter.api.AfterEach
    void detach() {
        com.zeroz4j.api.BinaryRegistry.setLazyAdapter(null);
        server.shutDown();
    }

    @Test
    void lazyIsRejectedWhenNoAdapterIsInstalled() {
        // A tier without EclipseStore has no lazy handling, and must say so rather than guess.
        com.zeroz4j.api.BinaryRegistry.setLazyAdapter(null);
        Lazy<List<String>> lazy = Lazy.Reference(Arrays.asList("a", "b"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> BinarySerializer.writeValue(new GrowableBuffer(16), lazy, new ObjectMapper()));

        assertTrue(error.getMessage().contains("Unsupported type"),
                "expected an unsupported-type error, got: " + error.getMessage());
    }

    @Test
    void aLazyFieldTravelsAsAHandleNotItsContents() {
        EclipseStoreLazyAdapter.install();
        try (com.zeroz4j.server.LazyHandles.Write write =
                     com.zeroz4j.server.LazyHandles.writingTo(server, "session-A")) {
            List<String> heavy = new java.util.ArrayList<>();
            for (int i = 0; i < 500; i++) {
                heavy.add("row-" + i + "-with-padding-so-a-by-value-encoding-would-be-obvious");
            }
            Lazy<List<String>> lazy = Lazy.Reference(heavy);

            GrowableBuffer buffer = new GrowableBuffer(32);
            BinarySerializer.writeValue(buffer, lazy, new ObjectMapper());
            byte[] bytes = buffer.toByteArray();

            assertEquals(BinarySerializer.TAG_LAZY, bytes[0]);
            assertTrue(bytes.length < 64,
                    "the deferred subgraph must stay behind, but " + bytes.length + " bytes were written");
        }
    }

    @Test
    void theHandleResolvesBackToTheSameLazyForThatSession() {
        EclipseStoreLazyAdapter.install();
        Lazy<List<String>> lazy = Lazy.Reference(Arrays.asList("a", "b"));

        GrowableBuffer buffer = new GrowableBuffer(32);
        try (com.zeroz4j.server.LazyHandles.Write write =
                     com.zeroz4j.server.LazyHandles.writingTo(server, "session-A")) {
            BinarySerializer.writeValue(buffer, lazy, new ObjectMapper());
        }

        // Recover the handle the same way the resolve handler does.
        java.nio.ByteBuffer read = java.nio.ByteBuffer.wrap(buffer.toByteArray());
        read.get();                                   // skip TAG_LAZY
        String handle = BinarySerializer.readString(read);

        Object resolved = resolveOn(server, handle, "session-A");
        assertEquals(Arrays.asList("a", "b"),
                new EclipseStoreLazyAdapter().contentsOf(resolved));
        assertNull(resolveOn(server, handle, "another-session"),
                "another connection must not be able to resolve it");
        assertNull(resolveOn(new com.zeroz4j.server.ServerRuntime(), handle, "session-A"),
                "a second server in this process must not be able to resolve it either");
    }

    @Test
    void aLazyIsNotResolvedJustBySerializingIt() {
        EclipseStoreLazyAdapter.install();
        try (com.zeroz4j.server.LazyHandles.Write write =
                     com.zeroz4j.server.LazyHandles.writingTo(server, "session-A")) {
            // Lazy.Reference is already loaded, so instead assert the contents were never *read*:
            // a by-value encoding would have had to walk the list.
            final boolean[] walked = {false};
            List<String> tracking = new java.util.ArrayList<>(Arrays.asList("a")) {
                @Override
                public java.util.Iterator<String> iterator() {
                    walked[0] = true;
                    return super.iterator();
                }
            };

            BinarySerializer.writeValue(new GrowableBuffer(32),
                    Lazy.Reference(tracking), new ObjectMapper());

            assertFalse(walked[0], "serializing a lazy reference must not traverse its contents");
        }
    }

    /**
     * Looks a handle up on one server, from inside that server's own write bracket — the same way
     * the lazy-resolve handler does.
     *
     * @param runtime   the server being asked
     * @param handle    the handle presented
     * @param sessionId the connection presenting it
     * @return the lazy reference, or null when that server never gave it to that connection
     */
    private static Object resolveOn(com.zeroz4j.server.ServerRuntime runtime, String handle,
                                    String sessionId) {
        try (com.zeroz4j.server.LazyHandles.Write write =
                     com.zeroz4j.server.LazyHandles.writingTo(runtime, sessionId)) {
            return com.zeroz4j.server.LazyHandles.resolve(handle, sessionId);
        }
    }

    @Test
    void unwrappingLazyYieldsAnOrdinarySerializableValue() {
        // The supported pattern: resolve on the server, send the resolved value.
        Lazy<List<String>> lazy = Lazy.Reference(Arrays.asList("a", "b"));

        GrowableBuffer buffer = new GrowableBuffer(16);
        BinarySerializer.writeValue(buffer, lazy.get(), new ObjectMapper());

        Object result = BinarySerializer.readValue(
                java.nio.ByteBuffer.wrap(buffer.toByteArray()), new ObjectMapper());

        assertEquals(Arrays.asList("a", "b"), result);
    }
}
