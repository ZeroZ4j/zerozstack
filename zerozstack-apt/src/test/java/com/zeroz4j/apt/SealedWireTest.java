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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A sealed interface or sealed abstract class travels as itself: "this value is one of a known
 * set". Applications fake that today with a type field and a cast.
 *
 * <p>{@code sealed} is what makes it safe. The complete list of classes the value may be is fixed
 * when the code is compiled, so the annotation processor can write that list into the generated
 * registrar and the reader can turn away any other name <i>before</i> it builds anything.</p>
 */
class SealedWireTest {

    /** A message family: two records and one ordinary class, all under one sealed interface. */
    private static java.util.Map<String, String> messageFamily(String pkg) {
        return GeneratedWire.sources(
                pkg + ".Message",
                "package " + pkg + ";\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "@DataModel\n"
                + "public sealed interface Message permits Text, Ping, Attachment {}\n",
                pkg + ".Text",
                "package " + pkg + ";\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "@DataModel\n"
                + "public record Text(String author, String body) implements Message {}\n",
                pkg + ".Ping",
                "package " + pkg + ";\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "@DataModel\n"
                + "public record Ping(long sentAt) implements Message {}\n",
                pkg + ".Attachment",
                "package " + pkg + ";\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "@DataModel\n"
                + "public final class Attachment implements Message {\n"
                + "    private String fileName;\n"
                + "    public Attachment() {}\n"
                + "    public String getFileName() { return fileName; }\n"
                + "    public void setFileName(String fileName) { this.fileName = fileName; }\n"
                + "}\n",
                pkg + ".Envelope",
                "package " + pkg + ";\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "import java.util.List;\n"
                + "@DataModel\n"
                + "public record Envelope(Message head, List<Message> rest) {}\n");
    }

    @Test
    void everyMemberOfTheFamilyTravelsAsTheInterface(@TempDir Path tempDir) throws Exception {
        GeneratedWire wire =
                GeneratedWire.compileAndRegister(tempDir, messageFamily("sealed.family"));

        Object text = wire.make("sealed.family.Text", "Ada", "hello");
        Object ping = wire.make("sealed.family.Ping", 99L);
        Object attachment = wire.make("sealed.family.Attachment");
        attachment.getClass().getMethod("setFileName", String.class)
                .invoke(attachment, "notes.txt");

        Object backText = GeneratedWire.roundTrip(text);
        assertEquals(wire.type("sealed.family.Text"), backText.getClass());
        assertEquals("hello", GeneratedWire.read(backText, "body"));

        Object backPing = GeneratedWire.roundTrip(ping);
        assertEquals(wire.type("sealed.family.Ping"), backPing.getClass());
        assertEquals(99L, GeneratedWire.read(backPing, "sentAt"));

        Object backAttachment = GeneratedWire.roundTrip(attachment);
        assertEquals(wire.type("sealed.family.Attachment"), backAttachment.getClass());
        assertEquals("notes.txt", GeneratedWire.read(backAttachment, "getFileName"));
    }

    @Test
    void aFieldDeclaredAsTheSealedTypeKeepsItsConcreteType(@TempDir Path tempDir) throws Exception {
        GeneratedWire wire =
                GeneratedWire.compileAndRegister(tempDir, messageFamily("sealed.field"));

        Object text = wire.make("sealed.field.Text", "Ada", "hello");
        Object envelope = wire.make("sealed.field.Envelope", text, new ArrayList<>());

        Object back = GeneratedWire.roundTrip(envelope);
        Object head = GeneratedWire.read(back, "head");

        assertEquals(wire.type("sealed.field.Text"), head.getClass());
        assertEquals("Ada", GeneratedWire.read(head, "author"));
    }

