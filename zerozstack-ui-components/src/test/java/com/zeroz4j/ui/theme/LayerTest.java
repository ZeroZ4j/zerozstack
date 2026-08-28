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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The promises the layer scale makes, so that nobody quietly reorders it.
 *
 * <p>The order of these tiers is not a preference. Each one is above the one before it because
 * something real breaks otherwise: a menu opened from a sticky header has to clear the header, a
 * drawer opening over an open menu has to cover it, and a message about what just happened in the
 * drawer has to be readable over the drawer.</p>
 */
class LayerTest {

    /** The order the enum is written in is the order things stack. */
    @Test
    void tiersAreOrderedFromTheBottomUp() {
        Layer[] tiers = Layer.values();
        for (int i = 1; i < tiers.length; i++) {
            assertTrue(tiers[i].getZIndex() > tiers[i - 1].getZIndex(),
                    tiers[i] + " must be above " + tiers[i - 1] + ", but its number is not larger. "
                            + "The order the constants are written in is the order they stack.");
        }
    }

    /** Written down so a reorder has to be deliberate rather than a side effect of an edit. */
    @Test
    void theOrderIsTheOneEveryOverlayWasBuiltAgainst() {
        assertEquals("PAGE, STICKY, DROPDOWN, OVERLAY, TOAST, TOOLTIP",
                String.join(", ", java.util.Arrays.stream(Layer.values())
                        .map(Enum::name).toArray(String[]::new)),
                "The scale changed. Every overlay in the library was built expecting this order, "
                        + "and so were the applications on it.");
    }

    /**
     * The stylesheet the library is built on uses numbers up to 999 inside its own components — a
     * modal is 999, a drawer side is 10. Anything on this scale has to clear all of them without
     * anybody having to check.
     */
    @Test
    void everyFloatingTierIsAboveTheStylesheetsOwnNumbers() {
        for (Layer tier : Layer.values()) {
            if (tier == Layer.PAGE) {
                continue;
            }
            assertTrue(tier.getZIndex() > 999,
                    tier + " is " + tier.getZIndex() + ", which is not above the 999 daisyUI puts "
                            + "on its own modal. It would lose to a piece of stylesheet furniture.");
        }
    }

    /** Room for an application to slot a tier of its own in between two of these. */
    @Test
    void tiersLeaveRoomBetweenThem() {
        Layer[] tiers = Layer.values();
        for (int i = 2; i < tiers.length; i++) {
            assertEquals(100, tiers[i].getZIndex() - tiers[i - 1].getZIndex(),
                    "The gap between " + tiers[i - 1] + " and " + tiers[i] + " changed. Ninety-nine "
                            + "free values between tiers is what lets an application add one of "
                            + "its own without touching this scale.");
        }
    }

    /** The marker class is what a person, and a test, can see on the page. */
    @Test
    void everyTierHasItsOwnMarkerClass() {
        Set<String> seen = new HashSet<>();
        Set<Integer> numbers = new HashSet<>();
        for (Layer tier : Layer.values()) {
            assertTrue(tier.getClassName().startsWith("zz-layer-"),
                    tier + " must be marked with a zz-layer- class; it is " + tier.getClassName());
            assertTrue(seen.add(tier.getClassName()), "two tiers share the class " + tier.getClassName());
            assertTrue(numbers.add(tier.getZIndex()), "two tiers share the number " + tier.getZIndex());
        }
    }

    /** The names in the class are the names in the enum, so neither can drift from the other. */
    @Test
    void theMarkerClassNamesTheTier() {
        assertEquals("zz-layer-toast", Layer.TOAST.getClassName());
        assertEquals("zz-layer-dropdown", Layer.DROPDOWN.getClassName());
        assertEquals("zz-layer-overlay", Layer.OVERLAY.getClassName());
        assertEquals("zz-layer-tooltip", Layer.TOOLTIP.getClassName());
        assertEquals("zz-layer-sticky", Layer.STICKY.getClassName());
        assertEquals("zz-layer-page", Layer.PAGE.getClassName());
    }

    /** Page content is at the bottom; asking for it is how you take a layer off again. */
    @Test
    void ordinaryPageContentIsAtZero() {
        assertEquals(0, Layer.PAGE.getZIndex());
    }
}
