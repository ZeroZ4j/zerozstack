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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

public class BinarySerializerTest {

    @BeforeAll
    public static void setup() {
        // Register the TestModel manually since APT is not active on this test class
        BinaryRegistry.register(TestModel.class.getName(), TestModel::new);
    }

    @Test
    public void testPrimitives() {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        ObjectMapper mapper = new ObjectMapper();

        // Write primitive wrappers
        BinarySerializer.writeValue(buffer, 42, mapper);
        BinarySerializer.writeValue(buffer, 999999999L, mapper);
        BinarySerializer.writeValue(buffer, true, mapper);
        BinarySerializer.writeValue(buffer, 3.14159, mapper);
        BinarySerializer.writeValue(buffer, (short) 256, mapper);
        BinarySerializer.writeValue(buffer, (byte) 0x7F, mapper);
        BinarySerializer.writeValue(buffer, 'Z', mapper);
        BinarySerializer.writeValue(buffer, null, mapper);

        // Flip buffer to read
        buffer.flip();

        assertEquals(42, BinarySerializer.readValue(buffer, mapper));
        assertEquals(999999999L, BinarySerializer.readValue(buffer, mapper));
        assertEquals(true, BinarySerializer.readValue(buffer, mapper));
        assertEquals(3.14159, BinarySerializer.readValue(buffer, mapper));
        assertEquals((short) 256, BinarySerializer.readValue(buffer, mapper));
        assertEquals((byte) 0x7F, BinarySerializer.readValue(buffer, mapper));
        assertEquals('Z', BinarySerializer.readValue(buffer, mapper));
        assertNull(BinarySerializer.readValue(buffer, mapper));
    }

    @Test
    public void testStrings() {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        ObjectMapper mapper = new ObjectMapper();

        BinarySerializer.writeValue(buffer, "Hello Wasm RMI!", mapper);
        BinarySerializer.writeValue(buffer, "", mapper);
        BinarySerializer.writeValue(buffer, null, mapper);

        buffer.flip();

        assertEquals("Hello Wasm RMI!", BinarySerializer.readValue(buffer, mapper));
        assertEquals("", BinarySerializer.readValue(buffer, mapper));
        assertNull(BinarySerializer.readValue(buffer, mapper));
    }

    @Test
    public void testObjects() {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        ObjectMapper mapper = new ObjectMapper();

        TestModel original = new TestModel("Franz", 950);
        BinarySerializer.writeValue(buffer, original, mapper);

        buffer.flip();

        Object readObj = BinarySerializer.readValue(buffer, mapper);
        assertNotNull(readObj);
        assertInstanceOf(TestModel.class, readObj);

        TestModel readModel = (TestModel) readObj;
        assertEquals("Franz", readModel.getField());
        assertEquals(950, readModel.getNumber());
    }

    @Test
    public void testCollectionsAndArrays() {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        ObjectMapper mapper = new ObjectMapper();

        List<Object> list = Arrays.asList("A", 42, true);
        Map<String, Object> map = new HashMap<>();
        map.put("key1", "val1");
        map.put("key2", 100);

        byte[] bytes = new byte[]{1, 2, 3, 4, 5};

        BinarySerializer.writeValue(buffer, list, mapper);
        BinarySerializer.writeValue(buffer, map, mapper);
        BinarySerializer.writeValue(buffer, bytes, mapper);

        buffer.flip();

        List<?> readList = (List<?>) BinarySerializer.readValue(buffer, mapper);
        assertEquals(3, readList.size());
        assertEquals("A", readList.get(0));
        assertEquals(42, readList.get(1));
        assertEquals(true, readList.get(2));

        Map<?, ?> readMap = (Map<?, ?>) BinarySerializer.readValue(buffer, mapper);
        assertEquals(2, readMap.size());
        assertEquals("val1", readMap.get("key1"));
        assertEquals(100, readMap.get("key2"));

        byte[] readBytes = (byte[]) BinarySerializer.readValue(buffer, mapper);
        assertArrayEquals(bytes, readBytes);
    }

    @Test
    public void testCircularReferences() {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        ObjectMapper mapper = new ObjectMapper();

        // Create a circular reference using a new class
        Node node1 = new Node("Node1");
        Node node2 = new Node("Node2");
        node1.next = node2;
        node2.next = node1;

        BinaryRegistry.register(Node.class.getName(), Node::new);

        BinarySerializer.writeValue(buffer, node1, mapper);
        buffer.flip();

        ObjectMapper readMapper = new ObjectMapper();
        Node readNode1 = (Node) BinarySerializer.readValue(buffer, readMapper);

        assertEquals("Node1", readNode1.name);
        assertEquals("Node2", readNode1.next.name);
        assertSame(readNode1, readNode1.next.next);
    }

