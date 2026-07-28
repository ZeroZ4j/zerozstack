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

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@code @DataModel} field whose type cannot be serialized used to compile cleanly and fail at
 * runtime — silently, on the event and shared-signal paths. These tests pin the compile-time refusal.
 *
 * <p>The check is deliberately a blocklist. Half of these tests exist to prove it does <b>not</b>
 * reject valid programs: a field typed {@code Object}, an interface, or a supported value type must
 * still compile, because serialization dispatches on the runtime type.</p>
 */
class FieldTypeCheckTest {

    /** Compiles a single @DataModel source and returns the processor's error messages. */
    private List<String> errorsFor(Path tempDir, String className, String fields, String imports)
            throws Exception {
        Path srcDir = tempDir.resolve("src");
        Files.createDirectories(srcDir.resolve("com/test"));

        String source = "package com.test;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + imports
                + "@DataModel\n"
                + "public class " + className + " {\n"
                + fields
                + "    public " + className + "() {}\n"
                + "}\n";
        Path file = srcDir.resolve("com/test/" + className + ".java");
        Files.writeString(file, source);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        Path outDir = tempDir.resolve("out");
        Files.createDirectories(outDir);

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaCompiler.CompilationTask task = compiler.getTask(
                null, fileManager, diagnostics,
                Arrays.asList("-d", outDir.toString(), "-s", outDir.toString()),
                null,
                fileManager.getJavaFileObjectsFromFiles(Collections.singletonList(file.toFile())));
        task.setProcessors(Collections.singletonList(new RmiAnnotationProcessor()));
        task.call();

        // Only the field-type check's own diagnostics: these bare models have no accessors, so the
        // generated serializer reports its own unrelated errors.
        return diagnostics.getDiagnostics().stream()
                .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                .map(d -> d.getMessage(null))
                .filter(m -> m.contains("@DataModel field"))
                .collect(Collectors.toList());
    }

    @Test
    void objectArraysAreRefused(@TempDir Path tempDir) throws Exception {
        List<String> errors = errorsFor(tempDir, "WithArray",
                "    private String[] tags;\n", "");

        assertTrue(errors.stream().anyMatch(e -> e.contains("array of")),
                "an object array must be refused: " + errors);
        assertTrue(errors.stream().anyMatch(e -> e.contains("List<")),
                "the error must suggest a List: " + errors);
    }

    @Test
    void primitiveArraysAreAccepted(@TempDir Path tempDir) throws Exception {
        List<String> errors = errorsFor(tempDir, "WithPrimitiveArrays",
                "    private byte[] blob;\n"
                + "    private int[] counts;\n"
                + "    private double[] samples;\n"
                + "    private boolean[] flags;\n", "");

        assertTrue(errors.isEmpty(), "primitive arrays are supported: " + errors);
    }

    @Test
    void zoneAwareDateTimesAreRefusedWithAdvice(@TempDir Path tempDir) throws Exception {
        List<String> errors = errorsFor(tempDir, "WithZoned",
                "    private java.time.ZonedDateTime when;\n", "");

        assertTrue(errors.stream().anyMatch(e -> e.contains("ZonedDateTime")), errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.contains("Instant")),
                "the error must name the replacement: " + errors);
    }

    @Test
    void legacyDateIsRefused(@TempDir Path tempDir) throws Exception {
        List<String> errors = errorsFor(tempDir, "WithLegacyDate",
                "    private java.util.Date created;\n", "");
        assertTrue(errors.stream().anyMatch(e -> e.contains("java.util.Date")), errors.toString());
    }

    @Test
    void concreteCollectionsOutsideTheRebuiltHierarchyAreRefused(@TempDir Path tempDir) throws Exception {
        List<String> errors = errorsFor(tempDir, "WithTreeSet",
                "    private java.util.TreeSet<String> sorted;\n", "");

        assertTrue(errors.stream().anyMatch(e -> e.contains("TreeSet")), errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.contains("interface type")),
                "the error must explain the cast failure: " + errors);
    }

    @Test
    void interfaceCollectionsAreAccepted(@TempDir Path tempDir) throws Exception {
        List<String> errors = errorsFor(tempDir, "WithCollections",
                "    private java.util.List<String> items;\n"
                + "    private java.util.Set<String> tags;\n"
                + "    private java.util.Map<String, Integer> counts;\n", "");

        assertTrue(errors.isEmpty(), "interface collection types are the supported form: " + errors);
    }

    @Test
    void arrayListAndHashSetAreAccepted(@TempDir Path tempDir) throws Exception {
        // These survive their cast because collections are rebuilt as ArrayList and LinkedHashSet,
        // and LinkedHashSet extends HashSet. Refusing them would be a false positive.
        List<String> errors = errorsFor(tempDir, "WithConcreteButCompatible",
                "    private java.util.ArrayList<String> items;\n"
                + "    private java.util.HashSet<String> tags;\n"
                + "    private java.util.HashMap<String, Integer> counts;\n", "");

        assertTrue(errors.isEmpty(), "these concrete types are cast-compatible: " + errors);
    }

    @Test
    void supportedValueTypesAreAccepted(@TempDir Path tempDir) throws Exception {
        List<String> errors = errorsFor(tempDir, "WithValues",
                "    private String name;\n"
                + "    private int count;\n"
                + "    private java.util.UUID id;\n"
                + "    private java.math.BigDecimal amount;\n"
                + "    private java.time.Instant at;\n"
                + "    private java.time.LocalDate day;\n"
                + "    private java.time.Duration elapsed;\n"
                + "    private java.util.Optional<String> note;\n", "");

        assertTrue(errors.isEmpty(), "every one of these has a wire tag: " + errors);
    }

    @Test
    void objectAndInterfaceTypedFieldsAreNotRejected(@TempDir Path tempDir) throws Exception {
        // Serialization dispatches on the runtime type, so these are legitimate. An allowlist-based
        // check would reject them, which is why the check is a blocklist.
        List<String> errors = errorsFor(tempDir, "WithLooseTypes",
                "    private Object payload;\n"
                + "    private Comparable<String> key;\n"
                + "    private CharSequence text;\n", "");

        assertFalse(errors.stream().anyMatch(e -> e.contains("unsupported type")),
                "loosely typed fields must still compile: " + errors);
    }

    @Test
    void staticAndTransientFieldsAreIgnored(@TempDir Path tempDir) throws Exception {
        List<String> errors = errorsFor(tempDir, "WithSkippedFields",
                "    private static java.time.ZonedDateTime SHARED;\n"
                + "    private transient java.util.Date scratch;\n"
                + "    private String name;\n", "");

        assertTrue(errors.isEmpty(),
                "fields that are never serialized must not be checked: " + errors);
    }
}
