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
import com.zeroz4j.ui.component.Footer;
import com.zeroz4j.ui.component.Link;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.Span;

/**
 * A footer is mostly links, so this page is mostly about links having somewhere to go. Every one
 * here does; a link with no destination cannot be reached with the keyboard at all.
 */
public class FooterShowcase extends ComponentShowcase {

    private static final String HERE = "#footer-showcase";

    public FooterShowcase() {
        super();
        setId("footer-showcase");
        addTitle("Footer");
        addDescription("The strip at the bottom of a page: columns of links, a legal line, and "
                + "sometimes a language chooser. Long names in several languages are in here on "
                + "purpose.");

        addWhatToCheck("Try this",
                "Tab through the whole footer. Every link takes its turn, in reading order.",
                "Make the window narrow. The columns stack; nothing runs off the side.",
                "The German and Japanese entries are longer than the column they sit in. They may "
                        + "wrap, but they must stay inside their column.",
                "Broken looks like: a link Tab walks past, or a column pushing the page sideways.");

        addSection("Three columns of links", threeColumns());
        addSection("A footer with a legal line under it", withLegalLine());
        addSection("Long names in several languages", longNames());
    }

    // ------------------------------------------------------------------ sections

    private static Component threeColumns() {
        Footer footer = new Footer();
        footer.setId("footer-columns");
        footer.addClassName("p-10 bg-neutral text-neutral-content rounded-box w-full "
                + "grid grid-cols-1 sm:grid-cols-3 gap-8");
        footer.add(
                column("Services", link("Branding"), link("Design"), link("Marketing"),
                        link("Advertisement")),
                column("Company", link("About us"), link("Contact"), link("Jobs"),
                        link("Press kit")),
                column("Legal", link("Terms of use"), link("Privacy policy"),
                        link("Cookie policy"), link("Imprint")));
        return footer;
    }

    private static Component withLegalLine() {
        Footer footer = new Footer();
        footer.setId("footer-legal");
        footer.addClassName("p-6 bg-base-200 rounded-box w-full flex flex-col gap-4");

        Div row = new Div();
        row.addClassName("flex flex-wrap gap-6");
        row.add(column("Product", link("What it does"), link("Prices"), link("What changed")),
                column("Help", link("Handbook"), link("Ask a question")));

        Span legal = new Span("Copyright 2026 ZeroZ. All rights reserved.");
        legal.addClassName("text-sm text-base-content/60");

        footer.add(row, legal);
        return footer;
    }

    private static Component longNames() {
        Footer footer = new Footer();
        footer.setId("footer-long");
        footer.addClassName("p-6 bg-base-200 rounded-box w-full grid grid-cols-1 sm:grid-cols-2 gap-6");
        footer.add(
                column("Deutsch",
                        link("Allgemeine Geschäftsbedingungen"),
                        link("Datenschutzerklärung"),
                        link("Widerrufsbelehrung für Verbraucherinnen und Verbraucher")),
                column("日本語",
                        link("利用規約"),
                        link("個人情報の取り扱いについて"),
                        link("特定商取引法に基づく表記")));
        return footer;
    }

    // ------------------------------------------------------------------ helpers

    private static Component column(String heading, Component... links) {
        Div column = new Div();
        column.addClassName("flex flex-col gap-2 min-w-0");
        Span title = new Span(heading);
        title.addClassName("footer-title opacity-70");
        column.add(title);
        column.add(links);
        return column;
    }

    /** Every link made here has a destination, because a link without one is not focusable. */
    private static Link link(String text) {
        Link link = new Link(text, HERE);
        link.addClassName("link-hover break-words");
        return link;
    }
}
