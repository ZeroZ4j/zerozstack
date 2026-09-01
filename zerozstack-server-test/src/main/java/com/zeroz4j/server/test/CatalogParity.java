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
package com.zeroz4j.server.test;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/**
 * Fails the build when one language of a catalog has drifted away from the others.
 *
 * <h2>Why this is a test and not something the compiler does</h2>
 *
 * <p>Only the fallback language is read when the code is compiled, on purpose: adding a language
 * has to be dropping a file in and restarting, with nothing regenerated and no browser bundle
 * growing. So nothing about the other languages is checked by the compiler at all, and the two
 * ways a translation goes wrong are both silent:</p>
 *
 * <ul>
 *   <li><b>A key that is missing</b> leaves an empty space, or English, where a translated sentence
 *       should be.</li>
 *   <li><b>A key whose blanks disagree</b> is worse. A German sentence written with one blank where
 *       English has two either leaves a value out that the reader needed, or shows a blank on
 *       somebody's screen.</li>
 * </ul>
 *
 * <h2>Using it</h2>
 *
 * <p>One test in the shared module, three lines long:</p>
 *
 * <pre>{@code
 * @Test
 * void everyLanguageSaysTheSameThings() {
 *     CatalogParity.assertConsistent(Paths.get("src/main/resources/i18n"), "app");
 * }
 * }</pre>
 *
 * @since 0.9.0
 */
public final class CatalogParity {

    private CatalogParity() {
    }

    /**
     * Checks one catalog and fails with a list of everything wrong with it.
     *
     * @param folder   the folder holding the {@code .properties} files
     * @param baseName the catalog's file name with no language suffix, for example {@code "app"}
     * @throws AssertionError when a language is missing keys, carries keys the fallback has not, or
     *                        gives a key a different number of blanks
     */
    public static void assertConsistent(Path folder, String baseName) {
        List<String> findings = check(folder, baseName);
        if (!findings.isEmpty()) {
            throw new AssertionError("The languages of the catalog " + baseName + " in " + folder
                    + " do not say the same things." + System.lineSeparator()
                    + "A missing key leaves a blank space on a screen, and a key whose blanks do "
                    + "not match either drops a value the reader needed or leaves a blank showing."
                    + System.lineSeparator()
                    + String.join(System.lineSeparator(), findings));
        }
    }

    /**
     * Everything wrong with one catalog, as sentences.
     *
     * @param folder   the folder holding the {@code .properties} files
     * @param baseName the catalog's file name with no language suffix
     * @return the findings, empty when every language agrees
     */
    public static List<String> check(Path folder, String baseName) {
        List<String> findings = new ArrayList<>();
        Path fallbackFile = folder.resolve(baseName + ".properties");
        if (!Files.isRegularFile(fallbackFile)) {
            findings.add("  " + fallbackFile + " is not there. That is the fallback language, and "
                    + "every other language is compared with it.");
            return findings;
        }
        Map<String, String> fallback = read(fallbackFile);
        for (Path translation : siblings(folder, baseName)) {
            Map<String, String> entries = read(translation);
            String name = translation.getFileName().toString();

            Set<String> missing = new TreeSet<>(fallback.keySet());
            missing.removeAll(entries.keySet());
            for (String key : missing) {
                findings.add("  " + name + " has no " + key
                        + ", so that sentence comes out in the fallback language.");
            }

            Set<String> extra = new TreeSet<>(entries.keySet());
            extra.removeAll(fallback.keySet());
            for (String key : extra) {
                findings.add("  " + name + " has " + key
                        + ", which the fallback language does not. Nothing will ever read it.");
            }

            for (Map.Entry<String, String> entry : entries.entrySet()) {
                String original = fallback.get(entry.getKey());
                if (original == null) {
                    continue;
                }
                Set<Integer> here = blanksIn(entry.getValue());
                Set<Integer> there = blanksIn(original);
                if (!here.equals(there)) {
                    findings.add("  " + name + " gives " + entry.getKey() + " the blanks " + here
                            + " where the fallback language gives it " + there + ".");
                }
            }
        }
        return findings;
    }

    /**
     * Reads one {@code .properties} file as UTF-8, keeping the order the keys were written in.
     *
     * <p>The values come from {@code java.util.Properties}, so escapes and separators behave
     * exactly as they do when the server reads the same file. The order comes from a scan of the
     * text, because a hash table has no order to report.</p>
     *
     * @param file the file
     * @return its entries
     */
    public static Map<String, String> read(Path file) {
        String contents;
        try {
            contents = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
        Properties values = new Properties();
        try {
            values.load(new StringReader(contents));
        } catch (IOException impossible) {
            throw new UncheckedIOException(impossible);
        }
        Map<String, String> entries = new LinkedHashMap<>();
        boolean continued = false;
        for (String rawLine : contents.split("\r\n|\n|\r", -1)) {
            boolean wasContinued = continued;
            continued = rawLine.endsWith("\\") && !rawLine.endsWith("\\\\");
            if (wasContinued) {
                continue;
            }
            String trimmed = rawLine.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
                continue;
            }
            StringBuilder key = new StringBuilder();
            for (int at = 0; at < trimmed.length(); at++) {
                char here = trimmed.charAt(at);
                if (here == '\\' && at + 1 < trimmed.length()) {
                    key.append(trimmed.charAt(++at));
                    continue;
                }
                if (here == '=' || here == ':' || here == ' ' || here == '\t') {
                    break;
                }
                key.append(here);
            }
            String found = key.length() == 0 ? null : values.getProperty(key.toString());
            if (found != null) {
                entries.put(key.toString(), found);
            }
        }
        for (String key : values.stringPropertyNames()) {
            entries.putIfAbsent(key, values.getProperty(key));
        }
        return entries;
    }

    /**
     * The numbers of the blanks in one pattern.
     *
     * @param pattern the pattern
     * @return the blank numbers it uses
     */
    public static Set<Integer> blanksIn(String pattern) {
        Set<Integer> found = new TreeSet<>();
        int at = 0;
        while (at < pattern.length()) {
            int open = pattern.indexOf('{', at);
            if (open < 0) {
                break;
            }
            int close = pattern.indexOf('}', open);
            if (close < 0) {
                break;
            }
            String inside = pattern.substring(open + 1, close);
            at = close + 1;
            boolean digitsOnly = !inside.isEmpty();
            for (int scan = 0; scan < inside.length(); scan++) {
                char here = inside.charAt(scan);
                if (here < '0' || here > '9') {
                    digitsOnly = false;
                    break;
                }
            }
            if (digitsOnly) {
                try {
                    found.add(Integer.valueOf(Integer.parseInt(inside)));
                } catch (NumberFormatException tooBig) {
                    // Not a blank. Leave it alone.
                }
            }
        }
        return found;
    }

    /**
     * Every translated file beside the fallback one.
     *
     * @param folder   the folder to look in
     * @param baseName the catalog's file name with no language suffix
     * @return the translation files, in name order
     */
    public static List<Path> siblings(Path folder, String baseName) {
        Set<Path> found = new LinkedHashSet<>();
        String prefix = baseName + "_";
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(folder)) {
            List<Path> sorted = new ArrayList<>();
            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                if (name.startsWith(prefix) && name.endsWith(".properties")) {
                    sorted.add(entry);
                }
            }
            sorted.sort((left, right) -> left.getFileName().toString()
                    .compareTo(right.getFileName().toString()));
            found.addAll(sorted);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
        return new ArrayList<>(found);
    }
}
