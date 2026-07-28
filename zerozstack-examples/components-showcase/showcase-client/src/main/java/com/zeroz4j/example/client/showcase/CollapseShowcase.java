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

public class CollapseShowcase extends ComponentShowcase {

    public CollapseShowcase() {
        addTitle("Collapse");
        addDescription("Collapse is used to show and hide content on user click or focus trigger.");

        // Checkbox-controlled collapse (toggles state with click)
        Collapse collapse1 = new Collapse();
        collapse1.addClassName("bg-base-200 border border-base-300 rounded-box w-full");

        Component input1 = new Component("input") {};
        input1.getElement().setAttribute("type", "checkbox");

        Div title1 = new Div("Click to toggle content expansion");
        title1.addClassName("collapse-title text-xl font-medium");

        Div content1 = new Div("Hello! This content is expandable and collapsible via the checkbox controller inside.");
        content1.addClassName("collapse-content");

        collapse1.add(input1, title1, content1);

        addSection("Collapse Example", collapse1);
    }
}
