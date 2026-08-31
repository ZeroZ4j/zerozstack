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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;

/**
 * Handles binary serialization and deserialization for supported RPC primitive types,
 * collection structures, and custom binary packable classes.
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>Type Tag Encoding:</b> Every serialized payload begins with a 1-byte header tag (0x00 to 0x21)
 *       identifying the data type (Primitives, String, Object, List, Map, Set, Byte Array, Reference, UUID, Instant, Enum).</li>
 *   <li><b>Cycle &amp; Reference Detection:</b> Uses a {@link ThreadLocal} set of object identities to detect cyclic graphs
 *       and duplicate instances. Serializes subsequent occurrences of an object as a lightweight reference tag
 *       ({@link #TAG_REF}) containing only the object's name.</li>
 *   <li><b>Two kinds of name (0.8.0+):</b> a {@code @LiveSync} model, and everything reachable
 *       inside one, is written under a lasting handle from {@link ObjectMapper} — those are the
 *       objects a later message has to be able to name again. Every other value is written under a
 *       name counted from zero inside the top-level value being written, beginning with
 *       {@link #LOCAL_ID_PREFIX}, which is forgotten as soon as that value has been read. A handle
 *       is a UUID and can never begin with that character, so the reader tells them apart without
 *       knowing anything about the class.</li>
 *   <li><b>State Mutations:</b> Mutates the target {@link ByteBuffer} or {@link GrowableBuffer} position. When deserializing,
 *       instantiates objects via {@link BinaryRegistry} and registers the ones that earn a handle
 *       with {@link ObjectMapper}.</li>
 *   <li><b>Dependencies:</b> Relies on {@link BinaryRegistry} for generated serializer delegates/factories and
 *       {@link ObjectMapper} for reference ID tracking.</li>
 * </ul>
 */
public class BinarySerializer {
    /** Tag byte indicating a {@code null} reference (0x00). No payload bytes follow. */
    public static final byte TAG_NULL = 0x00;
    /** Tag byte indicating a 4-byte signed integer (0x01). */
    public static final byte TAG_INT = 0x01;
    /** Tag byte indicating an 8-byte signed long integer (0x02). */
    public static final byte TAG_LONG = 0x02;
    /** Tag byte indicating an 8-byte double precision float (0x03). */
    public static final byte TAG_DOUBLE = 0x03;
    /** Tag byte indicating a 4-byte single precision float (0x04). */
    public static final byte TAG_FLOAT = 0x04;
    /** Tag byte indicating a 1-byte boolean flag (0x05; 0 for false, 1 for true). */
    public static final byte TAG_BOOLEAN = 0x05;
    /** Tag byte indicating a UTF-8 encoded String (0x06) prefixed with a 4-byte length. */
    public static final byte TAG_STRING = 0x06;
    /** Tag byte indicating a full complex object instance (0x07) with reference ID, class FQCN, and field payload. */
    public static final byte TAG_OBJECT = 0x07;
    /** Tag byte indicating a 2-byte signed short integer (0x08). */
    public static final byte TAG_SHORT = 0x08;
    /** Tag byte indicating a single byte value (0x09). */
    public static final byte TAG_BYTE = 0x09;
    /** Tag byte indicating a 2-byte character value (0x0A). */
    public static final byte TAG_CHAR = 0x0A;
    /** Tag byte indicating a List (0x0B) prefixed with a 4-byte element count. */
    public static final byte TAG_LIST = 0x0B;
    /** Tag byte indicating a Map (0x0C) prefixed with a 4-byte entry count. */
    public static final byte TAG_MAP = 0x0C;
    /** Tag byte indicating a raw byte array (0x0D) prefixed with a 4-byte length. */
    public static final byte TAG_BYTE_ARRAY = 0x0D;
    /** Tag byte indicating an existing object reference (0x0E) containing only its string ID. */
    public static final byte TAG_REF = 0x0E;
    /**
     * Tag byte indicating a {@link java.util.UUID} (0x0F): the canonical 36-char string form
     * (via {@link java.util.UUID#toString()} / {@link java.util.UUID#fromString(String)}).
     * String form is used rather than two longs because TeaVM does not emulate
     * {@code UUID.getMostSignificantBits()} or {@code new UUID(long, long)}, so this keeps the
     * same code linking on both the JVM server and the Wasm browser client.
     */
    public static final byte TAG_UUID = 0x0F;
    /** Tag byte indicating a {@link java.time.Instant} (0x10): an 8-byte epoch-second long followed by a 4-byte nano int. */
    public static final byte TAG_INSTANT = 0x10;
    /** Tag byte indicating an {@link java.lang.Enum} constant (0x11): declaring-class FQCN string plus its {@code name()} string. */
    public static final byte TAG_ENUM = 0x11;
    /**
     * Tag byte indicating a {@link java.util.Set} (0x12) prefixed with a 4-byte element count.
     *
     * <p>Deserialized as a {@link java.util.LinkedHashSet}, so encounter order survives the round
     * trip for ordered implementations. A {@link java.util.TreeSet} arrives as a
     * {@code LinkedHashSet} preserving the sorted order it was written in, but without its
     * {@code Comparator} — subsequent insertions on the receiving side are not re-sorted.</p>
     */
    public static final byte TAG_SET = 0x12;
    /**
     * Tag byte indicating a {@link java.math.BigDecimal} (0x13): its exact {@code toString()} form.
     *
     * <p>The string form is used rather than unscaled-value-plus-scale so precision and scale survive
     * exactly, and so the same code links under TeaVM on the client.</p>
     */
    public static final byte TAG_BIG_DECIMAL = 0x13;
    /** Tag byte indicating a {@link java.math.BigInteger} (0x14): its exact {@code toString()} form. */
    public static final byte TAG_BIG_INTEGER = 0x14;
    /** Tag byte indicating a {@link java.time.LocalDate} (0x15): an 8-byte epoch-day long. */
    public static final byte TAG_LOCAL_DATE = 0x15;
    /** Tag byte indicating a {@link java.time.LocalTime} (0x16): an 8-byte nano-of-day long. */
    public static final byte TAG_LOCAL_TIME = 0x16;
    /** Tag byte indicating a {@link java.time.LocalDateTime} (0x17): an epoch-day long then a nano-of-day long. */
    public static final byte TAG_LOCAL_DATE_TIME = 0x17;
    /** Tag byte indicating a {@link java.time.Duration} (0x18): an 8-byte seconds long then a 4-byte nano int. */
    public static final byte TAG_DURATION = 0x18;
    /**
     * Tag byte indicating a {@link java.util.Optional} (0x19): the contained value, or
     * {@link #TAG_NULL} when empty.
     *
     * <p>An {@code Optional} cannot contain null, so empty and null-content are the same state and the
     * round trip is unambiguous.</p>
     */
    public static final byte TAG_OPTIONAL = 0x19;
    /** Tag byte indicating an {@code int[]} (0x1A) prefixed with a 4-byte length. */
    public static final byte TAG_INT_ARRAY = 0x1A;
    /** Tag byte indicating a {@code long[]} (0x1B) prefixed with a 4-byte length. */
    public static final byte TAG_LONG_ARRAY = 0x1B;
    /** Tag byte indicating a {@code double[]} (0x1C) prefixed with a 4-byte length. */
    public static final byte TAG_DOUBLE_ARRAY = 0x1C;
    /** Tag byte indicating a {@code float[]} (0x1D) prefixed with a 4-byte length. */
    public static final byte TAG_FLOAT_ARRAY = 0x1D;
    /** Tag byte indicating a {@code short[]} (0x1E) prefixed with a 4-byte length. */
    public static final byte TAG_SHORT_ARRAY = 0x1E;
    /** Tag byte indicating a {@code char[]} (0x1F) prefixed with a 4-byte length. */
    public static final byte TAG_CHAR_ARRAY = 0x1F;
    /** Tag byte indicating a {@code boolean[]} (0x20) prefixed with a 4-byte length, one byte per element. */
    public static final byte TAG_BOOLEAN_ARRAY = 0x20;
    /**
     * Tag byte indicating an EclipseStore {@code Lazy} reference (0x21): a single string handle.
     *
     * <p><b>The contents are never written.</b> A lazy reference stays deferred across the network;
     * the client resolves it with an RMI round trip on first {@code get()}. Handling is delegated to
     * the tier's {@link LazyAdapter} so that this module needs no EclipseStore dependency.</p>
     */
    public static final byte TAG_LAZY = 0x21;
    /**
     * Tag byte indicating a {@code record} {@link DataModel} (0x22): an object id string, the
     * record's class FQCN, then its components in canonical order.
     *
     * <p>The bytes look like {@link #TAG_OBJECT}; what differs is when the value exists. An ordinary
     * model is created empty, registered under its id, and only then filled — so anything read
     * afterwards can point back at it. A record's components are final and are set by its canonical
     * constructor, so every component must be read before the record can exist, and it is
     * registered under its id only once it does.</p>
     *
     * <p><b>A record therefore cannot take part in a reference cycle.</b> A cycle would need the
     * record to already exist while its own components are still being read. Both sides refuse it
     * with an explanation rather than quietly producing a null: the writer when a value inside a
     * record points back at that record, the reader when a payload does the same. Two separate
     * appearances of the same record in one payload are fine — the second is a {@link #TAG_REF},
     * and by then the record has been built.</p>
     */
    public static final byte TAG_RECORD = 0x22;
    /**
     * Tag byte indicating a value of a sealed {@link DataModel} type (0x23): the sealed base's FQCN,
     * followed by the value itself as an ordinary {@link #TAG_OBJECT}, {@link #TAG_RECORD} or
     * {@link #TAG_NULL}.
     *
     * <p>The base name is what makes the payload checkable. {@code sealed} means the compiler knows
     * the complete permitted set, the annotation processor writes that set into the generated
     * registrar, and the reader looks up the named base and refuses any class the base does not
     * permit — <i>before</i> constructing anything. A payload naming a type outside the set fails
     * with a message saying which type it named and what the base permits.</p>
     */
    public static final byte TAG_SEALED = 0x23;

