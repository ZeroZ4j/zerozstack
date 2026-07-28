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
package com.zeroz4j.example.client.showcase;

import com.zeroz4j.ui.component.*;
import com.zeroz4j.ui.layout.*;
import com.zeroz4j.ui.theme.*;

public class BadgeShowcase extends ComponentShowcase {

    public BadgeShowcase() {
        addTitle("Badge");
        addDescription("Badges are small status descriptors that highlight brief metadata, categories, or notifications.");

        // Color variants
        Badge primary = new Badge("Primary").setThemeColor(ThemeColor.PRIMARY);
        Badge secondary = new Badge("Secondary").setThemeColor(ThemeColor.SECONDARY);
        Badge accent = new Badge("Accent").setThemeColor(ThemeColor.ACCENT);
        Badge neutral = new Badge("Neutral").setThemeColor(ThemeColor.NEUTRAL);
        Badge info = new Badge("Info").setThemeColor(ThemeColor.INFO);
        Badge success = new Badge("Success").setThemeColor(ThemeColor.SUCCESS);
        Badge warning = new Badge("Warning").setThemeColor(ThemeColor.WARNING);
        Badge error = new Badge("Error").setThemeColor(ThemeColor.ERROR);

        addSection("Badge Colors", primary, secondary, accent, neutral, info, success, warning, error);

        // Size variants
        Badge xs = new Badge("Extra Small").setThemeSize(ThemeSize.XS);
        Badge sm = new Badge("Small").setThemeSize(ThemeSize.SM);
        Badge md = new Badge("Medium").setThemeSize(ThemeSize.MD);
        Badge lg = new Badge("Large").setThemeSize(ThemeSize.LG);

        addSection("Badge Sizes", xs, sm, md, lg);

        // Outline variants
        Badge outlinePrimary = new Badge("Outline Primary").setThemeColor(ThemeColor.PRIMARY).setOutline(true);
        Badge outlineSecondary = new Badge("Outline Secondary").setThemeColor(ThemeColor.SECONDARY).setOutline(true);
        Badge outlineAccent = new Badge("Outline Accent").setThemeColor(ThemeColor.ACCENT).setOutline(true);

        addSection("Badge Outline", outlinePrimary, outlineSecondary, outlineAccent);
    }
}