    @Test
    void aMixedFamilyInsideACollection(@TempDir Path tempDir) throws Exception {
        GeneratedWire wire =
                GeneratedWire.compileAndRegister(tempDir, messageFamily("sealed.collection"));

        Object text = wire.make("sealed.collection.Text", "Ada", "hello");
        Object ping = wire.make("sealed.collection.Ping", 7L);
        Object attachment = wire.make("sealed.collection.Attachment");
        attachment.getClass().getMethod("setFileName", String.class).invoke(attachment, "a.pdf");

        Object envelope = wire.make("sealed.collection.Envelope", ping,
                new ArrayList<>(Arrays.asList(text, ping, attachment)));

        Object back = GeneratedWire.roundTrip(envelope);
        @SuppressWarnings("unchecked")
        List<Object> rest = (List<Object>) GeneratedWire.read(back, "rest");

        assertEquals(3, rest.size());
        assertEquals(wire.type("sealed.collection.Text"), rest.get(0).getClass());
        assertEquals(wire.type("sealed.collection.Ping"), rest.get(1).getClass());
        assertEquals(wire.type("sealed.collection.Attachment"), rest.get(2).getClass());
        assertEquals("hello", GeneratedWire.read(rest.get(0), "body"));
        assertEquals("a.pdf", GeneratedWire.read(rest.get(2), "getFileName"));
    }

    @Test
    void aNullOfTheSealedTypeStaysNull(@TempDir Path tempDir) throws Exception {
        GeneratedWire wire =
                GeneratedWire.compileAndRegister(tempDir, messageFamily("sealed.nulls"));

        Object envelope = wire.make("sealed.nulls.Envelope", null, new ArrayList<>());
        Object back = GeneratedWire.roundTrip(envelope);

        assertNull(GeneratedWire.read(back, "head"));
    }

    @Test
    void aSealedAbstractClassCarriesItsSharedFields(@TempDir Path tempDir) throws Exception {
        // A record cannot extend a class, so a sealed abstract base is a family of classes. The
        // base has no serializer of its own: what it declares is written as part of each member.
        GeneratedWire wire = GeneratedWire.compileAndRegister(tempDir, GeneratedWire.sources(
                "sealed.abstracts.Shape",
                "package sealed.abstracts;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "@DataModel\n"
                + "public sealed abstract class Shape permits Circle, Square {\n"
                + "    private String name;\n"
                + "    public String getName() { return name; }\n"
                + "    public void setName(String name) { this.name = name; }\n"
                + "}\n",
                "sealed.abstracts.Circle",
                "package sealed.abstracts;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "@DataModel\n"
                + "public final class Circle extends Shape {\n"
                + "    private double radius;\n"
                + "    public Circle() {}\n"
                + "    public double getRadius() { return radius; }\n"
                + "    public void setRadius(double radius) { this.radius = radius; }\n"
                + "}\n",
                "sealed.abstracts.Square",
                "package sealed.abstracts;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "@DataModel\n"
                + "public final class Square extends Shape {\n"
                + "    private double side;\n"
                + "    public Square() {}\n"
                + "    public double getSide() { return side; }\n"
                + "    public void setSide(double side) { this.side = side; }\n"
                + "}\n",
                "sealed.abstracts.Drawing",
                "package sealed.abstracts;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "import java.util.List;\n"
                + "@DataModel\n"
                + "public record Drawing(Shape main, List<Shape> others) {}\n"));

        Object circle = wire.make("sealed.abstracts.Circle");
        circle.getClass().getMethod("setName", String.class).invoke(circle, "sun");
        circle.getClass().getMethod("setRadius", double.class).invoke(circle, 2.5d);
        Object square = wire.make("sealed.abstracts.Square");
        square.getClass().getMethod("setName", String.class).invoke(square, "window");
        square.getClass().getMethod("setSide", double.class).invoke(square, 4.0d);

        Object drawing = wire.make("sealed.abstracts.Drawing", circle,
                new ArrayList<>(Arrays.asList(square, circle)));
        Object back = GeneratedWire.roundTrip(drawing);

        Object mainShape = GeneratedWire.read(back, "main");
        assertEquals(wire.type("sealed.abstracts.Circle"), mainShape.getClass());
        assertEquals("sun", GeneratedWire.read(mainShape, "getName"),
                "the field declared on the sealed base survives");
        assertEquals(2.5d, GeneratedWire.read(mainShape, "getRadius"));

        @SuppressWarnings("unchecked")
        List<Object> others = (List<Object>) GeneratedWire.read(back, "others");
        assertEquals(wire.type("sealed.abstracts.Square"), others.get(0).getClass());
        assertEquals("window", GeneratedWire.read(others.get(0), "getName"));
        assertEquals(4.0d, GeneratedWire.read(others.get(0), "getSide"));
    }