    private static final ThreadLocal<Set<Object>> seenObjects = ThreadLocal.withInitial(() -> Collections.newSetFromMap(new IdentityHashMap<>()));

    /**
     * The first character of a name that means nothing outside the message it traveled in.
     *
     * <p>A registry handle is a version-4 UUID, so it can never begin with this character. That is
     * what lets the reader tell the two apart without knowing anything about the class: a name
     * starting here is looked up in the message's own scratch table and never touches the
     * registry.</p>
     */
    static final char LOCAL_ID_PREFIX = '~';

    /**
     * Names handed out inside the top-level value being written right now, per thread.
     *
     * <p>Only values that are <b>not</b> given a lasting handle are in here. The second time such a
     * value is met inside one top-level value it is written as a {@link #TAG_REF} to this name, so
     * shared parts and loops still arrive with their shape intact — which is the only thing the
     * name was ever needed for.</p>
     */
    private static final ThreadLocal<Map<Object, String>> payloadIds =
            ThreadLocal.withInitial(IdentityHashMap::new);

    /** Counter behind {@link #payloadIds}; reset with it when a top-level value is finished. */
    private static final ThreadLocal<int[]> payloadIdCounter = ThreadLocal.withInitial(() -> new int[1]);

    /**
     * True while the model whose fields are being written carries a lasting handle, per thread.
     *
     * <p>Identity travels down. A {@code @LiveSync} object comes back up as one whole graph and is
     * applied part by part into the objects those parts name, so everything inside it needs a name
     * the server will still recognize. Anything outside such a graph does not.</p>
     */
    private static final ThreadLocal<boolean[]> insideHandleGraph =
            ThreadLocal.withInitial(() -> new boolean[1]);

    /** Names read inside the top-level value being read right now, per thread; the reader's half of {@link #payloadIds}. */
    private static final ThreadLocal<Map<String, Object>> payloadObjects =
            ThreadLocal.withInitial(LinkedHashMap::new);

    /** How many models deep the reader is; 1 is the payload's outermost model. */
    private static final ThreadLocal<int[]> modelReadDepth = ThreadLocal.withInitial(() -> new int[1]);

    /**
     * Records whose components are being written right now, innermost last, per thread.
     *
     * <p>A reference back into one of these is a cycle through a record, which the receiver could
     * not rebuild — see {@link #TAG_RECORD}. Identity-based, because two equal records are two
     * separate values here and only the same instance closes a loop.</p>
     */
    private static final ThreadLocal<Set<Object>> openRecords =
            ThreadLocal.withInitial(() -> Collections.newSetFromMap(new IdentityHashMap<>()));

    /**
     * Object ids of records whose components are being read right now, per thread. The reader's
     * half of {@link #openRecords}: it catches a hand-built payload that points back into a record
     * that does not exist yet, which would otherwise silently deserialize as null.
     */
    private static final ThreadLocal<Set<String>> openRecordIds =
            ThreadLocal.withInitial(LinkedHashSet::new);

    /**
     * How many levels of nesting {@link #readValue(ByteBuffer, ObjectMapper)} will descend before
     * refusing the stream.
     *
     * <p>Lists, sets, maps, {@code Optional} and object fields each recurse, so the wire format lets
     * a sender choose the recursion depth of the reader. A megabyte of nothing but list tags is
     * about a million levels, which overflows any thread stack long before the message-size limit
     * would notice.</p>
     *
     * <p>256 is chosen rather than a tighter number because one level of a domain model usually
     * costs two levels here — the collection that holds it and the object itself — so a genuinely
     * recursive structure such as a comment tree can be tens of levels deep and still be honest.
     * A few hundred frames is far below what any thread stack, browser or server, has trouble
     * with.</p>
     */
    public static final int MAX_NESTING_DEPTH = 256;

    /**
     * Current read recursion depth, per thread.
     *
     * <p>An {@code int[1]} rather than an {@code Integer} so the counter is updated in place; a
     * {@link ThreadLocal} rather than a parameter so the limit also covers generated delegates,
     * which call back into {@link #readValue(ByteBuffer, ObjectMapper)} through the public
     * signature. {@code ThreadLocal} is already used above and is supported by TeaVM.</p>
     */
    private static final ThreadLocal<int[]> readDepth = ThreadLocal.withInitial(() -> new int[1]);

