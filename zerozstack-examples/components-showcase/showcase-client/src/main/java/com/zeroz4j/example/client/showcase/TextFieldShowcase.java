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

public class TextFieldShowcase extends ComponentShowcase {

    public TextFieldShowcase() {
        addTitle("TextField");
        addDescription("TextField is a standard text input component.");

        // Basic TextField
        TextField basicTextField = new TextField("Enter text...");
        addSection("Basic TextField", basicTextField);

        // Colors
        TextField tfPrimary = new TextField("Primary").setThemeColor(ThemeColor.PRIMARY);
        TextField tfSecondary = new TextField("Secondary").setThemeColor(ThemeColor.SECONDARY);
        TextField tfAccent = new TextField("Accent").setThemeColor(ThemeColor.ACCENT);
        TextField tfNeutral = new TextField("Neutral").setThemeColor(ThemeColor.NEUTRAL);
        TextField tfInfo = new TextField("Info").setThemeColor(ThemeColor.INFO);
        TextField tfSuccess = new TextField("Success").setThemeColor(ThemeColor.SUCCESS);
        TextField tfWarning = new TextField("Warning").setThemeColor(ThemeColor.WARNING);
        TextField tfError = new TextField("Error").setThemeColor(ThemeColor.ERROR);

        addSection("Colors",
            tfPrimary, tfSecondary, tfAccent, tfNeutral,
            tfInfo, tfSuccess, tfWarning, tfError
        );

        // Sizes
        TextField tfXs = new TextField("Extra Small").setThemeSize(ThemeSize.XS);
        TextField tfSm = new TextField("Small").setThemeSize(ThemeSize.SM);
        TextField tfMd = new TextField("Medium").setThemeSize(ThemeSize.MD);
        TextField tfLg = new TextField("Large").setThemeSize(ThemeSize.LG);

        addSection("Sizes",
            tfXs, tfSm, tfMd, tfLg
        );

        // Data Binding Demo
        ValueSignal<String> signal = new ValueSignal<>("Hello");
        TextField component = new TextField();
        component.bindValue(signal);
        Span output = new Span();
        output.bindText(new Computed<>(() -> "Current value: " + signal.get()));
        addSection("Data Binding Demo", component, output);
    }
}
