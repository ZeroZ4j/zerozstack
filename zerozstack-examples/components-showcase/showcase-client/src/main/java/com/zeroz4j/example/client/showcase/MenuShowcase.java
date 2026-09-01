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

import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.component.Menu;
import com.zeroz4j.ui.layout.Div;

/**
 * The component the gallery's own sidebar is built from, which until now had no page of its own.
 * Since 0.8.0 a menu entry is a real button, so the keyboard reaches every one of them.
 */
public class MenuShowcase extends ComponentShowcase {

    /** Where the last press landed, so a reader can see that pressing an entry did something. */
    private final Div readout = new Div("Nothing pressed yet.");

    public MenuShowcase() {
        super();
        addTitle("Menu");
        addDescription("A list of things to go to or do. It can nest, and the nested parts can be "
                + "folded away. The sidebar of this gallery is one of these.");

        addWhatToCheck("Try this",
                "Tab down the whole menu. Every entry takes its turn, in the order it is written.",
                "Press Enter and Space on an entry. The line at the bottom should change.",
                "Open and close a folding section with the keyboard alone.",
                "In the folding menu, opening one section should close the other.",
                "The entries with long German and Japanese names must wrap inside the menu, not "
                        + "push it wider.",
                "Broken looks like: an entry Tab walks past, a folding section that only the mouse "
                        + "can open, or a name running out over the edge.");

        readout.setId("menu-readout");
        readout.addClassName("text-sm font-mono");

        addSection("A flat menu", flat());
        addSection("Nested, with folding sections", nested());
        addSection("Entries that are links rather than buttons", withLinks());
        addSection("Sizes", sizes());
        addSection("What was pressed", readout);
    }

    // ------------------------------------------------------------------ sections

    private Component flat() {
        Menu menu = new Menu();
        menu.setId("menu-flat");
        menu.addClassName("bg-base-200 rounded-box w-64");
        menu.addTitle("Workspace");
        menu.addItem("Dashboard", e -> pressed("Dashboard"));
        menu.addItem("Customers", e -> pressed("Customers"));
        menu.addItem("Invoices", e -> pressed("Invoices"));
        menu.addItem("Rechnungsprüfung und Freigabe", e -> pressed("Rechnungsprüfung"));
        menu.addItem("サポート案件の一覧", e -> pressed("Support cases"));
        return menu;
    }

    private Component nested() {
        Menu root = new Menu();
        root.setId("menu-nested");
        root.addClassName("bg-base-200 rounded-box w-72");
        root.setAccordion(true);
        root.addTitle("Everything");

        Menu reports = new Menu();
        reports.addClassName("p-0 flex-col");
        reports.addItem("Sales by month", e -> pressed("Sales by month"));
        reports.addItem("Sales by region", e -> pressed("Sales by region"));
        reports.addItem("Umsatzsteuervoranmeldung", e -> pressed("VAT return"));

        Menu people = new Menu();
        people.addClassName("p-0 flex-col");
        people.addItem("Everyone", e -> pressed("Everyone"));
        people.addItem("Only administrators", e -> pressed("Administrators"));

        Menu deeper = new Menu();
        deeper.addClassName("p-0 flex-col");
        deeper.addItem("Left this year", e -> pressed("Left this year"));
        deeper.addItem("Left before this year", e -> pressed("Left earlier"));
        people.addSubMenu("People who have left", deeper);

        root.addSubMenu("Reports", reports);
        root.addSubMenu("People", people);
        root.addItem("Settings", e -> pressed("Settings"));
        return root;
    }

    private static Component withLinks() {
        Menu menu = new Menu();
        menu.setId("menu-links");
        menu.addClassName("bg-base-200 rounded-box w-64");
        menu.addTitle("Documentation");
        // Every one of these has a destination; without one the browser will not focus it.
        menu.addLink("What the components do", "#menu-links");
        menu.addLink("How state moves", "#menu-links");
        menu.addLink("The project website", "https://www.zeroz4j.com");
        return menu;
    }

    private Component sizes() {
        Div host = new Div();
        host.addClassName("flex flex-wrap items-start gap-4 w-full");
        for (String size : new String[] { "menu-xs", "menu-sm", "menu-md", "menu-lg" }) {
            Menu menu = new Menu();
            menu.addClassName("bg-base-200 rounded-box w-44 " + size);
            menu.addTitle(size.substring("menu-".length()).toUpperCase());
            menu.addItem("One", e -> pressed(size + " one"));
            menu.addItem("Two", e -> pressed(size + " two"));
            host.add(menu);
        }
        return host;
    }

    private void pressed(String what) {
        readout.setText("Last pressed: " + what);
    }
}
