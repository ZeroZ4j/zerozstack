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

public class DropdownShowcase extends ComponentShowcase {

    public DropdownShowcase() {
        super();
        addTitle("Dropdown");
        addDescription("Dropdown is a menu that toggles when clicking/hovering a summary button.");

        // Helper to populate items
        Dropdown defaultDropdown = new Dropdown("Click Me");
        addDropdownItems(defaultDropdown);

        Dropdown hoverDropdown = new Dropdown("Hover Me");
        hoverDropdown.addClassName("dropdown-hover");
        addDropdownItems(hoverDropdown);

        Dropdown endDropdown = new Dropdown("Align End");
        endDropdown.addClassName("dropdown-end");
        addDropdownItems(endDropdown);

        Dropdown topDropdown = new Dropdown("Open Top");
        topDropdown.addClassName("dropdown-top");
        addDropdownItems(topDropdown);

        Dropdown leftDropdown = new Dropdown("Open Left");
        leftDropdown.addClassName("dropdown-left");
        addDropdownItems(leftDropdown);

        Dropdown rightDropdown = new Dropdown("Open Right");
        rightDropdown.addClassName("dropdown-right");
        addDropdownItems(rightDropdown);

        addSection("Default (Click to open)", defaultDropdown);
        addSection("Hover to trigger", hoverDropdown);
        addSection("Right Aligned (End)", endDropdown);
        addSection("Directional Dropdowns", topDropdown, leftDropdown, rightDropdown);
    }

    private void addDropdownItems(Dropdown dropdown) {
        Div item1 = new Div("Item 1");
        item1.addClassName("p-2");
        item1.addClassName("hover:bg-base-200");
        item1.addClassName("rounded");
        item1.addClassName("cursor-pointer");

        Div item2 = new Div("Item 2");
        item2.addClassName("p-2");
        item2.addClassName("hover:bg-base-200");
        item2.addClassName("rounded");
        item2.addClassName("cursor-pointer");

        dropdown.add(item1, item2);
    }
}
