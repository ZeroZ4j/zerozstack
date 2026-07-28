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

import java.nio.ByteBuffer;

/**
 * A dynamically-growing byte buffer that automatically expands when writes
 * would exceed current capacity. Replaces fixed-size {@code ByteBuffer.allocate(65536)}
 * calls throughout the framework to prevent {@code BufferOverflowException}.
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>State Mutations:</b> Encapsulates a internal {@link ByteBuffer}. Writing operations mutate position
 *       and reallocate the buffer array when capacity threshold is reached.</li>
 *   <li><b>Growth Policy:</b> Doubles capacity when filled, or expands to {@code position + requiredBytes}
 *       if doubling is insufficient. Performs array copy on growth.</li>
 *   <li><b>Side Effects:</b> Heap memory allocation during capacity doubling or array extraction in {@link #toByteArray()}.</li>
 * </ul>
 */
public class GrowableBuffer {
    private ByteBuffer buffer;

    /**
     * Creates a GrowableBuffer with a default initial capacity of 4096 bytes.
     *
     * <p><b>Under the hood:</b> Delegates to {@link #GrowableBuffer(int)} passing 4096.
     * Allocates a heap-backed {@link ByteBuffer}.</p>
     */
    public GrowableBuffer() {
        this(4096);
    }

    /**
     * Creates a GrowableBuffer with the specified initial capacity.
     *
     * @param initialCapacity the initial buffer size in bytes
     *
     * <p><b>Under the hood:</b> Instantiates {@link ByteBuffer#allocate(int)} with {@code initialCapacity}.
     * Position starts at 0, limit equals initial capacity.</p>
     */
    public GrowableBuffer(int initialCapacity) {
        this.buffer = ByteBuffer.allocate(initialCapacity);
    }

    private void ensureCapacity(int additionalBytes) {
        if (buffer.remaining() < additionalBytes) {
            int newCapacity = Math.max(buffer.capacity() * 2, buffer.position() + additionalBytes);
            ByteBuffer newBuffer = ByteBuffer.allocate(newCapacity);
            buffer.flip();
            newBuffer.put(buffer);
            buffer = newBuffer;
        }
    }

    /**
     * Writes a single byte into the buffer, expanding capacity if necessary.
     *
     * @param b the byte to write
     * @return this {@code GrowableBuffer} for method chaining
     *
     * <p><b>Under the hood:</b> Checks capacity for 1 byte, reallocating if needed, then executes
     * {@code buffer.put(b)} and increments position by 1.</p>
     */
    public GrowableBuffer put(byte b) {
        ensureCapacity(1);
        buffer.put(b);
        return this;
    }

    /**
     * Writes a 4-byte signed integer into the buffer in big-endian order.
     *
     * @param value the int value to write
     * @return this {@code GrowableBuffer} for method chaining
     *
     * <p><b>Under the hood:</b> Ensures 4 bytes available capacity, delegates to {@link ByteBuffer#putInt(int)},
     * advancing position by 4 bytes.</p>
     */
    public GrowableBuffer putInt(int value) {
        ensureCapacity(4);
        buffer.putInt(value);
        return this;
    }

    /**
     * Writes an 8-byte signed long into the buffer in big-endian order.
     *
     * @param value the long value to write
     * @return this {@code GrowableBuffer} for method chaining
     *
     * <p><b>Under the hood:</b> Ensures 8 bytes available capacity, delegates to {@link ByteBuffer#putLong(long)},
     * advancing position by 8 bytes.</p>
     */
    public GrowableBuffer putLong(long value) {
        ensureCapacity(8);
        buffer.putLong(value);
        return this;
    }

    /**
     * Writes an 8-byte double floating-point value into the buffer.
     *
     * @param value the double value to write
     * @return this {@code GrowableBuffer} for method chaining
     *
     * <p><b>Under the hood:</b> Ensures 8 bytes available capacity, delegates to {@link ByteBuffer#putDouble(double)},
     * advancing position by 8 bytes.</p>
     */
    public GrowableBuffer putDouble(double value) {
        ensureCapacity(8);
        buffer.putDouble(value);
        return this;
    }

    /**
     * Writes a 4-byte single-precision float value into the buffer.
     *
     * @param value the float value to write
     * @return this {@code GrowableBuffer} for method chaining
     *
     * <p><b>Under the hood:</b> Ensures 4 bytes available capacity, delegates to {@link ByteBuffer#putFloat(float)},
     * advancing position by 4 bytes.</p>
     */
    public GrowableBuffer putFloat(float value) {
        ensureCapacity(4);
        buffer.putFloat(value);
        return this;
    }

    /**
     * Writes a 2-byte signed short integer into the buffer.
     *
     * @param value the short value to write
     * @return this {@code GrowableBuffer} for method chaining
     *
     * <p><b>Under the hood:</b> Ensures 2 bytes available capacity, delegates to {@link ByteBuffer#putShort(short)},
     * advancing position by 2 bytes.</p>
     */
    public GrowableBuffer putShort(short value) {
        ensureCapacity(2);
        buffer.putShort(value);
        return this;
    }

    /**
     * Writes a 2-byte character value into the buffer.
     *
     * @param value the char value to write
     * @return this {@code GrowableBuffer} for method chaining
     *
     * <p><b>Under the hood:</b> Ensures 2 bytes available capacity, delegates to {@link ByteBuffer#putChar(char)},
     * advancing position by 2 bytes.</p>
     */
    public GrowableBuffer putChar(char value) {
        ensureCapacity(2);
        buffer.putChar(value);
        return this;
    }

    /**
     * Writes an array of bytes into the buffer.
     *
     * @param bytes the byte array to write
     * @return this {@code GrowableBuffer} for method chaining
     *
     * <p><b>Under the hood:</b> Checks capacity for {@code bytes.length}, reallocates if needed,
     * then bulk copies array contents via {@code buffer.put(bytes)}, advancing position by {@code bytes.length}.</p>
     */
    public GrowableBuffer put(byte[] bytes) {
        ensureCapacity(bytes.length);
        buffer.put(bytes);
        return this;
    }

    /**
     * Returns the current write position (number of written bytes).
     *
     * @return current write position in bytes
     *
     * <p><b>Under the hood:</b> Reads {@code buffer.position()}. Represents the exact size of written binary data.</p>
     */
    public int position() {
        return buffer.position();
    }

    /**
     * Returns the written content as a compact byte array.
     *
     * @return byte array containing only the written data from position 0 up to current position
     *
     * <p><b>Under the hood:</b> Saves current position, flips buffer to read mode, allocates exact-length byte array,
     * copies bytes into array via {@code buffer.get(result)}, then restores buffer's position and limit so subsequent
     * writes can continue seamlessly.</p>
     */
    public byte[] toByteArray() {
        int pos = buffer.position();
        byte[] result = new byte[pos];
        buffer.flip();
        buffer.get(result);
        buffer.position(pos);
        buffer.limit(buffer.capacity());
        return result;
    }
}