    /**
     * Packs an object value and its type tag into the target {@link ByteBuffer}.
     *
     * @param buffer the target buffer to write binary data into
     * @param val    the value object to serialize (may be primitive wrapper, String, List, Map, byte[], or {@link BinaryPackable})
     * @param mapper the object mapper tracking reference handles
     *
     * <p><b>Under the hood:</b> Evaluates the class type of {@code val}, emits the corresponding 1-byte type tag,
     * and appends the payload. For complex objects, checks {@code seenObjects} ThreadLocal. If previously seen during
     * this call tree, writes {@link #TAG_REF} and object ID. Otherwise writes {@link #TAG_OBJECT}, object ID, FQCN,
     * and delegates field serialization to generated {@link BinarySerializerDelegate} or {@link BinaryPackable#writeToBuffer}.</p>
     */
    public static void writeValue(ByteBuffer buffer, Object val, ObjectMapper mapper) {
        if (val == null) {
            buffer.put(TAG_NULL);
        } else if (val instanceof Integer) {
            buffer.put(TAG_INT);
            buffer.putInt((Integer) val);
        } else if (val instanceof Long) {
            buffer.put(TAG_LONG);
            buffer.putLong((Long) val);
        } else if (val instanceof Double) {
            buffer.put(TAG_DOUBLE);
            buffer.putDouble((Double) val);
        } else if (val instanceof Float) {
            buffer.put(TAG_FLOAT);
            buffer.putFloat((Float) val);
        } else if (val instanceof Boolean) {
            buffer.put(TAG_BOOLEAN);
            buffer.put((byte) ((Boolean) val ? 1 : 0));
        } else if (val instanceof String) {
            buffer.put(TAG_STRING);
            writeString(buffer, (String) val);
        } else if (BinaryRegistry.getLazyAdapter() != null
                && BinaryRegistry.getLazyAdapter().isLazy(val)) {
            // Only the handle goes on the wire — resolving is the receiver's decision.
            buffer.put(TAG_LAZY);
            writeString(buffer, BinaryRegistry.getLazyAdapter().handleFor(val, mapper));
        } else if (BinaryRegistry.getDelegate(val.getClass().getName()) != null
                || BinaryRegistry.getRecordDelegate(val.getClass().getName()) != null
                || val instanceof BinaryPackable) {
            String className = val.getClass().getName();
            @SuppressWarnings("unchecked")
            BinarySerializerDelegate<Object> delegate = BinaryRegistry.getDelegate(className);
            BinaryRecordDelegate<Object> recordDelegate = BinaryRegistry.getRecordDelegate(className);
            Set<Object> seen = seenObjects.get();
            Set<Object> open = openRecords.get();
            boolean[] inside = insideHandleGraph.get();
            boolean enclosingBearsHandle = inside[0];
            boolean isRoot = seen.isEmpty();
            if (seen.add(val)) {
                try {
                    String id = idForNewValue(val, className, mapper, enclosingBearsHandle);
                    inside[0] = !isLocalId(id);
                    writeSealedBaseIfAny(buffer, className);
                    GrowableBuffer temp = new GrowableBuffer();
                    if (recordDelegate != null) {
                        open.add(val);
                        buffer.put(TAG_RECORD);
                        writeString(buffer, id);
                        writeString(buffer, className);
                        recordDelegate.write(val, temp, mapper);
                    } else {
                        buffer.put(TAG_OBJECT);
                        writeString(buffer, id);
                        writeString(buffer, className);
                        if (delegate != null) {
                            delegate.write(val, temp, mapper);
                        } else {
                            ((BinaryPackable) val).writeToBuffer(temp, mapper);
                        }
                    }
                    buffer.put(temp.toByteArray());
                } finally {
                    inside[0] = enclosingBearsHandle;
                    open.remove(val);
                    if (isRoot) {
                        seen.clear();
                        open.clear();
                        endWrittenPayload();
                    }
                }
            } else {
                refuseRecordCycle(val, open);
                String id = idForRepeatedValue(val, mapper);
                buffer.put(TAG_REF);
                writeString(buffer, id);
            }
        } else if (val instanceof Short) {
            buffer.put(TAG_SHORT);
            buffer.putShort((Short) val);
        } else if (val instanceof Byte) {
            buffer.put(TAG_BYTE);
            buffer.put((Byte) val);
        } else if (val instanceof Character) {
            buffer.put(TAG_CHAR);
            buffer.putChar((Character) val);
        } else if (val instanceof UUID) {
            // TeaVM does not emulate UUID.getMostSignificantBits()/new UUID(long,long); serialize
            // via the canonical string form, which TeaVM supports, so the same code links on the
            // JVM server and the browser client.
            buffer.put(TAG_UUID);
            writeString(buffer, val.toString());
        } else if (val instanceof Instant) {
            buffer.put(TAG_INSTANT);
            Instant instant = (Instant) val;
            buffer.putLong(instant.getEpochSecond());
            buffer.putInt(instant.getNano());
        } else if (val instanceof Enum) {
            buffer.put(TAG_ENUM);
            Enum<?> constant = (Enum<?>) val;
            writeString(buffer, constant.getDeclaringClass().getName());
            writeString(buffer, constant.name());
        } else if (val instanceof List) {
            buffer.put(TAG_LIST);
            List<?> list = (List<?>) val;
            buffer.putInt(list.size());
            for (Object item : list) {
                writeValue(buffer, item, mapper);
            }
        } else if (val instanceof Set) {
            buffer.put(TAG_SET);
            Set<?> set = (Set<?>) val;
            buffer.putInt(set.size());
            for (Object item : set) {
                writeValue(buffer, item, mapper);
            }
        } else if (val instanceof Map) {
            buffer.put(TAG_MAP);
            Map<?, ?> map = (Map<?, ?>) val;
            buffer.putInt(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                writeValue(buffer, entry.getKey(), mapper);
                writeValue(buffer, entry.getValue(), mapper);
            }
        } else if (val instanceof java.math.BigDecimal) {
            buffer.put(TAG_BIG_DECIMAL);
            writeString(buffer, val.toString());
        } else if (val instanceof java.math.BigInteger) {
            buffer.put(TAG_BIG_INTEGER);
            writeString(buffer, val.toString());
        } else if (val instanceof java.time.LocalDate) {
            buffer.put(TAG_LOCAL_DATE);
            buffer.putLong(((java.time.LocalDate) val).toEpochDay());
        } else if (val instanceof java.time.LocalTime) {
            buffer.put(TAG_LOCAL_TIME);
            buffer.putLong(((java.time.LocalTime) val).toNanoOfDay());
        } else if (val instanceof java.time.LocalDateTime) {
            buffer.put(TAG_LOCAL_DATE_TIME);
            java.time.LocalDateTime dateTime = (java.time.LocalDateTime) val;
            buffer.putLong(dateTime.toLocalDate().toEpochDay());
            buffer.putLong(dateTime.toLocalTime().toNanoOfDay());
        } else if (val instanceof java.time.Duration) {
            buffer.put(TAG_DURATION);
            java.time.Duration duration = (java.time.Duration) val;
            buffer.putLong(duration.getSeconds());
            buffer.putInt(duration.getNano());
        } else if (val instanceof java.util.Optional) {
            buffer.put(TAG_OPTIONAL);
            writeValue(buffer, ((java.util.Optional<?>) val).orElse(null), mapper);
        } else if (val instanceof int[]) {
            buffer.put(TAG_INT_ARRAY);
            int[] ints = (int[]) val;
            buffer.putInt(ints.length);
            for (int item : ints) {
                buffer.putInt(item);
            }
        } else if (val instanceof long[]) {
            buffer.put(TAG_LONG_ARRAY);
            long[] longs = (long[]) val;
            buffer.putInt(longs.length);
            for (long item : longs) {
                buffer.putLong(item);
            }
        } else if (val instanceof double[]) {
            buffer.put(TAG_DOUBLE_ARRAY);
            double[] doubles = (double[]) val;
            buffer.putInt(doubles.length);
            for (double item : doubles) {
                buffer.putDouble(item);
            }
        } else if (val instanceof float[]) {
            buffer.put(TAG_FLOAT_ARRAY);
            float[] floats = (float[]) val;
            buffer.putInt(floats.length);
            for (float item : floats) {
                buffer.putFloat(item);
            }
        } else if (val instanceof short[]) {
            buffer.put(TAG_SHORT_ARRAY);
            short[] shorts = (short[]) val;
            buffer.putInt(shorts.length);
            for (short item : shorts) {
                buffer.putShort(item);
            }
        } else if (val instanceof char[]) {
            buffer.put(TAG_CHAR_ARRAY);
            char[] chars = (char[]) val;
            buffer.putInt(chars.length);
            for (char item : chars) {
                buffer.putChar(item);
            }
        } else if (val instanceof boolean[]) {
            buffer.put(TAG_BOOLEAN_ARRAY);
            boolean[] booleans = (boolean[]) val;
            buffer.putInt(booleans.length);
            for (boolean item : booleans) {
                buffer.put((byte) (item ? 1 : 0));
            }
        } else if (val instanceof byte[]) {
            buffer.put(TAG_BYTE_ARRAY);
            byte[] arr = (byte[]) val;
            buffer.putInt(arr.length);
            buffer.put(arr);
        } else {
            throw new IllegalArgumentException("Unsupported binary type tag for: " + val.getClass().getName());
        }
    }

