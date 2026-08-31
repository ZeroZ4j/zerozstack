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
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every sentence that says which version this framework is must say the version this build is.
 *
 * <p>The four documents an AI coding assistant reads — {@code AGENTS.md}, {@code llms.txt},
 * {@code context7.json} and {@code docs/AGENT_PROMPTS.md} — each state the version in prose, and
 * the release checklist used to mention none of them. {@code llms.txt} was found claiming 0.4.0
 * and describing the router as unimplemented: two releases stale, and nothing had noticed. This
 * check is what noticing looks like now. It reads every Markdown file in the checkout as well, so
 * a stale number anywhere fails the build naming the file, the line, what it found and what this
 * build actually is.</p>
 *
 * <h2>The one hard part: history keeps its number</h2>
 *
 * <p>A previous release bump walked the documentation incrementing every version it saw, including
 * inside sentences about the past, so pages ended up claiming that things which shipped in 0.5.0
 * had shipped in the version being prepared. A check that simply demanded the current number
 * everywhere would push a release in exactly that direction. So each mention is classified first,
 * and only from the words around it:</p>
 *
 * <ul>
 *   <li><b>History</b> — the mention carries a marker saying <i>when</i>: a trailing {@code +} as
 *       in {@code (0.7.0+)}, a lead-in such as {@code since}, {@code before}, {@code until},
 *       {@code pre-}, {@code added in}, {@code fixed in}, {@code released in}, or a follower such
 *       as {@code and earlier} or {@code or later}. History may name any version, and this check
 *       leaves it alone.</li>
 *   <li><b>A claim about now</b> — everything else. It must be the version in {@code <revision>}.</li>
 * </ul>
 *
 * <p><b>A bare {@code in} is not a marker, deliberately.</b> "Every known gap in 0.8.0" is a claim
 * about the version you are on and goes stale; "the fix landed in 0.6.0" is history and does not.
 * The difference is the verb, so the verb has to be there. Anything without one is treated as a
 * claim about now, which is the fail-closed direction: at the next release every unmarked mention
 * of the old number is reported and a person decides, for each, whether to bump it or to write
 * when it happened.</p>
 *
 * <p>The marker list is words, not a rule, for the same reason {@link DocumentationSpellingTest}'s
 * is: it was built by reading what the documentation actually says. It is deliberately incomplete.
 * Add a phrase when a real sentence needs one — and read the sentence first, because "the 0.7.0
 * component library" wanting to be "the component library released in 0.7.0" is the check doing
 * its job, not getting in the way.</p>
 *
 * <h2>Which numbers count as versions of this framework</h2>
 *
 * <p>Only the ones {@code CHANGELOG.md} lists as releases, plus the one being prepared. Helidon
 * 4.0.8, TeaVM 0.15.0, daisyUI 5.6.14 and a generated application's {@code 1.0.0-SNAPSHOT} are
 * other people's numbers and are left alone. A version that is neither a release nor the current
 * one is itself a finding: {@code (0.5.1+)} appeared in a guide for a release that was never
 * made.</p>
 *
 * <h2>What is out of scope, and why</h2>
 *
 * <ul>
 *   <li><b>{@code CHANGELOG.md}</b> — the register of what each release did. Every number in it is
 *       history by definition.</li>
 *   <li><b>{@code docs/design/} and {@code docs/contribute/documentation-plan.md}</b> — dated
 *       records of a decision, correct as of the day they were written and not maintained after.</li>
 *   <li><b>The archetype's project template</b> — its version is a placeholder the generator fills
 *       in, so there is no literal number to be stale.</li>
 * </ul>
 *
 * <h2>The escape hatch</h2>
 *
 * <p>A line carrying {@code <!-- version-check: why -->} is skipped, and the reason has to be
 * there. It exists for the one sentence that legitimately names the previous release while the
 * next one is being prepared: the temporary box in {@code README.md} that tells readers to keep
 * using the published version until this one ships.</p>
 */
class VersionStatementTest {

    // ------------------------------------------------------------------ the vocabulary of history

    /**
     * Phrases that, sitting immediately before a version, say the sentence is about the past.
     * Matched case-insensitively against the text just before the number, after markdown noise
     * such as {@code (}, {@code *} and backticks has been stripped off the end.
     */
    private static final List<String> HISTORY_LEAD_INS = List.of(
            "since", "as of", "until", "up to", "up to and including", "through",
            "before", "after", "prior to", "predating", "preceding",
            "added in", "new in", "fixed in", "changed in", "removed in", "released in",
            "published in", "introduced in", "shipped in", "landed in", "arrived in",
            "broke in", "back in", "left this list in");

