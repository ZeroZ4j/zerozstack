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

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Compiles model sources with the real annotation processor, loads the classes it generated, runs
 * the generated registrar, and then sends values through the real {@code BinarySerializer}.
 *
 * <p>Checking the generated <i>text</i> would only prove that some code was written. This proves the
 * code works: same processor, same registry, same wire format the framework uses at run time.</p>
 *
 * <p>Each test must use its own package name. The registry is keyed by class name and is global to
 * the JVM, so two tests defining the same name would overwrite each other's delegates.</p>
 */
final class GeneratedWire {

    private final ClassLoader loader;

    private GeneratedWire(ClassLoader loader) {
        this.loader = loader;
    }

    /** Compiles the given sources, keyed by fully-qualified name, and runs their registrar. */
    static GeneratedWire compileAndRegister(Path tempDir, Map<String, String> sources)
            throws Exception {
        Path outDir = compile(tempDir, sources, true);
        URLClassLoader loader = new URLClassLoader(
                new URL[] { outDir.toUri().toURL() }, GeneratedWire.class.getClassLoader());
        Path generatedDir = outDir.resolve("com/zeroz4j/generated");
        try (java.util.stream.Stream<Path> files = Files.list(generatedDir)) {
            for (Path registrar : files
                    .filter(p -> p.getFileName().toString().endsWith(".class"))
                    // Skip the anonymous delegate classes the registrar declares inside itself.
                    .filter(p -> !p.getFileName().toString().contains("$"))
                    .collect(Collectors.toList())) {
                String simpleName = registrar.getFileName().toString().replace(".class", "");
                Object instance = loader.loadClass("com.zeroz4j.generated." + simpleName)
                        .getDeclaredConstructor().newInstance();
                instance.getClass().getMethod("registerAll").invoke(instance);
            }
        }
        return new GeneratedWire(loader);
    }

    /** Compiles the sources and returns every error the processor reported. */
    static List<String> errorsFrom(Path tempDir, Map<String, String> sources) throws Exception {
        List<String> errors = new ArrayList<>();
        compile(tempDir, sources, false, errors);
        return errors;
    }

    private static Path compile(Path tempDir, Map<String, String> sources, boolean mustSucceed)
            throws Exception {
        List<String> errors = new ArrayList<>();
        Path outDir = compile(tempDir, sources, mustSucceed, errors);
        if (mustSucceed && !errors.isEmpty()) {
            throw new AssertionError("Compilation failed:\n" + String.join("\n", errors));
        }
        return outDir;
    }

    private static Path compile(Path tempDir, Map<String, String> sources, boolean mustSucceed,
                                List<String> errors) throws Exception {
        Path srcDir = tempDir.resolve("src");
        List<File> files = new ArrayList<>();
        for (Map.Entry<String, String> source : sources.entrySet()) {
            Path file = srcDir.resolve(source.getKey().replace('.', '/') + ".java");
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue());
            files.add(file.toFile());
        }

        Path outDir = tempDir.resolve("out");
        Path genDir = tempDir.resolve("gen");
        Files.createDirectories(outDir);
        Files.createDirectories(genDir);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaCompiler.CompilationTask task = compiler.getTask(
                null, fileManager, diagnostics,
                Arrays.asList("-d", outDir.toString(), "-s", genDir.toString()),
                null, fileManager.getJavaFileObjectsFromFiles(files));
        task.setProcessors(Collections.singletonList(new RmiAnnotationProcessor()));
        task.call();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                errors.add(diagnostic.getMessage(null));
            }
        }
        return outDir;
    }

    /** A model class the compiled sources defined. */
    Class<?> type(String fqcn) throws Exception {
        return loader.loadClass(fqcn);
    }

    /** Builds an instance by matching argument count against the declared constructors. */
    Object make(String fqcn, Object... args) throws Exception {
        Class<?> type = type(fqcn);
        for (java.lang.reflect.Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (constructor.getParameterCount() == args.length) {
                constructor.setAccessible(true);
                return constructor.newInstance(args);
            }
        }
        throw new AssertionError("No " + args.length + "-argument constructor on " + fqcn);
    }

    /** Reads one property, by accessor name — {@code name()} on a record, {@code getName()} else. */
    static Object read(Object target, String accessor) throws Exception {
        return target.getClass().getMethod(accessor).invoke(target);
    }

    /** Sends a value through the wire format and reads it back, as a call argument would travel. */
    static Object roundTrip(Object value) {
        GrowableBuffer out = new GrowableBuffer();
        BinarySerializer.writeValue(out, value, new ObjectMapper());
        return BinarySerializer.readValue(ByteBuffer.wrap(out.toByteArray()), new ObjectMapper());
    }

    /** Convenience for building the source map in declaration order. */
    static Map<String, String> sources(String... namesAndBodies) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < namesAndBodies.length; i += 2) {
            map.put(namesAndBodies[i], namesAndBodies[i + 1]);
        }
        return map;
    }
}
