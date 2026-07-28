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
package com.zeroz4j.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wire behaviour of lazy references, exercised through a stand-in {@link LazyAdapter} so this module
 * stays free of any EclipseStore dependency — which is the whole reason the adapter exists.
 *
 * <p>The contract under test: <b>a lazy reference puts a handle on the wire and never its
 * contents.</b></p>
 */
class LazyWireTest {

    /** Stands in for a server-side EclipseStore {@code Lazy}. */
    static final class FakeLazy {
        final Object contents;

        FakeLazy(Object contents) {
            this.contents = contents;
        }
    }

    /** Stands in for the client-side lazy that resolves over RMI. */
    static final class FakeClientLazy {
        final String handle;

        FakeClientLazy(String handle) {
            this.handle = handle;
        }
    }

    static final class FakeAdapter implements LazyAdapter {
        final Map<String, FakeLazy> registered = new HashMap<>();
        int handleRequests;

        @Override
        public boolean isLazy(Object value) {
            return value instanceof FakeLazy;
        }

        @Override
        public String handleFor(Object lazy, ObjectMapper mapper) {
            handleRequests++;
            String handle = "lazy-" + registered.size();
            registered.put(handle, (FakeLazy) lazy);
            return handle;
        }

        @Override
        public Object fromHandle(String handle) {
            return new FakeClientLazy(handle);
        }

        @Override
        public Object contentsOf(Object lazy) {
            return ((FakeLazy) lazy).contents;
        }
    }

    private final FakeAdapter adapter = new FakeAdapter();

    @AfterEach
    void detach() {
        BinaryRegistry.setLazyAdapter(null);
    }

    private Object roundTrip(Object value) {
        GrowableBuffer buffer = new GrowableBuffer(32);
        BinarySerializer.writeValue(buffer, value, new ObjectMapper());
        return BinarySerializer.readValue(ByteBuffer.wrap(buffer.toByteArray()), new ObjectMapper());
    }

    @Test
    void aLazyBecomesAHandleNotItsContents() {
        BinaryRegistry.setLazyAdapter(adapter);

        // A payload far larger than any handle, so a by-value encoding would be obvious.
        List<String> heavy = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            heavy.add("row-" + i + "-with-some-padding-to-make-it-bulky");
        }

        GrowableBuffer buffer = new GrowableBuffer(32);
        BinarySerializer.writeValue(buffer, new FakeLazy(heavy), new ObjectMapper());
        byte[] bytes = buffer.toByteArray();

        assertEquals(BinarySerializer.TAG_LAZY, bytes[0], "a lazy must use its own tag");
        assertTrue(bytes.length < 64,
                "only the handle should be written, but " + bytes.length + " bytes were");
        assertEquals(1, adapter.handleRequests);
    }

    @Test
    void theHandleRoundTripsToAClientLazy() {
        BinaryRegistry.setLazyAdapter(adapter);

        Object result = roundTrip(new FakeLazy("contents"));

        assertTrue(result instanceof FakeClientLazy, "expected the adapter's client-side lazy");
        assertEquals("lazy-0", ((FakeClientLazy) result).handle);
    }

    @Test
    void theServerKeepsTheHandleForLaterResolution() {
        BinaryRegistry.setLazyAdapter(adapter);
        BinarySerializer.writeValue(new GrowableBuffer(32), new FakeLazy("contents"), new ObjectMapper());

        assertEquals("contents", adapter.registered.get("lazy-0").contents,
                "the handle must still resolve to the original reference on the server");
    }

    @Test
    void aNullLazyFieldIsStillNull() {
        BinaryRegistry.setLazyAdapter(adapter);
        assertNull(roundTrip(null));
        assertEquals(0, adapter.handleRequests, "null must not allocate a handle");
    }

    @Test
    void lazyInsideAContainerIsAlsoDeferred() {
        BinaryRegistry.setLazyAdapter(adapter);

        List<Object> row = new ArrayList<>();
        row.add("eager");
        row.add(new FakeLazy("deferred"));

        @SuppressWarnings("unchecked")
        List<Object> result = (List<Object>) roundTrip(row);

        assertEquals("eager", result.get(0));
        assertTrue(result.get(1) instanceof FakeClientLazy,
                "a lazy nested in a collection must stay deferred too");
    }

    @Test
    void receivingALazyWithoutAnAdapterFailsLoudly() {
        BinaryRegistry.setLazyAdapter(adapter);
        GrowableBuffer buffer = new GrowableBuffer(32);
        BinarySerializer.writeValue(buffer, new FakeLazy("contents"), new ObjectMapper());
        byte[] frame = buffer.toByteArray();

        BinaryRegistry.setLazyAdapter(null);   // a tier with no lazy support

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> BinarySerializer.readValue(ByteBuffer.wrap(frame), new ObjectMapper()));
        assertTrue(error.getMessage().contains("LazyAdapter"),
                "the error must name the missing adapter, got: " + error.getMessage());
    }

    @Test
    void withoutAnAdapterNothingChangesForOrdinaryValues() {
        // The lazy branch must not interfere with the existing type set when no adapter is installed.
        BinaryRegistry.setLazyAdapter(null);
        assertEquals("plain", roundTrip("plain"));
        assertEquals(42, roundTrip(42));
        assertFalse(roundTrip(new ArrayList<>()) == null);
    }
}
