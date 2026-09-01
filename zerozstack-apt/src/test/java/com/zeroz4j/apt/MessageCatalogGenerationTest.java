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

import com.zeroz4j.api.i18n.Message;
import com.zeroz4j.api.i18n.Messages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A catalog's keys become methods, so a misspelled key and a wrong number of values are both
 * compile errors instead of something wrong on somebody's screen.
 *
 * <p>Each of the refusals below is proved by writing the fault and reading what the compiler says
 * about it. A check nobody has watched fail is a check nobody knows works.</p>
 */
class MessageCatalogGenerationTest {

    @Test
    @DisplayName("a key becomes a method, and each blank becomes a parameter")
    void keysBecomeMethods(@TempDir Path temp) throws Exception {
        Build build = compile(temp,
                "task.add = Add task\n"
                + "task.remaining = {0} of {1} tasks left\n"
                + "greeting-for.user = Hello {0}\n");

        assertTrue(build.succeeded, build.messages());
        String text = build.read("com/example/AppText_Text.java");
        assertTrue(text.contains("public static com.zeroz4j.api.i18n.Message taskAdd()"), text);
        assertTrue(text.contains(
                "public static com.zeroz4j.api.i18n.Message taskRemaining("
                + "java.lang.Object arg0, java.lang.Object arg1)"), text);
        assertTrue(text.contains(
                "public static com.zeroz4j.api.i18n.Message greetingForUser(java.lang.Object arg0)"),
                text);

        String catalog = build.read("com/example/AppText_Catalog.java");
        assertTrue(catalog.contains("case \"task.remaining\":"), catalog);
        assertTrue(catalog.contains("return \"{0} of {1} tasks left\";"), catalog);
        assertTrue(catalog.contains("FALLBACK_LANGUAGE = \"en\""), catalog);
    }

    @Test
    @DisplayName("the generated classes work: a message names its catalog and fills in its blanks")
    void theGeneratedClassesWork(@TempDir Path temp) throws Exception {
        Build build = compile(temp, "task.remaining = {0} of {1} tasks left\n");
        assertTrue(build.succeeded, build.messages());

        try (URLClassLoader loader = build.loader()) {
            Class<?> textClass = loader.loadClass("com.example.AppText_Text");
            Method taskRemaining = textClass.getMethod("taskRemaining", Object.class, Object.class);
            Message message = (Message) taskRemaining.invoke(null, 3, 7);

            assertEquals("i18n/app", message.catalog());
            assertEquals("task.remaining", message.key());

            Class<?> catalogClass = loader.loadClass("com.example.AppText_Catalog");
            String pattern = (String) catalogClass.getMethod("lookup", String.class)
                    .invoke(null, "task.remaining");
            assertEquals("3 of 7 tasks left", Messages.substitute(pattern, message.arguments()));
        }
    }

    @Test
    @DisplayName("two keys that would become the same method are refused, and both are named")
    void twoKeysCannotShareAMethodName(@TempDir Path temp) throws Exception {
        Build build = compile(temp, "task.add = Add task\ntaskAdd = Also add task\n");

        assertFalse(build.succeeded, "the build must fail rather than dropping one of them");
        assertTrue(build.messages().contains("both become the method taskAdd()"), build.messages());
        System.out.println("planted two keys with one method name, the compiler said:"
                + System.lineSeparator() + build.errorsOnly());
    }

    @Test
    @DisplayName("a pattern whose blanks skip a number is refused")
    void blanksMustRunFromZero(@TempDir Path temp) throws Exception {
        Build build = compile(temp, "task.remaining = {0} of {2} tasks left\n");

        assertFalse(build.succeeded, "a blank nobody can pass a value for must fail the build");
        assertTrue(build.messages().contains("run from {0} with no gaps"), build.messages());
        System.out.println("planted a gap in the blanks, the compiler said:"
                + System.lineSeparator() + build.errorsOnly());
    }

    @Test
    @DisplayName("a pattern that looks like MessageFormat is warned about, not silently obeyed")
    void aMessageFormatPatternIsWarnedAbout(@TempDir Path temp) throws Exception {
        Build build = compile(temp, "invoice.total = Total {0,number,currency}\n");

        assertTrue(build.succeeded, build.messages());
        assertTrue(build.messages().contains("reads like a MessageFormat pattern"), build.messages());
        System.out.println("planted a MessageFormat pattern, the compiler said:"
                + System.lineSeparator() + build.messages());
    }

    @Test
    @DisplayName("a catalog file that is not there says so, rather than producing an empty class")
    void anAbsentCatalogFileIsRefused(@TempDir Path temp) throws Exception {
        Build build = compile(temp, null);

        assertFalse(build.succeeded, "a catalog with no file behind it must fail the build");
        assertTrue(build.messages().contains("Could not find i18n/app.properties"), build.messages());
    }

