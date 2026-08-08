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
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The route table is generated at compile time because the browser runtime has no reflection, which
 * means a mistake in a {@code @Route} declaration has to be caught by the compiler or not at all.
 * These tests compile real sources through the processor and check both halves: that valid routes
 * produce a working table, and that the mistakes which would otherwise become a route that silently
 * never matches are refused outright.
 *
 * <p>The router interfaces are declared inline rather than depended on: {@code zerozstack-apt} must
 * not depend on {@code zerozstack-client}, and the processor identifies them by name anyway.</p>
 */
class RouteGenerationTest {

    /** Minimal stand-ins for the real router interfaces, compiled alongside the test sources. */
    private static final String ROUTER_STUBS_VIEW =
            "package com.zeroz4j.client.router;\n"
            + "public interface RouteView<T> {\n"
            + "    default T load(Object params) { return null; }\n"
            + "    Object render(T data, Object params);\n"
            + "}\n";

    private static final String ROUTER_STUBS_LAYOUT =
            "package com.zeroz4j.client.router;\n"
            + "public interface RouteLayout<T> {\n"
            + "    default T load(Object params) { return null; }\n"
            + "    Object render(T data, Object params, Object child);\n"
            + "}\n";

    // The generated table references these, so they have to compile too — which is itself the
    // check that the processor emits code matching the real signatures.
    private static final String ROUTER_STUBS_REGISTRAR =
            "package com.zeroz4j.client.router;\n"
            + "public interface RouteRegistrar {\n"
            + "    void registerAll();\n"
            + "}\n";

    private static final String ROUTER_STUBS_DEFINITION =
            "package com.zeroz4j.client.router;\n"
            + "public class RouteDefinition {\n"
            + "    public RouteDefinition(String pattern, String target, String layout,\n"
            + "            java.util.Set<String> roles, java.util.function.Supplier<Object> factory,\n"
            + "            boolean isLayout, String label, int order) { }\n"
            + "}\n";

    private static final String ROUTER_STUBS_REGISTRY =
            "package com.zeroz4j.client.router;\n"
            + "public class RouteRegistry {\n"
            + "    public static void register(RouteDefinition definition) { }\n"
            + "}\n";

    /** Compilation outcome: the processor's errors and whatever route table it wrote. */
    private static final class Result {
        List<String> errors = new ArrayList<>();
        String generatedTable = "";
        boolean wroteServiceFile;
    }

