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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No piece of embedded JavaScript names anything with a single letter.
 *
 * <p>This is the tripwire for a fault that shipped in two releases and was seen by every person
 * who ever lost their connection. The bar that says "Connection lost — reconnecting…" said
 * {@code [object HTMLDivElement]} instead.</p>
 *
 * <h2>What happens</h2>
 *
 * <p>A {@code @JSBody} script is not compiled. TeaVM drops the text of it into the generated file
 * as it stands and renames only the method's parameters. When the build is minified — which the
 * compiler does by default, and which is therefore what every application generated from the
 * archetype does — those parameters are renamed to single letters: the first is {@code b}, the
 * second {@code c}, and so on. The script's own names are left exactly as they were written.</p>
 *
 * <p>So a script that writes {@code var b = ...} and takes one parameter ends up with two things
 * called {@code b} in one function, and the second one wins. The connection bar did precisely
 * that: {@code b} was meant to be the message and became the element the script had just made,
 * and the browser printed the element the way it prints any object. Nothing threw, nothing was
 * logged, and every example in the checkout looked right, because every example's build turns
 * minifying off.</p>
 *
 * <h2>The rule</h2>
 *
 * <p>A single letter is the only name the minifier can produce for a parameter, so a script that
 * uses none is safe whatever the compiler does with it. Two letters is already out of reach. Call
 * the loop counter {@code idx}, the caught error {@code ignored}, the element {@code bar}.</p>
 *
 * <p>The rule applies to a script whose method takes at least one parameter, because a method
 * with no parameter has nothing that could be renamed into it. Adding a parameter to such a
 * method is a one-word edit, and this test is what stops that edit from being the one that breaks
 * the script beside it: it starts failing the moment the parameter is added.</p>
 */
class JsBodyNamingContractTest {

    /** {@code var x}, {@code let x}, {@code const x}. */
    private static final Pattern DECLARED =
            Pattern.compile("\\b(?:var|let|const)\\s+([A-Za-z_$][A-Za-z0-9_$]*)");

    /** {@code catch (x)}. */
    private static final Pattern CAUGHT =
            Pattern.compile("\\bcatch\\s*\\(\\s*([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\)");

    /** The parameter list of a function written inside the script. */
    private static final Pattern NESTED_FUNCTION =
            Pattern.compile("\\bfunction\\s*[A-Za-z0-9_$]*\\s*\\(([^)]*)\\)");

    private static final Pattern IDENTIFIER =
            Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");

    @Test
    void noEmbeddedScriptNamesAnythingWithASingleLetter() {
        List<String> findings = new ArrayList<>();

        for (Path file : javaSourcesInCheckout()) {
            if (isThisTest(file)) {
                continue;
            }
            String source = read(file);
            for (JsBody body : jsBodiesIn(source)) {
                if (body.parameters.isEmpty()) {
                    continue;
                }
                for (String name : namesBoundInside(body.script)) {
                    if (name.length() == 1) {
                        findings.add("  " + relative(file) + ":" + lineOf(source, body.at)
                                + "  the script calls something \"" + name + "\""
                                + ", and the method's parameters are " + body.parameters);
                    }
                }
            }
        }

        assertTrue(findings.isEmpty(),
                "A piece of embedded JavaScript names something with a single letter."
                        + System.lineSeparator()
                        + "TeaVM inlines the script as text and renames the method's parameters to "
                        + "single letters when the build is minified, which is what a generated "
                        + "application's build does. A one-letter name inside the script can "
                        + "therefore end up being the same name as a parameter, and then one of "
                        + "them quietly becomes the other."
                        + System.lineSeparator()
                        + "Use a name of two letters or more: idx, ignored, bar, node."
                        + System.lineSeparator()
                        + String.join(System.lineSeparator(), findings));
    }

    /**
     * The connection bar still puts its message on the page, rather than anything else.
     *
     * <p>Without this the rule above would pass on a checkout where the bar had been broken again
     * some other way. This is the thing a person actually sees.</p>
     */
    @Test
    void theConnectionBarShowsTheMessageItWasGiven() {
        Path banner = repositoryRoot().resolve(Paths.get("zerozstack-client", "src", "main", "java",
                "com", "zeroz4j", "client", "ConnectionBanner.java"));
        List<JsBody> bodies = jsBodiesIn(read(banner));

        JsBody show = null;
        for (JsBody body : bodies) {
            if (body.parameters.contains("text")) {
                show = body;
            }
        }
        assertTrue(show != null, "ConnectionBanner must still have a script that takes the message.");
        assertTrue(show.script.contains("textContent = text"),
                "The bar must put the message it was given on the page. It read "
                        + "\"[object HTMLDivElement]\" in 0.6.0 and 0.7.0 because this line wrote "
                        + "the element instead.");
    }

    // ---------------------------------------------------------------- reading the annotations

    private static final class JsBody {
        final int at;
        final List<String> parameters;
        final String script;

        JsBody(int at, List<String> parameters, String script) {
            this.at = at;
            this.parameters = parameters;
            this.script = script;
        }
    }

    /** Every {@code @JSBody(...)} in one file, with its parameter names and its script text. */
    private static List<JsBody> jsBodiesIn(String source) {
        List<JsBody> bodies = new ArrayList<>();
        int from = 0;
        while (true) {
            int at = source.indexOf("@JSBody", from);
            if (at < 0) {
                return bodies;
            }
            int open = source.indexOf('(', at);
            int close = open < 0 ? -1 : endOfCall(source, open);
            if (close < 0) {
                return bodies;
            }
            String call = source.substring(open, close + 1);
            bodies.add(new JsBody(at, parametersOf(call), scriptOf(call)));
            from = close + 1;
        }
    }

    /** The closing bracket of a call, skipping over anything inside a string. */
    private static int endOfCall(String source, int open) {
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '"') {
                i++;
                while (i < source.length() && source.charAt(i) != '"') {
                    i += source.charAt(i) == '\\' ? 2 : 1;
                }
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static List<String> parametersOf(String call) {
        List<String> names = new ArrayList<>();
        int at = call.indexOf("params");
        if (at < 0) {
            return names;
        }
        int open = call.indexOf('{', at);
        int close = open < 0 ? -1 : call.indexOf('}', open);
        if (open < 0 || close < 0) {
            return names;
        }
        Matcher m = Pattern.compile("\"([^\"]*)\"").matcher(call.substring(open, close));
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    /** The script, which is written as any number of string literals added together. */
    private static String scriptOf(String call) {
        int at = call.indexOf("script");
        if (at < 0) {
            return "";
        }
        StringBuilder script = new StringBuilder();
        Matcher m = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(call.substring(at));
        while (m.find()) {
            script.append(m.group(1));
        }
        return script.toString().replace("\\\"", "\"").replace("\\'", "'");
    }

    // ---------------------------------------------------------------- reading the script

    private static Set<String> namesBoundInside(String script) {
        Set<String> names = new LinkedHashSet<>();
        Matcher declared = DECLARED.matcher(script);
        while (declared.find()) {
            names.add(declared.group(1));
        }
        Matcher caught = CAUGHT.matcher(script);
        while (caught.find()) {
            names.add(caught.group(1));
        }
        Matcher nested = NESTED_FUNCTION.matcher(script);
        while (nested.find()) {
            Matcher parameter = IDENTIFIER.matcher(nested.group(1));
            while (parameter.find()) {
                names.add(parameter.group());
            }
        }
        return names;
    }

    // ---------------------------------------------------------------- helpers

    private static boolean isThisTest(Path file) {
        return "JsBodyNamingContractTest.java".equals(file.getFileName().toString());
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
