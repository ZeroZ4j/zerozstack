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

/**
 * The processor used to skip an annotated element it did not recognise without a word — no
 * serializer, no warning — so the failure arrived at runtime on the first call that tried to send
 * one, a long way from the annotation. A record is the obvious shape to reach for, which made this a
 * trap rather than an edge case.
 *
 * <p>These tests pin that every unsupported target is now a compile error naming the element.</p>
 */
class SilentSkipTest {

    private List<String> errorsFor(Path tempDir, String source) throws Exception {
        Path srcDir = tempDir.resolve("src/com/test");
        Files.createDirectories(srcDir);
        Path file = srcDir.resolve(typeNameOf(source) + ".java");
        Files.writeString(file, source);

        Path outDir = tempDir.resolve("out");
        Files.createDirectories(outDir);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaCompiler.CompilationTask task = compiler.getTask(
                null, fileManager, diagnostics,
                Arrays.asList("-d", outDir.toString(), "-s", outDir.toString()),
                null,
                fileManager.getJavaFileObjectsFromFiles(Collections.singletonList(file.toFile())));
        task.setProcessors(Collections.singletonList(new RmiAnnotationProcessor()));
        task.call();

        return diagnostics.getDiagnostics().stream()
                .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                .map(d -> d.getMessage(null))
                .collect(Collectors.toList());
    }

    private static String typeNameOf(String source) {
        for (String keyword : new String[] { "public record ", "public class ",
                                             "public interface ", "public enum " }) {
            int at = source.indexOf(keyword);
            if (at >= 0) {
                String rest = source.substring(at + keyword.length());
                int end = rest.length();
                for (int i = 0; i < rest.length(); i++) {
                    char c = rest.charAt(i);
                    if (c == ' ' || c == '(' || c == '{' || c == '<') {
                        end = i;
                        break;
                    }
                }
                return rest.substring(0, end).trim();
            }
        }
        throw new IllegalArgumentException("no type name in:\n" + source);
    }

    @Test
    void aRecordIsRefusedRatherThanSkipped(@TempDir Path tempDir) throws Exception {
        List<String> errors = errorsFor(tempDir,
                "package com.test;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "@DataModel\n"
                + "public record Money(long amount, String currency) {}\n");

        String joined = String.join("\n", errors);
        org.junit.jupiter.api.Assertions.assertTrue(joined.contains("@DataModel"), joined);
        org.junit.jupiter.api.Assertions.assertTrue(joined.contains("record"),
                "the message must name what was wrong with it: " + joined);
    }

    @Test
    void theRecordMessageExplainsThePersistenceRuleToo(@TempDir Path tempDir) throws Exception {
        List<String> errors = errorsFor(tempDir,
                "package com.test;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "@DataModel\n"
                + "public record Money(long amount) {}\n");

        // Someone who hits this will next ask "can I use a record anywhere?" — answer it here rather
        // than let them find the EclipseStore restriction the hard way.
        org.junit.jupiter.api.Assertions.assertTrue(
                String.join("\n", errors).contains("EclipseStore"),
                "expected the persistence caveat in the message: " + errors);
    }

    @Test
    void anInterfaceIsRefused(@TempDir Path tempDir) throws Exception {
        List<String> errors = errorsFor(tempDir,
                "package com.test;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "@DataModel\n"
                + "public interface Marker {}\n");

        org.junit.jupiter.api.Assertions.assertTrue(
                String.join("\n", errors).contains("An interface"), errors.toString());
    }

    @Test
    void anEnumIsRefused(@TempDir Path tempDir) throws Exception {
        List<String> errors = errorsFor(tempDir,
                "package com.test;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "@DataModel\n"
                + "public enum Colour { RED, GREEN }\n");

        org.junit.jupiter.api.Assertions.assertTrue(
                String.join("\n", errors).contains("An enum"), errors.toString());
    }

    @Test
    void aRouteOnARecordIsRefused(@TempDir Path tempDir) throws Exception {
        // The router has the same rule and had the same silence: a route quietly absent from the
        // table is a URL that 404s with nothing to explain why.
        List<String> errors = errorsFor(tempDir,
                "package com.test;\n"
                + "import com.zeroz4j.api.Route;\n"
                + "@Route(\"/nowhere\")\n"
                + "public record Nowhere(String x) {}\n");

        String joined = String.join("\n", errors);
        org.junit.jupiter.api.Assertions.assertTrue(joined.contains("@Route"), joined);
    }
}