    /** The same, written as a suffix rather than a word: {@code pre-0.3.0}. */
    private static final List<String> HISTORY_PREFIXES = List.of("pre-", "post-");

    /** Phrases that, sitting immediately after a version, say the same thing. */
    private static final List<String> HISTORY_FOLLOWERS = List.of(
            "+", "and earlier", "or earlier", "and later", "or later", "and before", "and after",
            "changelog");

    /** How much text on each side is examined. Enough for the longest lead-in and its punctuation. */
    private static final int LOOK_BEHIND = 40;
    private static final int LOOK_AHEAD = 16;

    /**
     * A dotted three-part number, optionally a snapshot. The guards on either side keep it from
     * biting a piece out of a longer dotted run such as {@code 0.0.0.0}, while still matching the
     * number in {@code myapp-server-0.8.0.jar}, where what follows is a dot and then a word.
     */
    private static final Pattern VERSION =
            Pattern.compile("(?<![\\d.])(\\d+\\.\\d+\\.\\d+)(-SNAPSHOT)?(?!\\.?\\d)");

    private static final Pattern EXEMPTION =
            Pattern.compile("<!--\\s*version-check:\\s*(\\S.*?)\\s*-->");

    // ------------------------------------------------------------------ the check

    @Test
    void everyClaimAboutTheCurrentVersionNamesTheCurrentVersion() throws IOException {
        Path root = repositoryRoot();
        String current = currentVersion(root);
        Set<String> released = releasedVersions(root);
        released.add(current);

        List<String> findings = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                 .filter(VersionStatementTest::isDocumentation)
                 .filter(p -> !isOutOfScope(root, p))
                 .sorted()
                 .forEach(p -> findings.addAll(problemsIn(root, p, current, released)));
        }

