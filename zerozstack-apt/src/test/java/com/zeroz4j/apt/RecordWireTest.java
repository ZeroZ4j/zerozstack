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
package com.zeroz4j.apt;

import com.zeroz4j.api.BinarySerializer;
import com.zeroz4j.api.GrowableBuffer;
import com.zeroz4j.api.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A record annotated {@code @DataModel} travels on the wire.
 *
 * <p>The framework's older rule — a public no-argument constructor plus getters and setters —
 * exists because the generated reader made an empty instance and then filled it. A record cannot be
 * filled: its components are final and only its canonical constructor sets them. So the generated
 * reader gathers every component first and constructs last, and these tests are what say that
 * actually works, end to end, through the same processor and the same wire format the framework
 * uses at run time.</p>
 */
class RecordWireTest {

    @Test
    void aSimpleRecordMakesTheRoundTrip(@TempDir Path tempDir) throws Exception {
        GeneratedWire wire = GeneratedWire.compileAndRegister(tempDir, GeneratedWire.sources(
                "rec.simple.Money",
                "package rec.simple;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "@DataModel\n"
                + "public record Money(long amount, String currency, boolean settled) {}\n"));

        Object sent = wire.make("rec.simple.Money", 1250L, "EUR", true);
        Object back = GeneratedWire.roundTrip(sent);

        assertEquals(sent, back, "a record round-trips by value");
        assertNotSame(sent, back);
        assertEquals(1250L, GeneratedWire.read(back, "amount"));
        assertEquals("EUR", GeneratedWire.read(back, "currency"));
        assertEquals(true, GeneratedWire.read(back, "settled"));
    }

    @Test
    void everySupportedComponentTypeSurvives(@TempDir Path tempDir) throws Exception {
        GeneratedWire wire = GeneratedWire.compileAndRegister(tempDir, GeneratedWire.sources(
                "rec.wide.Wide",
                "package rec.wide;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "import java.math.BigDecimal;\n"
                + "import java.time.Duration;\n"
                + "import java.time.Instant;\n"
                + "import java.time.LocalDate;\n"
                + "import java.util.Optional;\n"
                + "import java.util.UUID;\n"
                + "@DataModel\n"
                + "public record Wide(int i, long l, double d, float f, boolean b, short s,\n"
                + "                   byte by, char c, String text, UUID id, Instant at,\n"
                + "                   LocalDate day, Duration span, BigDecimal money,\n"
                + "                   Optional<String> maybe, int[] numbers, Colour colour) {}\n",
                "rec.wide.Colour",
                "package rec.wide;\n"
                + "public enum Colour { RED, GREEN }\n"));

        java.util.UUID id = java.util.UUID.randomUUID();
        java.time.Instant at = java.time.Instant.ofEpochSecond(1_700_000_000L, 123_456_789);
        Object sent = wire.make("rec.wide.Wide", 7, 8L, 9.5d, 1.5f, true, (short) 3, (byte) 4, 'z',
                "hello", id, at, java.time.LocalDate.of(2026, 8, 28),
                java.time.Duration.ofSeconds(90, 5), new java.math.BigDecimal("12.3400"),
                java.util.Optional.of("here"), new int[] { 1, 2, 3 },
                Enum.valueOf(wire.type("rec.wide.Colour").asSubclass(Enum.class), "GREEN"));

        Object back = GeneratedWire.roundTrip(sent);

        assertEquals(7, GeneratedWire.read(back, "i"));
        assertEquals('z', GeneratedWire.read(back, "c"));
        assertEquals(id, GeneratedWire.read(back, "id"));
        assertEquals(at, GeneratedWire.read(back, "at"));
        assertEquals(java.time.LocalDate.of(2026, 8, 28), GeneratedWire.read(back, "day"));
        assertEquals(java.time.Duration.ofSeconds(90, 5), GeneratedWire.read(back, "span"));
        assertEquals("12.3400", GeneratedWire.read(back, "money").toString(),
                "scale survives, as it does for a class");
        assertEquals(java.util.Optional.of("here"), GeneratedWire.read(back, "maybe"));
        assertArrayEqualsInt(new int[] { 1, 2, 3 }, (int[]) GeneratedWire.read(back, "numbers"));
        assertEquals("GREEN", GeneratedWire.read(back, "colour").toString());
    }

