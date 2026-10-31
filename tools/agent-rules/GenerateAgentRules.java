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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Writes the rule list that ships inside {@code zerozstack-shared-api.jar} at
 * {@code META-INF/zeroz4j/AGENTS.md}, and a copy of the documentation beside it at
 * {@code META-INF/zeroz4j/docs/}.
 *
 * <p>Maven runs it as a single-file source program — JDK 21 runs a {@code .java} file directly, so
 * there is nothing to compile first and nothing added to the build's dependencies:</p>
 *
 * <pre>java GenerateAgentRules.java &lt;context7.json&gt; &lt;output.md&gt; &lt;version&gt; &lt;repository root&gt;</pre>
 *
 * <h2>Why generate rather than write</h2>
 *
 * <p>{@code context7.json} already carries the rule list and is the best-maintained assistant
 * document in the repository. A second, hand-written copy would be a second release cycle, and a
 * document describing a version of the framework nobody is running is worse than no document at
 * all. So there is exactly one source, and this program projects it into the jar.</p>
 *
 * <h2>The documentation copy</h2>
 *
 * <p>Every Markdown page under the repository's {@code docs/} is copied to {@code docs/} beside the
 * output file, keeping the folder layout, together with {@code README.md}, {@code CHANGELOG.md} and
 * {@code llms.txt}. Links to {@code main} on GitHub that point at a copied file are rewritten to
 * relative paths, so a reader with no network is never sent to a version the jar does not describe.
 * The build stops when a relative link in the copy points at a file that is not in it.</p>
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
        if (args.length != 4) {
            fail("usage: GenerateAgentRules <context7.json> <output.md> <version> <repository root>");
        }
        Path source = Path.of(args[0]);
        Path target = Path.of(args[1]);
        String version = args[2];
        Path repository = Path.of(args[3]);

        String json = Files.readString(source, StandardCharsets.UTF_8);
        List<String> rules = readRules(json, source);
        if (rules.size() < FEWEST_PLAUSIBLE_RULES) {
            fail(source + " yielded only " + rules.size() + " rules, which cannot be right. Either "
                    + "the file changed shape or the parser in GenerateAgentRules no longer "
                    + "understands it. Nothing was written.");
        }
        List<String> components =
                readComponents(repository.resolve("docs").resolve("UI_COMPONENTS.md"));

        Files.createDirectories(target.getParent());
        Files.writeString(target, document(version, rules, components), StandardCharsets.UTF_8);
        int files = copyDocumentation(repository, target.getParent().resolve("docs"));
        System.out.println("Wrote " + rules.size() + " rules, " + components.size()
                + " components and " + files + " documentation files for " + version + " to "
                + target.getParent());
    }

    // ------------------------------------------------------------------ the documentation copy

    private static final String GITHUB_MAIN = "https://github.com/ZeroZ4j/zerozstack/blob/main/";
    private static final Pattern GITHUB_LINK =
            Pattern.compile(Pattern.quote(GITHUB_MAIN) + "([^)\\s>\"`]+)");
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\]\\(([^)\\s]+)\\)");
    /** Repository files that are not in the jar; in a root file a link to one becomes plain text. */
    private static final Pattern NOT_SHIPPED = Pattern.compile("\\[([^\\]]+)\\]\\("
            + "(?:CONTRIBUTING\\.md|RELEASING\\.md|LICENSE|NOTICE|zerozstack-examples/)[^)]*\\)");

    /**
     * Copies the documentation into {@code target}, keeping the folder layout of {@code docs/}. The
     * root files sit at the top of that folder, so the {@code docs/x.md} links in
     * {@code README.md} and {@code CHANGELOG.md} become {@code x.md}. Returns the number of files written.
     */
    private static int copyDocumentation(Path repository, Path target) throws IOException {
        Path docs = repository.resolve("docs");
        List<Path> written = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(docs)) {
            for (Path file : walk.filter(Files::isRegularFile).sorted().toList()) {
                if (file.getFileName().toString().endsWith(".md")) {
                    written.add(copyPage(file, target.resolve(docs.relativize(file).toString()),
                            false));
                }
            }
        }
        written.add(copyPage(repository.resolve("README.md"), target.resolve("README.md"), true));
        written.add(copyPage(repository.resolve("CHANGELOG.md"), target.resolve("CHANGELOG.md"),
                true));
        written.add(copyPage(repository.resolve("llms.txt"), target.resolve("llms.txt"), false));
        checkLinks(target, written);
        return written.size();
    }

    private static Path copyPage(Path from, Path to, boolean isRepositoryRootFile) throws IOException {
        String text = Files.readString(from, StandardCharsets.UTF_8);
        Path folder = to.getParent();
        // How many folders sit between this page and the top of the copied documentation.
        int depth = 0;
        for (Path p = folder; p != null && !p.getFileName().toString().equals("docs");
                p = p.getParent()) {
            depth++;
        }
        String up = "../".repeat(depth);

        Matcher m = GITHUB_LINK.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String path = m.group(1);
            String replacement;
            if (path.startsWith("docs/")) {
                replacement = up + path.substring("docs/".length());
            } else if (path.equals("AGENTS.md")) {
                replacement = up + "../AGENTS.md";       // the generated rules, one level above docs/
            } else if (path.equals("llms.txt") || path.equals("README.md")
                    || path.equals("CHANGELOG.md")) {
                replacement = up + path;
            } else {
                replacement = m.group();                  // not in the jar; stays as written
            }
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        text = out.toString();

        if (isRepositoryRootFile) {
            text = text.replace("](docs/", "](");
            text = NOT_SHIPPED.matcher(text).replaceAll("$1");
        }
        Files.createDirectories(folder);
        Files.writeString(to, text, StandardCharsets.UTF_8);
        return to;
    }

    /** Stops the build when a relative link in the copy points at a file that is not in it. */
    private static void checkLinks(Path root, List<Path> files) throws IOException {
        List<String> broken = new ArrayList<>();
        for (Path file : files) {
            boolean fenced = false;
            int number = 0;
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                number++;
                if (line.stripLeading().startsWith("```")) {
                    fenced = !fenced;
                }
                if (fenced) {
                    continue;
                }
                Matcher m = MARKDOWN_LINK.matcher(line.replaceAll("`[^`]*`", ""));   // not in code
                while (m.find()) {
                    String link = m.group(1);
                    if (link.startsWith("http") || link.startsWith("#") || link.startsWith("mailto:")) {
                        continue;
                    }
                    int hash = link.indexOf('#');
                    String path = hash >= 0 ? link.substring(0, hash) : link;
                    if (!path.isEmpty() && !Files.exists(file.getParent().resolve(path).normalize())) {
                        broken.add("  " + root.relativize(file) + ":" + number + "  " + link);
                    }
                }
            }
        }
        if (!broken.isEmpty()) {
            fail("the documentation copy has relative links to files that are not in it:\n"
                    + String.join("\n", broken) + "\nFix the link in the source page, or teach "
                    + "copyPage about the file.");
        }
    }

    // ------------------------------------------------------------------ the component list

    private static final Pattern COMPONENT_ENTRY = Pattern.compile("^- \\*\\*([^*]+)\\*\\*: (.*)$");
    private static final int FEWEST_PLAUSIBLE_COMPONENTS = 60;

    /**
     * One line per entry of the "Component Reference" section of {@code UI_COMPONENTS.md}: the name
     * and the first sentence of its description. Read from the page, so it cannot disagree with it.
     */
    private static List<String> readComponents(Path page) throws IOException {
        List<String> found = new ArrayList<>();
        boolean inReference = false;
        String name = null;
        StringBuilder description = new StringBuilder();
        for (String line : Files.readAllLines(page, StandardCharsets.UTF_8)) {
            if (line.startsWith("## ")) {
                inReference = line.trim().equals("## Component Reference");
            }
            if (!inReference) {
                continue;
            }
            Matcher m = COMPONENT_ENTRY.matcher(line);
            boolean entry = m.matches();
            boolean continuation = name != null && line.startsWith("  ") && !line.isBlank();
            if (entry || !continuation) {
                if (name != null) {
                    found.add(name + ": " + firstSentence(description.toString()));
                }
                name = null;
                description.setLength(0);
            }
            if (entry) {
                name = m.group(1).trim();
                description.append(m.group(2).trim());
            } else if (continuation) {
                description.append(' ').append(line.trim());
            }
        }
        if (name != null) {
            found.add(name + ": " + firstSentence(description.toString()));
        }
        if (found.size() < FEWEST_PLAUSIBLE_COMPONENTS) {
            fail(page + " yielded only " + found.size() + " component entries, which cannot be "
                    + "right. The Component Reference section changed shape. Nothing was written.");
        }
        return found;
    }

    /** Up to the first full stop followed by a space, or the whole text. */
    private static String firstSentence(String text) {
        int end = text.indexOf(". ");
        String sentence = end < 0 ? text : text.substring(0, end + 1);
        if (!sentence.endsWith(".")) {
            sentence = sentence + ".";
        }
        return sentence.replace("**", "");
    }

    // ------------------------------------------------------------------ the document

    private static final String NL = "\n";

    private static String document(String version, List<String> rules, List<String> components) {
        StringBuilder out = new StringBuilder();

        line(out, "# ZeroZ Stack " + version + " - rules for AI coding assistants");
        line(out, "");
        line(out, "You are reading this out of `zerozstack-shared-api-" + version + ".jar`. That matters: it");
        line(out, "describes **the version of ZeroZ Stack this project actually resolves**, not the newest one.");
        line(out, "The published documentation follows the framework's main line, so it describes things a");
        line(out, "project on an older version does not have. Where the two disagree, this file is the one that");
        line(out, "matches the code on this project's classpath.");
        line(out, "");
        line(out, "## The full documentation for this version is in this jar");
        line(out, "");
        line(out, "Every guide, reference page and decision page for " + version + " is in the same jar as this");
        line(out, "file, under `META-INF/zeroz4j/docs/`, in the folder layout of the repository's `docs/`");
        line(out, "folder. The pages were copied from the source when this jar was built, so they cannot");
        line(out, "describe another version. Beside them are `llms.txt` (the index of pages), `README.md`");
        line(out, "and `CHANGELOG.md`. Links between pages are relative and work inside that folder.");
        line(out, "Read the page for your task before you write code. \"Before you write\" below says which");
        line(out, "page, and `META-INF/zeroz4j/docs/index.md` starts the whole set.");
        line(out, "");
        line(out, "Read one page without the network. The jar is in the local Maven repository, which is");
        line(out, "`~/.m2/repository` unless `maven.repo.local` says otherwise:");
        line(out, "");
        line(out, "```bash");
        line(out, "JAR=~/.m2/repository/com/zeroz4j/zerozstack-shared-api/" + version + "/zerozstack-shared-api-" + version + ".jar");
        line(out, "unzip -p $JAR META-INF/zeroz4j/docs/guides/troubleshooting.md");
        line(out, "unzip -Z1 $JAR | grep '^META-INF/zeroz4j/docs/'");
        line(out, "```");
        line(out, "");
        line(out, "Without `unzip`, `jar xf $JAR META-INF/zeroz4j/docs` writes the folder into the current");
        line(out, "directory, and `jar tf $JAR` lists the jar. To put the pages on disk from a project:");
        line(out, "");
        line(out, "```bash");
        line(out, "mvn dependency:unpack -Dartifact=com.zeroz4j:zerozstack-shared-api:" + version + " -DoutputDirectory=target/zeroz4j-docs -Dmdep.unpack.includes='META-INF/zeroz4j/**'");
        line(out, "```");
        line(out, "");
        line(out, "Only the pages are in the jar. The example applications are in the repository,");
        line(out, "<https://github.com/ZeroZ4j/zerozstack>.");
        line(out, "");
        line(out, "Online copies of the documentation follow the framework's main line, not your version:");
        line(out, "");
        line(out, "- <https://stack.zeroz4j.com/>");
        line(out, "- Context7 index `/zeroz4j/zerozstack`");
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
        line(out, "## Components that already exist");
        line(out, "");
        line(out, "Check this list before you build a screen part. It is `com.zeroz4j.ui`, one line each, read");
        line(out, "from `docs/UI_COMPONENTS.md`, which has the full description and examples.");
        line(out, "");
        for (String component : components) {
            line(out, "- " + component);
        }
        line(out, "");
        line(out, "---");
        line(out, "");
        line(out, "Generated during the framework build from `context7.json` and `docs/UI_COMPONENTS.md` in the");
        line(out, "ZeroZ Stack repository, which are the single source of these rules. Editing this copy changes");
        line(out, "nothing; it is written again on every build.");
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
