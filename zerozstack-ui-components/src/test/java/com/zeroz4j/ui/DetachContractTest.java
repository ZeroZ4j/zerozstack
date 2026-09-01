/*
 * Copyright 2026 Franz Schöning
 * Project: https://www.zeroz4j.com
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
package com.zeroz4j.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nothing in the checkout empties an element by hand.
 *
 * <p>This is the tripwire for the worst fault the keyboard walkthrough found. Every screen in
 * every example was swapped for the next one by writing an empty string into the container's
 * HTML. That takes the old screen off the page without telling it, so {@code onDetach} never ran
 * and the screen the person had left went on working: its timer kept firing, its effect kept
 * running, and both kept rebuilding a list underneath whoever had moved on — which threw the
 * keyboard back to the page body every second and a half, for as long as the application was
 * open. Nothing errored and nothing was logged. Only counting it caught it.</p>
 *
 * <p>The supported operations are {@code HasComponents.replaceContents}, {@code removeAll} and
 * {@code remove} on a container, and {@code Component.replaceContents(element, ...)} for a plain
 * element such as an application's root {@code <div>}. All of them run {@code onDetach} on
 * everything leaving, nested parts included.</p>
 *
 * <p>It reads source text rather than driving components, for the reason
 * {@code OverlayContractTest} gives: the components wrap browser elements and none of them can be
 * constructed on the JVM.</p>
 */
class DetachContractTest {

    /**
     * The one place allowed to empty an element by hand.
     *
     * <p>{@code Alert} keeps a small box for its tone mark, fills it with a drawing, and empties
     * it again when the tone is taken away. Nothing else ever puts anything in that box, so there
     * is nothing in it to tell. That is a different act from emptying a container somebody else's
     * components are living in, which is what this test is here to stop.</p>
     */
    private static final Set<String> ALLOWED_TO_EMPTY_BY_HAND =
            new HashSet<>(Arrays.asList("Alert.java"));

    /** Writing an empty string into an element's HTML, in any form the language allows. */
    private static final Pattern EMPTIED_BY_HAND =
            Pattern.compile("setInnerHTML\\s*\\(\\s*\"\\s*\"\\s*\\)");

    /**
     * A hand-rolled "take every child out" loop, in Java or inside a piece of embedded JavaScript.
     * This is the other spelling of the same act, and the router used it until 0.8.0.
     */
    private static final Pattern EMPTIED_BY_LOOP = Pattern.compile(
            "while\\s*\\([^)]*(?:firstChild|lastChild|firstElementChild|lastElementChild)[^)]*\\)"
                    + "[^;{]*[{(][^}]*remove(?:Child)?\\s*\\(");

    @Test
    void nothingEmptiesAnElementByHand() {
        List<String> findings = new ArrayList<>();

        for (Path file : javaSourcesInCheckout()) {
            String name = file.getFileName().toString();
            if (ALLOWED_TO_EMPTY_BY_HAND.contains(name) || isThisTest(name)) {
                continue;
            }
            String source = blankOutComments(read(file));
            Matcher emptied = EMPTIED_BY_HAND.matcher(source);
            while (emptied.find()) {
                findings.add("  " + relative(file) + ":" + lineOf(source, emptied.start())
                        + "  empties an element by writing an empty string into its HTML");
            }
            if (!isTheOneImplementation(file)) {
                Matcher looped = EMPTIED_BY_LOOP.matcher(source);
                while (looped.find()) {
                    findings.add("  " + relative(file) + ":" + lineOf(source, looped.start())
                            + "  takes every child out of an element in a loop of its own");
                }
            }
        }

        assertTrue(findings.isEmpty(),
                "Something is emptying an element by hand. Whatever was inside is taken off the "
                        + "page without being told, so its timers, effects and subscriptions keep "
                        + "running and keep changing a screen nobody is looking at."
                        + System.lineSeparator()
                        + "Use replaceContents(...) or removeAll() on the container, or "
                        + "Component.replaceContents(element, ...) when the container is a plain "
                        + "element."
                        + System.lineSeparator()
                        + String.join(System.lineSeparator(), findings));
    }

