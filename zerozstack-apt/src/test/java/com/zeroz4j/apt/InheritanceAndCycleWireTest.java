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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two faults that were in the generated code long before records were, both found while adding
 * them, and both of the kind that only shows up once somebody writes ordinary Java.
 *
 * <p><b>Inherited fields used to vanish.</b> Factoring what several models share into a base class
 * is the most ordinary refactor there is. The generated serializer only ever looked at the fields
 * the class declared itself, so everything on the base stopped arriving — no compile error, no
 * error on the wire, just missing data.</p>
 *
 * <p><b>Two models referring to each other used to overflow the stack.</b> A field declared as a
 * model type was written straight into the buffer with no record of what was already being
 * written, so A holding a B holding an A never terminated.</p>
 */
class InheritanceAndCycleWireTest {

    @Test
    void aFieldOnTheBaseClassArrives(@TempDir Path tempDir) throws Exception {
        GeneratedWire wire = GeneratedWire.compileAndRegister(tempDir, GeneratedWire.sources(
                "inherit.plain.Entity",
                "package inherit.plain;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "@DataModel\n"
                + "public class Entity {\n"
                + "    private long id;\n"
                + "    private String createdBy;\n"
                + "    public Entity() {}\n"
                + "    public long getId() { return id; }\n"
                + "    public void setId(long id) { this.id = id; }\n"
                + "    public String getCreatedBy() { return createdBy; }\n"
                + "    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }\n"
                + "}\n",
                "inherit.plain.Invoice",
                "package inherit.plain;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "@DataModel\n"
                + "public class Invoice extends Entity {\n"
                + "    private String reference;\n"
                + "    public Invoice() {}\n"
                + "    public String getReference() { return reference; }\n"
                + "    public void setReference(String reference) { this.reference = reference; }\n"
                + "}\n"));

        Object invoice = wire.make("inherit.plain.Invoice");
        invoice.getClass().getMethod("setId", long.class).invoke(invoice, 42L);
        invoice.getClass().getMethod("setCreatedBy", String.class).invoke(invoice, "Ada");
        invoice.getClass().getMethod("setReference", String.class).invoke(invoice, "INV-1");

        Object back = GeneratedWire.roundTrip(invoice);

        assertEquals("INV-1", GeneratedWire.read(back, "getReference"), "the model's own field");
        assertEquals(42L, GeneratedWire.read(back, "getId"), "the field it inherited");
        assertEquals("Ada", GeneratedWire.read(back, "getCreatedBy"), "and the other one");
    }

    @Test
    void fieldsArriveThroughTwoLevelsOfInheritance(@TempDir Path tempDir) throws Exception {
        GeneratedWire wire = GeneratedWire.compileAndRegister(tempDir, GeneratedWire.sources(
                "inherit.deep.Top",
                "package inherit.deep;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "@DataModel\n"
                + "public abstract class Top {\n"
                + "    private String topField;\n"
                + "    public String getTopField() { return topField; }\n"
                + "    public void setTopField(String v) { this.topField = v; }\n"
                + "}\n",
                "inherit.deep.Middle",
                "package inherit.deep;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "@DataModel\n"
                + "public abstract class Middle extends Top {\n"
                + "    private int middleField;\n"
                + "    public int getMiddleField() { return middleField; }\n"
                + "    public void setMiddleField(int v) { this.middleField = v; }\n"
                + "}\n",
                "inherit.deep.Leaf",
                "package inherit.deep;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "@DataModel\n"
                + "public class Leaf extends Middle {\n"
                + "    private boolean leafField;\n"
                + "    public Leaf() {}\n"
                + "    public boolean isLeafField() { return leafField; }\n"
                + "    public void setLeafField(boolean v) { this.leafField = v; }\n"
                + "}\n"));

        Object leaf = wire.make("inherit.deep.Leaf");
        leaf.getClass().getMethod("setTopField", String.class).invoke(leaf, "top");
        leaf.getClass().getMethod("setMiddleField", int.class).invoke(leaf, 5);
        leaf.getClass().getMethod("setLeafField", boolean.class).invoke(leaf, true);

        Object back = GeneratedWire.roundTrip(leaf);

        assertEquals("top", GeneratedWire.read(back, "getTopField"));
        assertEquals(5, GeneratedWire.read(back, "getMiddleField"));
        assertEquals(true, GeneratedWire.read(back, "isLeafField"));
    }

