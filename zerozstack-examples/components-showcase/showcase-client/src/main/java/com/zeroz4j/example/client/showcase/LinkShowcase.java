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
import com.zeroz4j.ui.component.Link;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.theme.ThemeColor;

/**
 * Every link on this page has somewhere to go, which is the whole point of the page: a link with
 * no destination is not a link, and the keyboard cannot reach it at all.
 */
public class LinkShowcase extends ComponentShowcase {

    /** Somewhere real to go that does not leave the gallery. */
    private static final String HERE = "#link-showcase";

    public LinkShowcase() {
        super();
        setId("link-showcase");
        addTitle("Link");
        addDescription("A link is words you can go somewhere from. Give every one a destination "
                + "when you make it — a link without one is invisible to the keyboard.");

        addWhatToCheck("Try this",
                "Press Tab over and over. Every link on this page has to take its turn.",
                "A link you cannot Tab to has no destination. That is the fault, not the styling.",
                "The last section has one link with no destination on purpose, so the difference "
                        + "is visible. Tab past it and see that it is skipped.",
                "Broken looks like: a word that looks like a link, is coloured like a link, and "
                        + "that Tab walks straight past.");

        addSection("The two ways to give a link a destination",
                new Link("Written in the constructor", HERE),
                withHrefAfterwards(),
                setHrefAfterwards());

        Link plain = new Link("Underlined all the time", HERE);
        Link onHover = new Link("Underlined only when pointed at", HERE);
        onHover.addClassName("link-hover");
        addSection("Underlining", plain, onHover);

        addSection("Colours",
                coloured("Primary", ThemeColor.PRIMARY),
                coloured("Secondary", ThemeColor.SECONDARY),
                coloured("Accent", ThemeColor.ACCENT),
                coloured("Neutral", ThemeColor.NEUTRAL),
                coloured("Info", ThemeColor.INFO),
                coloured("Success", ThemeColor.SUCCESS),
                coloured("Warning", ThemeColor.WARNING),
                coloured("Error", ThemeColor.ERROR));

        addSection("Where it goes",
                new Link("Somewhere on this page", HERE),
                new Link("Another page of the gallery", "#dialog"),
                openInANewTab(),
                new Link("An email address", "mailto:nobody@example.com"),
                new Link("A telephone number", "tel:+493012345678"));

        addSection("Longer than the line it is on", longLink());

        addSection("A link with no destination, kept on purpose so the fault can be seen",
                brokenOnPurpose());
    }

    // ------------------------------------------------------------------ pieces

    private static Component withHrefAfterwards() {
        Link link = new Link();
        link.setText("Chained with withHref");
        return link.withHref(HERE);
    }

    private static Component setHrefAfterwards() {
        Link link = new Link();
        link.setText("Destination set by a later call");
        link.setHref(HERE);
        return link;
    }

    private static Link coloured(String text, ThemeColor colour) {
        Link link = new Link(text + " link", HERE);
        link.setThemeColor(colour);
        return link;
    }

    private static Component openInANewTab() {
        // A link that leaves the page says so, because the browser will not.
        Link link = new Link("The project website (opens in a new tab)", "https://www.zeroz4j.com");
        link.getElement().setAttribute("target", "_blank");
        link.getElement().setAttribute("rel", "noopener noreferrer");
        return link;
    }

    private static Component longLink() {
        Div host = new Div();
        host.addClassName("w-full max-w-sm rounded-box border border-base-300 p-3");
        Link link = new Link("Read the whole section about how the persistence layer decides "
                + "which objects to keep in memory and which to write out again", HERE);
        link.addClassName("break-words");
        host.add(link);
        return host;
    }

    private static Component brokenOnPurpose() {
        Div host = new Div();
        host.addClassName("flex flex-wrap items-center gap-4 w-full");

        Link broken = new Link();
        broken.setText("This has no destination — Tab cannot reach it");
        broken.setId("link-with-no-destination");

        Button fix = new Button("Give it a destination", e -> broken.setHref(HERE));
        fix.setId("link-fix");
        fix.addClassName("btn-sm");

        host.add(broken, fix);
        return host;
    }
}
