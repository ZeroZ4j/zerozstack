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

public class FileInputShowcase extends ComponentShowcase {

    public FileInputShowcase() {
        super();
        addTitle("FileInput");
        addDescription("FileInput is an input field to select files from local storage.");

        // Section 1: Colors
        FileInput primary = new FileInput().setThemeColor(ThemeColor.PRIMARY);
        FileInput secondary = new FileInput().setThemeColor(ThemeColor.SECONDARY);
        FileInput accent = new FileInput().setThemeColor(ThemeColor.ACCENT);
        FileInput neutral = new FileInput().setThemeColor(ThemeColor.NEUTRAL);
        FileInput info = new FileInput().setThemeColor(ThemeColor.INFO);
        FileInput success = new FileInput().setThemeColor(ThemeColor.SUCCESS);
        FileInput warning = new FileInput().setThemeColor(ThemeColor.WARNING);
        FileInput error = new FileInput().setThemeColor(ThemeColor.ERROR);

        addSection("Color Variants", primary, secondary, accent, neutral, info, success, warning, error);

        // Section 2: Sizes
        FileInput xs = new FileInput().setThemeSize(ThemeSize.XS);
        FileInput sm = new FileInput().setThemeSize(ThemeSize.SM);
        FileInput md = new FileInput().setThemeSize(ThemeSize.MD);
        FileInput lg = new FileInput().setThemeSize(ThemeSize.LG);

        addSection("Size Variants", xs, sm, md, lg);

        // Section 3: Extra Properties
        FileInput bordered = new FileInput();
        bordered.addClassName("file-input-bordered");
        
        FileInput ghost = new FileInput();
        ghost.addClassName("file-input-ghost");

        addSection("Styles (Bordered & Ghost)", bordered, ghost);

        // Data Binding Demo
        ValueSignal<String> signal = new ValueSignal<>("");
        FileInput component = new FileInput();
        component.bindValue(signal);
        Span output = new Span();
        output.bindText(new Computed<>(() -> "Current value: " + signal.get()));
        addSection("Data Binding Demo", component, output);
    }
}
