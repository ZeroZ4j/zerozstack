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
import com.zeroz4j.signals.ValueSignal;
import com.zeroz4j.signals.Computed;

public class CheckboxShowcase extends ComponentShowcase {

    public CheckboxShowcase() {
        addTitle("Checkbox");
        addDescription("Checkboxes allow users to select one or more options from a set.");

        // Color variants
        Checkbox primary = new Checkbox();
        primary.setValue(true);
        primary.setThemeColor(ThemeColor.PRIMARY);

        Checkbox secondary = new Checkbox();
        secondary.setValue(true);
        secondary.setThemeColor(ThemeColor.SECONDARY);

        Checkbox accent = new Checkbox();
        accent.setValue(true);
        accent.setThemeColor(ThemeColor.ACCENT);

        Checkbox neutral = new Checkbox();
        neutral.setValue(true);
        neutral.setThemeColor(ThemeColor.NEUTRAL);

        Checkbox info = new Checkbox();
        info.setValue(true);
        info.setThemeColor(ThemeColor.INFO);

        Checkbox success = new Checkbox();
        success.setValue(true);
        success.setThemeColor(ThemeColor.SUCCESS);

        Checkbox warning = new Checkbox();
        warning.setValue(true);
        warning.setThemeColor(ThemeColor.WARNING);

        Checkbox error = new Checkbox();
        error.setValue(true);
        error.setThemeColor(ThemeColor.ERROR);

        addSection("Checkbox Colors", primary, secondary, accent, neutral, info, success, warning, error);

        // Size variants
        Checkbox xs = new Checkbox();
        xs.setValue(true);
        xs.setThemeSize(ThemeSize.XS);

        Checkbox sm = new Checkbox();
        sm.setValue(true);
        sm.setThemeSize(ThemeSize.SM);

        Checkbox md = new Checkbox();
        md.setValue(true);
        md.setThemeSize(ThemeSize.MD);

        Checkbox lg = new Checkbox();
        lg.setValue(true);
        lg.setThemeSize(ThemeSize.LG);

        addSection("Checkbox Sizes", xs, sm, md, lg);

        // Data Binding Demo
        ValueSignal<Boolean> signal = new ValueSignal<>(false);
        Checkbox component = new Checkbox();
        component.bindValue(signal);
        Span output = new Span();
        output.bindText(new Computed<>(() -> "Current value: " + signal.get()));
        addSection("Data Binding Demo", component, output);
    }
}
