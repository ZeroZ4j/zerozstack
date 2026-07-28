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
import com.zeroz4j.signals.*;

public class ProgressShowcase extends ComponentShowcase {

    public ProgressShowcase() {
        super();
        addTitle("Progress");
        addDescription("Progress is a thin loading bar showing completion progress of a task.");

        // Section 1: Progress with Values
        Progress pVal1 = new Progress().setThemeColor(ThemeColor.PRIMARY);
        pVal1.getElement().setAttribute("value", "25");
        pVal1.getElement().setAttribute("max", "100");

        Progress pVal2 = new Progress().setThemeColor(ThemeColor.SECONDARY);
        pVal2.getElement().setAttribute("value", "50");
        pVal2.getElement().setAttribute("max", "100");

        Progress pVal3 = new Progress().setThemeColor(ThemeColor.SUCCESS);
        pVal3.getElement().setAttribute("value", "75");
        pVal3.getElement().setAttribute("max", "100");

        addSection("Determinate Progress Bars", pVal1, pVal2, pVal3);

        // Section 2: Colors (Determinate 40%)
        Progress primary = new Progress().setThemeColor(ThemeColor.PRIMARY);
        primary.getElement().setAttribute("value", "40");
        primary.getElement().setAttribute("max", "100");

        Progress secondary = new Progress().setThemeColor(ThemeColor.SECONDARY);
        secondary.getElement().setAttribute("value", "40");
        secondary.getElement().setAttribute("max", "100");

        Progress accent = new Progress().setThemeColor(ThemeColor.ACCENT);
        accent.getElement().setAttribute("value", "40");
        accent.getElement().setAttribute("max", "100");

        Progress neutral = new Progress().setThemeColor(ThemeColor.NEUTRAL);
        neutral.getElement().setAttribute("value", "40");
        neutral.getElement().setAttribute("max", "100");

        Progress info = new Progress().setThemeColor(ThemeColor.INFO);
        info.getElement().setAttribute("value", "40");
        info.getElement().setAttribute("max", "100");

        Progress success = new Progress().setThemeColor(ThemeColor.SUCCESS);
        success.getElement().setAttribute("value", "40");
        success.getElement().setAttribute("max", "100");

        Progress warning = new Progress().setThemeColor(ThemeColor.WARNING);
        warning.getElement().setAttribute("value", "40");
        warning.getElement().setAttribute("max", "100");

        Progress error = new Progress().setThemeColor(ThemeColor.ERROR);
        error.getElement().setAttribute("value", "40");
        error.getElement().setAttribute("max", "100");

        addSection("Color Variants", primary, secondary, accent, neutral, info, success, warning, error);

        // Section 3: Indeterminate
        Progress ind1 = new Progress().setThemeColor(ThemeColor.PRIMARY);
        Progress ind2 = new Progress().setThemeColor(ThemeColor.SECONDARY);

        addSection("Indeterminate (Loading State)", ind1, ind2);
    }
}