    @Test
    void aPayloadNamingATypeOutsideThePermittedSetIsRefused(@TempDir Path tempDir)
            throws Exception {
        GeneratedWire wire =
                GeneratedWire.compileAndRegister(tempDir, GeneratedWire.sources(
                        "sealed.intruder.Message",
                        "package sealed.intruder;\n"
                        + "import com.zeroz4j.api.DataModel;\n"
                        + "@DataModel\n"
                        + "public sealed interface Message permits Text {}\n",
                        "sealed.intruder.Text",
                        "package sealed.intruder;\n"
                        + "import com.zeroz4j.api.DataModel;\n"
                        + "@DataModel\n"
                        + "public record Text(String body) implements Message {}\n",
                        "sealed.intruder.Intruder",
                        "package sealed.intruder;\n"
                        + "import com.zeroz4j.api.DataModel;\n"
                        + "@DataModel\n"
                        + "public class Intruder {\n"
                        + "    private String secret;\n"
                        + "    public Intruder() {}\n"
                        + "    public String getSecret() { return secret; }\n"
                        + "    public void setSecret(String secret) { this.secret = secret; }\n"
                        + "}\n"));
        assertTrue(wire.type("sealed.intruder.Intruder") != null);

        // Intruder is a perfectly good @DataModel. It is simply not one of the classes Message
        // permits, and that is the whole check.
        GrowableBuffer out = new GrowableBuffer();
        out.put(BinarySerializer.TAG_SEALED);
        BinarySerializer.writeString(out, "sealed.intruder.Message");
        out.put(BinarySerializer.TAG_OBJECT);
        BinarySerializer.writeString(out, "id-1");
        BinarySerializer.writeString(out, "sealed.intruder.Intruder");

        ByteBuffer in = ByteBuffer.wrap(out.toByteArray());
        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> BinarySerializer.readValue(in, new ObjectMapper()));

