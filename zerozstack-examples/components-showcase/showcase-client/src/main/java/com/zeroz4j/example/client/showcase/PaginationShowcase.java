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

public class PaginationShowcase extends ComponentShowcase {

    public PaginationShowcase() {
        super();
        addTitle("Pagination");
        addDescription("Pagination is a group of buttons to navigate through multi-page content, built on top of the Join component.");

        // Showcase 1: Default Pagination
        Pagination p1 = new Pagination();
        Button prev1 = new Button("Ã‚Â«"); prev1.addClassName("join-item");
        Button page1_1 = new Button("1"); page1_1.addClassName("join-item");
        Button page1_2 = new Button("2"); page1_2.addClassName("join-item"); page1_2.addClassName("btn-active");
        Button page1_3 = new Button("3"); page1_3.addClassName("join-item");
        Button next1 = new Button("Ã‚Â»"); next1.addClassName("join-item");
        p1.add(prev1, page1_1, page1_2, page1_3, next1);

        addSection("Standard Pagination", p1);

        // Showcase 2: Small Pagination (btn-sm)
        Pagination p2 = new Pagination();
        Button prev2 = new Button("Ã‚Â«"); prev2.addClassName("join-item"); prev2.addClassName("btn-sm");
        Button page2_1 = new Button("1"); page2_1.addClassName("join-item"); page2_1.addClassName("btn-sm");
        Button page2_2 = new Button("2"); page2_2.addClassName("join-item"); page2_2.addClassName("btn-sm"); page2_2.addClassName("btn-active");
        Button page2_3 = new Button("3"); page2_3.addClassName("join-item"); page2_3.addClassName("btn-sm");
        Button next2 = new Button("Ã‚Â»"); next2.addClassName("join-item"); next2.addClassName("btn-sm");
        p2.add(prev2, page2_1, page2_2, page2_3, next2);

        addSection("Small Size (using btn-sm on items)", p2);
    }
}
