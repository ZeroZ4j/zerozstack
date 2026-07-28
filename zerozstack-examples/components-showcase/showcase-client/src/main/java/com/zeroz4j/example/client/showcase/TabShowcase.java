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

public class TabShowcase extends ComponentShowcase {

    public TabShowcase() {
        addTitle("Tab");
        addDescription("Tab component is used to switch between different content views.");

        // Tabs container
        Div tabsContainer = new Div();
        tabsContainer.addClassName("tabs");
        tabsContainer.addClassName("tabs-boxed");

        Tab tab1 = new Tab();
        tab1.setText("Tab 1");
        tab1.addClassName("tab-active");

        Tab tab2 = new Tab();
        tab2.setText("Tab 2");

        Tab tab3 = new Tab();
        tab3.setText("Tab 3");

        tabsContainer.add(tab1, tab2, tab3);

        addSection("Boxed Tabs", tabsContainer);
    }
}
