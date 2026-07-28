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

public class RadioButtonGroupShowcase extends ComponentShowcase {

    public RadioButtonGroupShowcase() {
        super();
        addTitle("RadioButtonGroup");
        addDescription("RadioButtonGroup groups multiple radio button inputs to select a single value.");

        // Showcase 1: Simple Radio Group
        RadioButtonGroup group = new RadioButtonGroup("simple-options");
        group.setItems(Arrays.asList("Option A", "Option B", "Option C"));

        addSection("Default Radio Buttons", group);

        // Data Binding Demo
        ValueSignal<String> signal = new ValueSignal<>("Banana");
        RadioButtonGroup component = new RadioButtonGroup("demo-fruits");
        component.setItems(Arrays.asList("Apple", "Banana", "Orange"));
        component.bindValue(signal);
        Span output = new Span();
        output.bindText(new Computed<>(() -> "Current value: " + signal.get()));
        addSection("Data Binding Demo", component, output);
    }
}