    /**
     * The supported operations exist and do the telling, so the rule above has somewhere to point.
     *
     * <p>Without this, deleting {@code replaceContents} would leave the rule above passing on a
     * checkout where there was no right way to do it at all.</p>
     */
    @Test
    void theSupportedOperationsRunOnDetach() {
        String container = read(componentSource("HasComponents.java"));
        assertTrue(container.contains("default void replaceContents("),
                "HasComponents must offer replaceContents, which is what a screen swap uses.");
        assertTrue(container.contains("c.detach()"),
                "removeAll and remove must detach what they take out; that is the whole point.");

        String component = read(componentSource("Component.java"));
        assertTrue(component.contains("public static void replaceContents(HTMLElement host"),
                "Component must offer replaceContents for a plain element, which is how an "
                        + "application swaps the screen inside its own root div.");
        assertTrue(component.contains("child.detach()"),
                "detach must reach everything inside, not only the outermost component. A timer "
                        + "three levels down is still a timer.");
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Returns the file with every comment turned into spaces, keeping every character position.
     *
     * <p>Without this the test fails on the sentences explaining why the rule exists. Every place
     * that was fixed now carries a line saying what it must not go back to, and that line names
     * the thing it must not go back to - so the rule would fire on its own explanation. Positions
     * are preserved so a finding still names the right line.</p>
     */
    static String blankOutComments(String source) {
        char[] out = source.toCharArray();
        int i = 0;
        int n = out.length;
        while (i < n) {
            char c = out[i];
            if (c == '"' || c == '\'') {
                char quote = c;
                i++;
                while (i < n && out[i] != quote) {
                    i += out[i] == '\\' ? 2 : 1;
                }
                i++;
            } else if (c == '/' && i + 1 < n && out[i + 1] == '/') {
                while (i < n && out[i] != '\n') {
                    out[i++] = ' ';
                }
            } else if (c == '/' && i + 1 < n && out[i + 1] == '*') {
                while (i < n && !(out[i] == '*' && i + 1 < n && out[i + 1] == '/')) {
                    if (out[i] != '\n') {
                        out[i] = ' ';
                    }
                    i++;
                }
                if (i < n) {
                    out[i++] = ' ';
                }
                if (i < n) {
                    out[i++] = ' ';
                }
            } else {
                i++;
            }
        }
        return new String(out);
    }

    /**
     * The two supported implementations of emptying, which is where the loop belongs:
     * {@code HasComponents.removeAll} for a container, {@code Component.replaceContents} for a
     * plain element. Both detach what they take out first.
     */
    private static boolean isTheOneImplementation(Path file) {
        String name = file.getFileName().toString();
        return "HasComponents.java".equals(name) || "Component.java".equals(name);
    }

    private static boolean isThisTest(String fileName) {
        return "DetachContractTest.java".equals(fileName);
    }

    private static List<Path> javaSourcesInCheckout() {
        Path root = repositoryRoot();
        List<Path> all = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                 .filter(p -> p.getFileName().toString().endsWith(".java"))
                 .filter(p -> !p.toString().contains(java.io.File.separator + "target"
                         + java.io.File.separator))
                 .sorted()
                 .forEach(all::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertTrue(all.size() > 100,
                "expected to read the whole checkout, found only " + all.size() + " files");
        return all;
    }

    private static Path componentSource(String fileName) {
        return repositoryRoot().resolve(Paths.get("zerozstack-ui-components", "src", "main", "java",
                "com", "zeroz4j", "ui", "component", fileName));
    }

    private static String relative(Path file) {
        return repositoryRoot().relativize(file).toString().replace('\\', '/');
    }

    private static int lineOf(String source, int index) {
        int line = 1;
        for (int i = 0; i < index && i < source.length(); i++) {
            if (source.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static String read(Path file) {
        assertTrue(Files.isRegularFile(file), "expected to find " + file);
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Walks up from the working directory until the checkout's own root pom is found. */
    private static Path repositoryRoot() {
        Path here = Paths.get("").toAbsolutePath();
        for (Path candidate = here; candidate != null; candidate = candidate.getParent()) {
            if (Files.isDirectory(candidate.resolve("zerozstack-ui-components"))
                    && Files.isRegularFile(candidate.resolve("pom.xml"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("could not find the checkout root from " + here);
    }
}
