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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which objects get a lasting name, and what it costs to keep one.
 *
 * <h2>What this protects</h2>
 *
 * <p>Before 0.8.0 every object that had ever been written to a client was put in the handle registry
 * and never taken out again, on both tiers. A screen that redraws itself builds fresh objects each
 * time, so a browser tab left open collected millions of them; the objects could never be collected,
 * and after a dropped connection the tab tried to send the whole list back to the server in one
 * message far larger than a connection accepts. Two things now stop that, and both are pinned
 * here.</p>
 *
 * <ul>
 *   <li><b>Only what needs a name gets one.</b> A name exists so a later message can mean the same
 *       object again — a client edit coming back, a re-sync after a reconnect, a lock. That is a
 *       {@code @LiveSync} model and the objects inside one. Everything else travels with a name good
 *       for its own message and never reaches the registry.</li>
 *   <li><b>A name does not keep an object alive.</b> The registry holds what it names weakly, so an
 *       entry disappears once the application itself has let go.</li>
 * </ul>
 *
 * <p>What must not break in the process: the same instance appearing twice inside one value still
 * arrives once, and a loop still arrives as a loop.</p>
 */
public class HandleEconomyTest {

    /** An ordinary value: returned from a call, drawn, and forgotten. */
    public static class Row {
        private String label;
        private Tag tag;
        public Row() { }
        public Row(String label, Tag tag) { this.label = label; this.tag = tag; }
        public String getLabel() { return label; }
        public void setLabel(String v) { label = v; }
        public Tag getTag() { return tag; }
        public void setTag(Tag v) { tag = v; }
    }

    /** Nested inside {@link Row} and inside {@link Board}. */
    public static class Tag {
        private String name;
        public Tag() { }
        public Tag(String name) { this.name = name; }
        public String getName() { return name; }
        public void setName(String v) { name = v; }
    }

    /** Stands in for a {@code @LiveSync} model: edited in place and re-read by name. */
    public static class Board {
        private String title;
        private Tag tag;
        private Board partner;
        public Board() { }
        public Board(String title) { this.title = title; }
        public String getTitle() { return title; }
        public void setTitle(String v) { title = v; }
        public Tag getTag() { return tag; }
        public void setTag(Tag v) { tag = v; }
        public Board getPartner() { return partner; }
        public void setPartner(Board v) { partner = v; }
    }

    @BeforeAll
    public static void register() {
        BinaryRegistry.register(Tag.class.getName(), Tag::new,
                new BinarySerializerDelegate<Tag>() {
                    @Override public void write(Tag o, GrowableBuffer b, ObjectMapper m) {
                        BinarySerializer.writeString(b, o.getName());
                    }
                    @Override public void read(Tag o, ByteBuffer b, ObjectMapper m) {
                        o.setName(BinarySerializer.readString(b));
                    }
                });
        BinaryRegistry.register(Row.class.getName(), Row::new,
                new BinarySerializerDelegate<Row>() {
                    @Override public void write(Row o, GrowableBuffer b, ObjectMapper m) {
                        BinarySerializer.writeString(b, o.getLabel());
                        BinarySerializer.writeValue(b, o.getTag(), m);
                    }
                    @Override public void read(Row o, ByteBuffer b, ObjectMapper m) {
                        o.setLabel(BinarySerializer.readString(b));
                        o.setTag((Tag) BinarySerializer.readValue(b, m));
                    }
                });
        BinaryRegistry.register(Board.class.getName(), Board::new,
                new BinarySerializerDelegate<Board>() {
                    @Override public void write(Board o, GrowableBuffer b, ObjectMapper m) {
                        BinarySerializer.writeString(b, o.getTitle());
                        BinarySerializer.writeValue(b, o.getTag(), m);
                        BinarySerializer.writeValue(b, o.getPartner(), m);
                    }
                    @Override public void read(Board o, ByteBuffer b, ObjectMapper m) {
                        o.setTitle(BinarySerializer.readString(b));
                        o.setTag((Tag) BinarySerializer.readValue(b, m));
                        o.setPartner((Board) BinarySerializer.readValue(b, m));
                    }
                });
        // What the generated registrar marks for a @LiveSync model, and nothing else.
        BinaryRegistry.registerHandleBearing(Board.class.getName());
    }

    private static byte[] write(Object value, ObjectMapper mapper) {
        GrowableBuffer buffer = new GrowableBuffer();
        BinarySerializer.writeValue(buffer, value, mapper);
        return buffer.toByteArray();
    }

    private static Object read(byte[] bytes, ObjectMapper mapper) {
        return BinarySerializer.readValue(ByteBuffer.wrap(bytes), mapper);
    }

    // ------------------------------------------------------------------ what gets a name

    @Test
    @DisplayName("an ordinary value never reaches the registry, on either side")
    public void anOrdinaryValueIsNotRegistered() {
        ObjectMapper sender = new ObjectMapper();
        ObjectMapper receiver = new ObjectMapper();

        Row row = new Row("first", new Tag("urgent"));
        Row arrived = (Row) read(write(row, sender), receiver);

        assertEquals("first", arrived.getLabel());
        assertEquals("urgent", arrived.getTag().getName());
        assertEquals(0, sender.size(), "the sender registered nothing");
        assertEquals(0, receiver.size(), "and neither did the receiver");
        assertNull(sender.getId(row), "so the value has no lasting name");
    }