    @Test
    @DisplayName("a key camel-cases the way a Java developer would expect")
    void keysCamelCaseSensibly() {
        assertEquals("taskAdd", RmiAnnotationProcessor.methodNameFor("task.add"));
        assertEquals("taskAdd", RmiAnnotationProcessor.methodNameFor("task_add"));
        assertEquals("taskAdd", RmiAnnotationProcessor.methodNameFor("task-add"));
        assertEquals("invoiceLine2Total", RmiAnnotationProcessor.methodNameFor("invoice.line2.total"));
        assertEquals("new_", RmiAnnotationProcessor.methodNameFor("new"));
        assertEquals(null, RmiAnnotationProcessor.methodNameFor("123"));
    }

    // ---------------------------------------------------------------- the harness

    /** What one compilation produced. */
    private static final class Build {
        boolean succeeded;
        Path out;
        DiagnosticCollector<JavaFileObject> diagnostics;

        String read(String relative) throws Exception {
            return new String(Files.readAllBytes(out.resolve(relative)), StandardCharsets.UTF_8);
        }

        String messages() {
            StringBuilder all = new StringBuilder();
            for (Diagnostic<? extends JavaFileObject> one : diagnostics.getDiagnostics()) {
                all.append(one.getKind()).append(": ").append(one.getMessage(null))
                   .append(System.lineSeparator());
            }
            return all.toString();
        }

        String errorsOnly() {
            StringBuilder all = new StringBuilder();
            for (Diagnostic<? extends JavaFileObject> one : diagnostics.getDiagnostics()) {
                if (one.getKind() == Diagnostic.Kind.ERROR) {
                    all.append("  ").append(one.getMessage(null)).append(System.lineSeparator());
                }
            }
            return all.toString();
        }

        URLClassLoader loader() throws Exception {
            return new URLClassLoader(new URL[] {out.toUri().toURL()},
                    MessageCatalogGenerationTest.class.getClassLoader());
        }
    }

    /**
     * Compiles one marker class against one catalog file.
     *
     * @param catalogContents the fallback {@code .properties} file, or null to write none
     */
    private static Build compile(Path temp, String catalogContents) throws Exception {
        Path source = temp.resolve("src");
        Files.createDirectories(source.resolve("com/example"));
        Files.write(source.resolve("com/example/AppText.java"), (""
                + "package com.example;\n"
                + "import com.zeroz4j.api.i18n.MessageCatalog;\n"
                + "@MessageCatalog(baseName = \"i18n/app\", fallback = \"en\")\n"
                + "public final class AppText {\n"
                + "    private AppText() { }\n"
                + "}\n").getBytes(StandardCharsets.UTF_8));

        Path resources = temp.resolve("resources");
        Files.createDirectories(resources.resolve("i18n"));
        if (catalogContents != null) {
            Files.write(resources.resolve("i18n/app.properties"),
                    catalogContents.getBytes(StandardCharsets.UTF_8));
        }

        Path out = temp.resolve("out");
        Files.createDirectories(out);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager files = compiler.getStandardFileManager(null, null, null);
        Iterable<? extends JavaFileObject> units = files.getJavaFileObjectsFromFiles(
                Collections.singletonList(source.resolve("com/example/AppText.java").toFile()));

        List<String> options = new ArrayList<>(Arrays.asList(
                "-d", out.toString(), "-s", out.toString(),
                "-Azeroz4j.i18n.resources=" + resources));

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaCompiler.CompilationTask task =
                compiler.getTask(null, files, diagnostics, options, null, units);
        task.setProcessors(Collections.singletonList(new RmiAnnotationProcessor()));

        Build build = new Build();
        build.succeeded = task.call();
        build.out = out;
        build.diagnostics = diagnostics;

        if (build.succeeded) {
            // The generated sources have to compile too, or "it generated something" proves nothing.
            List<File> generated = new ArrayList<>();
            for (String name : new String[] {"AppText_Text.java", "AppText_Catalog.java"}) {
                File file = out.resolve("com/example/" + name).toFile();
                if (file.isFile()) {
                    generated.add(file);
                }
            }
            if (!generated.isEmpty()) {
                DiagnosticCollector<JavaFileObject> second = new DiagnosticCollector<>();
                JavaCompiler.CompilationTask compileGenerated = compiler.getTask(null, files, second,
                        Arrays.asList("-d", out.toString(), "-proc:none"), null,
                        files.getJavaFileObjectsFromFiles(generated));
                build.succeeded = compileGenerated.call();
                for (Diagnostic<? extends JavaFileObject> one : second.getDiagnostics()) {
                    if (one.getKind() == Diagnostic.Kind.ERROR) {
                        throw new AssertionError("The generated catalog classes do not compile: "
                                + one.getMessage(null));
                    }
                }
            }
        }
        return build;
    }
}
