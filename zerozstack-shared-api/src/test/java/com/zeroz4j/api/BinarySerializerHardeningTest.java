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

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the reader cannot be made to allocate memory from a number a sender chose.
 *
 * <p>Every length and element count on the wire is four bytes the sender controls. Before this
 * hardening, an eight-byte message could ask for a two-gigabyte array and get it, because the
 * allocation happened first and the buffer underflow was noticed afterwards. These tests hand the
 * reader exactly those messages and require a refusal.</p>
 *
 * <p>Refusal alone is not the whole claim — a reader that allocates two gigabytes and then throws
 * has still been exhausted. {@link #refusingAHugeClaimAllocatesAlmostNothing()} measures the bytes
 * the thread actually allocated while refusing.</p>
 */
class BinarySerializerHardeningTest {

    /** A length no honest message carries, and the one that used to reserve about 2 GB. */
    private static final int ABSURD = 0x7FFFFFFF;

    // ---------------------------------------------------------------- strings

    @Test
    void aStringClaimingTwoBillionBytesInATenByteMessageIsRefused() {
        ByteBuffer attack = ByteBuffer.allocate(10);
        attack.put(BinarySerializer.TAG_STRING);
        attack.putInt(ABSURD);
        attack.put(new byte[5]);
        attack.flip();

        IllegalStateException refused =
                assertThrows(IllegalStateException.class, () -> read(attack));
        assertTrue(refused.getMessage().contains("string"), refused.getMessage());
    }

    @Test
    void aNegativeStringLengthIsRefusedWithAnExplanation() {
        // -1 means null and is legal. Anything else negative is a malformed or hostile stream, and
        // must not escape as a raw NegativeArraySizeException.
        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> BinarySerializer.readString(lengthPrefixed(-2)));
        assertTrue(refused.getMessage().contains("-2"), refused.getMessage());
    }

    @Test
    void aStringLengthOfMinusOneStillMeansNull() {
        org.junit.jupiter.api.Assertions.assertNull(BinarySerializer.readString(lengthPrefixed(-1)));
    }

    // ---------------------------------------------------------------- arrays

    @Test
    void everyArrayTagRefusesAnAbsurdLength() {
        for (byte tag : arrayTags()) {
            ByteBuffer attack = ByteBuffer.allocate(10);
            attack.put(tag);
            attack.putInt(ABSURD);
            attack.put(new byte[5]);
            attack.flip();

            assertThrows(IllegalStateException.class, () -> read(attack),
                    "tag 0x" + Integer.toHexString(tag & 0xFF) + " allocated from the wire length");
        }
    }

    @Test
    void everyArrayTagRefusesANegativeLength() {
        for (byte tag : arrayTags()) {
            ByteBuffer attack = ByteBuffer.allocate(10);
            attack.put(tag);
            attack.putInt(-7);
            attack.put(new byte[5]);
            attack.flip();

            IllegalStateException refused = assertThrows(IllegalStateException.class,
                    () -> read(attack),
                    "tag 0x" + Integer.toHexString(tag & 0xFF) + " let a negative length through");
            assertTrue(refused.getMessage().contains("negative"), refused.getMessage());
        }
    }

    @Test
    void aLongArrayCountEqualToTheBytesRemainingIsRefusedBecauseEachElementIsEight() {
        // This is the case a naive "length <= remaining bytes" check waves through: 64 elements do
        // fit in 64 bytes by that test, but a long[64] is 512 bytes. Without the element width, a
        // one-megabyte message still buys an eight-megabyte allocation.
        ByteBuffer attack = ByteBuffer.allocate(1 + 4 + 64);
        attack.put(BinarySerializer.TAG_LONG_ARRAY);
        attack.putInt(64);
        attack.put(new byte[64]);
        attack.flip();

        IllegalStateException refused =
                assertThrows(IllegalStateException.class, () -> read(attack));
        assertTrue(refused.getMessage().contains("long[]"), refused.getMessage());
    }

    @Test
    void anHonestLongArrayInTheSameSpaceIsStillRead() {
        // The mirror of the test above: 8 longs genuinely fit in those 64 bytes, so the width-aware
        // check must let them through rather than being merely strict.
        ByteBuffer honest = ByteBuffer.allocate(1 + 4 + 64);
        honest.put(BinarySerializer.TAG_LONG_ARRAY);
        honest.putInt(8);
        for (int i = 0; i < 8; i++) {
            honest.putLong(i);
        }
        honest.flip();

        long[] read = (long[]) read(honest);
        assertEquals(8, read.length);
        assertEquals(7L, read[7]);
    }

    @Test
    void refusingAHugeClaimAllocatesAlmostNothing() {
        java.lang.management.ThreadMXBean bean = java.lang.management.ManagementFactory.getThreadMXBean();
        org.junit.jupiter.api.Assumptions.assumeTrue(bean instanceof com.sun.management.ThreadMXBean,
                "per-thread allocation counting is a HotSpot extension");
        com.sun.management.ThreadMXBean hotspot = (com.sun.management.ThreadMXBean) bean;
        org.junit.jupiter.api.Assumptions.assumeTrue(hotspot.isThreadAllocatedMemorySupported());

        // Warm the classes and the exception path so the measurement below is the read itself.
        for (int i = 0; i < 50; i++) {
            assertThrows(IllegalStateException.class, () -> read(absurdByteArrayClaim()));
        }

        long before = hotspot.getCurrentThreadAllocatedBytes();
        for (int i = 0; i < 100; i++) {
            assertThrows(IllegalStateException.class, () -> read(absurdByteArrayClaim()));
        }
        long allocated = hotspot.getCurrentThreadAllocatedBytes() - before;

        // 100 refusals of a 2 GB claim. Anything remotely like the old behaviour is 200 GB; a
        // megabyte total is generous room for the exception objects and their messages.
        assertTrue(allocated < 1024 * 1024,
                "refusing 100 hostile messages allocated " + allocated + " bytes");
    }

    // ---------------------------------------------------------------- collections

    @Test
    void aListClaimingTwoBillionElementsIsRefusedUpFront() {
        ByteBuffer attack = ByteBuffer.allocate(10);
        attack.put(BinarySerializer.TAG_LIST);
        attack.putInt(ABSURD);
        attack.put(new byte[5]);
        attack.flip();

        IllegalStateException refused =
                assertThrows(IllegalStateException.class, () -> read(attack));
        assertTrue(refused.getMessage().contains("list"), refused.getMessage());
    }

    @Test
    void aSetAndAMapClaimingTwoBillionEntriesAreRefusedUpFront() {
        for (byte tag : new byte[] {BinarySerializer.TAG_SET, BinarySerializer.TAG_MAP}) {
            ByteBuffer attack = ByteBuffer.allocate(10);
            attack.put(tag);
            attack.putInt(ABSURD);
            attack.put(new byte[5]);
            attack.flip();

            assertThrows(IllegalStateException.class, () -> read(attack));
        }
    }

    @Test
    void aMapCountIsCheckedAtTwoBytesPerEntryBecauseAnEntryIsAKeyAndAValue() {
        // 40 bytes remain, so 40 single-byte tags could fit — but 40 entries need 80 at the very
        // least, and the reader must say so before it starts looping.
        ByteBuffer attack = ByteBuffer.allocate(1 + 4 + 40);
        attack.put(BinarySerializer.TAG_MAP);
        attack.putInt(40);
        attack.put(new byte[40]);
        attack.flip();

        IllegalStateException refused =
                assertThrows(IllegalStateException.class, () -> read(attack));
        assertTrue(refused.getMessage().contains("map"), refused.getMessage());
    }

    @Test
    void negativeCollectionCountsAreRefused() {
        for (byte tag : new byte[] {BinarySerializer.TAG_LIST, BinarySerializer.TAG_SET,
                BinarySerializer.TAG_MAP}) {
            ByteBuffer attack = ByteBuffer.allocate(10);
            attack.put(tag);
            attack.putInt(-3);
            attack.put(new byte[5]);
            attack.flip();

            IllegalStateException refused =
                    assertThrows(IllegalStateException.class, () -> read(attack));
            assertTrue(refused.getMessage().contains("negative"), refused.getMessage());
        }
    }

    // ---------------------------------------------------------------- nesting

    @Test
    void nestingDeeperThanTheCapIsRefusedRatherThanOverflowingTheStack() {
        ByteBuffer attack = nestedLists(BinarySerializer.MAX_NESTING_DEPTH + 50);

        IllegalStateException refused =
                assertThrows(IllegalStateException.class, () -> read(attack));
        assertTrue(refused.getMessage().contains("nests"), refused.getMessage());
    }

    @Test
    void nestingJustUnderTheCapIsStillRead() {
        // The cap has to be a cap on hostile input, not on a deep-but-honest object graph.
        Object read = read(nestedLists(BinarySerializer.MAX_NESTING_DEPTH - 2));
        assertTrue(read instanceof java.util.List);
    }

    @Test
    void aRefusedDeepMessageLeavesTheDepthCounterCleanForTheNextOne() {
        // The counter is per thread and reused, so an early return that skipped the decrement would
        // poison every later message on the same connection.
        assertThrows(IllegalStateException.class,
                () -> read(nestedLists(BinarySerializer.MAX_NESTING_DEPTH + 50)));

        assertEquals("still works", read(written("still works")));
        Object deep = read(nestedLists(BinarySerializer.MAX_NESTING_DEPTH - 2));
        assertTrue(deep instanceof java.util.List);
    }

    // ---------------------------------------------------------------- nothing legitimate broke

    @Test
    void aHundredThousandElementListStillRoundTrips() {
        java.util.List<Object> big = new java.util.ArrayList<>();
        for (int i = 0; i < 100_000; i++) {
            big.add(i);
        }

        @SuppressWarnings("unchecked")
        java.util.List<Object> back = (java.util.List<Object>) read(written(big));
        assertEquals(100_000, back.size());
        assertEquals(0, back.get(0));
        assertEquals(99_999, back.get(99_999));
    }

    @Test
    void aOneMegabyteByteArrayStillRoundTrips() {
        byte[] payload = new byte[1024 * 1024];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) i;
        }

        org.junit.jupiter.api.Assertions.assertArrayEquals(payload, (byte[]) read(written(payload)));
    }

    @Test
    void aLongStringStillRoundTrips() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 200_000; i++) {
            text.append('x');
        }

        assertEquals(text.toString(), read(written(text.toString())));
    }

    @Test
    void everySupportedTypeStillRoundTrips() {
        assertEquals(42, read(written(42)));
        assertEquals(42L, read(written(42L)));
        assertEquals(1.5d, read(written(1.5d)));
        assertEquals(1.5f, read(written(1.5f)));
        assertEquals(true, read(written(true)));
        assertEquals((short) 7, read(written((short) 7)));
        assertEquals((byte) 7, read(written((byte) 7)));
        assertEquals('q', read(written('q')));
        assertEquals("text", read(written("text")));
        org.junit.jupiter.api.Assertions.assertNull(read(written(null)));

        java.util.UUID id = java.util.UUID.randomUUID();
        assertEquals(id, read(written(id)));
        java.time.Instant now = java.time.Instant.ofEpochSecond(1_700_000_000L, 123);
        assertEquals(now, read(written(now)));
        assertEquals(new java.math.BigDecimal("1.2300"), read(written(new java.math.BigDecimal("1.2300"))));
        assertEquals(new java.math.BigInteger("90000000000000000000"),
                read(written(new java.math.BigInteger("90000000000000000000"))));
        assertEquals(java.time.LocalDate.of(2026, 8, 20), read(written(java.time.LocalDate.of(2026, 8, 20))));
        assertEquals(java.time.LocalTime.of(9, 30, 15), read(written(java.time.LocalTime.of(9, 30, 15))));
        assertEquals(java.time.LocalDateTime.of(2026, 8, 20, 9, 30),
                read(written(java.time.LocalDateTime.of(2026, 8, 20, 9, 30))));
        assertEquals(java.time.Duration.ofSeconds(90, 5), read(written(java.time.Duration.ofSeconds(90, 5))));
        assertEquals(java.util.Optional.of("here"), read(written(java.util.Optional.of("here"))));
        assertEquals(java.util.Optional.empty(), read(written(java.util.Optional.empty())));

        assertEquals(java.util.List.of(1, "two", 3.0), read(written(java.util.List.of(1, "two", 3.0))));
        assertEquals(new java.util.LinkedHashSet<>(java.util.List.of("a", "b")),
                read(written(new java.util.LinkedHashSet<>(java.util.List.of("a", "b")))));
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("one", 1);
        map.put("nested", java.util.List.of("x"));
        assertEquals(map, read(written(map)));

        org.junit.jupiter.api.Assertions.assertArrayEquals(new int[] {1, -2, Integer.MAX_VALUE},
                (int[]) read(written(new int[] {1, -2, Integer.MAX_VALUE})));
        org.junit.jupiter.api.Assertions.assertArrayEquals(new long[] {1L, Long.MIN_VALUE},
                (long[]) read(written(new long[] {1L, Long.MIN_VALUE})));
        org.junit.jupiter.api.Assertions.assertArrayEquals(new double[] {1.5, -0.25},
                (double[]) read(written(new double[] {1.5, -0.25})));
        org.junit.jupiter.api.Assertions.assertArrayEquals(new float[] {1.5f, -0.25f},
                (float[]) read(written(new float[] {1.5f, -0.25f})));
        org.junit.jupiter.api.Assertions.assertArrayEquals(new short[] {1, -2},
                (short[]) read(written(new short[] {1, -2})));
        org.junit.jupiter.api.Assertions.assertArrayEquals(new char[] {'a', 'Z'},
                (char[]) read(written(new char[] {'a', 'Z'})));
        org.junit.jupiter.api.Assertions.assertArrayEquals(new boolean[] {true, false, true},
                (boolean[]) read(written(new boolean[] {true, false, true})));
        org.junit.jupiter.api.Assertions.assertArrayEquals(new byte[] {1, 2, 3},
                (byte[]) read(written(new byte[] {1, 2, 3})));
    }

    @Test
    void emptyCollectionsAndArraysStillRoundTrip() {
        // Zero is a legal count and must not be caught by a check aimed at absurd ones.
        assertEquals(java.util.List.of(), read(written(new java.util.ArrayList<>())));
        assertEquals(java.util.Map.of(), read(written(new java.util.LinkedHashMap<>())));
        org.junit.jupiter.api.Assertions.assertArrayEquals(new int[0], (int[]) read(written(new int[0])));
        org.junit.jupiter.api.Assertions.assertArrayEquals(new byte[0], (byte[]) read(written(new byte[0])));
        assertEquals("", read(written("")));
    }

    // ---------------------------------------------------------------- helpers

    private static byte[] arrayTags() {
        return new byte[] {
                BinarySerializer.TAG_BYTE_ARRAY,
                BinarySerializer.TAG_BOOLEAN_ARRAY,
                BinarySerializer.TAG_SHORT_ARRAY,
                BinarySerializer.TAG_CHAR_ARRAY,
                BinarySerializer.TAG_INT_ARRAY,
                BinarySerializer.TAG_FLOAT_ARRAY,
                BinarySerializer.TAG_LONG_ARRAY,
                BinarySerializer.TAG_DOUBLE_ARRAY,
        };
    }

    private static ByteBuffer absurdByteArrayClaim() {
        ByteBuffer attack = ByteBuffer.allocate(10);
        attack.put(BinarySerializer.TAG_BYTE_ARRAY);
        attack.putInt(ABSURD);
        attack.put(new byte[5]);
        attack.flip();
        return attack;
    }

    /** A four-byte length header with nothing behind it, for exercising {@code readString}. */
    private static ByteBuffer lengthPrefixed(int length) {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(length);
        buffer.flip();
        return buffer;
    }

    /** {@code levels} single-element lists, one inside the next, ending in a null. */
    private static ByteBuffer nestedLists(int levels) {
        ByteBuffer buffer = ByteBuffer.allocate(levels * 5 + 1);
        for (int i = 0; i < levels; i++) {
            buffer.put(BinarySerializer.TAG_LIST);
            buffer.putInt(1);
        }
        buffer.put(BinarySerializer.TAG_NULL);
        buffer.flip();
        return buffer;
    }

    private static ByteBuffer written(Object value) {
        GrowableBuffer out = new GrowableBuffer();
        BinarySerializer.writeValue(out, value, new ObjectMapper());
        return ByteBuffer.wrap(out.toByteArray());
    }

    private static Object read(ByteBuffer buffer) {
        return BinarySerializer.readValue(buffer, new ObjectMapper());
    }
}
