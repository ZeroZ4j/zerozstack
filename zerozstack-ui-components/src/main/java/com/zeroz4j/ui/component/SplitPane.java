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
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.events.EventListener;
import org.teavm.jso.dom.events.KeyboardEvent;
import org.teavm.jso.dom.events.MouseEvent;

/**
 * Two panels with a draggable divider. The FIRST panel has a pixel size (width for
 * horizontal, height for vertical); the second takes the rest. The size persists to
 * localStorage under the given key so layouts survive restarts.
 *
 * <p>The divider is not only draggable. It sits in the tab order, and the arrow keys that point
 * across it move it a little at a time, Shift and an arrow key move it a lot, and Home and End
 * send it to its two limits. Every one of those saves the new size, exactly as letting go of a
 * drag does, so a layout set from the keyboard survives a restart too.</p>
 */
public final class SplitPane extends Div {

    /** How far one arrow key press moves the divider, in pixels. */
    private static final int STEP_PX = 16;

    /** How far Shift and an arrow key move it, for crossing a wide panel in a few presses. */
    private static final int BIG_STEP_PX = 64;

    private final Div first = new Div();
    private final Div second = new Div();
    private final Div divider = new Div();
    private final boolean horizontal;
    private final String storageKey;
    private final int minPx;
    private final int maxPx;
    private int sizePx;
    private boolean dragging;

    public static SplitPane horizontal(String storageKey, int defaultPx, int minPx, int maxPx) {
        return new SplitPane(true, storageKey, defaultPx, minPx, maxPx);
    }

    public static SplitPane vertical(String storageKey, int defaultPx, int minPx, int maxPx) {
        return new SplitPane(false, storageKey, defaultPx, minPx, maxPx);
    }

    private SplitPane(boolean horizontal, String storageKey, int defaultPx, int minPx, int maxPx) {
        this.horizontal = horizontal;
        this.storageKey = "split." + storageKey;
        this.minPx = minPx;
        this.maxPx = maxPx;
        this.sizePx = restore(defaultPx);

        addClassName(horizontal ? "flex flex-row min-h-0 min-w-0 h-full w-full"
                                : "flex flex-col min-h-0 min-w-0 h-full w-full");
        first.addClassName("shrink-0 min-w-0 min-h-0 overflow-hidden flex flex-col");
        second.addClassName("flex-1 min-w-0 min-h-0 overflow-hidden flex flex-col");

        divider.addClassName(horizontal
            ? "shrink-0 w-1 cursor-col-resize bg-base-300 hover:bg-primary/60 transition-colors"
            : "shrink-0 h-1 cursor-row-resize bg-base-300 hover:bg-primary/60 transition-colors");

        // A bar between two areas that a person moves is a separator. Side-by-side panels are
        // parted by an upright bar, stacked panels by a flat one, which is what a listener is
        // told by the orientation - and it is also which pair of arrow keys makes sense.
        divider.getElement().setAttribute("role", "separator");
        divider.getElement().setAttribute("aria-orientation", horizontal ? "vertical" : "horizontal");
        divider.getElement().setAttribute("tabindex", "0");
        divider.getElement().setAttribute("aria-valuemin", String.valueOf(minPx));
        divider.getElement().setAttribute("aria-valuemax", String.valueOf(maxPx));
        setAriaLabel("Move the divider between the two panels");

        divider.getElement().addEventListener("mousedown", (EventListener<MouseEvent>) e -> {
            e.preventDefault();
            dragging = true;
        });

        divider.getElement().addEventListener("keydown", (EventListener<KeyboardEvent>) e -> {
            String key = Js.eventKey(e);
            int step = e.isShiftKey() ? BIG_STEP_PX : STEP_PX;
            int wanted;
            if (horizontal ? "ArrowLeft".equals(key) : "ArrowUp".equals(key)) {
                wanted = sizePx - step;
            } else if (horizontal ? "ArrowRight".equals(key) : "ArrowDown".equals(key)) {
                wanted = sizePx + step;
            } else if ("Home".equals(key)) {
                wanted = minPx;
            } else if ("End".equals(key)) {
                wanted = maxPx;
            } else {
                return;
            }
            // Stop the page scrolling under the divider, then move and save it the same way a
            // finished drag does.
            e.preventDefault();
            setSize(wanted);
            Js.localSet(this.storageKey, String.valueOf(sizePx));
        });
        // Track on the document so fast drags don't escape the divider.
        Window.current().getDocument().addEventListener("mousemove", (EventListener<MouseEvent>) e -> {
            if (dragging) {
                var rect = getElement().getBoundingClientRect();
                int pos = horizontal ? e.getClientX() - rect.getLeft() : e.getClientY() - rect.getTop();
                setSize(pos);
            }
        });
        Window.current().getDocument().addEventListener("mouseup", (EventListener<MouseEvent>) e -> {
            if (dragging) {
                dragging = false;
                Js.localSet(this.storageKey, String.valueOf(sizePx));
            }
        });

        apply();
        add(first, divider, second);
    }

    public void setFirst(Component component) {
        first.removeAll();
        first.add(component);
    }

    public void setSecond(Component component) {
        second.removeAll();
        second.add(component);
    }

    /**
     * Says what this particular divider splits, for somebody who cannot see it.
     *
     * <p>The default, "Move the divider between the two panels", is true anywhere and useful
     * nowhere. An application that knows the two sides should say them - "Move the divider
     * between the file list and the editor" - so that a page with two splitters does not
     * announce the same sentence twice.</p>
     *
     * @param label short, plain words for what this divider splits
     */
    public void setAriaLabel(String label) {
        divider.getElement().setAttribute("aria-label", label);
    }

    private void setSize(int px) {
        sizePx = Math.max(minPx, Math.min(maxPx, px));
        apply();
    }

    private void apply() {
        first.setStyle(horizontal ? "width" : "height", sizePx + "px");
        divider.getElement().setAttribute("aria-valuenow", String.valueOf(sizePx));
    }

    private int restore(int defaultPx) {
        try {
            String stored = Js.localGet(storageKey);
            return stored == null ? defaultPx
                : Math.max(minPx, Math.min(maxPx, Integer.parseInt(stored)));
        } catch (Exception e) {
            return defaultPx;
        }
    }
}

