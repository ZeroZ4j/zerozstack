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
import org.teavm.jso.dom.events.MouseEvent;
import org.teavm.jso.dom.html.HTMLElement;

/**
 * A draggable resizer component to adjust the width or height of an adjacent target element.
 */
public class Resizer extends Div {

    public enum Orientation {
        HORIZONTAL, VERTICAL
    }

    private boolean dragging;
    private int startPos;
    private int startSize;

    private EventListener<MouseEvent> mouseMoveListener;
    private EventListener<MouseEvent> mouseUpListener;

    public Resizer(Component targetComponent, Orientation orientation, boolean reverse) {
        HTMLElement targetElement = targetComponent.getElement();
        
        if (orientation == Orientation.HORIZONTAL) {
            addClassName("h-1 cursor-row-resize hover:bg-primary/50 active:bg-primary transition-colors z-10 shrink-0 w-full");
        } else {
            addClassName("w-1 cursor-col-resize hover:bg-primary/50 active:bg-primary transition-colors z-10 shrink-0 h-full");
        }

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

        mouseMoveListener = (EventListener<MouseEvent>) e -> {
            if (dragging) {
                int diff = (orientation == Orientation.HORIZONTAL) ? (e.getClientY() - startPos) : (e.getClientX() - startPos);
                int newSize = reverse ? startSize - diff : startSize + diff;
                if (newSize < 0) newSize = 0;
                
                if (orientation == Orientation.HORIZONTAL) {
                    targetElement.getStyle().setProperty("height", newSize + "px");
                    targetElement.getStyle().setProperty("max-height", "none");
                    targetElement.getStyle().setProperty("flex", "0 0 auto");
                } else {
                    targetElement.getStyle().setProperty("width", newSize + "px");
                    targetElement.getStyle().setProperty("max-width", "none");
                    targetElement.getStyle().setProperty("flex", "0 0 auto");
                }
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
}
