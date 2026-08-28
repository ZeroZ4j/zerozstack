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
package com.zeroz4j.ui.theme;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The scale exists so that "quiet supporting text" is asked for by name instead of being described
 * again on every screen. These tests guard the properties that make that work: there is exactly
 * one definition of each size, no two sizes are the same size, and there is exactly one quiet -
 * which any size can be asked for, and which the charts fade by too.
 *
 * <p>They cannot run the components - a component needs a browser - so they check the definitions
 * themselves. What each size looks like on a page is a matter for a screenshot.</p>
 */
class TextStyleTest {

    @Test
    void everySizeIsDefinedAndDistinct() {
        Set<String> seen = new HashSet<>();
        for (TextStyle style : TextStyle.values()) {
            String classes = style.getClassNames();
            assertFalse(classes == null || classes.trim().isEmpty(),
                    style + " has no definition at all");
            assertTrue(seen.add(classes),
                    style + " is defined identically to an earlier size, so the two cannot be "
                            + "told apart on a page and one of them is pointless");
        }
    }

    @Test
    void everySizeSaysExactlyOneSize() {
        for (TextStyle style : TextStyle.values()) {
            int sizes = 0;
            for (String token : style.getClassNames().split(" ")) {
                if (token.startsWith("text-") && !token.startsWith("text-base-content")) {
                    sizes++;
                }
            }
            assertEquals(1, sizes,
                    style + " names " + sizes + " text sizes; a size that says two things is the "
                            + "drift this scale exists to stop");
        }
    }

    @Test
    void quietTextFadesTheColourItInheritsRatherThanNamingOne() {
        for (TextStyle style : TextStyle.values()) {
            assertFalse(style.getClassNames().contains("text-base-content"),
                    style + " names a colour. Quiet has to be a fade of whatever colour the text "
                            + "is sitting on, or the same words come out wrong on a tinted notice, "
                            + "a coloured card or a dark background");
        }
    }

    @Test
    void theSupportingSizesAreQuietAndTheReadingSizesAreNot() {
        assertFalse(fadeOf(TextStyle.SECONDARY).isEmpty(), "SECONDARY supports the prose");
        assertFalse(fadeOf(TextStyle.CAPTION).isEmpty(), "CAPTION supports the prose");
        assertEquals("", fadeOf(TextStyle.BODY), "BODY is the full-strength one");
        assertEquals("", fadeOf(TextStyle.PAGE_TITLE), "a page title is never faded");
        assertEquals("", fadeOf(TextStyle.SECTION_TITLE), "a section title is never faded");
    }

    @Test
    void thereIsExactlyOneQuietInTheWholeScale() {
        for (TextStyle style : TextStyle.values()) {
            String fade = fadeOf(style);
            if (!fade.isEmpty()) {
                assertEquals(Emphasis.QUIET.getClassName(), fade,
                        style + " fades by an amount of its own. Two quiets that were meant to look "
                                + "the same are the drift this scale exists to stop");
            }
        }
    }

    @Test
    void anySizeCanBeAskedForLoudOrQuiet() {
        for (TextStyle style : TextStyle.values()) {
            assertEquals("", fadeIn(style.getClassNames(Emphasis.FULL)),
                    style + " is still faded when asked for at full strength, so a measurement or "
                            + "an error line cannot be written at this size");
            assertEquals(Emphasis.QUIET.getClassName(), fadeIn(style.getClassNames(Emphasis.QUIET)),
                    style + " cannot be asked for quietly");
        }
    }

    @Test
    void askingForNothingGivesTheSizeItsUsualStrength() {
        for (TextStyle style : TextStyle.values()) {
            assertEquals(style.getClassNames(style.getNaturalEmphasis()), style.getClassNames(),
                    style + " means something different depending on which way you ask for it");
        }
    }

    @Test
    void theTwoMechanismsAgreeOnHowQuietQuietIs() {
        assertEquals(1.0, Emphasis.FULL.getOpacity(),
                "full strength has to mean nothing is taken off");
        assertEquals("", Emphasis.FULL.getClassName(),
                "full strength has to add no class, or it would fight the size it is put beside");
        assertEquals("opacity-" + Math.round(Emphasis.QUIET.getOpacity() * 100),
                Emphasis.QUIET.getClassName(),
                "the fade a chart draws and the fade a page uses have drifted apart, so an axis "
                        + "label and the legend under it are quiet by different amounts");
    }

    @Test
    void theScaleIsSmallEnoughToRemember() {
        assertTrue(TextStyle.values().length <= 6,
                "a scale nobody can hold in their head gets ignored and typed out again");
    }

    private static String fadeOf(TextStyle style) {
        return fadeIn(style.getClassNames());
    }

    private static String fadeIn(String classNames) {
        for (String token : classNames.split(" ")) {
            if (token.startsWith("opacity-")) {
                return token;
            }
        }
        return "";
    }
}
