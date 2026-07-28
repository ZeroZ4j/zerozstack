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

public class ToggleShowcase extends ComponentShowcase {

    public ToggleShowcase() {
        addTitle("Toggle");
        addDescription("Toggle component is a checkbox styled as a switch button.");

        // Basic Toggle
        Toggle basicToggle = new Toggle();
        basicToggle.setValue(true);
        addSection("Basic Toggle", basicToggle);

        // Colors
        Toggle togglePrimary = new Toggle();
        togglePrimary.setThemeColor(ThemeColor.PRIMARY);
        togglePrimary.setValue(true);

        Toggle toggleSecondary = new Toggle();
        toggleSecondary.setThemeColor(ThemeColor.SECONDARY);
        toggleSecondary.setValue(true);

        Toggle toggleAccent = new Toggle();
        toggleAccent.setThemeColor(ThemeColor.ACCENT);
        toggleAccent.setValue(true);

        Toggle toggleNeutral = new Toggle();
        toggleNeutral.setThemeColor(ThemeColor.NEUTRAL);
        toggleNeutral.setValue(true);

        Toggle toggleInfo = new Toggle();
        toggleInfo.setThemeColor(ThemeColor.INFO);
        toggleInfo.setValue(true);

        Toggle toggleSuccess = new Toggle();
        toggleSuccess.setThemeColor(ThemeColor.SUCCESS);
        toggleSuccess.setValue(true);

        Toggle toggleWarning = new Toggle();
        toggleWarning.setThemeColor(ThemeColor.WARNING);
        toggleWarning.setValue(true);

        Toggle toggleError = new Toggle();
        toggleError.setThemeColor(ThemeColor.ERROR);
        toggleError.setValue(true);

        addSection("Colors",
            togglePrimary, toggleSecondary, toggleAccent, toggleNeutral,
            toggleInfo, toggleSuccess, toggleWarning, toggleError
        );

        // Sizes
        Toggle toggleXs = new Toggle();
        toggleXs.setThemeSize(ThemeSize.XS);
        toggleXs.setValue(true);

        Toggle toggleSm = new Toggle();
        toggleSm.setThemeSize(ThemeSize.SM);
        toggleSm.setValue(true);

        Toggle toggleMd = new Toggle();
        toggleMd.setThemeSize(ThemeSize.MD);
        toggleMd.setValue(true);

        Toggle toggleLg = new Toggle();
        toggleLg.setThemeSize(ThemeSize.LG);
        toggleLg.setValue(true);

        addSection("Sizes",
            toggleXs, toggleSm, toggleMd, toggleLg
        );

        // Data Binding Demo
        ValueSignal<Boolean> signal = new ValueSignal<>(false);
        Toggle component = new Toggle();
        component.bindValue(signal);
        Span output = new Span();
        output.bindText(new Computed<>(() -> "Current value: " + signal.get()));
        addSection("Data Binding Demo", component, output);
    }
}
