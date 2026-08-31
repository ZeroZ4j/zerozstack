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
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Central registry mapping class names to dynamic object creators and serializer delegates,
 * bypassing reflection inside browser WebAssembly compilation sandbox environments.
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>State Mutations:</b> Maintains two thread-safe {@link ConcurrentHashMap} maps ({@code suppliers} and {@code delegates})
 *       keyed by class FQCN. Modifying registration alters global serialization factory lookup.</li>
 *   <li><b>Service Discovery:</b> {@link #init()} executes SPI discovery via {@link ServiceLoader} to automatically trigger
 *       all generated {@link BinaryRegistrar} classes.</li>
 *   <li><b>Side Effects:</b> Creates object instances without using Java reflection {@code Class.forName()} or
 *       {@code Constructor.newInstance()}, making it fully TeaVM WasmGC compatible.</li>
 * </ul>
 */
public class BinaryRegistry {
    private static final Map<String, Supplier<Object>> suppliers = new ConcurrentHashMap<>();
    private static final Map<String, BinarySerializerDelegate<?>> delegates = new ConcurrentHashMap<>();
    /** Reflection-free enum resolvers keyed by declaring-class FQCN (e.g. {@code Priority::valueOf}). */
    private static final Map<String, Function<String, Enum<?>>> enumResolvers = new ConcurrentHashMap<>();
    /** Instrumented (mutation-tracking) suppliers for @ClientWritable models. */
    private static final Map<String, Supplier<Object>> liveSuppliers = new ConcurrentHashMap<>();

    /**
     * Class names whose instances are given a lasting handle when they go on the wire.
     *
     * <p>A handle exists so that a later message can name the same object again — a client edit
     * coming back up, a re-sync after a reconnect, a lock request. That is exactly what a
     * {@code @LiveSync} model is for, so the generated registrar marks those and nothing else.
     * Everything else on the wire is a value: it is written with a name that means nothing outside
     * the one message it traveled in, and the registry never hears about it.</p>
     */
    // Collections.newSetFromMap rather than ConcurrentHashMap.newKeySet(): TeaVM does not
    // emulate newKeySet, and this class is compiled for the browser too.
    private static final Set<String> handleBearing =
            java.util.Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static volatile boolean preferLiveInstances = false;

    /**
     * Read/write pairs for {@code record} models, keyed by FQCN. Separate from {@code delegates}
     * because a record is constructed from its components rather than filled in afterwards.
     */
    private static final Map<String, BinaryRecordDelegate<?>> recordDelegates = new ConcurrentHashMap<>();

    /** For each sealed base FQCN, the class names it permits. Nothing else may be read for it. */
    private static final Map<String, Set<String>> sealedPermitted = new ConcurrentHashMap<>();

    /** Reverse of {@link #sealedPermitted}: the sealed base a permitted class belongs to. */
    private static final Map<String, String> sealedBases = new ConcurrentHashMap<>();

    /** Tier-specific handling of EclipseStore {@code Lazy} fields; null when the tier has none. */
    private static volatile LazyAdapter lazyAdapter;

    /**
     * Installs the tier's lazy-reference handling. Called by the server engine and the client
     * bootstrap, not by applications.
     *
     * @param adapter the adapter, or null to detach
     */
    public static void setLazyAdapter(LazyAdapter adapter) {
        lazyAdapter = adapter;
    }

    /**
     * @return the installed lazy adapter, or null when this tier does not support lazy references
     */
    public static LazyAdapter getLazyAdapter() {
        return lazyAdapter;
    }

    /**
     * Discovers and invokes all {@link BinaryRegistrar} implementations on the
     * classpath via {@link ServiceLoader}. Call this once at application startup
     * instead of manually invoking generated registrar classes.
     *
     * <p><b>Under the hood:</b> Scans SPI META-INF/services/com.zeroz4j.api.BinaryRegistrar.
     * For each discovered registrar, invokes {@link BinaryRegistrar#registerAll()}, which populates
     * the static {@code suppliers} and {@code delegates} maps in {@code BinaryRegistry}.</p>
     */
    public static void init() {
        for (BinaryRegistrar registrar : ServiceLoader.load(BinaryRegistrar.class)) {
            registrar.registerAll();
        }
    }

    /**
     * Registers a supplier factory and serializer delegate for a given binary model class name.
     *
     * @param <T>       the type of object to register
     * @param className the canonical FQCN of the model class
     * @param supplier  the zero-arg constructor supplier
     * @param delegate  the compile-time generated binary serializer delegate
     *
     * <p><b>Under the hood:</b> Puts {@code supplier} into the {@code suppliers} map and {@code delegate} into
     * the {@code delegates} map, keying both by {@code className}. Overwrites any previous registration for the same key.</p>
     */
    public static <T> void register(String className, Supplier<T> supplier, BinarySerializerDelegate<T> delegate) {
        suppliers.put(className, (Supplier<Object>) (Supplier<?>) supplier);
        delegates.put(className, delegate);
    }

    /**
     * Registers the mutation-tracking supplier for a {@code @ClientWritable} model.
     * Called by generated registrars; only used when {@link #setPreferLiveInstances(boolean)}
     * has enabled live instantiation (the Wasm client tier).
     *
     * @param className the canonical FQCN of the model class
     * @param supplier  supplier for the generated {@code <Model>_Live} subclass
     */
    public static void registerLive(String className, Supplier<?> supplier) {
        liveSuppliers.put(className, (Supplier<Object>) (Supplier<?>) supplier);
        registerHandleBearing(className);
    }

    /**
     * Marks a class whose instances need a lasting handle on the wire.
     *
     * <p>Called for you by {@link #registerLive(String, Supplier)}, which the generated registrar
     * invokes for every {@code @LiveSync} model. It is public so a hand-written test that registers
     * models itself can say the same thing.</p>
     *
     * @param className the canonical FQCN of the model class
     */
    public static void registerHandleBearing(String className) {
        handleBearing.add(className);
    }

    /**
     * @param className the runtime class name of a value about to be written
     * @return true when instances of it are given a lasting handle rather than a name good for one
     *         message
     *
     * <p><b>Under the hood:</b> a set membership test. On the browser tier the runtime class of a
     * live instance is the generated {@code <Model>_Live} subclass, which is not in this set; the
     * writer recognizes those by {@link LiveObservable} instead.</p>
     */
    public static boolean bearsHandle(String className) {
        return handleBearing.contains(className);
    }

    /**
     * Enables or disables preferring mutation-tracking instances during deserialization.
     * The Wasm client runtime enables this at bootstrap; the server never does.
     *
     * @param prefer true to instantiate {@code <Model>_Live} subclasses where registered
     */
    public static void setPreferLiveInstances(boolean prefer) {
        preferLiveInstances = prefer;
    }
    
    /**
     * Legacy registration for manual {@link BinaryPackable} implementations.
     *
     * @param className the canonical class name
     * @param supplier  the supplier returning a new instance
     *
     * <p><b>Under the hood:</b> Wraps the {@code BinaryPackable} object's instance methods
     * ({@code writeToBuffer} and {@code readFromBuffer}) inside an anonymous {@link BinarySerializerDelegate} adapter
     * and puts both the supplier and delegate into internal maps.</p>
     */
    public static void register(String className, Supplier<BinaryPackable> supplier) {
        suppliers.put(className, (Supplier<Object>) (Supplier<?>) supplier);
        delegates.put(className, new BinarySerializerDelegate<BinaryPackable>() {
            @Override
            public void write(BinaryPackable obj, GrowableBuffer buffer, ObjectMapper mapper) {
                obj.writeToBuffer(buffer, mapper);
            }
            @Override
            public void read(BinaryPackable obj, ByteBuffer buffer, ObjectMapper mapper) {
                obj.readFromBuffer(buffer, mapper);
            }
        });
    }

    /**
     * Instantiates an uninitialized object instance of the specified class name using its registered supplier.
     *
     * @param className the canonical FQCN of the model class
     * @return a new, unpopulated instance of the model class
     * @throws IllegalArgumentException if the class is not registered
     *
     * <p><b>Under the hood:</b> Fetches the registered {@link Supplier} from {@code suppliers} map.
     * Executes {@code supplier.get()} to instantiate the object without reflection.</p>
     */
    public static Object create(String className) {
        if (preferLiveInstances) {
            Supplier<Object> liveSupplier = liveSuppliers.get(className);
            if (liveSupplier != null) {
                return liveSupplier.get();
            }
        }
        Supplier<Object> supplier = suppliers.get(className);
        if (supplier == null) {
            throw new IllegalArgumentException("Unknown @DataModel class: " + className
                + ". Make sure it is registered.");
        }
        return supplier.get();
    }
    
    /**
     * Registers a reflection-free resolver for an enum type, so enum constants appearing
     * inside generic containers ({@code List<MyEnum>}, {@code Map<..,MyEnum>}) can be
     * reconstructed from their {@code name()} without {@code Class.forName}/{@code Enum.valueOf}
     * reflection (the TeaVM/Wasm client tier has no runtime reflection).
     *
     * @param fqcn     the enum's declaring-class fully-qualified name
     * @param resolver a function mapping a constant name to its enum value, typically {@code MyEnum::valueOf}
     *
     * <p><b>Under the hood:</b> Puts {@code resolver} into the {@code enumResolvers} map keyed by
     * {@code fqcn}. Generated registrars call this for every enum type reachable from a {@code @DataModel}.</p>
     */
    public static void registerEnum(String fqcn, Function<String, Enum<?>> resolver) {
        enumResolvers.put(fqcn, resolver);
    }

    /**
     * Resolves an enum constant from its declaring-class FQCN and constant name using a
     * previously {@linkplain #registerEnum registered} resolver.
     *
     * @param fqcn the enum's declaring-class fully-qualified name
     * @param name the enum constant name ({@code Enum#name()})
     * @return the resolved enum constant, or {@code null} if {@code name} is {@code null}
     * @throws IllegalArgumentException if no resolver is registered for {@code fqcn}
     *
     * <p><b>Under the hood:</b> Looks up the resolver in {@code enumResolvers} and applies it to
     * {@code name}. No reflection is involved, keeping the read path TeaVM-safe.</p>
     */
    public static Enum<?> resolveEnum(String fqcn, String name) {
        if (name == null) {
            return null;
        }
        Function<String, Enum<?>> resolver = enumResolvers.get(fqcn);
        if (resolver == null) {
            throw new IllegalArgumentException("Unknown enum type: " + fqcn
                + ". Make sure it is registered via BinaryRegistry.registerEnum(...).");
        }
        return resolver.apply(name);
    }

    /**
     * Registers the generated read/write pair for a {@code record} model. Called by generated
     * registrars.
     *
     * @param <T>       the record type
     * @param className the canonical FQCN of the record
     * @param delegate  the compile-time generated record delegate
     *
     * <p><b>Under the hood:</b> Puts {@code delegate} into the {@code recordDelegates} map. A record
     * has no entry in {@code suppliers}: there is no empty instance to supply, because every
     * component is set by the canonical constructor.</p>
     */
    public static <T> void registerRecord(String className, BinaryRecordDelegate<T> delegate) {
        recordDelegates.put(className, delegate);
    }

    /**
     * Retrieves the generated read/write pair for a record model.
     *
     * @param <T>       the record type
     * @param className the canonical FQCN of the record
     * @return the registered {@link BinaryRecordDelegate}, or {@code null} if the class is not a
     *         registered record model
     */
    @SuppressWarnings("unchecked")
    public static <T> BinaryRecordDelegate<T> getRecordDelegate(String className) {
        return (BinaryRecordDelegate<T>) recordDelegates.get(className);
    }

    /**
     * Registers the complete permitted set of a sealed {@link DataModel} base — a sealed interface
     * or sealed abstract class — so the reader can accept those types and nothing else.
     *
     * @param baseClassName the FQCN of the sealed interface or sealed abstract class
     * @param permitted     the FQCNs of every class it permits
     * @throws IllegalStateException if a permitted class already belongs to a different sealed base;
     *         the wire names a base and the reader resolves the type within it, so one class cannot
     *         answer to two
     *
     * <p><b>Under the hood:</b> Stores the permitted set under the base name and the base name under
     * each permitted class. The set is fixed at compile time — {@code sealed} means the compiler
     * knows every member — which is what lets the reader refuse an unknown name outright rather than
     * instantiating it and finding out later.</p>
     */
    public static void registerSealed(String baseClassName, String[] permitted) {
        Set<String> members = new LinkedHashSet<>(Arrays.asList(permitted));
        sealedPermitted.put(baseClassName, members);
        for (String member : members) {
            String existing = sealedBases.put(member, baseClassName);
            if (existing != null && !existing.equals(baseClassName)) {
                throw new IllegalStateException(member + " is permitted by two sealed wire types, "
                        + existing + " and " + baseClassName
                        + ". A value on the wire names one base, so a class can belong to only one.");
            }
        }
    }

    /**
     * @param className a concrete model class name
     * @return the sealed base it was registered under, or {@code null} if it belongs to none
     */
    public static String sealedBaseOf(String className) {
        return sealedBases.get(className);
    }

    /**
     * @param baseClassName the FQCN of a sealed base
     * @return the classes that base permits, or {@code null} if the base is not registered
     */
    public static Set<String> permittedFor(String baseClassName) {
        return sealedPermitted.get(baseClassName);
    }

    /**
     * Refuses a class name the wire has offered for a sealed base unless that base permits it.
     * Called before anything is instantiated, so a payload naming an unpermitted type never
     * reaches a constructor.
     *
     * @param baseClassName the sealed base named by the payload
     * @param className     the concrete class name the payload wants built
     * @throws IllegalStateException if the base is unknown, or does not permit that class
     */
    public static void checkPermitted(String baseClassName, String className) {
        Set<String> members = sealedPermitted.get(baseClassName);
        if (members == null) {
            throw new IllegalStateException("Unknown sealed wire type: " + baseClassName
                    + ". Annotate the sealed interface or sealed abstract class with @DataModel so "
                    + "its permitted set is registered.");
        }
        if (!members.contains(className)) {
            throw new IllegalStateException("Refusing to build " + className + ": " + baseClassName
                    + " does not permit it. Permitted: " + members + ".");
        }
    }

    /**
     * Retrieves the compiled serializer delegate for a given model class name.
     *
     * @param <T>       the object type
     * @param className the canonical FQCN of the model class
     * @return the registered {@link BinarySerializerDelegate}, or {@code null} if not found
     *
     * <p><b>Under the hood:</b> Performs a thread-safe map lookup on {@code delegates.get(className)}.</p>
     */
    @SuppressWarnings("unchecked")
    public static <T> BinarySerializerDelegate<T> getDelegate(String className) {
        return (BinarySerializerDelegate<T>) delegates.get(className);
    }
}