    @Test
    void aRecordHoldingCollectionsAndAnotherRecord(@TempDir Path tempDir) throws Exception {
        GeneratedWire wire = GeneratedWire.compileAndRegister(tempDir, GeneratedWire.sources(
                "rec.nested.Line",
                "package rec.nested;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "@DataModel\n"
                + "public record Line(String sku, int quantity) {}\n",
                "rec.nested.Order",
                "package rec.nested;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "import java.util.List;\n"
                + "import java.util.Map;\n"
                + "import java.util.Set;\n"
                + "@DataModel\n"
                + "public record Order(String reference, Line first, List<Line> lines,\n"
                + "                    Set<String> tags, Map<String, Integer> counts) {}\n"));

        Object first = wire.make("rec.nested.Line", "A-1", 2);
        Object second = wire.make("rec.nested.Line", "B-2", 5);
        List<Object> lines = new ArrayList<>(Arrays.asList(first, second));
        java.util.Set<String> tags = new java.util.LinkedHashSet<>(Arrays.asList("rush", "gift"));
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("boxes", 3);

        Object sent = wire.make("rec.nested.Order", "R-9", first, lines, tags, counts);
        Object back = GeneratedWire.roundTrip(sent);

        assertEquals("R-9", GeneratedWire.read(back, "reference"));
        assertEquals(first, GeneratedWire.read(back, "first"));
        assertEquals(lines, GeneratedWire.read(back, "lines"));
        assertEquals(tags, GeneratedWire.read(back, "tags"));
        assertEquals(counts, GeneratedWire.read(back, "counts"));
        assertEquals(sent, back);
    }

    @Test
    void theSameRecordTwiceArrivesAsOneValue(@TempDir Path tempDir) throws Exception {
        // The second appearance travels as a back-reference. By the time the reader meets it the
        // record has been built, so it resolves — which is the case a cycle is not.
        GeneratedWire wire = GeneratedWire.compileAndRegister(tempDir, GeneratedWire.sources(
                "rec.shared.Tag",
                "package rec.shared;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "@DataModel\n"
                + "public record Tag(String label) {}\n",
                "rec.shared.Pair",
                "package rec.shared;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "import java.util.List;\n"
                + "@DataModel\n"
                + "public record Pair(Tag first, Tag second, List<Tag> all) {}\n"));

        Object tag = wire.make("rec.shared.Tag", "urgent");
        Object pair = wire.make("rec.shared.Pair", tag, tag,
                new ArrayList<>(Arrays.asList(tag)));

        Object back = GeneratedWire.roundTrip(pair);

        Object first = GeneratedWire.read(back, "first");
        assertEquals(tag, first);
        assertSame(first, GeneratedWire.read(back, "second"), "one value, referenced twice");
        @SuppressWarnings("unchecked")
        List<Object> all = (List<Object>) GeneratedWire.read(back, "all");
        assertSame(first, all.get(0), "and again from inside a collection");
    }

    @Test
    void aRecordInsideAClassAndAClassInsideARecord(@TempDir Path tempDir) throws Exception {
        GeneratedWire wire = GeneratedWire.compileAndRegister(tempDir, GeneratedWire.sources(
                "rec.mixed.Address",
                "package rec.mixed;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "@DataModel\n"
                + "public record Address(String street, String city) {}\n",
                "rec.mixed.Customer",
                "package rec.mixed;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "@DataModel\n"
                + "public class Customer {\n"
                + "    private String name;\n"
                + "    private Address address;\n"
                + "    public Customer() {}\n"
                + "    public String getName() { return name; }\n"
                + "    public void setName(String name) { this.name = name; }\n"
                + "    public Address getAddress() { return address; }\n"
                + "    public void setAddress(Address address) { this.address = address; }\n"
                + "}\n",
                "rec.mixed.Delivery",
                "package rec.mixed;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "@DataModel\n"
                + "public record Delivery(Customer customer, Address to) {}\n"));

        Object address = wire.make("rec.mixed.Address", "1 High Street", "Bath");
        Object customer = wire.make("rec.mixed.Customer");
        customer.getClass().getMethod("setName", String.class).invoke(customer, "Ada");
        customer.getClass().getMethod("setAddress", wire.type("rec.mixed.Address"))
                .invoke(customer, address);

        Object backCustomer = GeneratedWire.roundTrip(customer);
        assertEquals("Ada", GeneratedWire.read(backCustomer, "getName"));
        assertEquals(address, GeneratedWire.read(backCustomer, "getAddress"),
                "a record sitting in an ordinary model");

        Object delivery = wire.make("rec.mixed.Delivery", customer, address);
        Object backDelivery = GeneratedWire.roundTrip(delivery);
        assertEquals(address, GeneratedWire.read(backDelivery, "to"));
        assertEquals("Ada",
                GeneratedWire.read(GeneratedWire.read(backDelivery, "customer"), "getName"),
                "an ordinary model sitting in a record");
    }

    @Test
    void nullComponentsStayNull(@TempDir Path tempDir) throws Exception {
        GeneratedWire wire = GeneratedWire.compileAndRegister(tempDir, GeneratedWire.sources(
                "rec.nulls.Inner",
                "package rec.nulls;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "@DataModel\n"
                + "public record Inner(String v) {}\n",
                "rec.nulls.Holder",
                "package rec.nulls;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "import java.util.List;\n"
                + "@DataModel\n"
                + "public record Holder(String text, Inner inner, List<String> items) {}\n"));

        Object sent = wire.make("rec.nulls.Holder", null, null, null);
        Object back = GeneratedWire.roundTrip(sent);

        assertNull(GeneratedWire.read(back, "text"));
        assertNull(GeneratedWire.read(back, "inner"));
        assertNull(GeneratedWire.read(back, "items"));
    }