    /**
     * Whether a value about to be written needs a name that outlives its message.
     *
     * @param val       the value
     * @param className its runtime class name
     * @return true for a {@code @LiveSync} model on either tier
     *
     * <p><b>Under the hood:</b> the generated registrar marks every {@code @LiveSync} model, which
     * is what {@link BinaryRegistry#bearsHandle(String)} answers from. In the browser the runtime
     * class is the generated {@code <Model>_Live} subclass, whose name is not marked, so
     * {@link LiveObservable} answers for it instead.</p>
     */
    private static boolean bearsHandle(Object val, String className) {
        return val instanceof LiveObservable || BinaryRegistry.bearsHandle(className);
    }

    /**
     * The name to write for a value being written for the first time inside this top-level value.
     *
     * @param val       the value
     * @param className its runtime class name
     * @param mapper    the registry, used only when the value earns a lasting handle
     * @param inherited true when the enclosing model has a lasting handle, which passes down
     * @return a registry handle, or a name good only for this message
     */
    private static String idForNewValue(Object val, String className, ObjectMapper mapper,
                                        boolean inherited) {
        if (inherited || bearsHandle(val, className)) {
            return mapper.register(val);
        }
        String id = LOCAL_ID_PREFIX + Integer.toString(payloadIdCounter.get()[0]++);
        payloadIds.get().put(val, id);
        return id;
    }

    /**
     * The name to write for a value met a second time inside this top-level value.
     *
     * @param val    the value
     * @param mapper the registry
     * @return whichever name it was given the first time
     */
    private static String idForRepeatedValue(Object val, ObjectMapper mapper) {
        String local = payloadIds.get().get(val);
        // A registry handle is re-registered rather than just looked up: re-sending an object is
        // still disclosing it, and the recipient may be a different one this time.
        return local != null ? local : mapper.register(val);
    }

    /** @param id a name from the wire @return true when it is good only for the message it arrived in */
    private static boolean isLocalId(String id) {
        return id != null && !id.isEmpty() && id.charAt(0) == LOCAL_ID_PREFIX;
    }

    /** Forgets the names handed out inside the top-level value that has just finished being written. */
    private static void endWrittenPayload() {
        payloadIds.get().clear();
        payloadIdCounter.get()[0] = 0;
    }

    /**
     * Writes the {@link #TAG_SEALED} wrapper when this class belongs to a sealed wire type, so the
     * reader learns which permitted set to check the class name against.
     *
     * @param buffer    the destination buffer
     * @param className the runtime class name of the value about to be written
     */
    private static void writeSealedBaseIfAny(ByteBuffer buffer, String className) {
        String sealedBase = BinaryRegistry.sealedBaseOf(className);
        if (sealedBase != null) {
            buffer.put(TAG_SEALED);
            writeString(buffer, sealedBase);
        }
    }

    /**
     * {@link #writeSealedBaseIfAny(ByteBuffer, String)} for the growable buffer.
     *
     * @param buffer    the destination buffer
     * @param className the runtime class name of the value about to be written
     */
    private static void writeSealedBaseIfAny(GrowableBuffer buffer, String className) {
        String sealedBase = BinaryRegistry.sealedBaseOf(className);
        if (sealedBase != null) {
            buffer.put(TAG_SEALED);
            writeString(buffer, sealedBase);
        }
    }

    /**
     * Refuses, at the point it happens, a value that points back at a record still being written.
     *
     * @param val  the value about to be written as a back-reference
     * @param open the records whose components are being written right now
     * @throws IllegalArgumentException if {@code val} is one of those records
     */
    private static void refuseRecordCycle(Object val, Set<Object> open) {
        if (open.contains(val)) {
            throw new IllegalArgumentException("Cannot send " + val.getClass().getName()
                    + ": it is a record that points back at itself through this object graph. A "
                    + "record's components are final, so the receiver cannot build it until every "
                    + "component has been read — and a component that refers back to the record "
                    + "needs the record to exist first. Use a class for the type that closes the "
                    + "loop: a class is created empty and filled afterwards, so it can point back "
                    + "at itself.");
        }
    }

