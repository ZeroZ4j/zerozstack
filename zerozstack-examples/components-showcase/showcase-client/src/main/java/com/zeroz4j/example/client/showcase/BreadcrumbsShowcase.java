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

import com.zeroz4j.ui.component.Breadcrumbs;
import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.component.Link;
import com.zeroz4j.ui.layout.Div;

/**
 * A trail of where you are. Every step except the last is a link with a real destination, and the
 * last one is not a link at all, because you are already there.
 */
public class BreadcrumbsShowcase extends ComponentShowcase {

    private static final String HERE = "#breadcrumbs-showcase";

    public BreadcrumbsShowcase() {
        super();
        setId("breadcrumbs-showcase");
        addTitle("Breadcrumbs");
        addDescription("The line at the top of a page saying where you are and how to get back. "
                + "Each step is a link except the last one, which is the page you are on.");

        addWhatToCheck("Try this",
                "Tab along the trail. Every step but the last takes its turn.",
                "The last step is not a link, on purpose. Tab should skip it.",
                "Listen to what a screen reader calls the trail. It should have a name of its own.",
                "The deep trail is nine steps and the long one has German and Japanese folder "
                        + "names. Make the window narrow and check the trail scrolls sideways "
                        + "inside its own box rather than pushing the page out.",
                "Broken looks like: a step you cannot Tab to that still looks like a link, or the "
                        + "whole page sliding sideways.");

        addSection("Three steps", trail("Where you are",
                new String[] { "Home", "Settings" }, "Profile"));

        addSection("Nine steps", trail("Where you are, nine levels deep",
                new String[] { "Home", "Customers", "Northern region", "Berlin", "Retail",
                        "Store 4711", "Staff", "Full time" }, "Anna Bergström"));

        addSection("Long names in other languages", trail("Where you are, in German and Japanese",
                new String[] { "Start", "Rechnungswesen",
                        "Umsatzsteuervoranmeldung", "会計年度 2026", "第 4 四半期" },
                "Buchungsbelege für den Monat Dezember"));

        addSection("One step, because there is nowhere to go back to",
                trail("Where you are", new String[] {}, "Dashboard"));
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Builds one trail. The steps before the last are links; the last is plain words, because a
     * link to the page you are already on is a promise the page cannot keep.
     */
    private static Component trail(String name, String[] steps, String current) {
        Breadcrumbs crumbs = new Breadcrumbs();
        crumbs.addClassName("text-sm");
        crumbs.withAriaLabel(name);

        Component list = element("ul");
        for (String step : steps) {
            Component item = element("li");
            Link link = new Link(step, HERE);
            link.addClassName("link-hover");
            item.getElement().appendChild(link.getOuterElement());
            list.getElement().appendChild(item.getElement());
        }

        Component last = element("li");
        last.getElement().setTextContent(current);
        // The page you are on, said out loud for anybody who cannot see where the trail stops.
        last.getElement().setAttribute("aria-current", "page");
        list.getElement().appendChild(last.getElement());

        crumbs.getElement().appendChild(list.getElement());

        Div host = new Div();
        host.addClassName("w-full min-w-0 overflow-x-auto");
        host.add(crumbs);
        return host;
    }

    private static Component element(String tag) {
        return new Component(tag) {
        };
    }
}
