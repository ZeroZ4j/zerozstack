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

public class NavbarShowcase extends ComponentShowcase {

    public NavbarShowcase() {
        super();
        addTitle("Navbar");
        addDescription("Navbar is a navigation header bar containing logos, titles, menus, search inputs, and CTA buttons.");

        // Showcase: Standard Navbar
        Navbar navbar = new Navbar();
        navbar.addClassName("bg-neutral");
        navbar.addClassName("text-neutral-content");
        navbar.addClassName("rounded-box");
        navbar.addClassName("px-4");

        // Start
        Div start = new Div();
        start.addClassName("navbar-start");
        
        Button logo = new Button("zeroz4j");
        logo.addClassName("btn-ghost");
        logo.addClassName("text-xl");
        start.add(logo);

        // Center
        Div center = new Div();
        center.addClassName("navbar-center");
        center.addClassName("flex");
        center.addClassName("gap-2");
        
        Button linkHome = new Button("Home");
        linkHome.addClassName("btn-ghost");
        linkHome.addClassName("btn-sm");
        
        Button linkDocs = new Button("Docs");
        linkDocs.addClassName("btn-ghost");
        linkDocs.addClassName("btn-sm");
        
        center.add(linkHome, linkDocs);

        // End
        Div end = new Div();
        end.addClassName("navbar-end");
        
        Button cta = new Button("Sign In");
        cta.setThemeColor(ThemeColor.PRIMARY);
        cta.addClassName("btn-sm");
        end.add(cta);

        // Assemble
        navbar.add(start, center, end);

        addSection("Navbar Layout", navbar);
    }
}