    /**
     * Reads a value whose declared type is a sealed wire type, refusing anything that is not a
     * member of that exact sealed family before it is built.
     *
     * @param buffer       the source buffer, positioned at a tag byte
     * @param mapper       the object mapper resolving reference handles
     * @param expectedBase the FQCN of the sealed base the reader expects
     * @return the deserialized value, or {@code null}
     * @throws IllegalStateException if the payload holds something other than a member of
     *         {@code expectedBase}
     *
     * <p><b>Under the hood:</b> Peeks the tag and, for {@link #TAG_SEALED}, the base name written
     * with it, then rewinds and reads normally. Called from generated serializers for a field whose
     * declared type is a sealed base; values reached through a {@code List}, {@code Set} or
     * {@code Map} are checked by {@link #TAG_SEALED} itself against the base named on the wire.</p>
     */
    public static Object readSealed(ByteBuffer buffer, ObjectMapper mapper, String expectedBase) {
        if (buffer.remaining() < 1) {
            throw new IllegalStateException("Binary stream ended where a value of the sealed type "
                    + expectedBase + " was expected.");
        }
        int start = buffer.position();
        byte tag = buffer.get();
        if (tag == TAG_NULL) {
            return null;
        }
        if (tag != TAG_SEALED && tag != TAG_REF) {
            buffer.position(start);
            throw new IllegalStateException("Expected a value of the sealed type " + expectedBase
                    + ", but the payload carries tag 0x" + Integer.toHexString(tag & 0xFF)
                    + ". Only a class the sealed type permits may appear here.");
        }
        if (tag == TAG_SEALED) {
            String base = readString(buffer);
            if (!expectedBase.equals(base)) {
                buffer.position(start);
                throw new IllegalStateException("Expected a value of the sealed type "
                        + expectedBase + ", but the payload names " + base + ".");
            }
        }
        buffer.position(start);
        return readValue(buffer, mapper);
    }

    /**
     * Unpacks an object from the current position in the given {@link ByteBuffer}.
     *
     * @param buffer the source buffer positioned at a 1-byte type tag
     * @param mapper the object mapper to resolve or register object reference handles
     * @return the deserialized Object (primitive wrapper, String, List, Map, byte[], or domain object)
     * @throws IllegalStateException if an unmapped or invalid type tag byte is encountered, if a
     *         declared length or element count cannot be satisfied by the bytes actually present, or
     *         if the stream nests deeper than {@link #MAX_NESTING_DEPTH}
     *
     * <p><b>Under the hood:</b> Reads the tag byte via {@code buffer.get()}. Switches on tag value:
     * <ul>
     *   <li>{@link #TAG_REF}: Reads string ID, retrieves object instance from {@code mapper.getObject(id)}.</li>
     *   <li>{@link #TAG_OBJECT}: Reads object ID and class FQCN. If instance not in mapper, creates new instance via
     *       {@code BinaryRegistry.create(className)} and registers with mapper ID. Populates fields via delegate or
     *       {@link BinaryPackable#readFromBuffer}.</li>
     *   <li>Collections/Maps: Reads size int, loops and recursively calls {@code readValue}.</li>
     * </ul>
     *
     * <p><b>Untrusted input:</b> every length and element count on the wire is checked against the
     * bytes actually remaining, at the element's own width, <i>before</i> anything is allocated.
     * Nothing here sizes an array or a collection from a number a sender chose, so a short message
     * cannot make the reader reserve a large amount of memory.</p>
     */
    public static Object readValue(ByteBuffer buffer, ObjectMapper mapper) {
        int[] depth = readDepth.get();
        if (depth[0] >= MAX_NESTING_DEPTH) {
            throw new IllegalStateException("Binary stream nests deeper than " + MAX_NESTING_DEPTH
                    + " levels; refusing to read further.");
        }
        depth[0]++;
        try {
            return readTaggedValue(buffer, mapper);
        } finally {
            // Decremented on the way out whether the read returned or threw, so a refused message
            // leaves the counter where the next message on this thread expects it.
            depth[0]--;
        }
    }

    /** The body of {@link #readValue(ByteBuffer, ObjectMapper)}, called with the depth already counted. */
    private static Object readTaggedValue(ByteBuffer buffer, ObjectMapper mapper) {
        byte tag = buffer.get();
        switch (tag) {
            case TAG_NULL:
                return null;
            case TAG_INT:
                return buffer.getInt();
            case TAG_LONG:
                return buffer.getLong();
            case TAG_DOUBLE:
                return buffer.getDouble();
            case TAG_FLOAT:
                return buffer.getFloat();
            case TAG_BOOLEAN:
                return buffer.get() != 0;
            case TAG_STRING:
                return readString(buffer);
            case TAG_REF: {
                String id = readString(buffer);
                if (openRecordIds.get().contains(id)) {
                    throw new IllegalStateException("The payload points back at a record that is "
                            + "still being read. A record's components are final, so it does not "
                            + "exist until all of them have been read; a reference to it from "
                            + "inside itself can never be resolved.");
                }
                return resolveReference(id, mapper);
            }
            case TAG_OBJECT: {
                String id = readString(buffer);
                String className = readString(buffer);
                return readObjectBody(buffer, mapper, id, className);
            }
            case TAG_RECORD: {
                String id = readString(buffer);
                String className = readString(buffer);
                return readRecordBody(buffer, mapper, id, className);
            }
            case TAG_SEALED: {
                String base = readString(buffer);
                if (buffer.remaining() < 1) {
                    throw new IllegalStateException("Binary stream ended after naming the sealed "
                            + "type " + base + ", before the value itself.");
                }
                byte inner = buffer.get();
                if (inner == TAG_NULL) {
                    return null;
                }
                if (inner == TAG_REF) {
                    String refId = readString(buffer);
                    if (openRecordIds.get().contains(refId)) {
                        throw new IllegalStateException("The payload points back at a record that "
                                + "is still being read.");
                    }
                    return resolveReference(refId, mapper);
                }
                if (inner != TAG_OBJECT && inner != TAG_RECORD) {
                    throw new IllegalStateException("Sealed type " + base + " is followed by tag 0x"
                            + Integer.toHexString(inner & 0xFF) + ", which is not a model value.");
                }
                String id = readString(buffer);
                String className = readString(buffer);
                // Checked before anything is built: a name the sealed type does not permit never
                // reaches a constructor.
                BinaryRegistry.checkPermitted(base, className);
                return inner == TAG_RECORD
                        ? readRecordBody(buffer, mapper, id, className)
                        : readObjectBody(buffer, mapper, id, className);
            }
            case TAG_SHORT:
                return buffer.getShort();
            case TAG_BYTE:
                return buffer.get();
            case TAG_CHAR:
                return buffer.getChar();
            case TAG_UUID: {
                // Matches the string-form write above (TeaVM-safe, no new UUID(long,long)).
                return UUID.fromString(readString(buffer));
            }
            case TAG_INSTANT: {
                long seconds = buffer.getLong();
                int nanos = buffer.getInt();
                return Instant.ofEpochSecond(seconds, nanos);
            }
            case TAG_ENUM: {
                String fqcn = readString(buffer);
                String name = readString(buffer);
                return name == null ? null : BinaryRegistry.resolveEnum(fqcn, name);
            }
            case TAG_LIST: {
                // Not pre-sized: an empty slot costs far more memory than the one tag byte that
                // asked for it, so capacity from the wire amplifies a small message into a big
                // allocation even when the count itself is within bounds.
                int size = checkedCount(buffer, 1, "list");
                List<Object> list = new ArrayList<>();
                for (int i = 0; i < size; i++) {
                    list.add(readValue(buffer, mapper));
                }
                return list;
            }
            case TAG_SET: {
                int size = checkedCount(buffer, 1, "set");
                Set<Object> set = new LinkedHashSet<>();
                for (int i = 0; i < size; i++) {
                    set.add(readValue(buffer, mapper));
                }
                return set;
            }
            case TAG_MAP: {
                // Two values per entry, so two tag bytes at least.
                int size = checkedCount(buffer, 2, "map");
                Map<Object, Object> map = new LinkedHashMap<>();
                for (int i = 0; i < size; i++) {
                    Object key = readValue(buffer, mapper);
                    Object value = readValue(buffer, mapper);
                    map.put(key, value);
                }
                return map;
            }
            case TAG_BIG_DECIMAL: {
                String text = readString(buffer);
                return text == null ? null : new java.math.BigDecimal(text);
            }
            case TAG_BIG_INTEGER: {
                String text = readString(buffer);
                return text == null ? null : new java.math.BigInteger(text);
            }
            case TAG_LOCAL_DATE:
                return java.time.LocalDate.ofEpochDay(buffer.getLong());
            case TAG_LOCAL_TIME:
                return java.time.LocalTime.ofNanoOfDay(buffer.getLong());
            case TAG_LOCAL_DATE_TIME: {
                java.time.LocalDate date = java.time.LocalDate.ofEpochDay(buffer.getLong());
                java.time.LocalTime time = java.time.LocalTime.ofNanoOfDay(buffer.getLong());
                return java.time.LocalDateTime.of(date, time);
            }
            case TAG_DURATION: {
                long seconds = buffer.getLong();
                int nanos = buffer.getInt();
                return java.time.Duration.ofSeconds(seconds, nanos);
            }
            case TAG_OPTIONAL:
                return java.util.Optional.ofNullable(readValue(buffer, mapper));
            case TAG_INT_ARRAY: {
                int len = checkedCount(buffer, 4, "int[]");
                int[] ints = new int[len];
                for (int i = 0; i < len; i++) {
                    ints[i] = buffer.getInt();
                }
                return ints;
            }
            case TAG_LONG_ARRAY: {
                int len = checkedCount(buffer, 8, "long[]");
                long[] longs = new long[len];
                for (int i = 0; i < len; i++) {
                    longs[i] = buffer.getLong();
                }
                return longs;
            }
            case TAG_DOUBLE_ARRAY: {
                int len = checkedCount(buffer, 8, "double[]");
                double[] doubles = new double[len];
                for (int i = 0; i < len; i++) {
                    doubles[i] = buffer.getDouble();
                }
                return doubles;
            }
            case TAG_FLOAT_ARRAY: {
                int len = checkedCount(buffer, 4, "float[]");
                float[] floats = new float[len];
                for (int i = 0; i < len; i++) {
                    floats[i] = buffer.getFloat();
                }
                return floats;
            }
            case TAG_SHORT_ARRAY: {
                int len = checkedCount(buffer, 2, "short[]");
                short[] shorts = new short[len];
                for (int i = 0; i < len; i++) {
                    shorts[i] = buffer.getShort();
                }
                return shorts;
            }
            case TAG_CHAR_ARRAY: {
                int len = checkedCount(buffer, 2, "char[]");
                char[] chars = new char[len];
                for (int i = 0; i < len; i++) {
                    chars[i] = buffer.getChar();
                }
                return chars;
            }
            case TAG_BOOLEAN_ARRAY: {
                int len = checkedCount(buffer, 1, "boolean[]");
                boolean[] booleans = new boolean[len];
                for (int i = 0; i < len; i++) {
                    booleans[i] = buffer.get() != 0;
                }
                return booleans;
            }
            case TAG_LAZY: {
                String handle = readString(buffer);
                LazyAdapter adapter = BinaryRegistry.getLazyAdapter();
                if (adapter == null) {
                    throw new IllegalStateException(
                            "Received a lazy reference but no LazyAdapter is installed on this tier. "
                            + "Lazy fields require zerozstack-store-eclipsestore on the server and the "
                            + "zeroz4j client runtime in the browser.");
                }
                return handle == null ? null : adapter.fromHandle(handle);
            }
            case TAG_BYTE_ARRAY: {
                int len = checkedCount(buffer, 1, "byte[]");
                byte[] arr = new byte[len];
                buffer.get(arr);
                return arr;
            }
            default:
                throw new IllegalStateException("Unknown type tag in binary stream: " + tag);
        }
    }

