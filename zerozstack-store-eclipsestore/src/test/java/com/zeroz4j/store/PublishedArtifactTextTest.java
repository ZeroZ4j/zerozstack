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
package com.zeroz4j.store;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nothing this repository publishes carries text that was UTF-8 once and got read back through a
 * single-byte code page - and the check knows which modules it was supposed to read, so it cannot
 * quietly cover fewer of them than it claims.
 *
 * <p>This is the release gate for the fault that shipped in 0.7.0: strings in the component library
 * were stored corrupted in the committed source, so every build reproduced them faithfully and the
 * published jar rendered Latin-1 punctuation where a dash, a play triangle or a block cursor
 * belonged. {@code SourceTextEncodingTest} in {@code zerozstack-ui-components} guards the Java a
 * developer types. This one guards everything that leaves the building, including the three things
 * a Java-source check cannot see: the strings baked into compiled classes, resources that are not
 * Java at all, and the project template the archetype hands to every new application.</p>
 *
 * <h2>It works out for itself what it must read</h2>
 *
 * <p>The list of modules that publish is read out of the POM files, starting at the root and
 * following the module declarations down. A module that turns publishing off with
 * {@code maven.deploy.skip} is dropped together with everything beneath it, which is how the
 * examples are excluded. Nothing is hard-coded and no prose is trusted: a module added, removed,
 * renamed or re-scoped changes what this check demands, in the same commit that changes it.</p>
 *
 * <p>Each module then has to account for itself, by its packaging:</p>
 *
 * <ul>
 *   <li><b>A jar module</b> must show its Java sources - which are what the sources jar is made of
 *       - and its compiled form, either as a packaged jar or as {@code target/classes}.</li>
 *   <li><b>The archetype</b> must show {@code src/main/resources}. That directory is the project
 *       template, copied into every application anyone generates, and it is checked from the
 *       checkout rather than from the archetype jar deliberately: the archetype is built at the
 *       very end of the reactor and its jar does not exist while this test runs, and the checkout
 *       is where a contributor would introduce the fault in the first place.</li>
 *   <li><b>Every module</b>, whatever its packaging, must show its own {@code pom.xml}.</li>
 * </ul>
 *
 * <p>If any of that produced nothing to read, the test fails and names the module and what was
 * missing. An expected artifact that is absent is a finding, not a skip. That single rule is what
 * stops this check rotting: reorder the reactor, add a module, rename an output directory, and it
 * says so instead of passing on a smaller job than it was given. It matters here because the
 * reactor order is not the order the modules are declared in - the examples depend on the store
 * module, which moves it a long way up the build - so reasoning about what will have been built by
 * the time this runs is exactly the kind of thing that quietly stops being true.</p>
 *
 * <h2>How it detects corruption</h2>
 *
 * <p>The damage is reversible, and that is the whole test. Take a run of non-ASCII characters,
 * write it back out in one of the code pages that cause this, and try to read those bytes as UTF-8.
 * Text that was never corrupted is not valid UTF-8 in that form and the read fails, which is the
 * passing case. Text that survives the round trip and comes back different was UTF-8 misread, and
 * what comes back is what the author actually typed. The reversal repeats until it stops changing,
 * because a string can go through this more than once - the 0.7.0 strings went through it three
 * times.</p>
 *
 * <p><b>Why it does not fire on good text.</b> Surviving that round trip is a demanding accident:
 * the run has to read as a UTF-8 lead byte followed by continuation bytes, which ordinary accented
 * words, quotation marks, arrows, mathematical symbols and box-drawing diagrams are not. Measured
 * on this repository it reads several thousand class files, sources, resources and generated files
 * - including the complete javadoc of every module and the ASCII-art directory trees in the
 * documentation - and reports nothing, while the same check against the jar actually published as
 * 0.7.0 recovers every damaged string in it.</p>
 *
 * <p>This file deliberately contains no corrupted text of its own, since it would then fail on its
 * own compiled constants.</p>
 */
class PublishedArtifactTextTest {

