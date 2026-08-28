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
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.events.EventListener;
import org.teavm.jso.dom.events.KeyboardEvent;
import org.teavm.jso.dom.events.MouseEvent;
import org.teavm.jso.dom.events.WheelEvent;
import org.teavm.jso.dom.xml.Element;

/**
 * Interactive SVG surface: a viewport {@code <g>} that pans (drag on empty space) and
 * zooms (wheel, anchored at the cursor). Consumers draw into {@link #viewport()} using
 * {@link #el(String)} to create namespaced SVG elements. Foundation for GraphView,
 * LaneTimeline, and the chart set.
 *
 * <p>A mouse is not required. The surface sits in the tab order, and once the keyboard is on
 * it the arrow keys move the picture (Shift moves it further), plus and minus zoom in and out,
 * and 0 puts it back where it started. Those keys are taken over while the keyboard is here,
 * which is why the surface tells a screen reader it is an application: without that a reader
 * would keep the arrow keys for reading the page and the picture would never move.</p>
 */
public class SvgCanvas extends Div {

    public static final String SVG_NS = "http://www.w3.org/2000/svg";

    /** How far one arrow key press moves the picture, in screen pixels. */
    private static final double PAN_STEP = 40;

    /** How far Shift and an arrow key move it, for crossing a big drawing quickly. */
    private static final double PAN_BIG_STEP = 160;

    /** How much closer one press of plus gets, and how much further one press of minus. */
    private static final double ZOOM_STEP = 1.12;

    private final Element svg;
    private final Element viewport;

    private double panX = 0;
    private double panY = 0;
    private double scale = 1.0;
    private boolean panning = false;
    private double lastX;
    private double lastY;

    public SvgCanvas() {
        addClassName("relative overflow-hidden w-full h-full select-none");
        svg = el("svg");
        svg.setAttribute("width", "100%");
        svg.setAttribute("height", "100%");
        viewport = el("g");
        svg.appendChild(viewport);
        getElement().appendChild(svg);

        // Listeners live on the wrapper div (xml.Element is not an EventTarget in TeaVM);
        // pointer events bubble up from the SVG content.
        getElement().addEventListener("mousedown", (EventListener<MouseEvent>) e -> {
            panning = true;
            lastX = e.getClientX();
            lastY = e.getClientY();
        });
        getElement().addEventListener("mousemove", (EventListener<MouseEvent>) e -> {
            if (panning) {
                panX += e.getClientX() - lastX;
                panY += e.getClientY() - lastY;
                lastX = e.getClientX();
                lastY = e.getClientY();
                applyTransform();
            }
        });
        EventListener<MouseEvent> stop = e -> panning = false;
        getElement().addEventListener("mouseup", stop);
        getElement().addEventListener("mouseleave", stop);
        getElement().addEventListener("wheel", (EventListener<WheelEvent>) e -> {
            e.preventDefault();
            // Zoom anchored at the cursor: keep the world point under it fixed.
            var rect = getElement().getBoundingClientRect();
            zoomAt(e.getDeltaY() < 0 ? ZOOM_STEP : 1 / ZOOM_STEP,
                e.getClientX() - rect.getLeft(), e.getClientY() - rect.getTop());
        });

        getElement().setAttribute("role", "application");
        getElement().setAttribute("tabindex", "0");
        setAriaLabel("Drawing you can move and zoom");

        getElement().addEventListener("keydown", (EventListener<KeyboardEvent>) e -> {
            String key = Js.eventKey(e);
            double step = e.isShiftKey() ? PAN_BIG_STEP : PAN_STEP;
            if ("ArrowLeft".equals(key)) {
                panX += step;
            } else if ("ArrowRight".equals(key)) {
                panX -= step;
            } else if ("ArrowUp".equals(key)) {
                panY += step;
            } else if ("ArrowDown".equals(key)) {
                panY -= step;
            } else if ("+".equals(key) || "=".equals(key)) {
                zoomBy(ZOOM_STEP);
                e.preventDefault();
                return;
            } else if ("-".equals(key) || "_".equals(key)) {
                zoomBy(1 / ZOOM_STEP);
                e.preventDefault();
                return;
            } else if ("0".equals(key)) {
                panX = 0;
                panY = 0;
                scale = 1.0;
                applyTransform();
                e.preventDefault();
                return;
            } else {
                return;   // not ours - leave it to the page
            }
            // Only now, once a key is known to be one we act on: otherwise Tab would be
            // swallowed and the keyboard could never leave the drawing.
            e.preventDefault();
            applyTransform();
        });
    }

