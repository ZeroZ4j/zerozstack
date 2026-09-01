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
 * Words read once never change again. Nothing in browser code reads a message outside an effect.
 *
 * <h2>The mistake</h2>
 *
 * <pre>{@code
 * // WRONG - the words are read at construction and the button keeps them forever.
 * Button add = new Button(AppText_Text.taskAdd().text());
 *
 * // RIGHT - read inside an effect, so the language is a dependency and the words come back.
 * Button add = new Button();
 * Effect.create(() -> add.setText(AppText_Text.taskAdd().text()));
 * }</pre>
 *
 * <p>It is the same shape as the LiveSync hazard - a getter read outside an effect, so nothing
 * subscribed - and it will be far more common, because a screen has many more labels on it than it
 * has live objects.</p>
 *
 * <p>It is also nearly invisible in testing. Route views are rebuilt when you navigate, so anybody
 * who switches language and then moves around the application sees every screen correct. The stale
 * label only shows on the screen that was open at the moment of the switch, which is exactly the
 * screen nobody thinks to check.</p>
 *
 * <h2>What this catches, exactly</h2>
 *
 * <p>{@code Message.text()} is the only way words are ever produced, so one method name is the
 * whole surface to watch. This reads the source text of every file that can run in a browser and
 * fails the build when a {@code .text()} call is not lexically inside an {@code Effect.create(...)}
 * or a {@code new Computed<>(...)}.</p>
 *
 * <p>The way out, where a read really is a once-only read, is
 * {@code @ReadsMessagesOnce} on the method. The failure message says so.</p>
 *
 * <h2>What it does not catch. All of these are real</h2>
 *
 * <ul>
 *   <li><b>A read one call deep.</b> {@code Effect.create(() -> label.setText(caption()))}, where
 *       {@code caption()} calls {@code .text()}, is correct and this calls it wrong; a constructor
 *       calling that same {@code caption()} is wrong and this calls it right. The check reads one
 *       file's text and cannot follow a call. The annotation is what makes the first survivable.
 *       <b>Nothing makes the second visible.</b> The single exception is a method handed straight
 *       to {@code Effect.create(this::redraw)}, which is followed one hop within the same file,
 *       because that is how an effect longer than one line is normally written.</li>
 *   <li><b>Words put in a variable and used later.</b>
 *       {@code String label = AppText_Text.taskAdd().text();} inside an effect passes here, and
 *       every later use of {@code label} outside that effect is stale. Correct at the moment it
 *       runs and indistinguishable from correct code afterwards.</li>
 *   <li><b>Anything on the server.</b> Server files are not read at all. There are no effects on a
 *       server and a message rendered there is always a once-only read, so checking would mean an
 *       annotation on hundreds of correct methods - which teaches people to add it without
 *       thinking, and then it stops meaning anything.</li>
 *   <li><b>Test sources.</b> Not read either. A test has no screen to leave behind.</li>
 *   <li><b>English left hard-coded.</b> A screen with {@code new Button("Add task")} on it is not
 *       translated at all, and nothing here notices. No check can reliably tell a user-visible
 *       sentence from a CSS class, a DOM attribute or a log line. A narrow version - fail when a
 *       component in this library hands a literal to {@code setText} - is possible and is
 *       deliberately not built yet, because this library's own words are all still literals.</li>
 *   <li><b>Whether the translation is any good.</b> A German {@code taskAdd} that says "subtract
 *       task" passes everything here and everywhere else.</li>
 * </ul>
 *
 * <p>Do not trust this check further than that list allows. A check people believe covers more than
 * it does is worse than no check, because it stops them looking.</p>
 */
class MessageReadContractTest {

    /**
     * Turning a message into words. One name, because a catalog method returns a value rather than
     * a string - which is what keeps this check down to a single token to watch.
     */
    private static final Pattern PRODUCES_WORDS = Pattern.compile("\\.text\\s*\\(\\s*\\)");

    /** The two places a read is subscribed, and therefore correct. */
    private static final Pattern SUBSCRIBING = Pattern.compile(
            "Effect\\s*\\.\\s*create\\s*\\(|new\\s+Computed\\s*<");

    /**
     * A method handed straight to one of them - {@code Effect.create(this::redraw)}.
     *
     * <p>Following this one hop is worth the twenty lines it costs. It is the ordinary way to write
     * an effect whose body is longer than a line, and without it the check would push people into
     * inlining every render method into a lambda to keep it quiet - a worse shape of code arrived
     * at for the wrong reason.</p>
     */
    private static final Pattern SUBSCRIBED_METHOD_REFERENCE = Pattern.compile(
            "(?:Effect\\s*\\.\\s*create|new\\s+Computed\\s*<[^>]*>)\\s*\\(\\s*"
                    + "(?:this|[A-Za-z_$][\\w$]*)\\s*::\\s*([A-Za-z_$][\\w$]*)\\s*\\)");

