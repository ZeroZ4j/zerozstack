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

public class ButtonShowcase extends ComponentShowcase {

    public ButtonShowcase() {
        addTitle("Button");
        addDescription("Buttons allow users to take actions, submit forms, or navigate with a single click.");

        // Color variants
        Button primary = new Button("Primary").setThemeColor(ThemeColor.PRIMARY);
        Button secondary = new Button("Secondary").setThemeColor(ThemeColor.SECONDARY);
        Button accent = new Button("Accent").setThemeColor(ThemeColor.ACCENT);
        Button neutral = new Button("Neutral").setThemeColor(ThemeColor.NEUTRAL);
        Button info = new Button("Info").setThemeColor(ThemeColor.INFO);
        Button success = new Button("Success").setThemeColor(ThemeColor.SUCCESS);
        Button warning = new Button("Warning").setThemeColor(ThemeColor.WARNING);
        Button error = new Button("Error").setThemeColor(ThemeColor.ERROR);
        Button ghost = new Button("Ghost").setThemeColor(ThemeColor.GHOST);
        Button link = new Button("Link").setThemeColor(ThemeColor.LINK);

        addSection("Button Colors", primary, secondary, accent, neutral, info, success, warning, error, ghost, link);

        // Size variants
        Button xs = new Button("Extra Small").setThemeSize(ThemeSize.XS);
        Button sm = new Button("Small").setThemeSize(ThemeSize.SM);
        Button md = new Button("Medium").setThemeSize(ThemeSize.MD);
        Button lg = new Button("Large").setThemeSize(ThemeSize.LG);

        addSection("Button Sizes", xs, sm, md, lg);

        // Outline variants
        Button outlinePrimary = new Button("Outline Primary").setThemeColor(ThemeColor.PRIMARY).setOutline(true);
        Button outlineSecondary = new Button("Outline Secondary").setThemeColor(ThemeColor.SECONDARY).setOutline(true);
        Button outlineAccent = new Button("Outline Accent").setThemeColor(ThemeColor.ACCENT).setOutline(true);

        addSection("Button Outline", outlinePrimary, outlineSecondary, outlineAccent);
    }
}