    /**
     * Says what this particular drawing shows, for somebody who cannot see it.
     *
     * <p>The default, "Drawing you can move and zoom", says what can be done to it and nothing
     * about what it is. An application should replace it with the subject - "Run graph for
     * last night's build" - because that is the only part a listener cannot work out.</p>
     *
     * @param label short, plain words for what this drawing shows
     */
    public void setAriaLabel(String label) {
        getElement().setAttribute("aria-label", label);
    }

    /** The pannable/zoomable group all content goes into. */
    public Element viewport() {
        return viewport;
    }

    public Element svg() {
        return svg;
    }

    /** Creates a properly namespaced SVG element ({@code createElement} silently fails for SVG). */
    public static Element el(String tag) {
        return Window.current().getDocument().createElementNS(SVG_NS, tag);
    }

    public static Element el(String tag, String... attrPairs) {
        Element element = el(tag);
        for (int i = 0; i + 1 < attrPairs.length; i += 2) {
            element.setAttribute(attrPairs[i], attrPairs[i + 1]);
        }
        return element;
    }

    public void clearContent() {
        while (viewport.getFirstChild() != null) {
            viewport.removeChild(viewport.getFirstChild());
        }
    }

    /** Positions the view so (x, y) in content coordinates lands at the top-left, at the given scale. */
    public void setView(double x, double y, double newScale) {
        this.panX = -x * newScale;
        this.panY = -y * newScale;
        this.scale = newScale;
        applyTransform();
    }

    /** Fits the given content bounds into the visible area with padding. */
    public void fit(double contentWidth, double contentHeight) {
        int w = getElement().getOffsetWidth();
        int h = getElement().getOffsetHeight();
        if (w == 0 || h == 0 || contentWidth <= 0 || contentHeight <= 0) {
            return;
        }
        double pad = 40;
        double fitScale = Math.min(1.0,
            Math.min((w - pad) / contentWidth, (h - pad) / contentHeight));
        scale = Math.max(0.15, fitScale);
        panX = (w - contentWidth * scale) / 2;
        panY = (h - contentHeight * scale) / 2;
        applyTransform();
    }

    /** Zooms about the middle of the visible area, which is where a key press has to aim. */
    private void zoomBy(double factor) {
        zoomAt(factor, getElement().getOffsetWidth() / 2.0, getElement().getOffsetHeight() / 2.0);
    }

    /**
     * Zooms by {@code factor}, keeping the point of the drawing that is under (ax, ay) exactly
     * where it is. The wheel aims at the pointer, a key press aims at the middle; the arithmetic
     * is the same either way, and lives here once so the two cannot drift apart.
     */
    private void zoomAt(double factor, double ax, double ay) {
        double newScale = Math.max(0.15, Math.min(4.0, scale * factor));
        panX = ax - (ax - panX) * (newScale / scale);
        panY = ay - (ay - panY) * (newScale / scale);
        scale = newScale;
        applyTransform();
    }

    private void applyTransform() {
        viewport.setAttribute("transform",
            "translate(" + panX + "," + panY + ") scale(" + scale + ")");
    }

    /** Suppress panning while a child element handles its own drag. */
    public void cancelPan() {
        panning = false;
    }
}