    /** A method that says it reads words once on purpose. */
    private static final Pattern EXEMPTED = Pattern.compile("@ReadsMessagesOnce\\b");

    /**
     * Signs that a file has anything to do with messages at all.
     *
     * <p>{@code .text()} is a common enough name that a file with no message in it is checked for
     * nothing. Without this, a piece of embedded JavaScript calling {@code response.text()} would
     * be reported as a stale label.</p>
     */
    private static final Pattern USES_MESSAGES = Pattern.compile(
            "com\\.zeroz4j\\.api\\.i18n|FrameworkText|\\w+_Text\\s*\\.|\\bMessage\\b");

    /**
     * The three files that define the mechanism rather than use it.
     *
     * <p>{@code Message} declares {@code text()}, {@code Messages} implements it, and
     * {@code ClientMessages} is the store both read from. A rule about calling a method cannot also
     * apply to the file that writes it.</p>
     */
    private static final Set<String> DEFINES_THE_MECHANISM = new HashSet<>(Arrays.asList(
            "Message.java", "Messages.java", "ClientMessages.java"));

    @Test
    void noBrowserCodeReadsAMessageOutsideAnEffect() {
        List<String> findings = new ArrayList<>();

        for (Path file : browserSources()) {
            if (DEFINES_THE_MECHANISM.contains(file.getFileName().toString())) {
                continue;
            }
            String raw = read(file);
            if (!USES_MESSAGES.matcher(raw).find()) {
                continue;
            }
            String source = blankOutCommentsAndStrings(raw);
            List<int[]> safe = subscribedRegions(source);
            safe.addAll(referencedMethodRegions(source));
            safe.addAll(exemptedRegions(source));

            Matcher words = PRODUCES_WORDS.matcher(source);
            while (words.find()) {
                if (!within(safe, words.start())) {
                    findings.add("  " + relative(file) + ":" + lineOf(source, words.start())
                            + "  reads a message into words outside an Effect");
                }
            }
        }

        assertTrue(findings.isEmpty(),
                "A message is turned into words where nothing is listening for the language to "
                        + "change. Those words are read once and stay on the screen in the old "
                        + "language after somebody switches, with nothing to say so."
                        + System.lineSeparator() + System.lineSeparator()
                        + String.join(System.lineSeparator(), findings)
                        + System.lineSeparator() + System.lineSeparator()
                        + "Move the read inside an effect:" + System.lineSeparator()
                        + "    Effect.create(() -> button.setText(AppText_Text.taskAdd().text()));"
                        + System.lineSeparator() + System.lineSeparator()
                        + "If the read genuinely happens once - a sentence sent to the server, a "
                        + "line written to a log, an alert that appears and is gone - put "
                        + "@ReadsMessagesOnce(\"why\") on the method and this stops asking."
                        + System.lineSeparator() + System.lineSeparator()
                        + "Read the javadoc on this test before trusting it: it cannot see a read "
                        + "one method call deep, and it cannot see English left hard-coded.");
    }

    /**
     * The escape hatch exists and is spelled the way the failure message says it is.
     *
     * <p>Without this, deleting the annotation would leave the rule above with an instruction
     * pointing at nothing, and the first person to hit a false positive would have no way out.</p>
     */
    @Test
    void theEscapeHatchExists() {
        Path annotation = repositoryRoot().resolve(Paths.get("zerozstack-shared-api", "src", "main",
                "java", "com", "zeroz4j", "api", "i18n", "ReadsMessagesOnce.java"));
        assertTrue(Files.isRegularFile(annotation),
                "@ReadsMessagesOnce must exist: the failure above tells people to use it.");
        String source = read(annotation);
        assertTrue(source.contains("ElementType.METHOD") && source.contains("ElementType.CONSTRUCTOR"),
                "@ReadsMessagesOnce goes on a method or a constructor. A class-wide exemption grows "
                        + "until nothing in that file is checked at all.");
    }

    // ---------------------------------------------------------------- helpers

    /** The half-open ranges covered by an {@code Effect.create(...)} or {@code new Computed<>(...)}. */
    private static List<int[]> subscribedRegions(String source) {
        List<int[]> regions = new ArrayList<>();
        Matcher opener = SUBSCRIBING.matcher(source);
        while (opener.find()) {
            int paren = source.indexOf('(', opener.start());
            if (paren < 0) {
                continue;
            }
            int close = matchingParen(source, paren);
            if (close > paren) {
                regions.add(new int[] { paren, close });
            }
        }
        return regions;
    }

    /**
     * The bodies of methods this file hands straight to {@code Effect.create} or a
     * {@code Computed}, one hop and in the same file.
     */
    private static List<int[]> referencedMethodRegions(String source) {
        List<int[]> regions = new ArrayList<>();
        Matcher handed = SUBSCRIBED_METHOD_REFERENCE.matcher(source);
        while (handed.find()) {
            String method = handed.group(1);
            Matcher declared = Pattern.compile("\\b" + Pattern.quote(method)
                    + "\\s*\\(\\s*\\)\\s*\\{").matcher(source);
            while (declared.find()) {
                int brace = source.indexOf('{', declared.start());
                int close = matchingBrace(source, brace);
                if (close > brace) {
                    regions.add(new int[] { brace, close });
                }
            }
        }
        return regions;
    }