    private Result compile(Path tempDir, String... sources) throws Exception {
        Path srcDir = tempDir.resolve("src");
        Files.createDirectories(srcDir);
        Path outDir = tempDir.resolve("out");
        Files.createDirectories(outDir);

        List<File> files = new ArrayList<>();
        List<String> allSources = new ArrayList<>(Arrays.asList(sources));
        allSources.add(ROUTER_STUBS_VIEW);
        allSources.add(ROUTER_STUBS_LAYOUT);
        allSources.add(ROUTER_STUBS_REGISTRAR);
        allSources.add(ROUTER_STUBS_DEFINITION);
        allSources.add(ROUTER_STUBS_REGISTRY);

        for (String source : allSources) {
            String packageName = source.substring(source.indexOf("package ") + 8,
                    source.indexOf(";")).trim();
            String typeName = typeNameOf(source);
            Path packageDir = srcDir.resolve(packageName.replace('.', '/'));
            Files.createDirectories(packageDir);
            Path file = packageDir.resolve(typeName + ".java");
            Files.writeString(file, source);
            files.add(file.toFile());
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaCompiler.CompilationTask task = compiler.getTask(
                null, fileManager, diagnostics,
                Arrays.asList("-d", outDir.toString(), "-s", outDir.toString()),
                null,
                fileManager.getJavaFileObjectsFromFiles(files));
        task.setProcessors(List.of(new RmiAnnotationProcessor()));
        task.call();

        Result result = new Result();
        result.errors = diagnostics.getDiagnostics().stream()
                .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                .map(d -> d.getMessage(null))
                .collect(Collectors.toList());

        try (var stream = Files.walk(outDir)) {
            for (Path path : stream.collect(Collectors.toList())) {
                String name = path.getFileName().toString();
                if (name.startsWith("RouteRegistrar_") && name.endsWith(".java")) {
                    result.generatedTable = Files.readString(path);
                }
                if (name.equals("com.zeroz4j.client.router.RouteRegistrar")) {
                    result.wroteServiceFile = true;
                }
            }
        }
        return result;
    }

    private static String typeNameOf(String source) {
        for (String keyword : new String[] { "public class ", "public interface " }) {
            int at = source.indexOf(keyword);
            if (at >= 0) {
                String rest = source.substring(at + keyword.length());
                int end = rest.length();
                for (int i = 0; i < rest.length(); i++) {
                    char c = rest.charAt(i);
                    if (c == ' ' || c == '<' || c == '{' || c == '\n') {
                        end = i;
                        break;
                    }
                }
                return rest.substring(0, end).trim();
            }
        }
        throw new IllegalArgumentException("Cannot find a type name in:\n" + source);
    }

    private static String view(String className, String annotation) {
        return "package com.test;\n"
                + "import com.zeroz4j.api.Route;\n"
                + "import com.zeroz4j.api.RequiresRole;\n"
                + "import com.zeroz4j.client.router.RouteView;\n"
                + annotation + "\n"
                + "public class " + className + " implements RouteView<String> {\n"
                + "    public String load(Object params) { return \"x\"; }\n"
                + "    public Object render(String data, Object params) { return null; }\n"
                + "}\n";
    }

    private static String layout(String className, String annotation) {
        return "package com.test;\n"
                + "import com.zeroz4j.api.Route;\n"
                + "import com.zeroz4j.client.router.RouteLayout;\n"
                + annotation + "\n"
                + "public class " + className + " implements RouteLayout<String> {\n"
                + "    public Object render(String data, Object params, Object child) { return null; }\n"
                + "}\n";
    }

    // ---------------------------------------------------------------- generation

    @Test
    void aRouteBecomesATableEntry(@TempDir Path tempDir) throws Exception {
        Result result = compile(tempDir, view("TaskListView", "@Route(\"/tasks\")"));

        assertTrue(result.errors.isEmpty(), "unexpected errors: " + result.errors);
        assertTrue(result.generatedTable.contains("\"/tasks\""),
                "the path should appear in the table:\n" + result.generatedTable);
        assertTrue(result.generatedTable.contains("com.test.TaskListView::new"),
                "the factory must be a direct constructor reference, since the browser runtime "
                        + "cannot instantiate reflectively:\n" + result.generatedTable);
        assertTrue(result.wroteServiceFile,
                "without the ServiceLoader entry the table is generated but never loaded");
    }

    @Test
    void aLayoutReferenceIsRecordedByName(@TempDir Path tempDir) throws Exception {
        Result result = compile(tempDir,
                layout("AppShell", "@Route(\"/\")"),
                view("TaskListView", "@Route(value = \"/tasks\", layout = AppShell.class)"));

        assertTrue(result.errors.isEmpty(), "unexpected errors: " + result.errors);
        assertTrue(result.generatedTable.contains("\"com.test.AppShell\""),
                "the layout should be linked by name:\n" + result.generatedTable);
        assertTrue(result.generatedTable.contains("true"),
                "the layout entry should be flagged as one:\n" + result.generatedTable);
    }

    @Test
    void aRouteWithoutALayoutRecordsNull(@TempDir Path tempDir) throws Exception {
        Result result = compile(tempDir, view("TaskListView", "@Route(\"/tasks\")"));

        assertTrue(result.generatedTable.contains("null"),
                "an unset layout must be null, not the NoLayout marker:\n" + result.generatedTable);
        assertFalse(result.generatedTable.contains("NoLayout"));
    }

    @Test
    void requiredRolesAreCarriedIntoTheTable(@TempDir Path tempDir) throws Exception {
        Result result = compile(tempDir,
                view("AdminView", "@Route(\"/admin\")\n@RequiresRole({\"admin\", \"owner\"})"));

        assertTrue(result.errors.isEmpty(), "unexpected errors: " + result.errors);
        assertTrue(result.generatedTable.contains("roles.add(\"admin\")"), result.generatedTable);
        assertTrue(result.generatedTable.contains("roles.add(\"owner\")"), result.generatedTable);
    }

    @Test
    void theLabelDefaultsToTheClassNameWithoutItsSuffix(@TempDir Path tempDir) throws Exception {
        Result result = compile(tempDir, view("TaskListView", "@Route(\"/tasks\")"));

        assertTrue(result.generatedTable.contains("\"TaskList\""),
                "expected a tidied default label:\n" + result.generatedTable);
    }

    // ---------------------------------------------------------------- refusals

    @Test
    void aRouteImplementingNeitherInterfaceIsRefused(@TempDir Path tempDir) throws Exception {
        String source = "package com.test;\n"
                + "import com.zeroz4j.api.Route;\n"
                + "@Route(\"/nowhere\")\n"
                + "public class Orphan {\n"
                + "    public Orphan() {}\n"
                + "}\n";

        Result result = compile(tempDir, source);

        assertTrue(result.errors.stream().anyMatch(e -> e.contains("RouteView")),
                "expected a refusal naming the interfaces: " + result.errors);
    }

    @Test
    void aRouteImplementingBothInterfacesIsRefused(@TempDir Path tempDir) throws Exception {
        String source = "package com.test;\n"
                + "import com.zeroz4j.api.Route;\n"
                + "import com.zeroz4j.client.router.RouteView;\n"
                + "import com.zeroz4j.client.router.RouteLayout;\n"
                + "@Route(\"/both\")\n"
                + "public class Both implements RouteView<String>, RouteLayout<String> {\n"
                + "    public Object render(String data, Object params) { return null; }\n"
                + "    public Object render(String data, Object params, Object child) { return null; }\n"
                + "}\n";

        Result result = compile(tempDir, source);

        assertTrue(result.errors.stream().anyMatch(e -> e.contains("ambiguous")),
                "expected a refusal: " + result.errors);
    }

    @Test
    void aRouteWithoutANoArgConstructorIsRefused(@TempDir Path tempDir) throws Exception {
        String source = "package com.test;\n"
                + "import com.zeroz4j.api.Route;\n"
                + "import com.zeroz4j.client.router.RouteView;\n"
                + "@Route(\"/tasks\")\n"
                + "public class NeedsArgs implements RouteView<String> {\n"
                + "    public NeedsArgs(String dependency) {}\n"
                + "    public Object render(String data, Object params) { return null; }\n"
                + "}\n";

        Result result = compile(tempDir, source);

        assertTrue(result.errors.stream().anyMatch(e -> e.contains("no-argument constructor")),
                "expected a refusal naming the constructor: " + result.errors);
    }

    @Test
    void aPathWithoutALeadingSlashIsRefused(@TempDir Path tempDir) throws Exception {
        Result result = compile(tempDir, view("TaskListView", "@Route(\"tasks\")"));

        assertTrue(result.errors.stream().anyMatch(e -> e.contains("must start with")),
                "expected a refusal: " + result.errors);
    }
}
