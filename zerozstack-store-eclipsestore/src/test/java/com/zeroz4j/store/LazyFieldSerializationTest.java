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

    @org.junit.jupiter.api.AfterEach
    void detach() {
        com.zeroz4j.api.BinaryRegistry.setLazyAdapter(null);
        com.zeroz4j.server.LazyHandles.resetForTesting();
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
        com.zeroz4j.server.LazyHandles.setCurrentSession("session-A");
        try {
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
        } finally {
            com.zeroz4j.server.LazyHandles.setCurrentSession(null);
        }
    }

    @Test
    void theHandleResolvesBackToTheSameLazyForThatSession() {
        EclipseStoreLazyAdapter.install();
        Lazy<List<String>> lazy = Lazy.Reference(Arrays.asList("a", "b"));

        com.zeroz4j.server.LazyHandles.setCurrentSession("session-A");
        GrowableBuffer buffer = new GrowableBuffer(32);
        BinarySerializer.writeValue(buffer, lazy, new ObjectMapper());
        com.zeroz4j.server.LazyHandles.setCurrentSession(null);

        // Recover the handle the same way the resolve handler does.
        java.nio.ByteBuffer read = java.nio.ByteBuffer.wrap(buffer.toByteArray());
        read.get();                                   // skip TAG_LAZY
        String handle = BinarySerializer.readString(read);

        Object resolved = com.zeroz4j.server.LazyHandles.resolve(handle, "session-A");
        assertEquals(Arrays.asList("a", "b"),
                new EclipseStoreLazyAdapter().contentsOf(resolved));
        assertNull(com.zeroz4j.server.LazyHandles.resolve(handle, "another-session"),
                "another session must not be able to resolve it");
    }

    @Test
    void aLazyIsNotResolvedJustBySerializingIt() {
        EclipseStoreLazyAdapter.install();
        com.zeroz4j.server.LazyHandles.setCurrentSession("session-A");
        try {
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
        } finally {
            com.zeroz4j.server.LazyHandles.setCurrentSession(null);
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