        assertTrue(refused.getMessage().contains("sealed.intruder.Intruder"),
                refused.getMessage());
        assertTrue(refused.getMessage().contains("does not permit"), refused.getMessage());
        assertTrue(refused.getMessage().contains("sealed.intruder.Text"),
                "the message should say what is permitted: " + refused.getMessage());
    }

    @Test
    void aPayloadNamingAnUnknownSealedTypeIsRefused(@TempDir Path tempDir) throws Exception {
        GeneratedWire.compileAndRegister(tempDir, GeneratedWire.sources(
                "sealed.unknown.Text",
                "package sealed.unknown;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "@DataModel\n"
                + "public record Text(String body) {}\n"));

        GrowableBuffer out = new GrowableBuffer();
        out.put(BinarySerializer.TAG_SEALED);
        BinarySerializer.writeString(out, "sealed.unknown.NoSuchFamily");
        out.put(BinarySerializer.TAG_RECORD);
        BinarySerializer.writeString(out, "id-1");
        BinarySerializer.writeString(out, "sealed.unknown.Text");

        ByteBuffer in = ByteBuffer.wrap(out.toByteArray());
        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> BinarySerializer.readValue(in, new ObjectMapper()));

        assertTrue(refused.getMessage().contains("Unknown sealed wire type"), refused.getMessage());
    }

    @Test
    void aFieldOfTheSealedTypeRefusesAValueOfAnotherFamily(@TempDir Path tempDir) throws Exception {
        GeneratedWire wire =
                GeneratedWire.compileAndRegister(tempDir, messageFamily("sealed.wrongbase"));
        assertTrue(wire.type("sealed.wrongbase.Envelope") != null);

        // An Envelope whose 'head' claims to be a member of some other sealed family.
        GrowableBuffer out = new GrowableBuffer();
        out.put(BinarySerializer.TAG_RECORD);
        BinarySerializer.writeString(out, "env-1");
        BinarySerializer.writeString(out, "sealed.wrongbase.Envelope");
        out.put((byte) 1);                                     // the envelope is present
        out.put(BinarySerializer.TAG_SEALED);
        BinarySerializer.writeString(out, "sealed.wrongbase.SomethingElse");
        out.put(BinarySerializer.TAG_RECORD);
        BinarySerializer.writeString(out, "t-1");
        BinarySerializer.writeString(out, "sealed.wrongbase.Text");

        ByteBuffer in = ByteBuffer.wrap(out.toByteArray());
        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> BinarySerializer.readValue(in, new ObjectMapper()));

        assertTrue(refused.getMessage().contains("sealed.wrongbase.Message"), refused.getMessage());
        assertTrue(refused.getMessage().contains("sealed.wrongbase.SomethingElse"),
                refused.getMessage());
    }

    @Test
    void aPlainInterfaceIsStillRefusedAndSaysWhy(@TempDir Path tempDir) throws Exception {
        List<String> errors = GeneratedWire.errorsFrom(tempDir, GeneratedWire.sources(
                "sealed.plain.Marker",
                "package sealed.plain;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "@DataModel\n"
                + "public interface Marker {}\n"));

        String joined = String.join("\n", errors);
        assertTrue(joined.contains("sealed"), joined);
        assertTrue(joined.contains("permits"), joined);
    }

    @Test
    void aPermittedClassThatIsNotAModelIsRefused(@TempDir Path tempDir) throws Exception {
        List<String> errors = GeneratedWire.errorsFrom(tempDir, GeneratedWire.sources(
                "sealed.unmodelled.Shape",
                "package sealed.unmodelled;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "@DataModel\n"
                + "public sealed interface Shape permits Circle {}\n",
                "sealed.unmodelled.Circle",
                "package sealed.unmodelled;\n"
                + "public record Circle(double radius) implements Shape {}\n"));

        String joined = String.join("\n", errors);
        assertTrue(joined.contains("sealed.unmodelled.Circle"), joined);
        assertTrue(joined.contains("@DataModel"), joined);
    }

    @Test
    void aNestedSealedFamilyIsRefused(@TempDir Path tempDir) throws Exception {
        List<String> errors = GeneratedWire.errorsFrom(tempDir, GeneratedWire.sources(
                "sealed.nested.Top",
                "package sealed.nested;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "@DataModel\n"
                + "public sealed interface Top permits Middle {}\n",
                "sealed.nested.Middle",
                "package sealed.nested;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "@DataModel\n"
                + "public sealed interface Middle extends Top permits Leaf {}\n",
                "sealed.nested.Leaf",
                "package sealed.nested;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "@DataModel\n"
                + "public record Leaf(int v) implements Middle {}\n"));

        String joined = String.join("\n", errors);
        assertTrue(joined.contains("one level"), joined);
    }

    @Test
    void aNonSealedMemberIsRefused(@TempDir Path tempDir) throws Exception {
        List<String> errors = GeneratedWire.errorsFrom(tempDir, GeneratedWire.sources(
                "sealed.open.Shape",
                "package sealed.open;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "@DataModel\n"
                + "public sealed interface Shape permits Circle {}\n",
                "sealed.open.Circle",
                "package sealed.open;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "@DataModel\n"
                + "public non-sealed class Circle implements Shape {\n"
                + "    public Circle() {}\n"
                + "}\n"));

        String joined = String.join("\n", errors);
        assertTrue(joined.contains("non-sealed"), joined);
        assertTrue(joined.contains("final"), joined);
    }
}
