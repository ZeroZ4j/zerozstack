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
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

public class GrowableBufferTest {

    @Test
    public void testPutAndGetPrimitives() {
        GrowableBuffer buffer = new GrowableBuffer(16);
        buffer.put((byte) 10);
        buffer.putShort((short) 20000);
        buffer.putInt(30000000);
        buffer.putLong(40000000000L);
        buffer.putFloat(3.14f);
        buffer.putDouble(2.71828);
        buffer.putChar('A');

        byte[] result = buffer.toByteArray();
        ByteBuffer readBuf = ByteBuffer.wrap(result);

        assertEquals((byte) 10, readBuf.get());
        assertEquals((short) 20000, readBuf.getShort());
        assertEquals(30000000, readBuf.getInt());
        assertEquals(40000000000L, readBuf.getLong());
        assertEquals(3.14f, readBuf.getFloat());
        assertEquals(2.71828, readBuf.getDouble());
        assertEquals('A', readBuf.getChar());
    }

    @Test
    public void testGrowBeyondInitialCapacity() {
        GrowableBuffer buffer = new GrowableBuffer(4); // tiny initial capacity
        for (int i = 0; i < 1000; i++) {
            buffer.putInt(i);
        }
        
        byte[] result = buffer.toByteArray();
        assertEquals(1000 * 4, result.length);

        ByteBuffer readBuf = ByteBuffer.wrap(result);
        for (int i = 0; i < 1000; i++) {
            assertEquals(i, readBuf.getInt());
        }
    }

    @Test
    public void testPutByteArray() {
        GrowableBuffer buffer = new GrowableBuffer();
        byte[] data = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        buffer.put(data);
        
        byte[] result = buffer.toByteArray();
        assertArrayEquals(data, result);
    }
}