    @Test
    void aBaseClassThatIsNotAModelIsRefused(@TempDir Path tempDir) throws Exception {
        // Its fields cannot be carried, and saying nothing is how the data went missing before.
        List<String> errors = GeneratedWire.errorsFrom(tempDir, GeneratedWire.sources(
                "inherit.unmarked.Base",
                "package inherit.unmarked;\n"
                + "public class Base {\n"
                + "    private String hidden;\n"
                + "    public String getHidden() { return hidden; }\n"
                + "    public void setHidden(String v) { this.hidden = v; }\n"
                + "}\n",
                "inherit.unmarked.Child",
                "package inherit.unmarked;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "@DataModel\n"
                + "public class Child extends Base {\n"
                + "    private int own;\n"
                + "    public Child() {}\n"
                + "    public int getOwn() { return own; }\n"
                + "    public void setOwn(int v) { this.own = v; }\n"
                + "}\n"));

        String joined = String.join("\n", errors);
        assertTrue(joined.contains("inherit.unmarked.Base"), joined);
        assertTrue(joined.contains("@DataModel"), joined);
    }

    @Test
    void aBaseClassWithNoFieldsNeedsNoAnnotation(@TempDir Path tempDir) throws Exception {
        // Nothing is lost, so nothing is refused. A marker or a bag of methods is fine.
        GeneratedWire wire = GeneratedWire.compileAndRegister(tempDir, GeneratedWire.sources(
                "inherit.markerbase.Base",
                "package inherit.markerbase;\n"
                + "public abstract class Base {\n"
                + "    public String describe() { return \"base\"; }\n"
                + "}\n",
                "inherit.markerbase.Child",
                "package inherit.markerbase;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "@DataModel\n"
                + "public class Child extends Base {\n"
                + "    private int own;\n"
                + "    public Child() {}\n"
                + "    public int getOwn() { return own; }\n"
                + "    public void setOwn(int v) { this.own = v; }\n"
                + "}\n"));

        Object child = wire.make("inherit.markerbase.Child");
        child.getClass().getMethod("setOwn", int.class).invoke(child, 11);

        assertEquals(11, GeneratedWire.read(GeneratedWire.roundTrip(child), "getOwn"));
    }

    @Test
    void aShadowedFieldNameIsRefused(@TempDir Path tempDir) throws Exception {
        // Both would be written, and both read back through the same accessor.
        List<String> errors = GeneratedWire.errorsFrom(tempDir, GeneratedWire.sources(
                "inherit.shadow.Base",
                "package inherit.shadow;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "@DataModel\n"
                + "public class Base {\n"
                + "    private String name;\n"
                + "    public Base() {}\n"
                + "    public String getName() { return name; }\n"
                + "    public void setName(String v) { this.name = v; }\n"
                + "}\n",
                "inherit.shadow.Child",
                "package inherit.shadow;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "@DataModel\n"
                + "public class Child extends Base {\n"
                + "    private String name;\n"
                + "    public Child() {}\n"
                + "    public String getName() { return name; }\n"
                + "    public void setName(String v) { this.name = v; }\n"
                + "}\n"));

        String joined = String.join("\n", errors);
        assertTrue(joined.contains("name"), joined);
        assertTrue(joined.contains("inherit.shadow.Base"), joined);
    }

    @Test
    void twoModelsPointingAtEachOtherMakeTheRoundTrip(@TempDir Path tempDir) throws Exception {
        // This used to run until the stack ran out.
        GeneratedWire wire = GeneratedWire.compileAndRegister(tempDir, GeneratedWire.sources(
                "cycle.pair.Author",
                "package cycle.pair;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "@DataModel\n"
                + "public class Author {\n"
                + "    private String name;\n"
                + "    private Book latest;\n"
                + "    public Author() {}\n"
                + "    public String getName() { return name; }\n"
                + "    public void setName(String v) { this.name = v; }\n"
                + "    public Book getLatest() { return latest; }\n"
                + "    public void setLatest(Book v) { this.latest = v; }\n"
                + "}\n",
                "cycle.pair.Book",
                "package cycle.pair;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "@DataModel\n"
                + "public class Book {\n"
                + "    private String title;\n"
                + "    private Author writtenBy;\n"
                + "    public Book() {}\n"
                + "    public String getTitle() { return title; }\n"
                + "    public void setTitle(String v) { this.title = v; }\n"
                + "    public Author getWrittenBy() { return writtenBy; }\n"
                + "    public void setWrittenBy(Author v) { this.writtenBy = v; }\n"
                + "}\n"));

        Object author = wire.make("cycle.pair.Author");
        Object book = wire.make("cycle.pair.Book");
        author.getClass().getMethod("setName", String.class).invoke(author, "Ada");
        author.getClass().getMethod("setLatest", wire.type("cycle.pair.Book")).invoke(author, book);
        book.getClass().getMethod("setTitle", String.class).invoke(book, "Notes");
        book.getClass().getMethod("setWrittenBy", wire.type("cycle.pair.Author"))
                .invoke(book, author);

        Object back = GeneratedWire.roundTrip(author);

        assertEquals("Ada", GeneratedWire.read(back, "getName"));
        Object backBook = GeneratedWire.read(back, "getLatest");
        assertNotNull(backBook);
        assertEquals("Notes", GeneratedWire.read(backBook, "getTitle"));
        assertSame(back, GeneratedWire.read(backBook, "getWrittenBy"),
                "the loop closes on the same instance, not a copy");
    }

