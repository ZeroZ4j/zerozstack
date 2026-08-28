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
 * single-byte code page.
 *
 * <p>This is the release gate for the fault that shipped in 0.7.0: strings in the component library
 * were stored corrupted in the committed source, so every build reproduced them faithfully and the
 * published jar rendered Latin-1 punctuation where a dash, a play triangle or a block cursor
 * belonged. {@code SourceTextEncodingTest} in {@code zerozstack-ui-components} guards the source a
 * developer types. This one guards what actually leaves the building, and so also covers the three
 * things a source check cannot see: string constants inside compiled classes, resources that are
 * not Java at all, and the files the annotation processor generates.</p>
 *
 * <p><b>What it reads.</b> Every jar this build produced - any {@code target/*.jar} in the
 * checkout, minus the copied dependency jars under {@code target/libs} - plus every module's
 * {@code target/classes}, {@code target/generated-sources} and {@code src/main/resources}. Reading
 * those directories as well as the jars is what makes the check independent of build order: a
 * module packaged later in the reactor than this one is still covered, because a jar is only ever
 * a copy of them.</p>
 *
 * <p><b>How it detects corruption.</b> The damage is reversible, and that is the whole test. Take a
 * run of non-ASCII characters, write it back out in one of the code pages that cause this, and try
 * to read those bytes as UTF-8. Text that was never corrupted is not valid UTF-8 in that form and
 * the read fails, which is the passing case. Text that survives the round trip and comes back
 * different was UTF-8 misread, and what comes back is what the author actually typed. The reversal
 * repeats until it stops changing, because a string can go through this more than once.</p>
 *
 * <p><b>Why it does not fire on good text.</b> Surviving that round trip is a demanding accident:
 * the run has to read as a UTF-8 lead byte followed by continuation bytes, which ordinary accented
 * words, quotation marks, arrows, mathematical symbols and box-drawing diagrams are not. Measured
 * on this repository it reads about three thousand class files, resources and generated sources -
 * including the complete javadoc of every module and the ASCII-art directory trees in the
 * documentation - and reports nothing, while it recovers every corrupted string the published
 * 0.7.0 jar carries.</p>
 *
 * <p>This file deliberately contains no corrupted text of its own, since it would then fail on its
 * own compiled constants.</p>
 */
class PublishedArtifactTextTest {

    @Test
    void nothingPublishedCarriesMisreadUtf8() throws IOException {
        Path root = repositoryRoot();
        List<String> findings = new ArrayList<>();
        int[] scanned = {0};

        for (Path jar : jarsUnder(root)) {
            readJar(root, jar, findings, scanned);
        }
        for (Path dir : directoriesUnder(root)) {
            readDirectory(root, dir, findings, scanned);
        }

        assertTrue(scanned[0] > 0,
                "This check found nothing to read under " + root + ". It reads compiled output, so "
                        + "build the modules first (mvn install) and run it again.");

        assertTrue(findings.isEmpty(), failureMessage(findings, scanned[0]));
    }

    private static String failureMessage(List<String> findings, int scanned) {
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
                + String.join(nl, findings) + nl
                + "(" + scanned + " entries read.)";
    }

    // ---------------------------------------------------------------- what to read

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

    /** The directories a jar is built from: compiled output, generated sources and resources. */
    private static List<Path> directoriesUnder(Path root) throws IOException {
        List<Path> dirs = new ArrayList<>();
        try (Stream<Path> all = Files.walk(root, 7)) {
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
        return name.equals("resources") && parentName.equals("main");
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

    private static void readJar(Path root, Path jar, List<String> findings, int[] scanned) {
        String label = root.relativize(jar).toString();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(jar))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    inspect(label + " ! " + entry.getName(), entry.getName(),
                            zip.readAllBytes(), findings, scanned);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + jar, e);
        }
    }

    private static void readDirectory(Path root, Path dir, List<String> findings, int[] scanned)
            throws IOException {
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path p : files.filter(Files::isRegularFile).toList()) {
                inspect(root.relativize(p).toString(), p.getFileName().toString(),
                        Files.readAllBytes(p), findings, scanned);
            }
        }
    }

    // ---------------------------------------------------------------- what counts as text

    /**
     * Extensions whose content is text somebody wrote. Everything else in a jar is left alone:
     * fonts, images, signatures and index files are not text and would only produce noise.
     */
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "java", "txt", "md", "html", "htm", "css", "js", "mjs", "json", "properties",
            "xml", "yml", "yaml", "svg", "sql", "csv", "mf", "bat", "sh", "cmd", "vm");

    private static void inspect(String label, String name, byte[] bytes,
                                List<String> findings, int[] scanned) {
        List<String> texts;
        if (name.endsWith(".class")) {
            texts = stringConstantsOf(bytes);
        } else if (isText(name)) {
            String decoded = decodeUtf8OrNull(bytes);
            if (decoded == null) {
                findings.add("  " + label + "  is not UTF-8 at all");
                scanned[0]++;
                return;
            }
            texts = List.of(decoded);
        } else {
            return;
        }
        scanned[0]++;
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
            if (rounds > 0) {
                return current;
            }
        }
        return null;
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

    /**
     * The code pages that turn UTF-8 into this. Windows-1252 is what a European Windows editor
     * uses; ISO-8859-1 is the default of many tools that predate UTF-8; IBM437 and IBM850 are the
     * console code pages, which give the corruption its box-drawing look.
     *
     * <p>Windows-1252 leaves five byte values undefined. The string that damaged
     * {@code PropertyGrid} in 0.7.0 contained one of them, so for that code page the undefined
     * five are mapped straight through - without which this check would miss exactly the kind of
     * string it was written for.</p>
     */
    /** What a decoder puts where a code page leaves a byte value undefined. */
    private static final char REPLACEMENT = '�';

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
