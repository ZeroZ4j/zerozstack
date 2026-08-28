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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The documentation is written in American English, as the style guide requires.
 *
 * <p>Every Markdown file in the checkout, plus {@code llms.txt} and {@code context7.json}, is read
 * and any British spelling in its prose fails the build, naming the file, the line and the word to
 * use instead. Before this check the guide said "American spelling" and every page ever written was
 * British — a rule that survives on one person's memory is not a rule.</p>
 *
 * <h2>The list is words, not a rule</h2>
 *
 * <p>There is no "-ise becomes -ize" pattern here on purpose. Such a rule catches
 * {@code recognise}, and also fires on {@code advertise}, {@code exercise}, {@code surprise} and
 * {@code promise}, which are spelled that way on both sides of the Atlantic. So {@link #PAIRS} is
 * an explicit list. It was built by reading the documentation as it stood, collecting every British
 * form that actually appeared, and adding the near neighbors of each one — the other tenses of the
 * same verb, the plural of the same noun — since those are what the next writer will type. It is
 * therefore deliberately incomplete: a British word nobody has ever written here is not on it. Add
 * to it when one appears rather than reaching for a pattern.</p>
 *
 * <h2>What is exempt, and why</h2>
 *
 * <ul>
 *   <li><b>Fenced code blocks</b> — everything between a pair of triple-backtick lines.</li>
 *   <li><b>Inline code spans</b> — anything between single backticks. API names, file names,
 *       command lines and settings keys all live there.</li>
 *   <li><b>{@code Flavour} with a capital F</b> — TeaVM Flavour is a product, and
 *       {@code FlavourWrapper} is a class in this library. The documentation must spell a real name
 *       the way the code spells it, or it is lying about the code.</li>
 * </ul>
 *
 * <p>The source code itself is <b>not</b> checked. Its comments and user-facing strings still carry
 * British spellings; converting those is a code change with its own risks, and is a separate
 * decision.</p>
 */
class DocumentationSpellingTest {

    /** British form, then the American form to use instead. Longest keys are tried first. */
    private static final Map<String, String> PAIRS = new LinkedHashMap<>();

    static {
        // -our
        put("colour", "color");
        put("colours", "colors");
        put("coloured", "colored");
        put("colouring", "coloring");
        put("colourful", "colorful");
        put("recolour", "recolor");
        put("recolours", "recolors");
        put("behaviour", "behavior");
        put("behaviours", "behaviors");
        put("behavioural", "behavioral");
        put("neighbour", "neighbor");
        put("neighbours", "neighbors");
        put("neighbouring", "neighboring");
        put("flavours", "flavors");                 // lowercase only; see Flavour exemption
        put("favour", "favor");
        put("favours", "favors");
        put("favoured", "favored");
        put("favourite", "favorite");
        put("honour", "honor");
        put("honours", "honors");
        put("honoured", "honored");
        put("labour", "labor");
        put("humour", "humor");
        put("endeavour", "endeavor");

        // -ise / -isation
        put("organise", "organize");
        put("organised", "organized");
        put("organising", "organizing");
        put("organisation", "organization");
        put("reorganisation", "reorganization");
        put("serialise", "serialize");
        put("serialised", "serialized");
        put("serialising", "serializing");
        put("serialisation", "serialization");
        put("deserialise", "deserialize");
        put("deserialised", "deserialized");
        put("deserialisation", "deserialization");
        put("normalise", "normalize");
        put("normalised", "normalized");
        put("normalising", "normalizing");
        put("normalisation", "normalization");
        put("synchronise", "synchronize");
        put("synchronised", "synchronized");
        put("synchronisation", "synchronization");
        put("recognise", "recognize");
        put("recognises", "recognizes");
        put("recognised", "recognized");
        put("recognising", "recognizing");
        put("initialise", "initialize");
        put("initialised", "initialized");
        put("initialisation", "initialization");
        put("optimise", "optimize");
        put("optimised", "optimized");
        put("optimising", "optimizing");
        put("optimisation", "optimization");
        put("standardise", "standardize");
        put("standardised", "standardized");
        put("customise", "customize");
        put("customised", "customized");
        put("customisation", "customization");
        put("summarise", "summarize");
        put("emphasise", "emphasize");
        put("prioritise", "prioritize");
        put("categorise", "categorize");
        put("minimise", "minimize");
        put("maximise", "maximize");
        put("utilise", "utilize");
        put("industrialise", "industrialize");
        put("industrialised", "industrialized");
        put("analyse", "analyze");
        put("analysed", "analyzed");
        put("analysing", "analyzing");

        // doubled l
        put("cancelled", "canceled");
        put("cancelling", "canceling");
        put("labelled", "labeled");
        put("labelling", "labeling");
        put("unlabelled", "unlabeled");
        put("modelled", "modeled");
        put("modelling", "modeling");
        put("travelled", "traveled");
        put("travelling", "traveling");
        put("signalled", "signaled");
        put("signalling", "signaling");
        put("fulfil", "fulfill");
        put("fulfilment", "fulfillment");
        put("skilful", "skillful");
        put("marvellous", "marvelous");

        // -re, -ce, -ogue, and the rest
        put("centre", "center");
        put("centres", "centers");
        put("centred", "centered");
        put("metre", "meter");
        put("metres", "meters");
        put("fibre", "fiber");
        put("litre", "liter");
        put("theatre", "theater");
        put("licence", "license");
        put("licences", "licenses");
        put("defence", "defense");
        put("offence", "offense");
        put("pretence", "pretense");
        put("practise", "practice");
        put("catalogue", "catalog");
        put("catalogues", "catalogs");
        put("catalogued", "cataloged");
        put("cataloguing", "cataloging");
        put("dialogue", "dialog");
        put("dialogues", "dialogs");
        put("grey", "gray");
        put("greys", "grays");
        put("greyed", "grayed");
        put("judgement", "judgment");
        put("judgements", "judgments");
        put("acknowledgement", "acknowledgment");
        put("acknowledgements", "acknowledgments");
        put("programme", "program");
        put("programmes", "programs");
        put("sceptical", "skeptical");
        put("scepticism", "skepticism");
        put("mould", "mold");
        put("storey", "story");
        put("ageing", "aging");
        put("whilst", "while");
        put("amongst", "among");
        put("speciality", "specialty");
        put("specialities", "specialties");
        put("aluminium", "aluminum");
    }

    private static void put(String british, String american) {
        PAIRS.put(british, american);
    }

    private static final Pattern WORD;

    static {
        List<String> keys = new ArrayList<>(PAIRS.keySet());
        keys.sort((a, b) -> b.length() - a.length());     // "colours" before "colour"
        WORD = Pattern.compile("\\b(" + String.join("|", keys) + ")\\b",
                Pattern.CASE_INSENSITIVE);
    }

    @Test
    void documentationIsWrittenInAmericanEnglish() throws IOException {
        Path root = repositoryRoot();
        List<String> findings = new ArrayList<>();

        try (Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                 .filter(DocumentationSpellingTest::isDocumentation)
                 .filter(p -> !isIgnoredLocation(root, p))
                 .sorted()
                 .forEach(p -> findings.addAll(britishSpellingsIn(root, p)));
        }

        assertTrue(findings.isEmpty(),
                "The documentation is written in American English (docs/contribute/docs-style-guide.md). "
                        + "Replace each word on the left with the one on the right. Code samples, "
                        + "command lines and anything inside backticks are exempt and are not listed here."
                        + System.lineSeparator()
                        + String.join(System.lineSeparator(), findings));
    }

    private static boolean isDocumentation(Path p) {
        String name = p.getFileName().toString();
        return name.endsWith(".md") || name.equals("llms.txt") || name.equals("context7.json");
    }

    private static boolean isIgnoredLocation(Path root, Path p) {
        String rel = root.relativize(p).toString().replace('\\', '/');
        return rel.contains("/target/") || rel.startsWith("target/")
                || rel.contains("/node_modules/") || rel.contains("/.venv/")
                || rel.contains("/site/") || rel.contains("tools/ui-proof/lib/")
                || rel.contains("tools/ui-proof/shots/");
    }

    private static List<String> britishSpellingsIn(Path root, Path file) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        boolean markdown = file.getFileName().toString().endsWith(".md");
        List<String> findings = new ArrayList<>();
        boolean inFence = false;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (markdown && line.trim().startsWith("```")) {
                inFence = !inFence;
                continue;
            }
            if (inFence) {
                continue;
            }
            for (String prose : outsideBackticks(line)) {
                Matcher m = WORD.matcher(prose);
                while (m.find()) {
                    String found = m.group();
                    if ("Flavour".equals(found)) {
                        continue;                 // TeaVM Flavour, FlavourWrapper: a real name
                    }
                    findings.add("  " + root.relativize(file).toString().replace('\\', '/')
                            + ":" + (i + 1) + "  " + found + "  ->  "
                            + matchCase(found, PAIRS.get(found.toLowerCase(Locale.ROOT))));
                }
            }
        }
        return findings;
    }

    /** The pieces of a line that are not inside a `code span`. */
    private static List<String> outsideBackticks(String line) {
        List<String> prose = new ArrayList<>();
        String[] parts = line.split("`", -1);
        for (int i = 0; i < parts.length; i += 2) {
            prose.add(parts[i]);
        }
        return prose;
    }

    private static String matchCase(String found, String american) {
        if (found.equals(found.toUpperCase(Locale.ROOT))) {
            return american.toUpperCase(Locale.ROOT);
        }
        if (Character.isUpperCase(found.charAt(0))) {
            return Character.toUpperCase(american.charAt(0)) + american.substring(1);
        }
        return american;
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
