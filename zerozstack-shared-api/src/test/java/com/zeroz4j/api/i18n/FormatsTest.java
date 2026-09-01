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
package com.zeroz4j.api.i18n;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Numbers written the reader's way, and the price of asking for them.
 *
 * <h2>What this protects</h2>
 *
 * <p>Two things. First, that the reader's language really does decide the format, on both tiers,
 * with nothing passed down a call chain.</p>
 *
 * <p>Second, and far more important: <b>that nothing in the framework calls any of this</b>. The
 * first call from a client module into {@code java.text} adds 43 KB gzip to what every visitor
 * downloads - more than twenty languages of translated text, several times over. It is opt-in, and
 * a single stray import in the framework would make it opt-out for everybody, silently, with no
 * error and nothing in a code review to see.</p>
 */
class FormatsTest {

    @AfterEach
    void tearDown() {
        Messages.useCurrentLanguage(null);
    }

    @Test
    @DisplayName("the reader's language decides how a number reads")
    void theReadersLanguageDecides() {
        Messages.useCurrentLanguage(() -> "de");
        assertEquals("1.234,5", Formats.number().format(1234.5d));

        Messages.useCurrentLanguage(() -> "en");
        assertEquals("1,234.5", Formats.number().format(1234.5d));
    }

    @Test
    @DisplayName("a region is honored when the tag carries one")
    void aRegionIsHonored() {
        assertEquals(new Locale("pt", "BR"), Formats.localeOf("pt-BR"));
        assertEquals(new Locale("pt", "BR"), Formats.localeOf("pt_BR"));
        assertEquals(new Locale("de"), Formats.localeOf("de"));
        assertEquals(Locale.ENGLISH, Formats.localeOf(null));
    }

    @Test
    @DisplayName("nothing else in this framework reaches java.text")
    void nothingElseReachesJavaText() {
        List<String> findings = new ArrayList<>();
        for (Path file : frameworkSources()) {
            String name = file.getFileName().toString();
            if ("Formats.java".equals(name)) {
                continue;   // the one door, and the price is written on it
            }
            String source = withoutComments(read(file));
            if (source.contains("import java.text.")
                    || source.contains("java.text.NumberFormat")
                    || source.contains("java.text.DateFormat")
                    || source.contains("java.text.SimpleDateFormat")
                    || source.contains("java.text.MessageFormat")
                    || source.contains("java.text.ChoiceFormat")) {
                findings.add("  " + name);
            }
            if (source.contains("Formats.")) {
                findings.add("  " + name + " calls Formats");
            }
        }
        assertTrue(findings.isEmpty(),
                "Something in the framework reaches java.text. One call adds 43 KB gzip to every "
                        + "application's download, whether or not that application ever formats a "
                        + "number, and there is no error and nothing in a review to see."
                        + System.lineSeparator()
                        + String.join(System.lineSeparator(), findings));
    }

    /** The framework's own client-side modules: what TeaVM compiles for every application. */
    private static List<Path> frameworkSources() {
        List<Path> all = new ArrayList<>();
        for (String module : Arrays.asList("zerozstack-shared-api", "zerozstack-client",
                "zerozstack-ui-components")) {
            Path main = repositoryRoot().resolve(Paths.get(module, "src", "main", "java"));
            if (!Files.isDirectory(main)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(main)) {
                files.filter(Files::isRegularFile)
                     .filter(p -> p.getFileName().toString().endsWith(".java"))
                     .forEach(all::add);
            } catch (IOException unreadable) {
                throw new UncheckedIOException(unreadable);
            }
        }
        assertTrue(all.size() > 50,
                "expected the framework's client-side sources, found " + all.size());
        return all;
    }

    /** Written as a number so that this file's own source does not contain an escape to strip. */
    private static final char NEWLINE = 10;

    /**
     * The file with its comments taken out.
     *
     * <p>Needed because the one place that explains <em>why</em> nothing calls
     * {@code java.text.MessageFormat} has to name it to explain it, and a rule that fires on its
     * own explanation is a rule people delete.</p>
     */
    private static String withoutComments(String source) {
        char[] out = source.toCharArray();
        int at = 0;
        int end = out.length;
        while (at < end) {
            if (out[at] == '/' && at + 1 < end && out[at + 1] == '/') {
                while (at < end && out[at] != NEWLINE) {
                    out[at++] = ' ';
                }
            } else if (out[at] == '/' && at + 1 < end && out[at + 1] == '*') {
                while (at < end && !(out[at] == '*' && at + 1 < end && out[at + 1] == '/')) {
                    if (out[at] != NEWLINE) {
                        out[at] = ' ';
                    }
                    at++;
                }
                if (at < end) {
                    out[at++] = ' ';
                }
                if (at < end) {
                    out[at++] = ' ';
                }
            } else {
                at++;
            }
        }
        return new String(out);
    }

    private static String read(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static Path repositoryRoot() {
        Path here = Paths.get("").toAbsolutePath();
        for (Path at = here; at != null; at = at.getParent()) {
            if (Files.isDirectory(at.resolve("zerozstack-shared-api"))
                    && Files.isRegularFile(at.resolve("pom.xml"))) {
                return at;
            }
        }
        throw new IllegalStateException("could not find the checkout root from " + here);
    }
}
