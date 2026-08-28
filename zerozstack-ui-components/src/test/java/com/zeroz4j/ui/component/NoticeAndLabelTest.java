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
package com.zeroz4j.ui.component;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Three things a screenshot cannot hold: which stylesheet class each notice tone asks for, what a
 * status dot says when nobody gave it words, and the fact that a lane name is never cut down to a
 * shorter string before it is drawn.
 *
 * <p>A component cannot be built here - it needs a browser, and {@code Window.current()} on a plain
 * JVM throws - so anything that would need a live element is checked either as the plain arithmetic
 * behind it or, for the one thing that has no arithmetic, by reading the source. Those source
 * checks are the same technique {@code SourceTextEncodingTest} uses, and they exist because the
 * fault they guard against is invisible in a screenshot: a name drawn short looks exactly like a
 * name that was short.</p>
 */
class NoticeAndLabelTest {

    // ---------------------------------------------------------------- the tinted notice

    @Test
    void eachToneAsksForItsOwnStylesheetClass() {
        assertEquals("alert-info", Alert.Tone.INFORMATION.getClassName());
        assertEquals("alert-success", Alert.Tone.SUCCESS.getClassName());
        assertEquals("alert-warning", Alert.Tone.CAUTION.getClassName());
        assertEquals("alert-error", Alert.Tone.DANGER.getClassName());
    }

    @Test
    void noTwoTonesLookAlike() {
        Set<String> seen = new HashSet<>();
        for (Alert.Tone tone : Alert.Tone.values()) {
            assertTrue(seen.add(tone.getClassName()),
                    tone + " is drawn identically to another tone, so a reader cannot tell the "
                            + "two apart");
        }
        assertEquals(4, Alert.Tone.values().length,
                "four tones: worth knowing, it worked, careful, it failed");
    }

    @Test
    void theNoticeIsBuiltFromTonesRatherThanHandTypedClassNames() {
        String source = read("zerozstack-ui-components/src/main/java/com/zeroz4j/ui/component/"
                + "Alert.java");
        assertTrue(source.contains("role"),
                "a notice has to tell a screen reader that it is a notice");
        assertTrue(source.contains("whitespace-normal") && source.contains("break-words"),
                "long text in a notice has to wrap, not run off the side");
    }

    // ---------------------------------------------------------------- the status dot

    @Test
    void aDotGivenOnlyAConstantStillHoversInOrdinaryWords() {
        assertEquals("Dispatched", StatusDot.readableLabel("DISPATCHED"));
        assertEquals("Design review", StatusDot.readableLabel("DESIGN_REVIEW"));
        assertEquals("Final integration", StatusDot.readableLabel("FINAL_INTEGRATION"));
        assertEquals("Stage 2 failed", StatusDot.readableLabel("STAGE_2_FAILED"));
    }

    @Test
    void wordsSomebodyWroteAreLeftExactlyAsTheyWere() {
        assertEquals("Sent to a worker", StatusDot.readableLabel("Sent to a worker"));
        assertEquals("Waiting for a slot", StatusDot.readableLabel("Waiting for a slot"));
        assertEquals("iPhone sync", StatusDot.readableLabel("iPhone sync"));
        assertEquals("", StatusDot.readableLabel(null));
        assertEquals("", StatusDot.readableLabel(""));
    }

    @Test
    void theCallersOwnWordsAreWhatTheDotShowsAndAnnounces() {
        String source = read("zerozstack-ui-components/src/main/java/com/zeroz4j/ui/component/"
                + "StatusDot.java");
        assertTrue(source.contains("setAttribute(\"title\", text)"),
                "the hover text has to be the label the caller gave, unchanged");
        assertTrue(source.contains("setAttribute(\"aria-label\", text)"),
                "and a screen reader has to be told the same words - a dot has no text in it");
    }

    // ---------------------------------------------------------------- the timeline labels

    @Test
    void aWrappedLaneNameTakesTheRightNumberOfLines() {
        // 120 pixels of column at about 6.1 pixels a character is roughly 19 characters.
        assertEquals(1, LaneTimeline.wrappedLineCount("worker-3", 120));
        assertEquals(2, LaneTimeline.wrappedLineCount("worker-4 devstral-small-2508", 120));
        assertEquals(1, LaneTimeline.wrappedLineCount("", 120));
        assertEquals(1, LaneTimeline.wrappedLineCount(null, 120));
        assertTrue(LaneTimeline.wrappedLineCount("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 120) > 1,
                "a single word longer than the column has to break, not disappear");
    }

    @Test
    void noLaneNameIsEverCutIntoAShorterString() {
        String source = read("zerozstack-ui-components/src/main/java/com/zeroz4j/ui/component/"
                + "LaneTimeline.java");
        assertFalse(source.contains("substring"),
                "LaneTimeline is drawing a shortened copy of a lane name again. Cutting the string "
                        + "puts characters nowhere on the page: they cannot be selected, found or "
                        + "read out, and a shortened name looks exactly like a short one. Let the "
                        + "browser do the shortening - put the whole name in the page and let the "
                        + "end of it be clipped, or turn wrapping on.");
        assertTrue(source.contains("setTextContent(name)"),
                "the whole lane name has to reach the page");
    }

    @Test
    void noTimelineEventIsEverCutIntoAShorterString() {
        String source = read("zerozstack-ui-components/src/main/java/com/zeroz4j/ui/component/"
                + "Timeline.java");
        assertFalse(source.contains("substring"),
                "Timeline is shortening an event's words. Long text wraps; it is never cut.");
        assertTrue(source.contains("whitespace-normal") && source.contains("break-words"),
                "an event's words have to wrap inside its box");
    }

    private static String read(String relativePath) {
        try {
            return new String(Files.readAllBytes(repositoryRoot().resolve(relativePath)),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