    /**
     * Builds an ordinary {@link DataModel} instance from the field bytes that follow, with its id
     * and class name already read.
     *
     * @param buffer    the source buffer, positioned at the model's field bytes
     * @param mapper    the object mapper resolving reference handles
     * @param id        the object id read from the payload
     * @param className the class name read from the payload
     * @return the instance, newly created or the one the mapper already held, with its fields set
     *
     * <p><b>Under the hood:</b> The instance is created and registered <i>before</i> its fields are
     * read, which is what lets something inside the graph point back at it.</p>
     */
    private static Object readObjectBody(ByteBuffer buffer, ObjectMapper mapper,
                                         String id, String className) {
        boolean local = isLocalId(id);
        Object obj = local ? payloadObjects.get().get(id) : mapper.getObject(id);
        boolean updatingExisting = obj != null;
        if (obj == null) {
            obj = BinaryRegistry.create(className);
            if (local) {
                payloadObjects.get().put(id, obj);
            } else {
                mapper.registerWithId(id, obj);
            }
        }
        int[] depth = modelReadDepth.get();
        depth[0]++;
        try {
            // Every model the payload reaches is shown to the server's guard, with or without a
            // lasting handle: a value the payload invented on the spot is still written into the
            // object graph, so "may this client write this" has to be asked about it too.
            ObjectMapper.checkDecodedModel(obj, depth[0]);
            @SuppressWarnings("unchecked")
            BinarySerializerDelegate<Object> delegate = BinaryRegistry.getDelegate(className);
            if (delegate != null) {
                delegate.read(obj, buffer, mapper);
            } else if (obj instanceof BinaryPackable) {
                ((BinaryPackable) obj).readFromBuffer(buffer, mapper);
            }
        } finally {
            endReadModel(depth);
        }
        if (updatingExisting) {
            // Fields of an instance the caller already holds were just replaced in place;
            // anything that read them needs to re-run. A freshly created instance has no
            // readers yet, so it is not reported.
            LiveMutationTracker.remoteObjectUpdated(obj);
        }
        return obj;
    }

    /**
     * Resolves a {@link #TAG_REF} back-reference from whichever table the name belongs to.
     *
     * @param id     the name read from the wire
     * @param mapper the registry, consulted only for a lasting handle
     * @return the instance, or null if nothing answers to that name
     */
    private static Object resolveReference(String id, ObjectMapper mapper) {
        if (isLocalId(id)) {
            return payloadObjects.get().get(id);
        }
        return mapper.getObject(id);
    }

    /**
     * Leaves one level of model nesting, forgetting the message's own names once the outermost
     * model is finished.
     *
     * <p>The boundary matches the writer's: identity holds within one top-level value and not
     * between two, so each element of a top-level list starts with an empty table on both sides.</p>
     *
     * @param depth the per-thread nesting counter
     */
    private static void endReadModel(int[] depth) {
        if (--depth[0] == 0) {
            payloadObjects.get().clear();
        }
    }