    @Test
    public void testGrowableBufferWrite() {
        GrowableBuffer buffer = new GrowableBuffer(16);
        ObjectMapper mapper = new ObjectMapper();

        BinarySerializer.writeValue(buffer, 12345, mapper);
        BinarySerializer.writeValue(buffer, "Testing Growable", mapper);
        BinarySerializer.writeValue(buffer, null, mapper);
        
        List<Integer> list = Arrays.asList(1, 2, 3);
        BinarySerializer.writeValue(buffer, list, mapper);

        Map<String, String> map = new HashMap<>();
        map.put("k", "v");
        BinarySerializer.writeValue(buffer, map, mapper);
        
        byte[] bytes = new byte[]{9, 8, 7};
        BinarySerializer.writeValue(buffer, bytes, mapper);

        TestModel model = new TestModel("Test", 100);
        BinarySerializer.writeValue(buffer, model, mapper);
        
        // Write short, byte, char, double, float, boolean, long
        BinarySerializer.writeValue(buffer, (short) 10, mapper);
        BinarySerializer.writeValue(buffer, (byte) 20, mapper);
        BinarySerializer.writeValue(buffer, 'c', mapper);
        BinarySerializer.writeValue(buffer, 3.14d, mapper);
        BinarySerializer.writeValue(buffer, 2.71f, mapper);
        BinarySerializer.writeValue(buffer, true, mapper);
        BinarySerializer.writeValue(buffer, 100000L, mapper);

        // test exceptions
        assertThrows(IllegalArgumentException.class, () -> BinarySerializer.writeValue(buffer, new Object(), mapper));
        assertThrows(IllegalArgumentException.class, () -> BinarySerializer.writeValue(ByteBuffer.allocate(10), new Object(), mapper));
        
        ByteBuffer readBuf = ByteBuffer.wrap(buffer.toByteArray());
        assertEquals(12345, BinarySerializer.readValue(readBuf, mapper));
        assertEquals("Testing Growable", BinarySerializer.readValue(readBuf, mapper));
        assertNull(BinarySerializer.readValue(readBuf, mapper));
        
        List<?> readList = (List<?>) BinarySerializer.readValue(readBuf, mapper);
        assertEquals(3, readList.size());
        
        Map<?, ?> readMap = (Map<?, ?>) BinarySerializer.readValue(readBuf, mapper);
        assertEquals("v", readMap.get("k"));

        byte[] readBytes = (byte[]) BinarySerializer.readValue(readBuf, mapper);
        assertArrayEquals(bytes, readBytes);

        TestModel readModel = (TestModel) BinarySerializer.readValue(readBuf, mapper);
        assertEquals("Test", readModel.getField());
        
        assertEquals((short) 10, BinarySerializer.readValue(readBuf, mapper));
        assertEquals((byte) 20, BinarySerializer.readValue(readBuf, mapper));
        assertEquals('c', BinarySerializer.readValue(readBuf, mapper));
        assertEquals(3.14d, BinarySerializer.readValue(readBuf, mapper));
        assertEquals(2.71f, BinarySerializer.readValue(readBuf, mapper));
        assertEquals(true, BinarySerializer.readValue(readBuf, mapper));
        assertEquals(100000L, BinarySerializer.readValue(readBuf, mapper));
    }
    
    @Test
    public void testReadInvalidTag() {
        ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put((byte) 0xFF); // Invalid tag
        buffer.flip();
        assertThrows(IllegalStateException.class, () -> BinarySerializer.readValue(buffer, new ObjectMapper()));
    }

    public static class Node implements BinaryPackable {
        public String name;
        public Node next;

        public Node() {}
        public Node(String name) { this.name = name; }

        @Override
        public void writeToBuffer(GrowableBuffer buffer, ObjectMapper mapper) {
            BinarySerializer.writeString(buffer, name);
            BinarySerializer.writeValue(buffer, next, mapper);
        }

        @Override
        public void readFromBuffer(ByteBuffer buffer, ObjectMapper mapper) {
            this.name = BinarySerializer.readString(buffer);
            this.next = (Node) BinarySerializer.readValue(buffer, mapper);
        }
    }
}
