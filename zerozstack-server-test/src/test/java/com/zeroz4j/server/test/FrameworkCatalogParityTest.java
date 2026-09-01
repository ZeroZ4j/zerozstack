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

import com.zeroz4j.api.i18n.FrameworkKeys;
import com.zeroz4j.api.i18n.FrameworkText;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The framework's own words are written twice, and this is what stops the two copies drifting.
 *
 * <p>They are written twice for a reason that will not go away: the annotation processor depends on
 * {@code zerozstack-shared-api}, so that module is built first and cannot run a processor that does
 * not exist yet. So {@code FrameworkText} is a hand-written {@code switch} and
 * {@code i18n/zeroz4j.properties} is the file translators work from, and if they disagree the
 * server says one thing and every translation is keyed to another.</p>
 *
 * <p>The second half of this test is the one that earns its keep every day: <b>every catalog
 * anywhere in the checkout</b> is checked for a language that has drifted from the fallback one. A
 * missing key leaves a blank space on a screen, and a key whose blanks disagree either drops a
 * value the reader needed or leaves a blank showing.</p>
 */
class FrameworkCatalogParityTest {

    private static final String FRAMEWORK_CATALOG = "zeroz4j";

    @Test
    @DisplayName("the compiled-in English and the .properties file say exactly the same things")
    void theHandWrittenCopyMatchesTheFile() {
        Map<String, String> file = CatalogParity.read(frameworkCatalogFile());
        Set<String> inCode = new LinkedHashSet<>(Arrays.asList(FrameworkText.keys()));

        Set<String> missingFromCode = new TreeSet<>(file.keySet());
        missingFromCode.removeAll(inCode);
        assertTrue(missingFromCode.isEmpty(),
                "i18n/zeroz4j.properties has keys FrameworkText does not: " + missingFromCode
                        + ". The server would answer with the key instead of the sentence.");

        Set<String> missingFromFile = new TreeSet<>(inCode);
        missingFromFile.removeAll(file.keySet());
        assertTrue(missingFromFile.isEmpty(),
                "FrameworkText has keys i18n/zeroz4j.properties does not: " + missingFromFile
                        + ". No translator would ever see them.");

        for (Map.Entry<String, String> entry : file.entrySet()) {
            assertEquals(entry.getValue(), FrameworkText.fallbackText(entry.getKey()),
                    "The English for " + entry.getKey() + " is written differently in "
                            + "FrameworkText than in i18n/zeroz4j.properties.");
        }
    }

    @Test
    @DisplayName("every name in FrameworkKeys is a key the catalog actually has")
    void everyNamedRefusalExists() {
        Set<String> inCode = new LinkedHashSet<>(Arrays.asList(FrameworkText.keys()));
        List<String> orphans = new ArrayList<>();
        for (Field field : FrameworkKeys.class.getDeclaredFields()) {
            if (!Modifier.isPublic(field.getModifiers()) || !Modifier.isStatic(field.getModifiers())
                    || field.getType() != String.class) {
                continue;
            }
            try {
                String key = (String) field.get(null);
                if (!inCode.contains(key)) {
                    orphans.add(field.getName() + " (" + key + ")");
                }
            } catch (IllegalAccessException unreachable) {
                throw new AssertionError(unreachable);
            }
        }
        assertTrue(orphans.isEmpty(),
                "These names in FrameworkKeys point at nothing, so a test asserting on one would "
                        + "never match a real refusal: " + orphans);
    }

    @Test
    @DisplayName("every language of every catalog in the checkout says the same things")
    void noTranslationHasDrifted() {
        List<String> findings = new ArrayList<>();
        int catalogsChecked = 0;
        for (Path folder : catalogFolders()) {
            for (String baseName : fallbackFilesIn(folder)) {
                catalogsChecked++;
                findings.addAll(CatalogParity.check(folder, baseName));
            }
        }
        assertTrue(catalogsChecked > 0,
                "expected to find at least the framework's own catalog in the checkout");
        assertTrue(findings.isEmpty(),
                "A translation has drifted away from the language it was translated from."
                        + System.lineSeparator()
                        + String.join(System.lineSeparator(), findings));
    }

    /**
     * A translation is checked against its fallback wherever in the checkout that fallback lives.
     *
     * <h2>The hole this closes</h2>
     *
     * <p>The check above walks each {@code i18n} folder and looks for files with no language suffix,
     * because that is the fallback every other language is compared with. A folder that holds only
     * translations therefore has nothing to compare against and was skipped in silence.</p>
     *
     * <p>That was not a corner case. Translating this framework's own words means putting
     * {@code i18n/zeroz4j_de.properties} somewhere - and the fallback it belongs to,
     * {@code i18n/zeroz4j.properties}, is in {@code zerozstack-shared-api} and stays there. So the
     * framework's own translations, in the framework's own repository, were the one set of files
     * the drift check could not see. It matters more now that this project ships German itself: its
     * own translation would go unchecked by the very test written to catch drift.</p>
     *
     * <p>So the fallback is looked for by catalog name across the whole checkout rather than beside
     * the file. A translation whose fallback exists nowhere at all is reported too: nothing will
     * ever read it correctly, because there is no key list to read it against.</p>
     */
    @Test
    @DisplayName("a translation whose fallback lives in another module is checked too")
    void aTranslationIsCheckedAgainstItsFallbackWhereverItLives() {
        List<String> findings = new ArrayList<>();
        int orphansChecked = 0;

        for (Path folder : catalogFolders()) {
            for (String baseName : translationsWithNoFallbackBesideThem(folder)) {
                Path fallbackFile = fallbackElsewhere(baseName, folder);
                if (fallbackFile == null) {
                    findings.add("  " + folder + " has translations of " + baseName
                            + " and nothing in this checkout is " + baseName + ".properties. "
                            + "There is no key list to read them against, so nothing can say "
                            + "whether they are complete or even spelled right.");
                    continue;
                }
                orphansChecked++;
                findings.addAll(checkAgainst(fallbackFile, folder, baseName));
            }
        }

        assertTrue(orphansChecked > 0,
                "expected to find at least the framework's own German, whose fallback is in "
                        + "zerozstack-shared-api and which is the reason this check exists");
        assertTrue(findings.isEmpty(),
                "A translation has drifted away from the language it was translated from."
                        + System.lineSeparator()
                        + String.join(System.lineSeparator(), findings));
    }