    @Test
    void nothingPublishedCarriesMisreadUtf8() throws IOException {
        Path root = repositoryRoot();
        List<PublishedModule> modules = publishedModules(root);

        assertTrue(modules.size() > 1,
                "Could not work out which modules this build publishes by reading the POM files "
                        + "under " + root + ". That is a fault in this check, not in the text it "
                        + "was asked to inspect.");

        Scan scan = new Scan(root);
        for (Path jar : jarsUnder(root)) {
            scan.readJar(jar);
        }
        for (Path dir : directoriesUnder(root)) {
            scan.readDirectory(dir);
        }
        for (PublishedModule module : modules) {
            scan.readFile(root.resolve(module.path()).resolve("pom.xml"));
        }

        List<String> gaps = coverageGaps(root, modules, scan);
        assertTrue(gaps.isEmpty(), coverageMessage(gaps, modules, scan));
        assertTrue(scan.findings.isEmpty(), failureMessage(scan));
    }

    // ---------------------------------------------------------------- what must be covered

    /** A module this build publishes, as the POM files describe it. */
    private record PublishedModule(String path, String packaging) { }

    /**
     * Every module the release publishes, read out of the POM files rather than named here. A
     * module that sets {@code maven.deploy.skip} is dropped together with everything beneath it.
     */
    private static List<PublishedModule> publishedModules(Path root) {
        List<PublishedModule> modules = new ArrayList<>();
        collectModules(root, "", modules);
        return modules;
    }

    private static void collectModules(Path root, String relativePath,
                                       List<PublishedModule> collected) {
        Path pom = root.resolve(relativePath).resolve("pom.xml");
        if (!Files.isRegularFile(pom)) {
            return;
        }
        Element project = parse(pom);
        if (Boolean.parseBoolean(property(project, "maven.deploy.skip"))) {
            return;                                        // this module and its children opt out
        }
        String packaging = textOf(child(project, "packaging"));
        collected.add(new PublishedModule(relativePath.isEmpty() ? "." : relativePath,
                packaging.isEmpty() ? "jar" : packaging));

        Element modules = child(project, "modules");
        if (modules == null) {
            return;
        }
        for (Element module : children(modules, "module")) {
            String name = textOf(module);
            if (!name.isEmpty()) {
                collectModules(root, relativePath.isEmpty() ? name : relativePath + "/" + name,
                        collected);
            }
        }
    }

    /** What each module had to show, and the complaint when it showed nothing. */
    private static List<String> coverageGaps(Path root, List<PublishedModule> modules, Scan scan) {
        List<String> gaps = new ArrayList<>();
        for (PublishedModule module : modules) {
            Path dir = root.resolve(module.path()).normalize();
            String name = module.path();

            if (!scan.read(dir.resolve("pom.xml"))) {
                gaps.add("  " + name + " - its own pom.xml was not read");
            }
            switch (module.packaging()) {
                case "jar" -> {
                    if (!scan.readAnythingIn(dir.resolve("src").resolve("main").resolve("java"))) {
                        gaps.add("  " + name + " - no Java source was read from src/main/java, "
                                + "which is what the sources jar is made of");
                    }
                    if (!scan.readAnythingIn(dir.resolve("target").resolve("classes"))
                            && !scan.readAnyJarIn(dir.resolve("target"))) {
                        gaps.add("  " + name + " - its compiled form was not read. Neither a "
                                + "packaged jar nor target/classes was there, so this module has "
                                + "not been built");
                    }
                }
                case "maven-archetype" -> {
                    if (!scan.readAnythingIn(
                            dir.resolve("src").resolve("main").resolve("resources"))) {
                        gaps.add("  " + name + " - the project template under src/main/resources "
                                + "was not read. That template is copied into every application "
                                + "generated from this archetype");
                    }
                }
                default -> {
                    // A pom module publishes only its pom, which is checked above.
                }
            }
        }
        return gaps;
    }

    private static String coverageMessage(List<String> gaps, List<PublishedModule> modules,
                                          Scan scan) {
        String nl = System.lineSeparator();
        return "This check is meant to read everything this build publishes, and it could not."
                + nl
                + "That matters more than it looks. A check that silently inspects less than it "
                + "claims is the same defect it was written to catch, so do not narrow it to make "
                + "this pass." + nl
                + "Missing:" + nl
                + String.join(nl, gaps) + nl
                + "If a module simply has not been compiled yet, build the whole reactor first - "
                + "mvn install from the repository root - and run this again. If a module was "
                + "moved, renamed or given different packaging, teach this check about it." + nl
                + "(" + modules.size() + " publishing modules found in the POM files, "
                + scan.entries + " entries read.)";
    }

