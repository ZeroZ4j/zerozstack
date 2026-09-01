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
import com.zeroz4j.ui.component.VirtualScroller;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.Span;
import java.util.ArrayList;
import java.util.List;
import com.zeroz4j.ui.theme.TextStyle;

public class VirtualScrollerShowcase extends ComponentShowcase {

    public VirtualScrollerShowcase() {
        super();
        addTitle("Virtual Scroller");
        addDescription("A windowed list with a fixed row height: only the visible slice exists in "
            + "the DOM, so a 50,000-row list scrolls at full speed under TeaVM. Follow-tail mode "
            + "sticks to the bottom for live streams until the user scrolls up, and re-arms when "
            + "they come back.");

        addWhatToCheck("What to watch while you scroll",
                "Drag the scrollbar from top to bottom in one go. The rows must keep up; blank "
                        + "space where rows should be means the window is being redrawn too slowly.",
                "Stop halfway and read a row number. Scroll away and come back. The same number "
                        + "should be in the same place.",
                "Press the Jump buttons. The list should land exactly on the row named, not near it.",
                "Tab into the list. The keyboard should be able to reach the rows and get out again.",
                "Scroll with the keyboard alone: Page Down, Home and End.",
                "Broken looks like: white gaps while scrolling, the list snapping back to the top, "
                        + "or the keyboard getting stuck inside it.");

        List<String> rows = new ArrayList<>();
        for (int i = 0; i < 50_000; i++) {
            rows.add("row " + i);
        }

        VirtualScroller<String> scroller = new VirtualScroller<>(26, item -> {
            Div row = new Div();
            row.addClassName("flex items-center gap-3 border-b border-base-300/40 px-3 "
                + "font-mono text-xs hover:bg-base-content/5");
            Span index = new Span(item);
            TextStyle.CAPTION.applyTo(index);
            Span payload = new Span("payload for " + item);
            row.add(index, payload);
            return row;
        });
        scroller.setItems(rows);

        Div host = new Div();
        host.addClassName("flex h-64 w-full rounded-lg border border-base-300 bg-base-200/40");
        host.add(scroller);
        addSection("50,000 rows", host);

        Button top = new Button("Top");
        top.addClassName("btn-sm btn-ghost");
        top.addClickListener(event -> scroller.scrollToIndex(0));
        Button middle = new Button("Row 25,000");
        middle.addClassName("btn-sm btn-ghost");
        middle.addClickListener(event -> scroller.scrollToIndex(25_000));
        Button bottom = new Button("Bottom");
        bottom.addClassName("btn-sm btn-ghost");
        bottom.addClickListener(event -> scroller.scrollToBottom());
        addSection("Jump", top, middle, bottom);
    }
}
