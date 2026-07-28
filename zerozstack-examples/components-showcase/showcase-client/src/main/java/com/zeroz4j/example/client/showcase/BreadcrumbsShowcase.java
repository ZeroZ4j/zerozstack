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

public class BreadcrumbsShowcase extends ComponentShowcase {

    public BreadcrumbsShowcase() {
        addTitle("Breadcrumbs");
        addDescription("Breadcrumbs help users understand their current location in the application hierarchy.");

        Breadcrumbs breadcrumbs = new Breadcrumbs();
        breadcrumbs.addClassName("text-sm");
        
        Component ul = new Component("ul") {};
        
        Component li1 = new Component("li") {};
        Link a1 = new Link();
        a1.setText("Home");
        a1.addClassName("link-hover");
        li1.getElement().appendChild(a1.getElement());

        Component li2 = new Component("li") {};
        Link a2 = new Link();
        a2.setText("Settings");
        a2.addClassName("link-hover");
        li2.getElement().appendChild(a2.getElement());

        Component li3 = new Component("li") {};
        li3.getElement().setTextContent("Profile");

        ul.getElement().appendChild(li1.getElement());
        ul.getElement().appendChild(li2.getElement());
        ul.getElement().appendChild(li3.getElement());

        breadcrumbs.getElement().appendChild(ul.getElement());

        addSection("Breadcrumbs Example", breadcrumbs);
    }
}