    /**
     * Builds a {@code record} {@link DataModel} from the component bytes that follow, with its id
     * and class name already read.
     *
     * @param buffer    the source buffer, positioned at the record's component bytes
     * @param mapper    the object mapper resolving reference handles
     * @param id        the object id read from the payload
     * @param className the record class name read from the payload
     * @return the newly constructed record, or {@code null} if the writer wrote a null
     * @throws IllegalStateException if the class is not a registered record model
     *
     * <p><b>Under the hood:</b> The opposite order to {@link #readObjectBody}: every component is
     * read first and the record is registered under its id only once the canonical constructor has
     * run. While that is happening the id sits in {@code openRecordIds}, so a payload referring
     * back into the record is refused with an explanation instead of resolving to null.</p>
     */
    private static Object readRecordBody(ByteBuffer buffer, ObjectMapper mapper,
                                         String id, String className) {
        BinaryRecordDelegate<Object> delegate = BinaryRegistry.getRecordDelegate(className);
        if (delegate == null) {
            throw new IllegalStateException("Unknown record @DataModel class: " + className
                    + ". Make sure it is registered.");
        }
        Set<String> open = openRecordIds.get();
        boolean added = open.add(id);
        Object value;
        int[] depth = modelReadDepth.get();
        depth[0]++;
        try {
            value = delegate.read(buffer, mapper);
            if (value != null) {
                // Named before the nesting level is left, so the name is forgotten with the rest of
                // the message's own names rather than outliving it.
                if (isLocalId(id)) {
                    payloadObjects.get().put(id, value);
                } else {
                    mapper.registerWithId(id, value);
                }
            }
        } finally {
            if (added) {
                open.remove(id);
            }
            endReadModel(depth);
        }
        return value;
    }

    /**
     * Writes a UTF-8 string to the buffer, prefixed by its length in bytes.
     *
     * @param buffer the destination buffer
     * @param str    the string to write (writes -1 integer length if null)
     *
     * <p><b>Under the hood:</b> Encodes string using {@link StandardCharsets#UTF_8}. Writes 4-byte length header followed
     * by raw UTF-8 byte payload into {@code buffer}.</p>
     */
    public static void writeString(ByteBuffer buffer, String str) {
        if (str == null) {
            buffer.putInt(-1);
        } else {
            byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
            buffer.putInt(bytes.length);
            buffer.put(bytes);
        }
    }

