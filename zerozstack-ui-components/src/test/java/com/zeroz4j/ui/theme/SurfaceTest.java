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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The definitions of the surfaces. What they look like is a matter for a screenshot. */
class SurfaceTest {

    @Test
    void everySurfaceHasItsOwnClassAndItsOwnRule() {
        Set<String> seen = new HashSet<>();
        String css = Surface.stylesheet();
        for (Surface surface : Surface.values()) {
            assertTrue(seen.add(surface.getClassNames()), surface + " is defined twice");
            String rule = ".zeroz4j-surface.zeroz4j-surface-" + surface.name().toLowerCase() + "{";
            assertTrue(css.contains(rule), surface + " has no rule in the stylesheet");
        }
    }

    @Test
    void theStylesheetHasNoFixedColorSoItFollowsTheTheme() {
        String css = Surface.stylesheet();
        assertFalse(css.contains("#"), "a hex color does not follow the theme");
        assertTrue(css.contains("light-dark("), "shadows must differ between light and dark");
    }
}
