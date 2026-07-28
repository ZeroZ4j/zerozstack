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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the 0.3.0 serializer extension: native {@link UUID} and {@link Instant} tags plus
 * TeaVM-safe {@link Enum} handling (registry-resolver {@code TAG_ENUM} path for enums inside
 * generic containers). Also asserts backward compatibility with pre-extension payloads.
 */
public class BinarySerializerTypesTest {

    public enum Priority { LOW, MEDIUM, HIGH }

    @BeforeAll
    public static void setup() {
        // Mirrors what the generated registrar does: register a reflection-free enum resolver.
        BinaryRegistry.registerEnum(Priority.class.getName(), Priority::valueOf);
    }

    @Test
    public void testUuidRoundTrip() {
        UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        assertEquals(id, roundTrip(id));
        assertEquals(id, roundTripGrowable(id));
    }

    @Test
    public void testUuidEncodedAsTeaVmSafeStringForm() {
        // TeaVM does not emulate UUID.getMostSignificantBits()/new UUID(long,long), so the wire
        // encoding must be the canonical string form for the same code to link on the Wasm client.
        // A pure JVM round-trip passes with either encoding, so pin the wire contract explicitly:
        // this fails if a future change reverts to the two-longs binary form.
        UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        BinarySerializer.writeValue(buffer, id, new ObjectMapper());
        buffer.flip();
        assertEquals(BinarySerializer.TAG_UUID, buffer.get());
        assertEquals(id.toString(), BinarySerializer.readString(buffer));
    }

    @Test
    public void testInstantRoundTrip() {
        Instant now = Instant.ofEpochSecond(1_753_000_000L, 123_456_789);
        assertEquals(now, roundTrip(now));
        assertEquals(now, roundTripGrowable(now));

        // Epoch and a zero-nanos instant.
        assertEquals(Instant.EPOCH, roundTrip(Instant.EPOCH));
        Instant whole = Instant.ofEpochSecond(42L);
        assertEquals(whole, roundTrip(whole));
    }

    @Test
    public void testEnumRoundTrip() {
        for (Priority p : Priority.values()) {
            assertEquals(p, roundTrip(p));
            assertEquals(p, roundTripGrowable(p));
        }
    }

    @Test
    public void testListOfEnums() {
        List<Priority> list = new ArrayList<>();
        list.add(Priority.HIGH);
        list.add(Priority.LOW);
        list.add(Priority.MEDIUM);

        @SuppressWarnings("unchecked")
        List<Object> read = (List<Object>) roundTrip(list);
        assertEquals(list, read);
    }

    @Test
    public void testSetRoundTrip() {
        java.util.Set<String> tags = new java.util.LinkedHashSet<>(java.util.Arrays.asList("a", "b", "c"));

        Object viaByteBuffer = roundTrip(tags);
        Object viaGrowable = roundTripGrowable(tags);

        assertTrue(viaByteBuffer instanceof java.util.Set, "a Set must deserialize as a Set");
        assertTrue(viaGrowable instanceof java.util.Set, "GrowableBuffer path must also yield a Set");
        assertEquals(tags, viaByteBuffer);
        assertEquals(tags, viaGrowable);
    }

    @Test
    public void testSetPreservesEncounterOrder() {
        // LinkedHashSet on both sides, so iteration order survives — callers relying on an ordered
        // set (or a TreeSet's sorted order at the time of writing) get it back.
        java.util.Set<String> ordered = new java.util.LinkedHashSet<>(
                java.util.Arrays.asList("zebra", "apple", "mango"));

        @SuppressWarnings("unchecked")
        java.util.Set<String> result = (java.util.Set<String>) roundTrip(ordered);

        assertEquals(java.util.Arrays.asList("zebra", "apple", "mango"), new ArrayList<>(result));
    }

    @Test
    public void testSetDeduplicates() {
        java.util.Set<String> tags = new java.util.LinkedHashSet<>(
                java.util.Arrays.asList("dup", "dup", "unique"));
        assertEquals(2, tags.size());
        assertEquals(tags, roundTrip(tags));
    }

    @Test
    public void testSetOfEnums() {
        java.util.Set<Priority> priorities = new java.util.LinkedHashSet<>(
                java.util.Arrays.asList(Priority.HIGH, Priority.LOW));
        assertEquals(priorities, roundTrip(priorities));
    }

    @Test
    public void testTreeSetArrivesAsAnOrderedSet() {
        // A TreeSet is written in its sorted order and read back as a LinkedHashSet: the order is
        // preserved but the Comparator is not, so declare such fields as Set, not TreeSet.
        java.util.Set<String> sorted = new java.util.TreeSet<>(
                java.util.Arrays.asList("charlie", "alpha", "bravo"));

        @SuppressWarnings("unchecked")
        java.util.Set<String> result = (java.util.Set<String>) roundTrip(sorted);

        assertEquals(java.util.Arrays.asList("alpha", "bravo", "charlie"), new ArrayList<>(result));
    }

