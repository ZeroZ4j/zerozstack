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
import com.zeroz4j.ui.component.Pagination;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.Span;

/**
 * Three pages fit on one line and prove nothing. Two hundred and forty-seven do not, so the
 * control has to leave some out — and that is the case worth looking at.
 */
public class PaginationShowcase extends ComponentShowcase {

    /** How many pages there really are. */
    private static final int PAGES = 247;

    private int currentPage = 1;

    private final Pagination control = new Pagination();
    private final Div readout = new Div();

    public PaginationShowcase() {
        super();
        addTitle("Pagination");
        addDescription("A row of page numbers. With more pages than fit on a line, the middle is "
                + "left out and shown as a gap, which is where this control gets interesting.");

        addWhatToCheck("Try this",
                "Tab along the row. Every number takes its turn; the gaps do not, because there is "
                        + "nothing to press.",
                "Go to page 1. Back and first should be unpressable, not merely grey.",
                "Go to the last page. Next and last should be unpressable in the same way.",
                "Walk to the middle and watch the gaps move.",
                "Check the page you are on is announced as the current page, not only coloured in.",
                "Broken looks like: a gap that Tab stops on, an unpressable button that Tab still "
                        + "stops on, or nothing saying which page you are on beyond the colour.");

        control.setId("pagination-control");
        control.addClassName("flex-wrap");
        readout.setId("pagination-readout");
        readout.addClassName("text-sm text-base-content/70");

        render();

        addSection("Two hundred and forty-seven pages", control);
        addSection("Where you are", readout);
        addSection("Jump straight to an edge", jumpButtons());
        addSection("Three pages, for comparison", smallControl());
    }

    // ------------------------------------------------------------------ the control

    private void render() {
        control.removeAll();
        control.add(step("« First", 1, currentPage > 1));
        control.add(step("‹ Back", currentPage - 1, currentPage > 1));
        int previous = 0;
        for (int page : pagesToShow()) {
            if (previous != 0 && page > previous + 1) {
                control.add(gap());
            }
            control.add(pageButton(page));
            previous = page;
        }
        control.add(step("Next ›", currentPage + 1, currentPage < PAGES));
        control.add(step("Last »", PAGES, currentPage < PAGES));

        readout.setText("Page " + currentPage + " of " + PAGES
                + ". Showing results " + ((currentPage - 1) * 20 + 1)
                + " to " + Math.min(currentPage * 20, PAGES * 20) + ".");
    }

    /** First, last, and a window of two either side of where you are. */
    private int[] pagesToShow() {
        java.util.TreeSet<Integer> pages = new java.util.TreeSet<>();
        pages.add(1);
        pages.add(PAGES);
        for (int page = currentPage - 2; page <= currentPage + 2; page++) {
            if (page >= 1 && page <= PAGES) {
                pages.add(page);
            }
        }
        int[] result = new int[pages.size()];
        int i = 0;
        for (int page : pages) {
            result[i++] = page;
        }
        return result;
    }

    private Button pageButton(int page) {
        Button button = new Button(String.valueOf(page));
        button.addClassName("join-item btn-sm");
        button.setId("pagination-page-" + page);
        if (page == currentPage) {
            button.addClassName("btn-active");
            // The colour is not enough on its own; this is the part a screen reader reads.
            button.getElement().setAttribute("aria-current", "page");
            button.withAriaLabel("Page " + page + ", the page you are on");
        } else {
            button.withAriaLabel("Go to page " + page);
        }
        button.addClickListener(e -> goTo(page));
        return button;
    }

    private Button step(String label, int target, boolean enabled) {
        Button button = new Button(label);
        button.addClassName("join-item btn-sm");
        button.setEnabled(enabled);
        button.addClickListener(e -> goTo(target));
        return button;
    }

    /** The gap where pages were left out. It is not a control, so nothing can press it. */
    private static Component gap() {
        Span dots = new Span("…");
        dots.addClassName("join-item btn btn-sm btn-disabled pointer-events-none");
        dots.getElement().setAttribute("aria-hidden", "true");
        return dots;
    }

    private void goTo(int page) {
        currentPage = Math.max(1, Math.min(PAGES, page));
        render();
    }

    // ------------------------------------------------------------------ the rest

    private Component jumpButtons() {
        Div host = new Div();
        host.addClassName("flex flex-wrap gap-2 w-full");
        host.add(jump("Page 1", 1), jump("Page 2", 2), jump("Page 124", 124),
                jump("Page 246", 246), jump("Page 247", PAGES));
        return host;
    }

    private Button jump(String label, int page) {
        Button button = new Button(label, e -> goTo(page));
        button.addClassName("btn-sm btn-outline");
        return button;
    }

    private static Component smallControl() {
        Pagination small = new Pagination();
        small.setId("pagination-small");
        for (int page = 1; page <= 3; page++) {
            Button button = new Button(String.valueOf(page));
            button.addClassName("join-item btn-sm");
            if (page == 2) {
                button.addClassName("btn-active");
                button.getElement().setAttribute("aria-current", "page");
            }
            small.add(button);
        }
        return small;
    }
}