        assertTrue(findings.isEmpty(),
                "This build is " + current + ", from <revision> in the root pom.xml." + System.lineSeparator()
                        // Plain ASCII: this text is read on a console, which is not always UTF-8.
                        + "A version with no marker saying when - no '+', no 'since', no 'added in' - reads as a"
                        + System.lineSeparator()
                        + "claim about the version you are on, and has to be this one. Either correct the number,"
                        + System.lineSeparator()
                        + "or, if the sentence is about the past, say so: 'added in 0.6.0', 'before 0.7.0',"
                        + System.lineSeparator()
                        + "'(0.6.0+)'. VersionStatementTest's javadoc lists every phrase it recognizes."
                        + System.lineSeparator()
                        + String.join(System.lineSeparator(), findings));
    }

    private List<String> problemsIn(Path root, Path file, String current, Set<String> released) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String where = root.relativize(file).toString().replace('\\', '/');

        List<String> problems = new ArrayList<>();
        Matcher m = VERSION.matcher(text);
        while (m.find()) {
            String base = m.group(1);
            String found = m.group();
            if (!released.contains(base) && !base.equals(current)) {
                if (isThisFrameworksNumber(text, m.start(), m.end())) {
                    problems.add("  " + where + ":" + lineOf(text, m.start()) + "  names " + found
                            + ", which CHANGELOG.md has no release for.");
                }
                continue;                                   // somebody else's version number
            }
            if (exemptionOn(text, m.start()) != null) {
                continue;
            }
            if (isHistory(text, m.start(), m.end())) {
                continue;
            }
            if (!base.equals(current)) {
                problems.add("  " + where + ":" + lineOf(text, m.start()) + "  says " + found
                        + " with nothing saying when, so it reads as this build's version, which is "
                        + current + ".");
            }
        }
        return problems;
    }

    /**
     * Whether a number that is not a release of this framework is nonetheless claiming to be one.
     * A version marked as history — {@code (0.5.1+)}, {@code since 0.5.1} — is asserting that such
     * a release exists, and that assertion can be wrong; a bare number next to somebody else's
     * product name is not making any claim about this framework at all.
     */
    private static boolean isThisFrameworksNumber(String text, int start, int end) {
        return isHistory(text, start, end);
    }

    // ------------------------------------------------------------------ classification

    private static boolean isHistory(String text, int start, int end) {
        String behind = normalize(text.substring(Math.max(0, start - LOOK_BEHIND), start));
        String ahead = text.substring(end, Math.min(text.length(), end + LOOK_AHEAD))
                           .toLowerCase(Locale.ROOT);

        for (String prefix : HISTORY_PREFIXES) {
            if (behind.endsWith(prefix)) {
                return true;
            }
        }
        String trimmedBehind = stripLeadingMarkup(behind);
        for (String leadIn : HISTORY_LEAD_INS) {
            if (trimmedBehind.endsWith(leadIn + " ")) {
                return true;
            }
        }
        for (String follower : HISTORY_FOLLOWERS) {
            if (ahead.startsWith(follower) || ahead.stripLeading().startsWith(follower)) {
                return true;
            }
        }
        return false;
    }

    /** Lowercases and turns every run of whitespace, newlines included, into one space. */
    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /**
     * Removes the markup that can sit between a lead-in and the number it introduces — the opening
     * bracket of "(0.7.0+)", the emphasis of "since **0.4.1**", a backtick, a quotation mark.
     */
    private static String stripLeadingMarkup(String behind) {
        int end = behind.length();
        while (end > 0 && "(*`[\"'_<".indexOf(behind.charAt(end - 1)) >= 0) {
            end--;
        }
        return behind.substring(0, end);
    }

    private static String exemptionOn(String text, int at) {
        int from = text.lastIndexOf('\n', at) + 1;
        int to = text.indexOf('\n', at);
        String line = to < 0 ? text.substring(from) : text.substring(from, to);
        Matcher m = EXEMPTION.matcher(line);
        return m.find() ? m.group(1) : null;
    }

    private static int lineOf(String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    // ------------------------------------------------------------------ what the build says it is

    /** The version under {@code <revision>}, without any {@code -SNAPSHOT}. */
    private static String currentVersion(Path root) throws IOException {
        String pom = Files.readString(root.resolve("pom.xml"), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("<revision>\\s*([^<\\s]+)\\s*</revision>").matcher(pom);
        assertTrue(m.find(), "The root pom.xml has no <revision>, so there is nothing to check against.");
        return m.group(1).replace("-SNAPSHOT", "");
    }

    /** Every version {@code CHANGELOG.md} has a release section for. */
    private static Set<String> releasedVersions(Path root) throws IOException {
        Set<String> versions = new LinkedHashSet<>();
        Matcher m = Pattern.compile("(?m)^##\\s*\\[(\\d+\\.\\d+\\.\\d+)]")
                           .matcher(Files.readString(root.resolve("CHANGELOG.md"), StandardCharsets.UTF_8));
        while (m.find()) {
            versions.add(m.group(1));
        }
        assertTrue(versions.size() > 3,
                "CHANGELOG.md yielded " + versions.size() + " release headings, which cannot be right. "
                        + "Without them this check cannot tell this framework's versions from anybody "
                        + "else's, so it would pass on anything.");
        return new TreeSet<>(versions);
    }

    // ------------------------------------------------------------------ which files

    private static boolean isDocumentation(Path p) {
        String name = p.getFileName().toString();
        return name.endsWith(".md") || name.equals("llms.txt") || name.equals("context7.json");
    }

    private static boolean isOutOfScope(Path root, Path p) {
        String rel = root.relativize(p).toString().replace('\\', '/');
        for (String part : rel.split("/")) {
            if (part.startsWith(".")) {
                return true;                                // .git, and any private Maven repository
            }
        }
        return rel.contains("/target/") || rel.startsWith("target/")
                || rel.contains("/node_modules/") || rel.contains("/site/")
                || rel.contains("tools/ui-proof/lib/") || rel.contains("tools/ui-proof/shots/")
                // The register of releases: every number in it is history by definition.
                || rel.equals("CHANGELOG.md")
                // Dated records of a decision, true on the day they were written.
                || rel.startsWith("docs/design/")
                || rel.equals("docs/contribute/documentation-plan.md")
                // A template. Its version is a placeholder the archetype fills in.
                || rel.contains("/archetype-resources/");
    }

    private static Path repositoryRoot() {
        Path here = Paths.get("").toAbsolutePath();
        for (Path p = here; p != null; p = p.getParent()) {
            if (Files.isRegularFile(p.resolve("pom.xml"))
                    && Files.isDirectory(p.resolve("zerozstack-ui-components"))) {
                return p;
            }
        }
        return here;
    }
}