    private static String failureMessage(Scan scan) {
        String nl = System.lineSeparator();
        return "Text that was UTF-8 and got read back through a single-byte code page is about to "
                + "be published." + nl
                + "Each line below names the file it is in, the characters that are there now, "
                + "and - after the arrow - what the author originally typed. Open the source file "
                + "behind that entry, put the right-hand side back in, and save the file as UTF-8. "
                + "Then rebuild. An artifact is only ever a copy of the source, so there is nothing "
                + "to fix in the build itself." + nl
                + "Both sides are given as Unicode code points first, because a console on a "
                + "Windows code page cannot print these characters and would mangle them a second "
                + "time. Look up a code point if the copy after it is unreadable." + nl
                + "A line that says a file is not UTF-8 at all means the whole file was saved in "
                + "another encoding - UTF-16 from a shell redirect is the usual one. Save it again "
                + "as UTF-8." + nl
                + String.join(nl, scan.findings) + nl
                + "(" + scan.entries + " entries read.)";
    }

    // ---------------------------------------------------------------- reading

    /** Everything read, what it said, and where it came from. */
    private static final class Scan {

        private final Path root;
        private final List<String> findings = new ArrayList<>();
        private final Set<Path> sources = new LinkedHashSet<>();
        private int entries;

        Scan(Path root) {
            this.root = root;
        }

        boolean read(Path file) {
            return sources.contains(file.normalize());
        }

        boolean readAnythingIn(Path directory) {
            Path wanted = directory.normalize();
            return sources.stream().anyMatch(p -> p.startsWith(wanted));
        }

        boolean readAnyJarIn(Path directory) {
            Path wanted = directory.normalize();
            return sources.stream().anyMatch(
                    p -> p.startsWith(wanted) && p.getFileName().toString().endsWith(".jar"));
        }

        void readJar(Path jar) {
            String label = root.relativize(jar).toString();
            try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(jar))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (!entry.isDirectory()) {
                        inspect(jar, label + " ! " + entry.getName(), entry.getName(),
                                zip.readAllBytes());
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read " + jar, e);
            }
        }

        void readDirectory(Path directory) throws IOException {
            try (Stream<Path> files = Files.walk(directory)) {
                for (Path p : files.filter(Files::isRegularFile).toList()) {
                    readFile(p);
                }
            }
        }

        void readFile(Path file) {
            if (!Files.isRegularFile(file)) {
                return;
            }
            try {
                inspect(file, root.relativize(file).toString(), file.getFileName().toString(),
                        Files.readAllBytes(file));
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read " + file, e);
            }
        }

        private void inspect(Path origin, String label, String name, byte[] bytes) {
            List<String> texts;
            if (name.endsWith(".class")) {
                texts = stringConstantsOf(bytes);
            } else if (isText(name)) {
                String decoded = decodeUtf8OrNull(bytes);
                if (decoded == null) {
                    findings.add("  " + label + "  is not UTF-8 at all");
                    accept(origin);
                    return;
                }
                texts = List.of(decoded);
            } else {
                return;
            }
            accept(origin);
            for (String text : texts) {
                for (String run : nonAsciiRunsOf(text)) {
                    String repaired = repair(run);
                    if (repaired != null) {
                        findings.add("  " + label + "  " + codePoints(run)
                                + "  ->  " + codePoints(repaired) + "   " + repaired);
                    }
                }
            }
        }