    /**
     * Reads a UTF-8 string from the buffer, prefixed by its 4-byte length.
     *
     * @param buffer the source buffer
     * @return decoded String, or {@code null} if the length header is -1
     * @throws IllegalStateException if the declared length is negative (other than the -1 that means
     *         null) or longer than the bytes actually remaining in the buffer
     *
     * <p><b>Under the hood:</b> Reads a 4-byte length integer via {@code buffer.getInt()}, checks it
     * against {@code buffer.remaining()}, allocates a byte array of that size, reads the bytes via
     * {@code buffer.get(bytes)}, and constructs a new String using the UTF-8 charset.</p>
     *
     * <p>The check comes first on purpose. Allocating from the declared length and letting
     * {@code buffer.get} fail afterwards means a ten-byte message claiming two billion characters
     * reserves two gigabytes before anything notices.</p>
     */
    public static String readString(ByteBuffer buffer) {
        int len = buffer.getInt();
        if (len == -1) {
            return null;
        }
        if (len < 0) {
            throw new IllegalStateException("Binary stream declares a string of " + len
                    + " bytes; only -1 (null) may be negative.");
        }
        if (len > buffer.remaining()) {
            throw new IllegalStateException("Binary stream declares a string of " + len
                    + " bytes but only " + buffer.remaining() + " bytes remain.");
        }
        byte[] bytes = new byte[len];
        buffer.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Reads a 4-byte element count and refuses it if the buffer could not possibly hold that many
     * elements.
     *
     * <p>The width matters. A {@code long[]} element occupies eight bytes, so a count equal to the
     * number of bytes left would still be eight times more memory than the whole message — a check
     * of "count against remaining bytes" alone lets a one-megabyte message allocate eight. For a
     * collection the width is the smallest an element can be: one tag byte, or two for a map entry
     * because it carries a key and a value.</p>
     *
     * @param buffer       the source buffer, positioned at the count
     * @param elementBytes the fewest bytes one element can occupy
     * @param what         what is being read, named in the exception message
     * @return the count, guaranteed to be zero or more and satisfiable by the bytes remaining
     * @throws IllegalStateException if the count is negative or larger than the remaining bytes allow
     */
    private static int checkedCount(ByteBuffer buffer, int elementBytes, String what) {
        int count = buffer.getInt();
        if (count < 0) {
            throw new IllegalStateException("Binary stream declares a negative " + what
                    + " length (" + count + ").");
        }
        int affordable = buffer.remaining() / elementBytes;
        if (count > affordable) {
            throw new IllegalStateException("Binary stream declares a " + what + " of " + count
                    + " elements, but the " + buffer.remaining() + " bytes remaining hold at most "
                    + affordable + ".");
        }
        return count;
    }

    /**
     * Packs an object value and its type tag into a {@link GrowableBuffer}.
     *
     * @param buffer the growable buffer to write to
     * @param val    the value to serialize
     * @param mapper the object mapper tracking reference handles
     *
     * <p><b>Under the hood:</b> Mirror implementation of {@link #writeValue(ByteBuffer, Object, ObjectMapper)}
     * optimized for writing into expanding {@link GrowableBuffer} without requiring explicit pre-allocation.</p>
     */
    public static void writeValue(GrowableBuffer buffer, Object val, ObjectMapper mapper) {
        if (val == null) {
            buffer.put(TAG_NULL);
        } else if (val instanceof Integer) {
            buffer.put(TAG_INT);
            buffer.putInt((Integer) val);
        } else if (val instanceof Long) {
            buffer.put(TAG_LONG);
            buffer.putLong((Long) val);
        } else if (val instanceof Double) {
            buffer.put(TAG_DOUBLE);
            buffer.putDouble((Double) val);
        } else if (val instanceof Float) {
            buffer.put(TAG_FLOAT);
            buffer.putFloat((Float) val);
        } else if (val instanceof Boolean) {
            buffer.put(TAG_BOOLEAN);
            buffer.put((byte) ((Boolean) val ? 1 : 0));
        } else if (val instanceof String) {
            buffer.put(TAG_STRING);
            writeString(buffer, (String) val);
        } else if (BinaryRegistry.getLazyAdapter() != null
                && BinaryRegistry.getLazyAdapter().isLazy(val)) {
            // Only the handle goes on the wire — resolving is the receiver's decision.
            buffer.put(TAG_LAZY);
            writeString(buffer, BinaryRegistry.getLazyAdapter().handleFor(val, mapper));
        } else if (BinaryRegistry.getDelegate(val.getClass().getName()) != null
                || BinaryRegistry.getRecordDelegate(val.getClass().getName()) != null
                || val instanceof BinaryPackable) {
            String className = val.getClass().getName();
            @SuppressWarnings("unchecked")
            BinarySerializerDelegate<Object> delegate = BinaryRegistry.getDelegate(className);
            BinaryRecordDelegate<Object> recordDelegate = BinaryRegistry.getRecordDelegate(className);
            Set<Object> seen = seenObjects.get();
            Set<Object> open = openRecords.get();
            boolean[] inside = insideHandleGraph.get();
            boolean enclosingBearsHandle = inside[0];
            boolean isRoot = seen.isEmpty();
            if (seen.add(val)) {
                try {
                    String id = idForNewValue(val, className, mapper, enclosingBearsHandle);
                    inside[0] = !isLocalId(id);
                    writeSealedBaseIfAny(buffer, className);
                    if (recordDelegate != null) {
                        open.add(val);
                        buffer.put(TAG_RECORD);
                        writeString(buffer, id);
                        writeString(buffer, className);
                        recordDelegate.write(val, buffer, mapper);
                    } else {
                        buffer.put(TAG_OBJECT);
                        writeString(buffer, id);
                        writeString(buffer, className);
                        if (delegate != null) {
                            delegate.write(val, buffer, mapper);
                        } else {
                            ((BinaryPackable) val).writeToBuffer(buffer, mapper);
                        }
                    }
                } finally {
                    inside[0] = enclosingBearsHandle;
                    open.remove(val);
                    if (isRoot) {
                        seen.clear();
                        open.clear();
                        endWrittenPayload();
                    }
                }
            } else {
                refuseRecordCycle(val, open);
                String id = idForRepeatedValue(val, mapper);
                buffer.put(TAG_REF);
                writeString(buffer, id);
            }
        } else if (val instanceof Short) {
            buffer.put(TAG_SHORT);
            buffer.putShort((Short) val);
        } else if (val instanceof Byte) {
            buffer.put(TAG_BYTE);
            buffer.put((Byte) val);
        } else if (val instanceof Character) {
            buffer.put(TAG_CHAR);
            buffer.putChar((Character) val);
        } else if (val instanceof UUID) {
            // TeaVM does not emulate UUID.getMostSignificantBits()/new UUID(long,long); serialize
            // via the canonical string form, which TeaVM supports, so the same code links on the
            // JVM server and the browser client.
            buffer.put(TAG_UUID);
            writeString(buffer, val.toString());
        } else if (val instanceof Instant) {
            buffer.put(TAG_INSTANT);
            Instant instant = (Instant) val;
            buffer.putLong(instant.getEpochSecond());
            buffer.putInt(instant.getNano());
        } else if (val instanceof Enum) {
            buffer.put(TAG_ENUM);
            Enum<?> constant = (Enum<?>) val;
            writeString(buffer, constant.getDeclaringClass().getName());
            writeString(buffer, constant.name());
        } else if (val instanceof List) {
            buffer.put(TAG_LIST);
            List<?> list = (List<?>) val;
            buffer.putInt(list.size());
            for (Object item : list) {
                writeValue(buffer, item, mapper);
            }
        } else if (val instanceof Set) {
            buffer.put(TAG_SET);
            Set<?> set = (Set<?>) val;
            buffer.putInt(set.size());
            for (Object item : set) {
                writeValue(buffer, item, mapper);
            }
        } else if (val instanceof Map) {
            buffer.put(TAG_MAP);
            Map<?, ?> map = (Map<?, ?>) val;
            buffer.putInt(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                writeValue(buffer, entry.getKey(), mapper);
                writeValue(buffer, entry.getValue(), mapper);
            }
        } else if (val instanceof java.math.BigDecimal) {
            buffer.put(TAG_BIG_DECIMAL);
            writeString(buffer, val.toString());
        } else if (val instanceof java.math.BigInteger) {
            buffer.put(TAG_BIG_INTEGER);
            writeString(buffer, val.toString());
        } else if (val instanceof java.time.LocalDate) {
            buffer.put(TAG_LOCAL_DATE);
            buffer.putLong(((java.time.LocalDate) val).toEpochDay());
        } else if (val instanceof java.time.LocalTime) {
            buffer.put(TAG_LOCAL_TIME);
            buffer.putLong(((java.time.LocalTime) val).toNanoOfDay());
        } else if (val instanceof java.time.LocalDateTime) {
            buffer.put(TAG_LOCAL_DATE_TIME);
            java.time.LocalDateTime dateTime = (java.time.LocalDateTime) val;
            buffer.putLong(dateTime.toLocalDate().toEpochDay());
            buffer.putLong(dateTime.toLocalTime().toNanoOfDay());
        } else if (val instanceof java.time.Duration) {
            buffer.put(TAG_DURATION);
            java.time.Duration duration = (java.time.Duration) val;
            buffer.putLong(duration.getSeconds());
            buffer.putInt(duration.getNano());
        } else if (val instanceof java.util.Optional) {
            buffer.put(TAG_OPTIONAL);
            writeValue(buffer, ((java.util.Optional<?>) val).orElse(null), mapper);
        } else if (val instanceof int[]) {
            buffer.put(TAG_INT_ARRAY);
            int[] ints = (int[]) val;
            buffer.putInt(ints.length);
            for (int item : ints) {
                buffer.putInt(item);
            }
        } else if (val instanceof long[]) {
            buffer.put(TAG_LONG_ARRAY);
            long[] longs = (long[]) val;
            buffer.putInt(longs.length);
            for (long item : longs) {
                buffer.putLong(item);
            }
        } else if (val instanceof double[]) {
            buffer.put(TAG_DOUBLE_ARRAY);
            double[] doubles = (double[]) val;
            buffer.putInt(doubles.length);
            for (double item : doubles) {
                buffer.putDouble(item);
            }
        } else if (val instanceof float[]) {
            buffer.put(TAG_FLOAT_ARRAY);
            float[] floats = (float[]) val;
            buffer.putInt(floats.length);
            for (float item : floats) {
                buffer.putFloat(item);
            }
        } else if (val instanceof short[]) {
            buffer.put(TAG_SHORT_ARRAY);
            short[] shorts = (short[]) val;
            buffer.putInt(shorts.length);
            for (short item : shorts) {
                buffer.putShort(item);
            }
        } else if (val instanceof char[]) {
            buffer.put(TAG_CHAR_ARRAY);
            char[] chars = (char[]) val;
            buffer.putInt(chars.length);
            for (char item : chars) {
                buffer.putChar(item);
            }
        } else if (val instanceof boolean[]) {
            buffer.put(TAG_BOOLEAN_ARRAY);
            boolean[] booleans = (boolean[]) val;
            buffer.putInt(booleans.length);
            for (boolean item : booleans) {
                buffer.put((byte) (item ? 1 : 0));
            }
        } else if (val instanceof byte[]) {
            buffer.put(TAG_BYTE_ARRAY);
            byte[] arr = (byte[]) val;
            buffer.putInt(arr.length);
            buffer.put(arr);
        } else {
            throw new IllegalArgumentException("Unsupported type for GrowableBuffer: " + val.getClass().getName());
        }
    }

    /**
     * Writes a UTF-8 string to a {@link GrowableBuffer}, prefixed by its length.
     *
     * @param buffer the destination growable buffer
     * @param str    the string to write (may be null)
     *
     * <p><b>Under the hood:</b> Writes 4-byte length integer into {@code buffer}, then writes UTF-8 encoded string bytes.</p>
     */
    public static void writeString(GrowableBuffer buffer, String str) {
        if (str == null) {
            buffer.putInt(-1);
        } else {
            byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
            buffer.putInt(bytes.length);
            buffer.put(bytes);
        }
    }
}
