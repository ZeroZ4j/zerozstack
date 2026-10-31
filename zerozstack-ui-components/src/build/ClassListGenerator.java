/*
 * Copyright 2026 Franz Schöning (https://www.franzschoning.com)
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
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Writes the list of CSS class names the component library can put on a page. Run by the build of
 * zerozstack-ui-components (a plain JDK source-file launch, no dependencies), and shipped in the jar
 * as META-INF/zeroz4j/css/library-classes.txt for the production stylesheet step of a generated
 * application.
 *
 * <p>Usage: {@code java ClassListGenerator.java OUT_FILE SOURCE_DIR...}. A source directory that
 * does not exist is skipped.</p>
 *
 * <p>Two sources of names. Every word inside a Java string literal is listed: Tailwind's scanner
 * ignores words that are not classes, so listing too many costs nothing. Names built by
 * concatenation, such as {@code prefix + "-" + color}, are not in any literal, so each theme prefix
 * returned by a {@code getThemePrefix()} is combined with every {@code ThemeColor}, every
 * {@code ThemeSize} and {@code outline}.</p>
 */
public final class ClassListGenerator {

    private static final Pattern LITERAL = Pattern.compile("\"((?:[^\"\\\\\\n]|\\\\.)*)\"");
    private static final Pattern WORD = Pattern.compile("[a-z!\\-\\[][A-Za-z0-9_:/\\[\\]().%#!&*>~=,'-]{0,99}");
    private static final Pattern PREFIX = Pattern.compile("getThemePrefix\\(\\)\\s*\\{\\s*return\\s+\"([^\"]+)\"");
    private static final Pattern ENUM_SUFFIX = Pattern.compile("(?m)^\\s*[A-Z_]+\\(\"([a-z]+)\"\\)");

    private ClassListGenerator() { }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            throw new IllegalArgumentException("usage: ClassListGenerator OUT_FILE SOURCE_DIR...");
        }
        Path out = Path.of(args[0]);
        TreeSet<String> names = new TreeSet<>();
        TreeSet<String> prefixes = new TreeSet<>();
        TreeSet<String> suffixes = new TreeSet<>();
        suffixes.add("outline");
        for (int i = 1; i < args.length; i++) {
            Path dir = Path.of(args[i]);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            List<Path> files = new ArrayList<>();
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.filter(p -> p.toString().endsWith(".java")).forEach(files::add);
            }
            for (Path file : files) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                Matcher literals = LITERAL.matcher(text);
                while (literals.find()) {
                    for (String word : literals.group(1).split("\\s+")) {
                        if (WORD.matcher(word).matches()) {
                            names.add(word);
                        }
                    }
                }
                Matcher prefix = PREFIX.matcher(text);
                while (prefix.find()) {
                    prefixes.add(prefix.group(1));
                }
                String fileName = file.getFileName().toString();
                if (fileName.equals("ThemeColor.java") || fileName.equals("ThemeSize.java")) {
                    Matcher suffix = ENUM_SUFFIX.matcher(text);
                    while (suffix.find()) {
                        suffixes.add(suffix.group(1));
                    }
                }
            }
        }
        for (String prefix : prefixes) {
            names.add(prefix);
            for (String suffix : suffixes) {
                names.add(prefix + "-" + suffix);
            }
        }
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out, String.join("\n", names) + "\n", StandardCharsets.UTF_8);
        System.out.println("ClassListGenerator: " + names.size() + " names, " + prefixes.size()
            + " theme prefixes -> " + out);
    }
}