    @Test
    @DisplayName("redrawing a screen a thousand times leaves the registry empty")
    public void redrawingDoesNotAccumulate() {
        ObjectMapper sender = new ObjectMapper();
        for (int redraw = 0; redraw < 1000; redraw++) {
            List<Row> screen = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                screen.add(new Row("row " + i, new Tag("t" + i)));
            }
            write(screen, sender);
        }
        assertEquals(0, sender.size(),
                "20,000 objects went out and not one of them needed a name that outlives its message");
        assertTrue(sender.ids().isEmpty(), "so a reconnect would ask for nothing");
    }

    @Test
    @DisplayName("a live model does get a lasting name, and so does everything inside it")
    public void aLiveModelAndItsContentsAreRegistered() {
        ObjectMapper sender = new ObjectMapper();
        Board board = new Board("Roadmap");
        board.setTag(new Tag("q3"));

        write(board, sender);

        assertEquals(2, sender.size(),
                "the board itself, and the tag inside it - a client edit comes back as one whole "
                        + "graph and is applied part by part, so the parts need names too");
        assertTrue(sender.getId(board) != null, "the board has one");
        assertTrue(sender.getId(board.getTag()) != null, "and so does the tag it contains");
    }

    @Test
    @DisplayName("a live model's name is stable, so a second send names the same object")
    public void aLiveModelKeepsItsName() {
        ObjectMapper sender = new ObjectMapper();
        Board board = new Board("Roadmap");

        write(board, sender);
        String first = sender.getId(board);
        write(board, sender);

        assertEquals(first, sender.getId(board), "the same object keeps the same name");
        assertEquals(1, sender.size(), "and sending it twice does not make two entries");
    }

    // ------------------------------------------------------------------ what must not break

    @Test
    @DisplayName("the same instance twice inside one ordinary value still arrives once")
    public void sharedReferencesInsideOneValueSurvive() {
        // Nothing here is a live model, so neither tag reference has a lasting name. The shape must
        // still survive: this is what the message's own names are for.
        Tag shared = new Tag("shared");
        List<Row> pair = new ArrayList<>();
        pair.add(new Row("only", shared));
        Row outer = new Row("outer", shared);

        ObjectMapper sender = new ObjectMapper();
        ObjectMapper receiver = new ObjectMapper();

        Board holder = new Board("holder");
        holder.setTag(shared);
        Board partner = new Board("partner");
        partner.setTag(shared);
        holder.setPartner(partner);

        Board arrived = (Board) read(write(holder, sender), receiver);

        assertSame(arrived.getTag(), arrived.getPartner().getTag(),
                "one instance went out, one instance must arrive");
        assertEquals("shared", arrived.getTag().getName());
        assertEquals("outer", outer.getLabel());
        assertEquals(1, pair.size());
    }

    @Test
    @DisplayName("a loop still arrives as a loop")
    public void cyclesStillWork() {
        Board left = new Board("left");
        Board right = new Board("right");
        left.setPartner(right);
        right.setPartner(left);

        ObjectMapper sender = new ObjectMapper();
        ObjectMapper receiver = new ObjectMapper();
        Board arrived = (Board) read(write(left, sender), receiver);

        assertEquals("left", arrived.getTitle());
        assertEquals("right", arrived.getPartner().getTitle());
        assertSame(arrived, arrived.getPartner().getPartner(), "the loop closed");
    }

    @Test
    @DisplayName("a loop through ordinary values, which have no lasting name, arrives as a loop too")
    public void cyclesWithoutLastingNamesStillWork() {
        // Two ordinary rows sharing one tag, inside a list. Nothing here is registered.
        Tag tag = new Tag("t");
        Row a = new Row("a", tag);
        Row b = new Row("b", tag);
        List<Row> both = new ArrayList<>();
        both.add(a);
        both.add(b);

        ObjectMapper sender = new ObjectMapper();
        ObjectMapper receiver = new ObjectMapper();
        @SuppressWarnings("unchecked")
        List<Row> arrived = (List<Row>) read(write(both, sender), receiver);

        assertEquals(0, sender.size(), "still nothing registered");
        assertNotSame(arrived.get(0).getTag(), arrived.get(1).getTag(),
                "each element of a top-level list is its own value, and identity does not hold "
                        + "between two of them - the documented rule, unchanged");
    }

    @Test
    @DisplayName("two separate messages give an ordinary value two separate arrivals")
    public void namesDoNotLeakBetweenMessages() {
        ObjectMapper sender = new ObjectMapper();
        ObjectMapper receiver = new ObjectMapper();

        Row first = (Row) read(write(new Row("one", new Tag("x")), sender), receiver);
        Row second = (Row) read(write(new Row("two", new Tag("y")), sender), receiver);

        assertEquals("one", first.getLabel(), "the first message is not overwritten by the second");
        assertEquals("two", second.getLabel());
        assertNotSame(first, second, "a name good for one message must not match across two");
    }

    // ------------------------------------------------------------------ weak holding

    @Test
    @DisplayName("the registry does not keep a live object alive by itself")
    public void aRegisteredObjectIsCollectedOnceTheApplicationLetsGo() {
        ObjectMapper mapper = new ObjectMapper();
        for (int i = 0; i < 5000; i++) {
            mapper.register(new Board("board " + i));
        }
        assertEquals(5000, mapper.size(), "all of them are named while they are held");

        Board kept = new Board("kept");
        String keptId = mapper.register(kept);

        int remaining = 5001;
        for (int attempt = 0; attempt < 20 && remaining > 1; attempt++) {
            System.gc();
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            remaining = mapper.size();
        }

        assertEquals(1, remaining,
                "everything the test stopped referring to is gone; only the one still held remains");
        assertSame(kept, mapper.getObject(keptId), "and the one still held is still there");
        assertEquals(List.of(keptId), mapper.ids(),
                "so a reconnect asks the server for exactly what is still in use");
    }
}
