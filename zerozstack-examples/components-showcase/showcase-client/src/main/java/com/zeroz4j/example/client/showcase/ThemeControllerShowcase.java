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

public class ThemeControllerShowcase extends ComponentShowcase {

    public ThemeControllerShowcase() {
        addTitle("ThemeController");
        addDescription("ThemeController is a toggle component that changes the active theme between light and dark modes.");

        // Theme Controller Toggle
        ThemeController themeController = new ThemeController();
        
        // Show status based on value
        Span statusSpan = new Span("Current: Light / Dark Toggle");
        
        addSection("Theme Toggle Control", themeController, statusSpan);

        // Data Binding Demo
        ValueSignal<Boolean> signal = new ValueSignal<>(false);
        ThemeController component = new ThemeController();
        component.bindValue(signal);
        Span output = new Span();
        output.bindText(new Computed<>(() -> "Current value: " + signal.get()));
        addSection("Data Binding Demo", component, output);
    }
}