    @Test
    void aRecordInALoopIsRefusedWhenItIsSent(@TempDir Path tempDir) throws Exception {
        // The decision: a record cannot take part in a reference cycle, and saying so is better
        // than the alternatives. The receiver cannot build the record until every component has
        // been read, and a component that points back at the record needs it to already exist.
        GeneratedWire wire = GeneratedWire.compileAndRegister(tempDir, GeneratedWire.sources(
                "rec.loop.Node",
                "package rec.loop;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "@DataModel\n"
                + "public class Node {\n"
                + "    private Ring ring;\n"
                + "    public Node() {}\n"
                + "    public Ring getRing() { return ring; }\n"
                + "    public void setRing(Ring ring) { this.ring = ring; }\n"
                + "}\n",
                "rec.loop.Ring",
                "package rec.loop;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "import java.util.List;\n"
                + "@DataModel\n"
                + "public record Ring(List<Node> nodes) {}\n"));

        List<Object> nodes = new ArrayList<>();
        Object ring = wire.make("rec.loop.Ring", nodes);
        Object node = wire.make("rec.loop.Node");
        node.getClass().getMethod("setRing", wire.type("rec.loop.Ring")).invoke(node, ring);
        nodes.add(node);

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> GeneratedWire.roundTrip(ring));

        assertTrue(refused.getMessage().contains("rec.loop.Ring"), refused.getMessage());
        assertTrue(refused.getMessage().contains("points back at itself"), refused.getMessage());
        assertTrue(refused.getMessage().contains("Use a class"),
                "the message must say what to do instead: " + refused.getMessage());
    }

    @Test
    void aLoopedRecordInAHandBuiltPayloadIsRefusedWhenItIsRead(@TempDir Path tempDir)
            throws Exception {
        // The writer above refuses to make such a payload. Nothing stops somebody else sending one,
        // so the reader refuses it too rather than quietly handing back a null.
        GeneratedWire wire = GeneratedWire.compileAndRegister(tempDir, GeneratedWire.sources(
                "rec.loopread.Node",
                "package rec.loopread;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "@DataModel\n"
                + "public class Node {\n"
                + "    private Ring ring;\n"
                + "    public Node() {}\n"
                + "    public Ring getRing() { return ring; }\n"
                + "    public void setRing(Ring ring) { this.ring = ring; }\n"
                + "}\n",
                "rec.loopread.Ring",
                "package rec.loopread;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "import java.util.List;\n"
                + "@DataModel\n"
                + "public record Ring(List<Node> nodes) {}\n"));
        assertNotNullType(wire, "rec.loopread.Ring");

        GrowableBuffer out = new GrowableBuffer();
        out.put(BinarySerializer.TAG_RECORD);
        BinarySerializer.writeString(out, "ring-1");
        BinarySerializer.writeString(out, "rec.loopread.Ring");
        out.put((byte) 1);                                  // the record is present
        out.put(BinarySerializer.TAG_LIST);
        out.putInt(1);
        out.put(BinarySerializer.TAG_OBJECT);
        BinarySerializer.writeString(out, "node-1");
        BinarySerializer.writeString(out, "rec.loopread.Node");
        out.put((byte) 1);                                  // the node is present
        out.put(BinarySerializer.TAG_REF);
        BinarySerializer.writeString(out, "ring-1");        // back into the unfinished record

        ByteBuffer in = ByteBuffer.wrap(out.toByteArray());
        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> BinarySerializer.readValue(in, new ObjectMapper()));

        assertTrue(refused.getMessage().contains("still being read"), refused.getMessage());
    }

    @Test
    void liveSyncOnARecordIsRefusedAtCompileTime(@TempDir Path tempDir) throws Exception {
        List<String> errors = GeneratedWire.errorsFrom(tempDir, GeneratedWire.sources(
                "rec.live.Counter",
                "package rec.live;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "import com.zeroz4j.api.LiveSync;\n"
                + "@DataModel\n"
                + "@LiveSync\n"
                + "public record Counter(int value) {}\n"));

        String joined = String.join("\n", errors);
        assertTrue(joined.contains("@LiveSync"), joined);
        assertTrue(joined.contains("Use a class"), joined);
    }

    private static void assertArrayEqualsInt(int[] expected, int[] actual) {
        assertEquals(Arrays.toString(expected), Arrays.toString(actual));
    }

    private static void assertNotNullType(GeneratedWire wire, String fqcn) throws Exception {
        assertTrue(wire.type(fqcn) != null);
    }
}
