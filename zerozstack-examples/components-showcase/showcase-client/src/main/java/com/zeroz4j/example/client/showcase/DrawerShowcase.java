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

public class DrawerShowcase extends ComponentShowcase {

    /** Counts every close, so the page shows that one close means one notification. */
    private int closes;

    public DrawerShowcase() {
        super();
        addTitle("Drawer");
        addDescription("A drawer is a panel that slides in from the side of the window. It covers "
                + "the page, dims it, and holds the keyboard until it is closed — Escape closes it, "
                + "and so does clicking the dimmed page beside it.");

        Div log = new Div("Nothing closed yet.");
        log.setId("drawer-close-log");
        log.addClassName("text-sm text-base-content/70");

        addSection("Default", defaultDrawer(log));
        addSection("A drawer that must be answered", mustAnswerDrawer(log));
        addSection("A sidebar beside the page", sidebar());
        addSection("Close events", log);
    }

    /**
     * Everything here is the component's: the panel, the dim, the heading, the stacking and the
     * keyboard. Before 0.8.0 an application had to build all of it out of a checkbox, three
     * {@code div}s and a stacking number it picked itself.
     */
    private Component[] defaultDrawer(Div log) {
        Drawer drawer = new Drawer("Navigation");
        drawer.addClassName("h-72 border border-base-300 rounded-box overflow-hidden");

        drawer.add(navItem("Dashboard"), navItem("Settings"));

        Button open = new Button("Open drawer");
        open.addClassName("btn-primary");
        open.addClickListener(e -> drawer.open());

        Div page = new Div();
        page.addClassName("flex flex-col items-center justify-center gap-4 h-full");
        page.add(new Span("Main content area"), open);
        drawer.addToPage(page);

        drawer.addCloseListener(e -> {
            closes++;
            log.getElement().setTextContent("Closed " + closes + " time(s); last close "
                    + (e.isFromClient() ? "came from the user." : "was asked for by the code."));
        });

        return new Component[] { drawer };
    }

    /** Both exits taken away, so the button inside is the only way out. */
    private Component[] mustAnswerDrawer(Div log) {
        Drawer drawer = new Drawer("Unsaved changes");
        drawer.addClassName("h-72 border border-base-300 rounded-box overflow-hidden");
        drawer.setCloseOnEsc(false);
        drawer.setCloseOnOutsideClick(false);

        Button done = new Button("Done");
        done.addClassName("btn-primary");
        done.addClickListener(e -> drawer.close());
        drawer.add(new Span("Escape does nothing here and neither does clicking outside."), done);

        Button open = new Button("Open drawer");
        open.addClickListener(e -> drawer.open());

        Div page = new Div();
        page.addClassName("flex flex-col items-center justify-center gap-4 h-full");
        page.add(new Span("When you take both exits away, leave a button."), open);
        drawer.addToPage(page);

        drawer.addCloseListener(e -> {
            closes++;
            log.getElement().setTextContent("Closed " + closes + " time(s).");
        });

        return new Component[] { drawer };
    }

    /**
     * The panel lives beside the page rather than over it. No dim, the page stays in use, and the
     * keyboard walks freely from one into the other — which is right, because nothing is blocked.
     */
    private Component[] sidebar() {
        Drawer drawer = new Drawer("Sections");
        drawer.addClassName("h-72 border border-base-300 rounded-box overflow-hidden");
        drawer.setModal(false);
        drawer.add(navItem("Overview"), navItem("Reports"));

        Div page = new Div();
        page.addClassName("flex items-center justify-center h-full p-4");
        page.add(new Span("The sidebar is always there and the page beside it is always live."));
        drawer.addToPage(page);

        return new Component[] { drawer };
    }

    private static Button navItem(String label) {
        Button item = new Button(label);
        item.addClassName("btn-ghost justify-start w-full");
        return item;
    }
}
