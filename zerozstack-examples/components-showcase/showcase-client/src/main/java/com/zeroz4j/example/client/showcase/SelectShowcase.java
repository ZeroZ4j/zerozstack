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
import java.util.Arrays;

public class SelectShowcase extends ComponentShowcase {

    public SelectShowcase() {
        addTitle("Select");
        addDescription("Select is used to pick an option from a dropdown list.");

        // Basic Select
        Select basicSelect = new Select();
        basicSelect.setItems(Arrays.asList("Option A", "Option B", "Option C"));
        basicSelect.setValue("Option B");
        addSection("Basic Select", basicSelect);

        // Colors
        Select selectPrimary = new Select().setThemeColor(ThemeColor.PRIMARY);
        selectPrimary.setItems(Arrays.asList("Primary 1", "Primary 2"));
        Select selectSecondary = new Select().setThemeColor(ThemeColor.SECONDARY);
        selectSecondary.setItems(Arrays.asList("Secondary 1", "Secondary 2"));
        Select selectAccent = new Select().setThemeColor(ThemeColor.ACCENT);
        selectAccent.setItems(Arrays.asList("Accent 1", "Accent 2"));
        Select selectNeutral = new Select().setThemeColor(ThemeColor.NEUTRAL);
        selectNeutral.setItems(Arrays.asList("Neutral 1", "Neutral 2"));
        Select selectInfo = new Select().setThemeColor(ThemeColor.INFO);
        selectInfo.setItems(Arrays.asList("Info 1", "Info 2"));
        Select selectSuccess = new Select().setThemeColor(ThemeColor.SUCCESS);
        selectSuccess.setItems(Arrays.asList("Success 1", "Success 2"));
        Select selectWarning = new Select().setThemeColor(ThemeColor.WARNING);
        selectWarning.setItems(Arrays.asList("Warning 1", "Warning 2"));
        Select selectError = new Select().setThemeColor(ThemeColor.ERROR);
        selectError.setItems(Arrays.asList("Error 1", "Error 2"));

        addSection("Colors",
            selectPrimary, selectSecondary, selectAccent, selectNeutral,
            selectInfo, selectSuccess, selectWarning, selectError
        );

        // Sizes
        Select selectXs = new Select().setThemeSize(ThemeSize.XS);
        selectXs.setItems(Arrays.asList("XS 1", "XS 2"));
        Select selectSm = new Select().setThemeSize(ThemeSize.SM);
        selectSm.setItems(Arrays.asList("SM 1", "SM 2"));
        Select selectMd = new Select().setThemeSize(ThemeSize.MD);
        selectMd.setItems(Arrays.asList("MD 1", "MD 2"));
        Select selectLg = new Select().setThemeSize(ThemeSize.LG);
        selectLg.setItems(Arrays.asList("LG 1", "LG 2"));

        addSection("Sizes",
            selectXs, selectSm, selectMd, selectLg
        );

        // Data Binding Demo
        ValueSignal<String> signal = new ValueSignal<>("Option B");
        Select component = new Select();
        component.setItems(Arrays.asList("Option A", "Option B", "Option C"));
        component.bindValue(signal);
        Span output = new Span();
        output.bindText(new Computed<>(() -> "Current value: " + signal.get()));
        addSection("Data Binding Demo", component, output);
    }
}