    /** The bodies of methods and constructors marked {@code @ReadsMessagesOnce}. */
    private static List<int[]> exemptedRegions(String source) {
        List<int[]> regions = new ArrayList<>();
        Matcher marked = EXEMPTED.matcher(source);
        while (marked.find()) {
            int brace = source.indexOf('{', marked.end());
            if (brace < 0) {
                continue;
            }
            int close = matchingBrace(source, brace);
            if (close > brace) {
                regions.add(new int[] { brace, close });
            }
        }
        return regions;
    }

    private static boolean within(List<int[]> regions, int index) {
        for (int[] region : regions) {
            if (index > region[0] && index < region[1]) {
                return true;
            }
        }
        return false;
    }

    private static int matchingParen(String source, int open) {
        return matching(source, open, '(', ')');
    }

    private static int matchingBrace(String source, int open) {
        return matching(source, open, '{', '}');
    }

    private static int matching(String source, int open, char opener, char closer) {
        int depth = 0;
        for (int at = open; at < source.length(); at++) {
            char here = source.charAt(at);
            if (here == opener) {
                depth++;
            } else if (here == closer) {
                depth--;
                if (depth == 0) {
                    return at;
                }
            }
        }
        return -1;
    }

    /**
     * Returns the file with every comment and every string literal turned into spaces, keeping
     * every character position so a finding still names the right line.
     *
     * <p>Strings go as well as comments, which {@code DetachContractTest} does not need to do. A
     * {@code @JSBody} script is a string, and one of them calls {@code response.text()} on a browser
     * fetch - which is not a message and never was.</p>
     */
    static String blankOutCommentsAndStrings(String source) {
        char[] out = source.toCharArray();
        int at = 0;
        int end = out.length;
        while (at < end) {
            char here = out[at];
            if (here == '"' || here == '\'') {
                char quote = here;
                at++;
                while (at < end && out[at] != quote) {
                    if (out[at] == '\\' && at + 1 < end) {
                        blank(out, at);
                        blank(out, at + 1);
                        at += 2;
                        continue;
                    }
                    blank(out, at);
                    at++;
                }
                at++;
            } else if (here == '/' && at + 1 < end && out[at + 1] == '/') {
                while (at < end && out[at] != '\n') {
                    blank(out, at);
                    at++;
                }
            } else if (here == '/' && at + 1 < end && out[at + 1] == '*') {
                while (at < end && !(out[at] == '*' && at + 1 < end && out[at + 1] == '/')) {
                    blank(out, at);
                    at++;
                }
                if (at < end) {
                    blank(out, at);
                    at++;
                }
                if (at < end) {
                    blank(out, at);
                    at++;
                }
            } else {
                at++;
            }
        }
        return new String(out);
    }

    private static void blank(char[] out, int at) {
        if (out[at] != '\n') {
            out[at] = ' ';
        }
    }

    /**
     * Every Java file that can end up running in a browser.
     *
     * <p>Named by where it lives rather than by what it imports, because a shared module's file
     * runs on both tiers and its imports say nothing about which. The modules TeaVM compiles are
     * the framework's own client-side ones, anything ending {@code -client} or {@code -shared}, the
     * keyboard proof page, and the skeleton an application is generated from.</p>
     */
    private static List<Path> browserSources() {
        Path root = repositoryRoot();
        List<Path> all = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                 .filter(p -> p.getFileName().toString().endsWith(".java"))
                 .filter(p -> !contains(p, "target"))
                 .filter(p -> !contains(p, "test"))
                 .filter(MessageReadContractTest::reachesABrowser)
                 .sorted()
                 .forEach(all::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertTrue(all.size() > 100,
                "expected to read the browser-side modules, found only " + all.size() + " files");
        return all;
    }

    private static boolean reachesABrowser(Path file) {
        String path = file.toString().replace('\\', '/');
        if (path.contains("/archetype-resources/") || path.contains("/tools/ui-proof/")) {
            return true;
        }
        return path.contains("/zerozstack-shared-api/")
                || path.contains("/zerozstack-client/")
                || path.contains("/zerozstack-ui-components/")
                || path.contains("-client/src/")
                || path.contains("-shared/src/");
    }

    private static boolean contains(Path file, String folder) {
        return file.toString().replace('\\', '/').contains("/" + folder + "/");
    }

    private static String relative(Path file) {
        return repositoryRoot().relativize(file).toString().replace('\\', '/');
    }

    private static int lineOf(String source, int index) {
        int line = 1;
        for (int at = 0; at < index && at < source.length(); at++) {
            if (source.charAt(at) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static String read(Path file) {
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
