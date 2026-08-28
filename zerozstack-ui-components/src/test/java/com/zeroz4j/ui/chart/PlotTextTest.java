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
package com.zeroz4j.ui.chart;

import com.zeroz4j.ui.theme.Emphasis;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The words a chart draws inside its own picture come from named roles, not from numbers typed at
 * the call.
 *
 * <p>They used to be numbers: twenty-four labels across this package, written at two sizes and
 * seven different degrees of fade, because the only way to draw text was to say how big and how
 * faded. The roles replaced that, and the last test here is what stops it coming back - it reads
 * the sources and fails if any chart draws text without naming a role.</p>
 */
class PlotTextTest {

    @Test
    void everyRoleIsADifferentSize() {
        Set<Double> sizes = new HashSet<>();
        for (PlotText role : PlotText.values()) {
            assertTrue(role.getFontSize() > 0, role + " has no size at all");
            assertTrue(sizes.add(role.getFontSize()),
                    role + " is the same size as an earlier role, so the two cannot be told apart "
                            + "in a picture and one of them is pointless");
        }
    }

    @Test
    void theSupportingRolesAreQuietAndTheHeadlineIsNot() {
        assertEquals(Emphasis.FULL, PlotText.FIGURE.getNaturalEmphasis(),
                "the reading in the middle of a dial is the whole point of the panel");
        assertEquals(Emphasis.QUIET, PlotText.LABEL.getNaturalEmphasis(),
                "the words around the plot support the data, they are not the data");
        assertEquals(Emphasis.QUIET, PlotText.CAPTION.getNaturalEmphasis(),
                "a number printed beside a mark supports that mark");
        assertEquals(Emphasis.QUIET, PlotText.MESSAGE.getNaturalEmphasis(),
                "an empty panel says so quietly");
    }

    @Test
    void onlyTheHeadlineCarriesAWeightOfItsOwn() {
        assertNotNull(PlotText.FIGURE.getFontWeight(), "a headline reading is drawn bold");
        assertEquals(null, PlotText.LABEL.getFontWeight(), "a label is ordinary weight");
        assertEquals(null, PlotText.CAPTION.getFontWeight(), "a caption is ordinary weight");
        assertEquals(null, PlotText.MESSAGE.getFontWeight(), "a message is ordinary weight");
    }

    @Test
    void theRolesFadeByTheSameAmountAsTheRestOfTheLibrary() {
        for (PlotText role : PlotText.values()) {
            double fade = role.getNaturalEmphasis().getOpacity();
            assertTrue(fade == Emphasis.FULL.getOpacity() || fade == Emphasis.QUIET.getOpacity(),
                    role + " fades by an amount of its own, so a chart's axis labels and the "
                            + "legend underneath them are quiet by different amounts");
        }
    }

    /**
     * Reads every chart in this package and fails if one draws text without naming a role.
     *
     * <p>Line breaks are squeezed out first, so a call wrapped over three lines reads the same as
     * one written on a single line. {@code ChartBase} itself is skipped: it is where the roles are
     * turned into numbers, and it also keeps the old size-and-fade methods so that an application
     * subclassing it still compiles.</p>
     */
    @Test
    void noChartDrawsTextWithoutNamingARole() throws IOException {
        Path charts = repositoryRoot().resolve("zerozstack-ui-components/src/main/java/com/zeroz4j/ui/chart");
        Pattern call = Pattern.compile("(?<![A-Za-z.])(?:monoText|text)\\(");
        List<String> findings = new ArrayList<>();

        try (Stream<Path> files = Files.walk(charts)) {
            for (Path file : files.filter(Files::isRegularFile)
                                  .filter(p -> p.getFileName().toString().endsWith(".java"))
                                  .filter(p -> !p.getFileName().toString().equals("ChartBase.java"))
                                  .toList()) {
                String flat = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                        .replaceAll("\\s+", " ");
                Matcher matcher = call.matcher(flat);
                while (matcher.find()) {
                    if (!flat.startsWith("PlotText.", matcher.end())) {
                        int from = Math.max(0, matcher.start() - 30);
                        findings.add(file.getFileName() + ": ..."
                                + flat.substring(from, Math.min(flat.length(), matcher.end() + 40)));
                    }
                }
            }
        }

        assertTrue(findings.isEmpty(),
                "these draw text with a size and a fade typed at the call instead of naming a "
                        + "role, which is how two sizes became seven degrees of fade:\n  "
                        + String.join("\n  ", findings));
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
