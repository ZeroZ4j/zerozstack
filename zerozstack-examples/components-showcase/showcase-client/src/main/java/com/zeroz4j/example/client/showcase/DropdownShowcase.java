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

import com.zeroz4j.ui.component.Button;
import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.component.Dialog;
import com.zeroz4j.ui.component.Dropdown;
import com.zeroz4j.ui.layout.Div;

/**
 * A menu opened from a button. The interesting cases are not the six directions it can open in —
 * they are: what happens with forty entries, what happens inside a box, and whether the keyboard
 * can open one and get out again.
 */
public class DropdownShowcase extends ComponentShowcase {

    /** Where the last choice landed, so pressing an entry visibly does something. */
    private final Div readout = new Div("Nothing chosen yet.");

    public DropdownShowcase() {
        super();
        addTitle("Dropdown");
        addDescription("A short menu that drops out of a button. It closes when you press Escape "
                + "or press something outside it.");

        addWhatToCheck("Try this",
                "Open one with the keyboard alone. Then close it again with Escape, and check the "
                        + "keyboard comes back to the button you opened it from.",
                "Tab through an open menu. Every entry has to take its turn.",
                "Open the one with forty entries. It should scroll inside itself, not run off the "
                        + "bottom of the window.",
                "Open the one at the very bottom of the page. It should flip upwards rather than "
                        + "opening off the screen.",
                "Open the one inside the box. The menu must appear over the box, not behind it.",
                "Broken looks like: a menu you can only close with the mouse, one that opens off "
                        + "the screen, or one that appears behind the thing it was opened from.");

        readout.setId("dropdown-readout");
        readout.addClassName("text-sm font-mono");

        addSection("Opened by pressing", byPressing());
        addSection("Opened by pointing", byPointing());
        addSection("Forty entries", manyEntries());
        addSection("Which way it opens", directions());
        addSection("Inside a box", insideADialog());
        addSection("Long entry names", longNames());
        addSection("Near the bottom of the page, where there is no room below", nearTheBottom());
        addSection("What was chosen", readout);
    }

    // ------------------------------------------------------------------ sections

    private Component byPressing() {
        Dropdown dropdown = new Dropdown("Actions");
        dropdown.setId("dropdown-default");
        fill(dropdown, "Rename", "Duplicate", "Move to another folder", "Delete");
        return dropdown;
    }

    private Component byPointing() {
        Dropdown dropdown = new Dropdown("Point at me");
        dropdown.setId("dropdown-hover");
        dropdown.addClassName("dropdown-hover");
        fill(dropdown, "Open", "Open in a new tab", "Copy the address");
        return dropdown;
    }

    private Component manyEntries() {
        Dropdown dropdown = new Dropdown("Assign to somebody");
        dropdown.setId("dropdown-many");
        Div scroller = new Div();
        scroller.addClassName("max-h-64 overflow-y-auto flex flex-col");
        for (int i = 1; i <= 40; i++) {
            scroller.add(entry("Person number " + i));
        }
        dropdown.add(scroller);
        return dropdown;
    }

    private Component directions() {
        Div host = new Div();
        host.addClassName("flex flex-wrap gap-4 w-full py-8");
        host.add(directional("Aligned to the end", "dropdown-end"),
                directional("Opens upwards", "dropdown-top"),
                directional("Opens leftwards", "dropdown-left"),
                directional("Opens rightwards", "dropdown-right"));
        return host;
    }

    private Component directional(String label, String className) {
        Dropdown dropdown = new Dropdown(label);
        dropdown.addClassName(className);
        fill(dropdown, "One", "Two", "Three");
        return dropdown;
    }

    private Component insideADialog() {
        Dialog dialog = new Dialog("A menu inside a box");
        dialog.setId("dropdown-dialog");
        Dropdown inside = new Dropdown("Choose a folder");
        inside.setId("dropdown-in-dialog");
        fill(inside, "Inbox", "Archive", "Rechnungen 2026", "サポート");
        dialog.add(new Div("The menu below is opened from inside a box. It has to appear over the "
                + "box, not behind it."), inside);
        Button close = new Button("Close", e -> dialog.close());
        close.addClassName("btn-primary");
        dialog.addAction(close);

        Button open = new Button("Open the box");
        open.setId("dropdown-open-dialog");
        open.addClickListener(e -> dialog.open());

        Div host = new Div();
        host.addClassName("flex gap-3 w-full");
        host.add(open, dialog);
        return host;
    }

    private Component longNames() {
        Dropdown dropdown = new Dropdown("Retention rules");
        dropdown.setId("dropdown-long");
        fill(dropdown,
                "Keep everything for seven years, including deleted files",
                "Rechnungsprüfung und automatische Freigabe nach vier Wochen",
                "すべての添付ファイルを保持する",
                "Keep nothing");
        return dropdown;
    }

    private Component nearTheBottom() {
        Div spacer = new Div("There is deliberately a lot of space above this one, so that "
                + "opening it has to decide between opening downwards off the screen and flipping "
                + "upwards.");
        spacer.addClassName("text-sm text-base-content/60 mb-40");

        Dropdown dropdown = new Dropdown("Open me at the bottom");
        dropdown.setId("dropdown-bottom");
        fill(dropdown, "One", "Two", "Three", "Four", "Five", "Six");

        Div host = new Div();
        host.addClassName("w-full");
        host.add(spacer, dropdown);
        return host;
    }

    // ------------------------------------------------------------------ helpers

    private void fill(Dropdown dropdown, String... labels) {
        for (String label : labels) {
            dropdown.add(entry(label));
        }
    }

    /** An entry is a real button, so Tab reaches it and Enter and Space both work. */
    private Button entry(String label) {
        Button item = new Button(label);
        item.addClassName("btn-ghost btn-sm justify-start w-full font-normal");
        item.addClickListener(e -> readout.setText("Last chosen: " + label));
        return item;
    }
}
