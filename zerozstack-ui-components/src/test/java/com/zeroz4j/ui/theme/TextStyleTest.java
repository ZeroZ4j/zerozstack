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
 * again on every screen. These tests guard the two properties that make that work: there is
 * exactly one definition of each size, and no two sizes are the same size.
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
    void thereAreTwoQuietSizesAndTheyAreQuietByDifferentAmounts() {
        String secondary = fadeOf(TextStyle.SECONDARY);
        String caption = fadeOf(TextStyle.CAPTION);
        assertFalse(secondary.isEmpty(), "SECONDARY is meant to be quieter than the prose");
        assertFalse(caption.isEmpty(), "CAPTION is meant to be quieter than the prose");
        assertFalse(secondary.equals(caption),
                "SECONDARY and CAPTION fade by the same amount, so one of them is not needed");
        assertEquals("", fadeOf(TextStyle.BODY), "BODY is the full-strength one");
        assertEquals("", fadeOf(TextStyle.PAGE_TITLE), "a page title is never faded");
        assertEquals("", fadeOf(TextStyle.SECTION_TITLE), "a section title is never faded");
    }

    @Test
    void theScaleIsSmallEnoughToRemember() {
        assertTrue(TextStyle.values().length <= 6,
                "a scale nobody can hold in their head gets ignored and typed out again");
    }

    private static String fadeOf(TextStyle style) {
        for (String token : style.getClassNames().split(" ")) {
            if (token.startsWith("opacity-")) {
                return token;
            }
        }
        return "";
    }
}
