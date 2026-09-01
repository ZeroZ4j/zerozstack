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

import com.zeroz4j.ui.layout.Div;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.events.EventListener;
import org.teavm.jso.dom.events.KeyboardEvent;
import org.teavm.jso.dom.events.MouseEvent;
import org.teavm.jso.dom.html.HTMLElement;

/**
 * A draggable resizer component to adjust the width or height of an adjacent target element.
 *
 * <p>It can also be moved without a mouse. The handle sits in the tab order, and once the
 * keyboard is on it the arrow keys that point along the handle's travel move it a little at a
 * time, Shift and an arrow key move it a lot, Home shuts the panel and End opens it as far as
 * the window allows. Dragging is the one gesture the browser gives no keyboard equivalent for,
 * so a handle that only answers a pointer is a panel that somebody with a tremor, a trackpad
 * they dislike, or no mouse at all can never resize.</p>
 */
public class Resizer extends Div {

    public enum Orientation {
        HORIZONTAL, VERTICAL
    }

    /** How far one arrow key press moves the handle, in pixels. */
    private static final int STEP_PX = 16;

    /** How far Shift and an arrow key move it, for crossing a wide panel without holding a key. */
    private static final int BIG_STEP_PX = 64;

    private final HTMLElement targetElement;
    private final Orientation orientation;
    private final boolean reverse;

    private boolean dragging;
    private int startPos;
    private int startSize;

    private EventListener<MouseEvent> mouseMoveListener;
    private EventListener<MouseEvent> mouseUpListener;

    public Resizer(Component targetComponent, Orientation orientation, boolean reverse) {
        HTMLElement targetElement = targetComponent.getElement();
        this.targetElement = targetElement;
        this.orientation = orientation;
        this.reverse = reverse;

        if (orientation == Orientation.HORIZONTAL) {
            addClassName("h-1 cursor-row-resize hover:bg-primary/50 active:bg-primary transition-colors z-10 shrink-0 w-full");
        } else {
            addClassName("w-1 cursor-col-resize hover:bg-primary/50 active:bg-primary transition-colors z-10 shrink-0 h-full");
        }

        // A handle between two areas, that a person moves: that is what a separator is, and a
        // separator somebody moves has to be reachable and has to report where it now sits.
        getElement().setAttribute("role", "separator");
        getElement().setAttribute("aria-orientation",
            orientation == Orientation.HORIZONTAL ? "horizontal" : "vertical");
        getElement().setAttribute("tabindex", "0");
        getElement().setAttribute("aria-valuemin", "0");
        setAriaLabel("Resize the panel");

        getElement().addEventListener("mousedown", (EventListener<MouseEvent>) e -> {
            dragging = true;
            if (orientation == Orientation.HORIZONTAL) {
                startPos = e.getClientY();
                startSize = targetElement.getOffsetHeight();
            } else {
                startPos = e.getClientX();
                startSize = targetElement.getOffsetWidth();
            }
            e.preventDefault();
            
            // Add global listeners
            Window.current().getDocument().getDocumentElement().addEventListener("mousemove", mouseMoveListener);
            Window.current().getDocument().getDocumentElement().addEventListener("mouseup", mouseUpListener);
            Window.current().getDocument().getDocumentElement().getStyle().setProperty("cursor", orientation == Orientation.HORIZONTAL ? "row-resize" : "col-resize");
        });

        getElement().addEventListener("keydown", (EventListener<KeyboardEvent>) e -> {
            String key = Js.eventKey(e);
            boolean sideways = orientation == Orientation.VERTICAL;
            int step = e.isShiftKey() ? BIG_STEP_PX : STEP_PX;
            int diff;
            if (sideways ? "ArrowLeft".equals(key) : "ArrowUp".equals(key)) {
                diff = -step;
            } else if (sideways ? "ArrowRight".equals(key) : "ArrowDown".equals(key)) {
                diff = step;
            } else if ("Home".equals(key)) {
                e.preventDefault();
                applySize(0);
                return;
            } else if ("End".equals(key)) {
                e.preventDefault();
                applySize(sideways ? Window.current().getInnerWidth()
                                   : Window.current().getInnerHeight());
                return;
            } else {
                return;
            }
            // Stop the page scrolling under the handle, and move it exactly as a drag would:
            // the same pixel difference, through the same code, including the reversed sense.
            e.preventDefault();
            applySize(currentSize() + (reverse ? -diff : diff));
        });

        mouseMoveListener = (EventListener<MouseEvent>) e -> {
            if (dragging) {
                int diff = (orientation == Orientation.HORIZONTAL) ? (e.getClientY() - startPos) : (e.getClientX() - startPos);
                applySize(reverse ? startSize - diff : startSize + diff);
            }
        };

        mouseUpListener = (EventListener<MouseEvent>) e -> {
            if (dragging) {
                dragging = false;
                Window.current().getDocument().getDocumentElement().removeEventListener("mousemove", mouseMoveListener);
                Window.current().getDocument().getDocumentElement().removeEventListener("mouseup", mouseUpListener);
                Window.current().getDocument().getDocumentElement().getStyle().removeProperty("cursor");
            }
        };
    }

    /**
     * Says what this particular handle resizes, for somebody who cannot see it.
     *
     * <p>The default is "Resize the panel", which is true and says nothing. An application that
     * knows what is on the other side of the handle should say so - "Resize the file list" -
     * because a page with three handles on it otherwise announces the same three words.</p>
     *
     * @param label short, plain words for what this handle resizes
     */
    public void setAriaLabel(String label) {
        getElement().setAttribute("aria-label", label);
    }

    /** The size the target has right now, along the direction this handle moves in. */
    private int currentSize() {
        return orientation == Orientation.HORIZONTAL
            ? targetElement.getOffsetHeight() : targetElement.getOffsetWidth();
    }

    /**
     * Gives the target a new size, and tells anyone listening where the handle now sits.
     *
     * <p>The one place the size is written, so a key press and a drag cannot drift apart.</p>
     */
    private void applySize(int wanted) {
        int newSize = Math.max(0, wanted);
        if (orientation == Orientation.HORIZONTAL) {
            targetElement.getStyle().setProperty("height", newSize + "px");
            targetElement.getStyle().setProperty("max-height", "none");
        } else {
            targetElement.getStyle().setProperty("width", newSize + "px");
            targetElement.getStyle().setProperty("max-width", "none");
        }
        targetElement.getStyle().setProperty("flex", "0 0 auto");
        getElement().setAttribute("aria-valuenow", String.valueOf(newSize));
    }
}
