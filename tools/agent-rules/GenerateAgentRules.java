/*
 * Copyright 2026 Franz Schoning
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the rule list that ships inside {@code zerozstack-shared-api.jar} at
 * {@code META-INF/zeroz4j/AGENTS.md}.
 *
 * <p>Maven runs it as a single-file source program — JDK 21 runs a {@code .java} file directly, so
 * there is nothing to compile first and nothing added to the build's dependencies:</p>
 *
 * <pre>java GenerateAgentRules.java &lt;context7.json&gt; &lt;output.md&gt; &lt;version&gt;</pre>
 *
 * <h2>Why generate rather than write</h2>
 *
 * <p>{@code context7.json} already carries the rule list and is the best-maintained assistant
 * document in the repository. A second, hand-written copy would be a second release cycle, and a
 * document describing a version of the framework nobody is running is worse than no document at
 * all. So there is exactly one source, and this program projects it into the jar.</p>
 *
 * <h2>The parser</h2>
 *
 * <p>It reads one JSON construct — an array of strings under the key {@code "rules"} — and it is
 * written out here rather than pulled in, because a build-time dependency to read forty-five
 * strings is a worse trade than thirty lines. It refuses to write anything it is unsure about: an
 * unterminated string, an unknown escape, or a suspiciously short list each stop the build with a
 * message instead of shipping a truncated file.</p>
 */
public final class GenerateAgentRules {

    /** A guard against a parser that silently read half the file. */
    private static final int FEWEST_PLAUSIBLE_RULES = 20;

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            fail("usage: GenerateAgentRules <context7.json> <output.md> <version>");
        }
        Path source = Path.of(args[0]);
        Path target = Path.of(args[1]);
        String version = args[2];

        String json = Files.readString(source, StandardCharsets.UTF_8);
        List<String> rules = readRules(json, source);
        if (rules.size() < FEWEST_PLAUSIBLE_RULES) {
            fail(source + " yielded only " + rules.size() + " rules, which cannot be right. Either "
                    + "the file changed shape or the parser in GenerateAgentRules no longer "
                    + "understands it. Nothing was written.");
        }

        Files.createDirectories(target.getParent());
        Files.writeString(target, document(version, rules), StandardCharsets.UTF_8);
        System.out.println("Wrote " + rules.size() + " rules for " + version + " to " + target);
    }

    // ------------------------------------------------------------------ the document

    private static final String NL = "\n";

    private static String document(String version, List<String> rules) {
        StringBuilder out = new StringBuilder();

        line(out, "# ZeroZ Stack " + version + " - rules for AI coding assistants");
        line(out, "");
        line(out, "You are reading this out of `zerozstack-shared-api-" + version + ".jar`. That matters: it");
        line(out, "describes **the version of ZeroZ Stack this project actually resolves**, not the newest one.");
        line(out, "The published documentation follows the framework's main line, so it describes things a");
        line(out, "project on an older version does not have. Where the two disagree, this file is the one that");
        line(out, "matches the code on this project's classpath.");
        line(out, "");
        line(out, "Fuller documentation, current with the framework's main line:");
        line(out, "");
        line(out, "- <https://stack.zeroz4j.com/>");
        line(out, "- Context7 index `/zeroz4j/zerozstack`");
        line(out, "- `llms.txt` at <https://github.com/ZeroZ4j/zerozstack/blob/main/llms.txt>");
        line(out, "");
        line(out, "Check anything you take from those against the version in the heading above before you use");
        line(out, "it.");
        line(out, "");
        line(out, "## Rules");
        line(out, "");
        for (String rule : rules) {
            line(out, "- " + rule);
            line(out, "");
        }
        line(out, "---");
        line(out, "");
        line(out, "Generated during the framework build from `context7.json` in the ZeroZ Stack repository,");
        line(out, "which is the single source of these rules. Editing this copy changes nothing; it is written");
        line(out, "again on every build.");
        return out.toString();
    }

    private static void line(StringBuilder out, String text) {
        out.append(text).append(NL);
    }

    // ------------------------------------------------------------------ the parser

    private static List<String> readRules(String json, Path source) {
        int key = json.indexOf("\"rules\"");
        if (key < 0) {
            fail(source + " has no \"rules\" key.");
        }
        int open = json.indexOf('[', key);
        if (open < 0) {
            fail(source + " has a \"rules\" key with no array after it.");
        }

        List<String> rules = new ArrayList<>();
        int i = open + 1;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == ']') {
                return rules;
            }
            if (c == ',' || Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c != '"') {
                fail(source + " holds something other than a string inside \"rules\", at character "
                        + i + ": '" + c + "'. Nothing was written.");
            }
            i = readString(json, i + 1, source, rules);
        }
        fail(source + " has an unterminated \"rules\" array. Nothing was written.");
        return rules;      // unreachable: fail() exits
    }

    /** Reads one JSON string starting after its opening quote; returns the index after its close. */
    private static int readString(String json, int start, Path source, List<String> into) {
        StringBuilder value = new StringBuilder();
        int i = start;
        while (i < json.length()) {
            char ch = json.charAt(i);
            if (ch == '"') {
                into.add(value.toString().trim());
                return i + 1;
            }
            if (ch == '\\') {
                i = readEscape(json, i + 1, source, value);
                continue;
            }
            value.append(ch);
            i++;
        }
        fail(source + " has an unterminated string inside \"rules\". Nothing was written.");
        return i;          // unreachable: fail() exits
    }

    /** Reads one escape sequence starting after the backslash; returns the index after it. */
    private static int readEscape(String json, int start, Path source, StringBuilder into) {
        if (start >= json.length()) {
            fail(source + " ends inside an escape sequence. Nothing was written.");
        }
        char esc = json.charAt(start);
        switch (esc) {
            case '"', '\\', '/' -> into.append(esc);
            // A rule is one paragraph once it reaches the jar, so any whitespace escape is a space.
            case 'n', 't' -> into.append(' ');
            case 'r', 'b', 'f' -> { }
            case 'u' -> {
                if (start + 4 >= json.length()) {
                    fail(source + " ends inside a unicode escape. Nothing was written.");
                }
                into.append((char) Integer.parseInt(json.substring(start + 1, start + 5), 16));
                return start + 5;
            }
            default -> fail(source + " uses the escape backslash-" + esc + ", which this generator "
                    + "does not understand. Nothing was written.");
        }
        return start + 1;
    }

    private static void fail(String message) {
        System.err.println("GenerateAgentRules: " + message);
        System.exit(1);
    }

    private GenerateAgentRules() {
    }
}