    @Test
    public void testEmptySetRoundTrip() {
        java.util.Set<String> empty = new java.util.LinkedHashSet<>();
        assertEquals(empty, roundTrip(empty));
        assertTrue(((java.util.Set<?>) roundTrip(empty)).isEmpty());
    }

    @Test
    public void testSetTagIsDistinctFromList() {
        // Pin the wire contract: a Set must not be encoded as TAG_LIST, or it would round-trip as a
        // List and silently lose its set semantics.
        GrowableBuffer buffer = new GrowableBuffer(8);
        BinarySerializer.writeValue(buffer,
                new java.util.LinkedHashSet<>(java.util.Arrays.asList("x")), new ObjectMapper());
        assertEquals(BinarySerializer.TAG_SET, buffer.toByteArray()[0]);
    }

    @Test
    public void testBigDecimalPreservesScaleExactly() {
        // The wire form is toString(), so trailing zeros (and therefore scale) survive. Encoding via
        // doubleValue() would silently corrupt monetary amounts, which is the main use for this type.
        java.math.BigDecimal amount = new java.math.BigDecimal("1234.5000");
        Object result = roundTrip(amount);

        assertEquals(amount, result);
        assertEquals(4, ((java.math.BigDecimal) result).scale());
        assertEquals("1234.5000", result.toString());
        assertEquals(amount, roundTripGrowable(amount));
    }

    @Test
    public void testBigDecimalBeyondDoublePrecision() {
        java.math.BigDecimal precise = new java.math.BigDecimal("0.1234567890123456789012345678901234567890");
        assertEquals(precise, roundTrip(precise));
    }

    @Test
    public void testBigIntegerBeyondLongRange() {
        java.math.BigInteger huge = new java.math.BigInteger("123456789012345678901234567890");
        assertEquals(huge, roundTrip(huge));
        assertEquals(huge, roundTripGrowable(huge));
    }

    @Test
    public void testLocalDateRoundTrip() {
        java.time.LocalDate date = java.time.LocalDate.of(2026, 7, 25);
        assertEquals(date, roundTrip(date));
        assertEquals(date, roundTripGrowable(date));
        // Pre-epoch dates use a negative epoch day, so check the sign is carried.
        java.time.LocalDate old = java.time.LocalDate.of(1900, 1, 1);
        assertEquals(old, roundTrip(old));
    }

    @Test
    public void testLocalTimeKeepsNanoPrecision() {
        java.time.LocalTime time = java.time.LocalTime.of(23, 59, 59, 123456789);
        assertEquals(time, roundTrip(time));
        assertEquals(123456789, ((java.time.LocalTime) roundTrip(time)).getNano());
    }

    @Test
    public void testLocalDateTimeRoundTrip() {
        java.time.LocalDateTime dt = java.time.LocalDateTime.of(2024, 2, 29, 6, 15, 30, 500);
        assertEquals(dt, roundTrip(dt));
        assertEquals(dt, roundTripGrowable(dt));
    }

    @Test
    public void testDurationRoundTrip() {
        java.time.Duration d = java.time.Duration.ofSeconds(90, 250);
        assertEquals(d, roundTrip(d));
        // Negative durations must survive too.
        java.time.Duration negative = java.time.Duration.ofSeconds(-5);
        assertEquals(negative, roundTrip(negative));
    }

    @Test
    public void testOptionalPresentAndEmpty() {
        assertEquals(java.util.Optional.of("value"), roundTrip(java.util.Optional.of("value")));
        assertEquals(java.util.Optional.empty(), roundTrip(java.util.Optional.empty()));
        assertEquals(java.util.Optional.of(42), roundTripGrowable(java.util.Optional.of(42)));
    }

    @Test
    public void testPrimitiveArrayRoundTrips() {
        assertArrayEquals(new int[] {1, -2, Integer.MAX_VALUE}, (int[]) roundTrip(new int[] {1, -2, Integer.MAX_VALUE}));
        assertArrayEquals(new long[] {1L, Long.MIN_VALUE}, (long[]) roundTrip(new long[] {1L, Long.MIN_VALUE}));
        assertArrayEquals(new double[] {1.5, -0.25}, (double[]) roundTrip(new double[] {1.5, -0.25}));
        assertArrayEquals(new float[] {1.5f, -0.25f}, (float[]) roundTrip(new float[] {1.5f, -0.25f}));
        assertArrayEquals(new short[] {1, -2}, (short[]) roundTrip(new short[] {1, -2}));
        assertArrayEquals(new char[] {'a', 'Z'}, (char[]) roundTrip(new char[] {'a', 'Z'}));
        assertArrayEquals(new boolean[] {true, false, true}, (boolean[]) roundTrip(new boolean[] {true, false, true}));
    }

    @Test
    public void testEmptyPrimitiveArray() {
        assertArrayEquals(new int[0], (int[]) roundTrip(new int[0]));
        assertArrayEquals(new boolean[0], (boolean[]) roundTripGrowable(new boolean[0]));
    }

