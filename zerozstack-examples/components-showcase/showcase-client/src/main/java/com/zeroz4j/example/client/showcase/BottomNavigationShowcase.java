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

public class BottomNavigationShowcase extends ComponentShowcase {

    public BottomNavigationShowcase() {
        addTitle("Bottom Navigation");
        addDescription("Bottom Navigation bar allows navigation between primary screens on mobile devices.");

        BottomNavigation nav = new BottomNavigation();
        nav.addClassName("relative w-full shadow border border-base-300 rounded-box");

        Button home = new Button("Home");
        home.addClassName("active text-primary");
        
        Button search = new Button("Search");
        Button settings = new Button("Settings");

        nav.add(home, search, settings);

        addSection("Bottom Navigation Example", nav);
    }
}
