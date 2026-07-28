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
package com.zeroz4j.ui.component;

import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.layout.Div;

import java.util.List;
import java.util.function.Function;

/**
 * Windowed list: renders only the visible slice of a large item list (fixed row height),
 * so 10k-event transcripts scroll at full speed in TeaVM. Supports follow-tail mode for
 * live streams ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â sticks to the bottom until the user scrolls up.
 *
 * @param <T> item type
 */
public final class VirtualScroller<T> extends Div {

    private final int rowHeight;
    private final Function<T, Component> renderer;
    private final Div spacer = new Div();
    private final Div window = new Div();
    private List<T> items = List.of();
    private boolean followTail;
    private int firstRendered = -1;
    private int lastRendered = -1;

    public VirtualScroller(int rowHeightPx, Function<T, Component> renderer) {
        this.rowHeight = rowHeightPx;
        this.renderer = renderer;
        addClassName("relative overflow-y-auto flex-1 min-h-0");
        spacer.addClassName("relative w-full");
        window.setStyle("position", "absolute");
        window.setStyle("left", "0");
        window.setStyle("right", "0");
        spacer.add(window);
        add(spacer);
        getElement().addEventListener("scroll", e -> {
            // Leaving the bottom cancels follow-tail; returning re-arms it.
            followTail = isAtBottom();
            renderWindow(false);
        });
    }

    public void setItems(List<T> newItems) {
        this.items = newItems == null ? List.of() : newItems;
        spacer.setStyle("height", (items.size() * rowHeight) + "px");
        renderWindow(true);
        if (followTail) {
            scrollToBottom();
        }
    }

    /** Live streams call this once; the scroller stays glued to the tail until the user scrolls. */
    public void followTail() {
        this.followTail = true;
        scrollToBottom();
    }

    public void scrollToBottom() {
        getElement().setScrollTop(Math.max(0, items.size() * rowHeight
            - getElement().getClientHeight()));
        renderWindow(false);
    }

    public void scrollToIndex(int index) {
        getElement().setScrollTop(Math.max(0, index * rowHeight));
        renderWindow(false);
    }

    private boolean isAtBottom() {
        return getElement().getScrollTop() + getElement().getClientHeight()
            >= items.size() * rowHeight - rowHeight;
    }

    private void renderWindow(boolean force) {
        int viewTop = getElement().getScrollTop();
        int viewHeight = Math.max(getElement().getClientHeight(), 200);
        int overscan = 6;
        int first = Math.max(0, viewTop / rowHeight - overscan);
        int last = Math.min(items.size() - 1, (viewTop + viewHeight) / rowHeight + overscan);
        if (!force && first == firstRendered && last == lastRendered) {
            return;
        }
        firstRendered = first;
        lastRendered = last;
        window.removeAll();
        window.setStyle("top", (first * rowHeight) + "px");
        for (int i = first; i <= last && i < items.size(); i++) {
            Component row = renderer.apply(items.get(i));
            row.getElement().getStyle().setProperty("height", rowHeight + "px");
            row.getElement().getStyle().setProperty("overflow", "hidden");
            window.add(row);
        }
    }
}