    @Test
    public void testByteArrayStillTakesItsOwnTag() {
        // byte[] predates the other array tags; make sure it was not captured by a later branch.
        GrowableBuffer buffer = new GrowableBuffer(8);
        BinarySerializer.writeValue(buffer, new byte[] {1, 2}, new ObjectMapper());
        assertEquals(BinarySerializer.TAG_BYTE_ARRAY, buffer.toByteArray()[0]);
    }

    @Test
    public void testTypesInsideCollections() {
        // Containers delegate per element, so the new tags must work nested as well as at top level.
        java.util.List<Object> mixed = java.util.Arrays.asList(
                new java.math.BigDecimal("9.99"),
                java.time.LocalDate.of(2026, 1, 1),
                java.time.Duration.ofMinutes(5));
        assertEquals(mixed, roundTrip(mixed));

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("price", new java.math.BigDecimal("19.95"));
        map.put("due", java.time.LocalDate.of(2026, 12, 31));
        assertEquals(map, roundTrip(map));
    }

    @Test
    public void testTagValuesAreStable() {
        // These are wire constants: changing one silently breaks every deployed client.
        assertEquals(0x12, BinarySerializer.TAG_SET);
        assertEquals(0x13, BinarySerializer.TAG_BIG_DECIMAL);
        assertEquals(0x14, BinarySerializer.TAG_BIG_INTEGER);
        assertEquals(0x15, BinarySerializer.TAG_LOCAL_DATE);
        assertEquals(0x16, BinarySerializer.TAG_LOCAL_TIME);
        assertEquals(0x17, BinarySerializer.TAG_LOCAL_DATE_TIME);
        assertEquals(0x18, BinarySerializer.TAG_DURATION);
        assertEquals(0x19, BinarySerializer.TAG_OPTIONAL);
        assertEquals(0x1A, BinarySerializer.TAG_INT_ARRAY);
        assertEquals(0x1B, BinarySerializer.TAG_LONG_ARRAY);
        assertEquals(0x1C, BinarySerializer.TAG_DOUBLE_ARRAY);
        assertEquals(0x1D, BinarySerializer.TAG_FLOAT_ARRAY);
        assertEquals(0x1E, BinarySerializer.TAG_SHORT_ARRAY);
        assertEquals(0x1F, BinarySerializer.TAG_CHAR_ARRAY);
        assertEquals(0x20, BinarySerializer.TAG_BOOLEAN_ARRAY);
    }

    @Test
    public void testMapOfUuidToInstant() {
        Map<UUID, Instant> map = new LinkedHashMap<>();
        map.put(UUID.randomUUID(), Instant.ofEpochSecond(1_000, 5));
        map.put(UUID.randomUUID(), Instant.ofEpochSecond(2_000, 750_000_000));

        @SuppressWarnings("unchecked")
        Map<Object, Object> read = (Map<Object, Object>) roundTrip(map);
        assertEquals(map, read);
    }

    @Test
    public void testNullEnumRemainsNull() {
        Priority nothing = null;
        // A null value is written as TAG_NULL by writeValue, so it comes back null.
        assertNull(roundTrip(nothing));
    }

    @Test
    public void testUnregisteredEnumTagThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> BinaryRegistry.resolveEnum("com.example.NotRegistered", "X"));
    }

    @Test
    public void testBackwardCompatibleOldPayload() {
        // Build a payload using only pre-0.3.0 tags (int, String, list of primitives).
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        ObjectMapper mapper = new ObjectMapper();
        BinarySerializer.writeValue(buffer, 7, mapper);
        BinarySerializer.writeValue(buffer, "legacy", mapper);
        BinarySerializer.writeValue(buffer, List.of(1, 2, 3), mapper);
        buffer.flip();

        assertEquals(7, BinarySerializer.readValue(buffer, mapper));
        assertEquals("legacy", BinarySerializer.readValue(buffer, mapper));
        assertEquals(List.of(1, 2, 3), BinarySerializer.readValue(buffer, mapper));
    }

    @Test
    public void testTagValuesUnchangedAndNewTagsAssigned() {
        // Existing tags must not shift; new tags occupy 0x0F–0x11.
        assertEquals(0x0E, BinarySerializer.TAG_REF);
        assertEquals(0x0F, BinarySerializer.TAG_UUID);
        assertEquals(0x10, BinarySerializer.TAG_INSTANT);
        assertEquals(0x11, BinarySerializer.TAG_ENUM);
    }

    private static Object roundTrip(Object value) {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        ObjectMapper mapper = new ObjectMapper();
        BinarySerializer.writeValue(buffer, value, mapper);
        buffer.flip();
        return BinarySerializer.readValue(buffer, mapper);
    }

    private static Object roundTripGrowable(Object value) {
        GrowableBuffer buffer = new GrowableBuffer(8);
        ObjectMapper mapper = new ObjectMapper();
        BinarySerializer.writeValue(buffer, value, mapper);
        ByteBuffer readBuf = ByteBuffer.wrap(buffer.toByteArray());
        return BinarySerializer.readValue(readBuf, mapper);
    }
}
