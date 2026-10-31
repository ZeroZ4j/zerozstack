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
        addDescription("ThemeController is the sun and moon button. The icon shows the theme that is showing now "
                + "and pressing it chooses the other one. ThemeSelector offers Light, Dark and System.");

        ThemeController themeController = new ThemeController();
        addSection("Theme Toggle Control", themeController);

        ThemeSelector selector = new ThemeSelector();
        Span output = new Span();
        output.bindText(new Computed<>(() -> "Choice: " + Theme.choice().get() + ", showing: " + Theme.showing().get()));
        addSection("Light, Dark, System", selector, output);
    }
}
