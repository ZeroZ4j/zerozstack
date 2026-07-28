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
import org.teavm.jso.dom.events.MouseEvent;

/**
 * Two panels with a draggable divider. The FIRST panel has a pixel size (width for
 * horizontal, height for vertical); the second takes the rest. The size persists to
 * localStorage under the given key so layouts survive restarts.
 */
public final class SplitPane extends Div {

    private final Div first = new Div();
    private final Div second = new Div();
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

        Div divider = new Div();
        divider.addClassName(horizontal
            ? "shrink-0 w-1 cursor-col-resize bg-base-300 hover:bg-primary/60 transition-colors"
            : "shrink-0 h-1 cursor-row-resize bg-base-300 hover:bg-primary/60 transition-colors");

        divider.getElement().addEventListener("mousedown", (EventListener<MouseEvent>) e -> {
            e.preventDefault();
            dragging = true;
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

    private void setSize(int px) {
        sizePx = Math.max(minPx, Math.min(maxPx, px));
        apply();
    }

    private void apply() {
        first.setStyle(horizontal ? "width" : "height", sizePx + "px");
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