    /** Catalog names in this folder that have translations here and no fallback file here. */
    private static List<String> translationsWithNoFallbackBesideThem(Path folder) {
        Set<String> withFallback = new LinkedHashSet<>(fallbackFilesIn(folder));
        Set<String> orphans = new TreeSet<>();
        try (Stream<Path> files = Files.list(folder)) {
            files.filter(Files::isRegularFile)
                 .map(file -> file.getFileName().toString())
                 .filter(name -> name.endsWith(".properties"))
                 .map(name -> name.substring(0, name.length() - ".properties".length()))
                 .filter(stem -> stem.indexOf('_') > 0)
                 .map(stem -> stem.substring(0, stem.indexOf('_')))
                 .filter(baseName -> !withFallback.contains(baseName))
                 .forEach(orphans::add);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
        return new ArrayList<>(orphans);
    }

    /** The one {@code <baseName>.properties} in the checkout, outside this folder, or null. */
    private static Path fallbackElsewhere(String baseName, Path except) {
        for (Path folder : catalogFolders()) {
            if (folder.equals(except)) {
                continue;
            }
            Path candidate = folder.resolve(baseName + ".properties");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * The same comparison {@link CatalogParity#check} makes, with the fallback taken from somewhere
     * else. Kept here rather than added to {@code CatalogParity}: an application's own catalog has
     * its languages in one folder, and a published helper that encouraged spreading them around
     * would be selling a shape nobody should copy.
     */
    private static List<String> checkAgainst(Path fallbackFile, Path folder, String baseName) {
        List<String> findings = new ArrayList<>();
        Map<String, String> fallback = CatalogParity.read(fallbackFile);
        List<Path> translations = new ArrayList<>();
        try (Stream<Path> files = Files.list(folder)) {
            files.filter(Files::isRegularFile)
                 .filter(file -> file.getFileName().toString().startsWith(baseName + "_")
                         && file.getFileName().toString().endsWith(".properties"))
                 .sorted()
                 .forEach(translations::add);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }

        for (Path translation : translations) {
            Map<String, String> entries = CatalogParity.read(translation);
            String name = translation.getFileName().toString();

            Set<String> missing = new TreeSet<>(fallback.keySet());
            missing.removeAll(entries.keySet());
            for (String key : missing) {
                findings.add("  " + name + " has no " + key + ", so that sentence comes out in the "
                        + "fallback language. Its fallback is " + fallbackFile + ".");
            }

            Set<String> extra = new TreeSet<>(entries.keySet());
            extra.removeAll(fallback.keySet());
            for (String key : extra) {
                findings.add("  " + name + " has " + key + ", which " + fallbackFile
                        + " does not. Nothing will ever read it.");
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
                            + " where " + fallbackFile + " gives it " + there + ".");
                }
            }
        }
        return findings;
    }

    /** The {@code {0}} blanks in one pattern, by number. */
    private static Set<Integer> blanksIn(String pattern) {
        Set<Integer> blanks = new TreeSet<>();
        if (pattern == null) {
            return blanks;
        }
        java.util.regex.Matcher blank =
                java.util.regex.Pattern.compile("\\{(\\d+)\\}").matcher(pattern);
        while (blank.find()) {
            blanks.add(Integer.valueOf(blank.group(1)));
        }
        return blanks;
    }

    // ---------------------------------------------------------------- helpers

    private static Path frameworkCatalogFile() {
        Path file = repositoryRoot().resolve(Paths.get("zerozstack-shared-api", "src", "main",
                "resources", "i18n", FRAMEWORK_CATALOG + ".properties"));
        assertTrue(Files.isRegularFile(file), "expected to find " + file);
        return file;
    }

    /** Every {@code i18n} folder in the checkout, built code excluded. */
    private static List<Path> catalogFolders() {
        List<Path> folders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(repositoryRoot())) {
            files.filter(Files::isDirectory)
                 .filter(p -> "i18n".equals(p.getFileName().toString()))
                 .filter(p -> !p.toString().contains(java.io.File.separator + "target"
                         + java.io.File.separator))
                 .sorted()
                 .forEach(folders::add);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
        return folders;
    }

    /** The catalogs in one folder: every file with no language suffix. */
    private static List<String> fallbackFilesIn(Path folder) {
        List<String> baseNames = new ArrayList<>();
        try (Stream<Path> files = Files.list(folder)) {
            files.filter(Files::isRegularFile)
                 .map(p -> p.getFileName().toString())
                 .filter(name -> name.endsWith(".properties"))
                 .map(name -> name.substring(0, name.length() - ".properties".length()))
                 .filter(stem -> stem.indexOf('_') < 0)
                 .sorted()
                 .forEach(baseNames::add);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
        return baseNames;
    }

    /** Walks up from the working directory until the checkout's own root pom is found. */
    private static Path repositoryRoot() {
        Path here = Paths.get("").toAbsolutePath();
        for (Path candidate = here; candidate != null; candidate = candidate.getParent()) {
            if (Files.isDirectory(candidate.resolve("zerozstack-shared-api"))
                    && Files.isRegularFile(candidate.resolve("pom.xml"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("could not find the checkout root from " + here);
    }
}
