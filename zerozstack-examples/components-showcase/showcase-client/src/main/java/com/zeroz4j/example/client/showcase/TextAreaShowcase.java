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

public class TextAreaShowcase extends ComponentShowcase {

    public TextAreaShowcase() {
        addTitle("TextArea");
        addDescription("TextArea allows users to enter multi-line text.");

        // Basic TextArea
        TextArea basicTextArea = new TextArea("Write your bio here...");
        addSection("Basic TextArea", basicTextArea);

        // Colors
        TextArea taPrimary = new TextArea("Primary").setThemeColor(ThemeColor.PRIMARY);
        TextArea taSecondary = new TextArea("Secondary").setThemeColor(ThemeColor.SECONDARY);
        TextArea taAccent = new TextArea("Accent").setThemeColor(ThemeColor.ACCENT);
        TextArea taNeutral = new TextArea("Neutral").setThemeColor(ThemeColor.NEUTRAL);
        TextArea taInfo = new TextArea("Info").setThemeColor(ThemeColor.INFO);
        TextArea taSuccess = new TextArea("Success").setThemeColor(ThemeColor.SUCCESS);
        TextArea taWarning = new TextArea("Warning").setThemeColor(ThemeColor.WARNING);
        TextArea taError = new TextArea("Error").setThemeColor(ThemeColor.ERROR);

        addSection("Colors",
            taPrimary, taSecondary, taAccent, taNeutral,
            taInfo, taSuccess, taWarning, taError
        );

        // Sizes
        TextArea taXs = new TextArea("Extra Small").setThemeSize(ThemeSize.XS);
        TextArea taSm = new TextArea("Small").setThemeSize(ThemeSize.SM);
        TextArea taMd = new TextArea("Medium").setThemeSize(ThemeSize.MD);
        TextArea taLg = new TextArea("Large").setThemeSize(ThemeSize.LG);

        addSection("Sizes",
            taXs, taSm, taMd, taLg
        );

        // Data Binding Demo
        ValueSignal<String> signal = new ValueSignal<>("Hello World");
        TextArea component = new TextArea();
        component.bindValue(signal);
        Span output = new Span();
        output.bindText(new Computed<>(() -> "Current value: " + signal.get()));
        addSection("Data Binding Demo", component, output);
    }
}