    @Test
    void aModelPointingAtItselfMakesTheRoundTrip(@TempDir Path tempDir) throws Exception {
        GeneratedWire wire = GeneratedWire.compileAndRegister(tempDir, GeneratedWire.sources(
                "cycle.self.Node",
                "package cycle.self;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "import java.util.List;\n"
                + "@DataModel\n"
                + "public class Node {\n"
                + "    private String label;\n"
                + "    private Node parent;\n"
                + "    private List<Node> children;\n"
                + "    public Node() {}\n"
                + "    public String getLabel() { return label; }\n"
                + "    public void setLabel(String v) { this.label = v; }\n"
                + "    public Node getParent() { return parent; }\n"
                + "    public void setParent(Node v) { this.parent = v; }\n"
                + "    public List<Node> getChildren() { return children; }\n"
                + "    public void setChildren(List<Node> v) { this.children = v; }\n"
                + "}\n"));

        Object root = wire.make("cycle.self.Node");
        Object child = wire.make("cycle.self.Node");
        Class<?> nodeType = wire.type("cycle.self.Node");
        root.getClass().getMethod("setLabel", String.class).invoke(root, "root");
        child.getClass().getMethod("setLabel", String.class).invoke(child, "child");
        child.getClass().getMethod("setParent", nodeType).invoke(child, root);
        root.getClass().getMethod("setChildren", List.class)
                .invoke(root, new ArrayList<>(Arrays.asList(child)));

        Object back = GeneratedWire.roundTrip(root);

        @SuppressWarnings("unchecked")
        List<Object> children = (List<Object>) GeneratedWire.read(back, "getChildren");
        assertEquals(1, children.size());
        assertEquals("child", GeneratedWire.read(children.get(0), "getLabel"));
        assertSame(back, GeneratedWire.read(children.get(0), "getParent"),
                "the child points back at the same root");
    }

    @Test
    void aNestedModelIsTheSameInstanceWhenItWasSentTwice(@TempDir Path tempDir) throws Exception {
        GeneratedWire wire = GeneratedWire.compileAndRegister(tempDir, GeneratedWire.sources(
                "cycle.shared.Address",
                "package cycle.shared;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "@DataModel\n"
                + "public class Address {\n"
                + "    private String city;\n"
                + "    public Address() {}\n"
                + "    public String getCity() { return city; }\n"
                + "    public void setCity(String v) { this.city = v; }\n"
                + "}\n",
                "cycle.shared.Order",
                "package cycle.shared;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "@DataModel\n"
                + "public class Order {\n"
                + "    private Address billing;\n"
                + "    private Address shipping;\n"
                + "    public Order() {}\n"
                + "    public Address getBilling() { return billing; }\n"
                + "    public void setBilling(Address v) { this.billing = v; }\n"
                + "    public Address getShipping() { return shipping; }\n"
                + "    public void setShipping(Address v) { this.shipping = v; }\n"
                + "}\n"));

        Object address = wire.make("cycle.shared.Address");
        address.getClass().getMethod("setCity", String.class).invoke(address, "Bath");
        Object order = wire.make("cycle.shared.Order");
        Class<?> addressType = wire.type("cycle.shared.Address");
        order.getClass().getMethod("setBilling", addressType).invoke(order, address);
        order.getClass().getMethod("setShipping", addressType).invoke(order, address);

        Object back = GeneratedWire.roundTrip(order);

        assertEquals("Bath", GeneratedWire.read(GeneratedWire.read(back, "getBilling"), "getCity"));
        assertSame(GeneratedWire.read(back, "getBilling"), GeneratedWire.read(back, "getShipping"),
                "one address sent, one address received");
    }
}