        private void accept(Path origin) {
            entries++;
            sources.add(origin.normalize());
        }
    }

    /** Every jar this build produced. Copied dependency jars and test scratch are not ours. */
    private static List<Path> jarsUnder(Path root) throws IOException {
        List<Path> jars = new ArrayList<>();
        try (Stream<Path> all = Files.walk(root)) {
            all.filter(p -> !hidden(root, p))
               .filter(Files::isRegularFile)
               .filter(p -> p.getFileName().toString().endsWith(".jar"))
               .filter(p -> hasSegment(p, "target"))
               .filter(p -> !hasSegment(p, "libs"))
               .filter(p -> !hasSegment(p, "test-classes"))
               .forEach(jars::add);
        }
        Collections.sort(jars);
        return jars;
    }

    /**
     * The directories an artifact is built from: sources, resources, compiled output and generated
     * output. Reading these as well as the jars is what makes the check independent of build order,
     * since a module packaged later in the reactor is still covered by what it is made of.
     */
    private static List<Path> directoriesUnder(Path root) throws IOException {
        List<Path> dirs = new ArrayList<>();
        try (Stream<Path> all = Files.walk(root, 8)) {
            all.filter(p -> !hidden(root, p))
               .filter(Files::isDirectory)
               .filter(PublishedArtifactTextTest::isBuildInput)
               .forEach(dirs::add);
        }
        Collections.sort(dirs);
        return dirs;
    }

    private static boolean isBuildInput(Path dir) {
        Path parent = dir.getParent();
        if (parent == null || parent.getFileName() == null) {
            return false;
        }
        String name = dir.getFileName().toString();
        String parentName = parent.getFileName().toString();
        if (parentName.equals("target")) {
            return name.equals("classes") || name.equals("generated-sources");
        }
        return parentName.equals("main") && (name.equals("java") || name.equals("resources"));
    }

    /** Skips .git, editor state and any private Maven repository a worktree keeps beside itself. */
    private static boolean hidden(Path root, Path p) {
        for (Path part : root.relativize(p)) {
            if (part.toString().startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSegment(Path p, String segment) {
        for (Path part : p) {
            if (part.toString().equals(segment)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- what counts as text

    /**
     * Extensions whose content is text somebody wrote. Everything else in a jar is left alone:
     * fonts, images, signatures and index files are not text and would only produce noise.
     */
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "java", "txt", "md", "html", "htm", "css", "js", "mjs", "json", "properties",
            "xml", "yml", "yaml", "svg", "sql", "csv", "mf", "bat", "sh", "cmd", "vm");

    private static boolean isText(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            // META-INF/services/... entries have no extension and are plain text.
            return name.contains("services/");
        }
        return TEXT_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private static String decodeUtf8OrNull(byte[] bytes) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException notUtf8) {
            return null;
        }
    }

    // ---------------------------------------------------------------- class files

    /** Every string constant in a compiled class - where a literal in the source ends up. */
    private static List<String> stringConstantsOf(byte[] classFile) {
        List<String> constants = new ArrayList<>();
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(classFile))) {
            if (in.readInt() != 0xCAFEBABE) {
                return constants;
            }
            in.readUnsignedShort();                        // minor version
            in.readUnsignedShort();                        // major version
            int count = in.readUnsignedShort();
            for (int i = 1; i < count; i++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case 1 -> {
                        byte[] utf8 = new byte[in.readUnsignedShort()];
                        in.readFully(utf8);
                        String decoded = decodeUtf8OrNull(utf8);
                        if (decoded != null) {
                            constants.add(decoded);
                        }
                    }
                    case 7, 8, 16, 19, 20 -> in.skipBytes(2);
                    case 15 -> in.skipBytes(3);
                    case 3, 4, 9, 10, 11, 12, 17, 18 -> in.skipBytes(4);
                    case 5, 6 -> {
                        in.skipBytes(8);
                        i++;                               // long and double take two slots
                    }
                    default -> {
                        return constants;                  // unknown tag: stop rather than guess
                    }
                }
            }
        } catch (IOException truncatedOrNotAClassFile) {
            return constants;
        }
        return constants;
    }

    // ---------------------------------------------------------------- the detection itself

    private static List<String> nonAsciiRunsOf(String text) {
        List<String> runs = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            if (text.charAt(i) < 0x80) {
                i++;
                continue;
            }
            int start = i;
            while (i < text.length() && text.charAt(i) >= 0x80) {
                i++;
            }
            runs.add(text.substring(start, i));
        }
        return runs;
    }

    /**
     * What the author typed, or null when the run is not misread text. The misreading is reversed
     * as many times as it applies, since a string can go through it more than once.
     */
    private static String repair(String run) {
        for (Map<Character, Byte> codePage : CODE_PAGES.values()) {
            String current = run;
            int rounds = 0;
            while (rounds < 8) {
                String next = readBackAsUtf8(current, codePage);
                if (next == null || next.equals(current)) {
                    break;
                }
                current = next;
                rounds++;
            }
            if (rounds > 0 && couldBeSomethingAWriterTyped(current)) {
                return current;
            }
        }
        return null;
    }

    /**
     * Whether the repaired text is something a person could have written.
     *
     * <p>The reversal is arithmetic, so any two bytes that happen to form valid UTF-8 come back as
     * "the original". Real Spanish is the case that catches this: {@code Íñ} in a name like
     * {@code Íñigo} is the byte pair CD F1, which reads back as U+05A4 — a Hebrew accent that
     * attaches to a letter and cannot stand alone. Nobody typed that, so nothing was misread.</p>
     *
     * <p>Every corruption this test was written for repaired to ordinary text: dashes, curly
     * quotes, umlauts, the play and pause marks. So a repair made only of marks that attach to
     * another character, or of characters with no printed form at all, is arithmetic rather than
     * evidence, and is not reported.</p>
     */
    private static boolean couldBeSomethingAWriterTyped(String repaired) {
        for (int i = 0; i < repaired.length(); i++) {
            int type = Character.getType(repaired.charAt(i));
            boolean attachesToAnother = type == Character.NON_SPACING_MARK
                    || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK;
            boolean hasNoPrintedForm = type == Character.CONTROL
                    || type == Character.FORMAT
                    || type == Character.UNASSIGNED
                    || type == Character.PRIVATE_USE
                    || type == Character.SURROGATE;
            if (!attachesToAnother && !hasNoPrintedForm) {
                return true;
            }
        }
        return false;
    }

    /** The run written out in one code page and read back as UTF-8, or null if either step refuses. */
    private static String readBackAsUtf8(String run, Map<Character, Byte> codePage) {
        byte[] bytes = new byte[run.length()];
        for (int i = 0; i < run.length(); i++) {
            Byte b = codePage.get(run.charAt(i));
            if (b == null) {
                return null;                               // this code page cannot have produced it
            }
            bytes[i] = b;
        }
        return decodeUtf8OrNull(bytes);
    }

    /** What a decoder puts where a code page leaves a byte value undefined. */
    private static final char REPLACEMENT = '\uFFFD';

    /**
     * The code pages that turn UTF-8 into this. Windows-1252 is what a European Windows editor
     * uses; ISO-8859-1 is the default of many tools that predate UTF-8; IBM437 and IBM850 are the
     * console code pages, which give the corruption its box-drawing look.
     *
     * <p>Windows-1252 leaves five byte values undefined. The string that damaged
     * {@code PropertyGrid} in 0.7.0 contained one of them, so for that code page the undefined five
     * are mapped straight through - without which this check would miss exactly the kind of string
     * it was written for.</p>
     */
    private static final Map<String, Map<Character, Byte>> CODE_PAGES = codePages();

    private static Map<String, Map<Character, Byte>> codePages() {
        Map<String, Map<Character, Byte>> pages = new LinkedHashMap<>();
        addCodePage(pages, "windows-1252", true);
        addCodePage(pages, "ISO-8859-1", false);
        addCodePage(pages, "IBM437", false);
        addCodePage(pages, "IBM850", false);
        return pages;
    }

    private static void addCodePage(Map<String, Map<Character, Byte>> pages, String name,
                                    boolean mapUndefinedSlotsStraightThrough) {
        if (!Charset.isSupported(name)) {
            return;
        }
        Charset charset = Charset.forName(name);
        Map<Character, Byte> table = new LinkedHashMap<>();
        for (int b = 0; b < 256; b++) {
            String decoded = new String(new byte[] {(byte) b}, charset);
            boolean defined = decoded.length() == 1 && decoded.charAt(0) != REPLACEMENT;
            if (defined) {
                table.putIfAbsent(decoded.charAt(0), (byte) b);
            } else if (mapUndefinedSlotsStraightThrough) {
                table.putIfAbsent((char) b, (byte) b);
            }
        }
        pages.put(name, table);
    }

    /** The offending run written as code points, so the message survives any console code page. */
    private static String codePoints(String run) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < run.length(); i++) {
            sb.append(String.format("U+%04X ", (int) run.charAt(i)));
        }
        return sb.toString().trim();
    }

    // ---------------------------------------------------------------- small XML helpers

    private static Element parse(Path pom) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(pom.toFile());
            return document.getDocumentElement();
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new IllegalStateException("Could not read " + pom, e);
        }
    }

    private static String property(Element project, String name) {
        Element properties = child(project, "properties");
        return properties == null ? "" : textOf(child(properties, name));
    }

    private static Element child(Element parent, String name) {
        List<Element> found = children(parent, name);
        return found.isEmpty() ? null : found.get(0);
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> found = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && node.getNodeName().equals(name)) {
                found.add((Element) node);
            }
        }
        return found;
    }

    private static String textOf(Element element) {
        return element == null ? "" : element.getTextContent().trim();
    }

    // ---------------------------------------------------------------- where the checkout is

    /** The checkout root, found by walking up from the module this test runs in. */
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
