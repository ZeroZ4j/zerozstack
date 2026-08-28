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
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every Java source file in the checkout is stored as UTF-8, and none of them contains text that
 * was already UTF-8 once and got read back as Windows-1252.
 *
 * <p>Eight strings in this module reached the published 0.7.0 jar in that state, three rounds of
 * it deep — the pause and play-speed labels of {@code LaneTimeline}, the deleted-lines marker of
 * {@code DiffView}, the empty-value dash of {@code PropertyGrid} and the caret of
 * {@code StreamingText} — so applications rendered {@code ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â} where a dash
 * belonged. The build was never at fault: the corruption was in the committed bytes, so every
 * build faithfully reproduced it and nothing anywhere reported a problem. Only reading the files
 * catches this, which is what this test does.</p>
 *
 * <p><b>How it detects it.</b> Corruption of this kind is reversible. Take a run of non-ASCII
 * characters, write it back out as Windows-1252, and try to read those bytes as UTF-8: text that
 * was never corrupted is not valid UTF-8 in that form and the read fails, which is the passing
 * case. Text that survives the round trip and comes back different was UTF-8 misread, and the
 * result is what the author actually typed.</p>
 */
class SourceTextEncodingTest {

    @Test
    void noSourceFileContainsMisreadUtf8() throws IOException {
        Path root = repositoryRoot();
        List<String> findings = new ArrayList<>();

        try (Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                 .filter(p -> p.getFileName().toString().endsWith(".java"))
                 .filter(p -> !p.toString().contains(java.io.File.separator + "target"
                         + java.io.File.separator))
                 .forEach(p -> findings.addAll(misreadRunsIn(root, p)));
        }

        assertTrue(findings.isEmpty(),
                "Source text that was UTF-8 and got read back as Windows-1252. The right-hand side "
                        + "is what the author typed; paste that back in and save the file as UTF-8."
                        + System.lineSeparator()
                        + String.join(System.lineSeparator(), findings));
    }

    private static List<String> misreadRunsIn(Path root, Path file) {
        String text;
        try {
            text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        List<String> findings = new ArrayList<>();
        int line = 1;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\n') {
                line++;
                i++;
                continue;
            }
            if (c < 0x80) {
                i++;
                continue;
            }
            int start = i;
            while (i < text.length() && text.charAt(i) >= 0x80) {
                i++;
            }
            String run = text.substring(start, i);
            String repaired = repair(run);
            if (repaired != null) {
                findings.add("  " + root.relativize(file) + ":" + line
                        + "  " + describe(run) + "  ->  " + repaired);
            }
        }
        return findings;
    }

    /**
     * Reverses the misreading as many times as it applies, or returns null when the run is not
     * misread text at all.
     */
    private static String repair(String run) {
        String current = run;
        for (int round = 0; round < 8; round++) {
            String next = readBackAsUtf8(current);
            if (next == null || next.equals(current)) {
                return round == 0 ? null : current;
            }
            current = next;
        }
        return current;
    }

    /** The run written as Windows-1252 and read back as UTF-8, or null if either step refuses. */
    private static String readBackAsUtf8(String run) {
        try {
            CharsetEncoder toBytes = WINDOWS_1252.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            ByteBuffer bytes = toBytes.encode(CharBuffer.wrap(run));

            CharsetDecoder asUtf8 = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            return asUtf8.decode(bytes).toString();
        } catch (CharacterCodingException notMisreadText) {
            return null;
        }
    }

    private static String describe(String run) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < run.length(); i++) {
            sb.append(String.format("U+%04X ", (int) run.charAt(i)));
        }
        return sb.toString().trim();
    }

    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

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
