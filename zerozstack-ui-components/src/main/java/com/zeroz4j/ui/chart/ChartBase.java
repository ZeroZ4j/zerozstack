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
package com.zeroz4j.ui.chart;

import com.zeroz4j.ui.component.Js;
import com.zeroz4j.ui.component.SvgCanvas;
import com.zeroz4j.ui.layout.Div;
import java.util.ArrayList;
import java.util.List;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.events.EventListener;
import org.teavm.jso.dom.events.MouseEvent;
import org.teavm.jso.dom.xml.Element;

/**
 * The drawing surface every chart in this package sits on: measurement, redraw lifecycle,
 * SVG element factories, a hover tooltip, a legend strip and an empty state.
 *
 * <p><b>Measure, then draw.</b> Charts render into a pixel-sized {@code <svg>} rather than
 * a scaled {@code viewBox}, because a viewBox stretches text and stroke widths along with
 * the geometry. That means width has to be measured, and a component measures zero until
 * the browser has laid it out — so the first draw is deferred and retried, and a
 * {@code ResizeObserver} redraws on every later size change (a window {@code resize}
 * listener misses a drawer opening or a split pane being dragged).</p>
 *
 * <p><b>Subclass contract:</b> implement {@link #draw()} and paint into {@link #add(Element)}
 * using the geometry accessors. Override {@link #hasData()} so an unpopulated chart shows
 * the empty state instead of bare axes.</p>
 */
public abstract class ChartBase extends Div {

    /** One legend entry: the mark's colour, its name, and optionally its current value. */
    public record LegendItem(String label, String color, String value) {

        public LegendItem(String label, String color) {
            this(label, color, null);
        }
    }

    private static final int MAX_MEASURE_RETRIES = 12;

    private final Div plotHost = new Div();
    private final Div svgHost = new Div();
    private final Div tooltip = new Div();
    private final Div legendHost = new Div();

    protected int marginTop = 10;
    protected int marginRight = 14;
    protected int marginBottom = 26;
    protected int marginLeft = 48;

    private Element svg;
    private int width;
    private int chartHeight = 200;
    private int measureRetries;
    private boolean legendVisible = true;
    private boolean drawn;
    private int batchDepth;
    private String emptyText = "No data";
    private List<LegendItem> legendItems = new ArrayList<>();

    protected ChartBase() {
        addClassName("relative flex w-full flex-col text-base-content");
        plotHost.addClassName("relative w-full");
        svgHost.addClassName("w-full");
        tooltip.addClassName("pointer-events-none absolute z-30 hidden max-w-xs rounded-md border "
            + "border-base-300 bg-base-100/95 px-2 py-1.5 text-xs leading-snug shadow-lg backdrop-blur-sm");
        legendHost.addClassName("flex flex-wrap items-center gap-x-4 gap-y-1 px-1 pt-2 text-xs "
            + "text-base-content/70");
        plotHost.add(svgHost, tooltip);
        add(plotHost, legendHost);

        plotHost.getElement().addEventListener("mousemove", (EventListener<MouseEvent>) event -> {
            var bounds = plotHost.getElement().getBoundingClientRect();
            onPointerMove(event.getClientX() - bounds.getLeft(), event.getClientY() - bounds.getTop());
        });
        plotHost.getElement().addEventListener("mouseleave", (EventListener<MouseEvent>) event -> onPointerLeave());

        Js.onResize(plotHost.getElement(), this::refresh);
        Window.setTimeout(this::refresh, 0);
    }

    // ------------------------------------------------------------------- lifecycle

    /** Paint the chart. Called after the surface is sized and cleared; never call directly. */
    protected abstract void draw();

    /** Whether there is anything to plot. False renders the empty state instead of {@link #draw()}. */
    protected boolean hasData() {
        return true;
    }

    /**
     * Measures the host and repaints. Cheap enough to call on every data update — the whole
     * SVG is rebuilt, which for the sample counts a dashboard shows is faster and far simpler
     * than diffing.
     */
    public void refresh() {
        if (batchDepth > 0) {
            return;
        }
        int measured = plotHost.getElement().getClientWidth();
        if (measured <= 0) {
            // Not laid out yet (hidden tab, drawer still closed). Try again shortly.
            if (measureRetries++ < MAX_MEASURE_RETRIES) {
                Window.setTimeout(this::refresh, 50);
            }
            return;
        }
        measureRetries = 0;
        width = measured;

        svgHost.removeAll();
        svg = SvgCanvas.el("svg",
            "width", String.valueOf(width),
            "height", String.valueOf(chartHeight),
            "class", "block select-none");
        svgHost.getElement().appendChild(svg);

        legendItems = new ArrayList<>();
        if (hasData()) {
            draw();
        } else {
            drawEmptyState();
        }
        renderLegend();
        drawn = true;
    }

    /** True once the chart has measured and painted at least once. */
    protected boolean isDrawn() {
        return drawn;
    }

    /**
     * Suppresses redraws until the matching {@link #endBatch()}. Every setter here refreshes,
     * which is right for a one-off change and wasteful when configuring several at once —
     * wrap those in a batch so the chart paints once. Nests safely.
     */
    public ChartBase beginBatch() {
        batchDepth++;
        return this;
    }

    /** Ends a batch and repaints once the outermost one closes. */
    public ChartBase endBatch() {
        batchDepth--;
        if (batchDepth <= 0) {
            batchDepth = 0;
            refresh();
        }
        return this;
    }

    @Override
    protected void onAttach() {
        super.onAttach();
        // A chart added long after construction has exhausted its retries; give it a fresh budget.
        measureRetries = 0;
        refresh();
    }

    // -------------------------------------------------------------------- geometry

    /** Full surface width in pixels, margins included. */
    protected int width() {
        return width;
    }

    /** Full surface height in pixels, margins included. */
    protected int height() {
        return chartHeight;
    }

    /** Width of the plotting area between the margins. */
    protected int plotWidth() {
        return Math.max(1, width - marginLeft - marginRight);
    }

    /** Height of the plotting area between the margins. */
    protected int plotHeight() {
        return Math.max(1, chartHeight - marginTop - marginBottom);
    }

    protected int plotLeft() {
        return marginLeft;
    }

    protected int plotTop() {
        return marginTop;
    }

    protected int plotRight() {
        return marginLeft + plotWidth();
    }

    protected int plotBottom() {
        return marginTop + plotHeight();
    }

    /** Overall height of the drawing surface; the legend sits below it. Default 200. */
    public ChartBase setChartHeight(int pixels) {
        this.chartHeight = Math.max(40, pixels);
        refresh();
        return this;
    }

    /** Axis gutters. Widen the left margin when y labels are long, e.g. formatted byte sizes. */
    public ChartBase setMargins(int top, int right, int bottom, int left) {
        this.marginTop = top;
        this.marginRight = right;
        this.marginBottom = bottom;
        this.marginLeft = left;
        refresh();
        return this;
    }

    // ------------------------------------------------------------- svg primitives

    /** The live {@code <svg>} root. Valid only inside {@link #draw()}. */
    protected Element svg() {
        return svg;
    }

    /** Appends an element to the surface. */
    protected void add(Element element) {
        if (svg != null && element != null) {
            svg.appendChild(element);
        }
    }

    protected static Element rect(double x, double y, double w, double h, String fill) {
        return SvgCanvas.el("rect",
            "x", num(x), "y", num(y),
            "width", num(Math.max(0, w)), "height", num(Math.max(0, h)),
            "fill", fill);
    }

    protected static Element line(double x1, double y1, double x2, double y2, String stroke, double strokeWidth) {
        return SvgCanvas.el("line",
            "x1", num(x1), "y1", num(y1), "x2", num(x2), "y2", num(y2),
            "stroke", stroke, "stroke-width", num(strokeWidth));
    }

    protected static Element circle(double cx, double cy, double r, String fill) {
        return SvgCanvas.el("circle", "cx", num(cx), "cy", num(cy), "r", num(r), "fill", fill);
    }

    protected static Element path(String d, String stroke, double strokeWidth) {
        return SvgCanvas.el("path",
            "d", d, "fill", "none", "stroke", stroke, "stroke-width", num(strokeWidth),
            "stroke-linejoin", "round", "stroke-linecap", "round");
    }

    /**
     * A text label. {@code anchor} is {@code start}, {@code middle} or {@code end};
     * {@code opacity} dims it against the plot without changing hue, so it stays legible
     * in every theme.
     */
    protected static Element text(double x, double y, String content, String anchor,
                                  double fontSize, double opacity) {
        Element element = SvgCanvas.el("text",
            "x", num(x), "y", num(y),
            "text-anchor", anchor,
            "font-size", num(fontSize),
            "fill", "currentColor",
            "fill-opacity", num(opacity),
            "dominant-baseline", "middle");
        element.appendChild(Window.current().getDocument().createTextNode(content == null ? "" : content));
        return element;
    }

    /** Monospaced label, for numbers that must not jitter as they change width. */
    protected static Element monoText(double x, double y, String content, String anchor,
                                      double fontSize, double opacity) {
        Element element = text(x, y, content, anchor, fontSize, opacity);
        element.setAttribute("font-family", "ui-monospace, SFMono-Regular, Menlo, monospace");
        return element;
    }

    /** A group, for anything that needs a shared transform or a shared opacity. */
    protected static Element group(String... attributePairs) {
        return SvgCanvas.el("g", attributePairs);
    }

    protected static String num(double value) {
        double rounded = Scales.px(value);
        return rounded == Math.floor(rounded) && !Double.isInfinite(rounded)
            ? String.valueOf((long) rounded)
            : String.valueOf(rounded);
    }

    // --------------------------------------------------------------------- tooltip

    /**
     * Shows the tooltip near ({@code x}, {@code y}) in host coordinates, flipping it to the
     * other side of the cursor when it would overflow the right edge.
     */
    protected void showTooltip(double x, double y, Div content) {
        tooltip.removeAll();
        tooltip.add(content);
        positionTooltip(x, y);
    }

    protected void showTooltip(double x, double y, String plainText) {
        Div content = new Div(plainText);
        showTooltip(x, y, content);
    }

    private void positionTooltip(double x, double y) {
        tooltip.removeClassName("hidden");
        int tooltipWidth = tooltip.getElement().getOffsetWidth();
        int tooltipHeight = tooltip.getElement().getOffsetHeight();
        double left = x + 14;
        if (left + tooltipWidth > width - 4) {
            left = x - tooltipWidth - 14;
        }
        double top = y - tooltipHeight - 10;
        if (top < 2) {
            top = y + 18;
        }
        tooltip.setStyle("left", Math.max(2, left) + "px");
        tooltip.setStyle("top", Math.max(2, top) + "px");
    }

    protected void hideTooltip() {
        tooltip.addClassName("hidden");
    }

    /** Pointer moved over the plot, in host coordinates. Charts with hover override this. */
    protected void onPointerMove(double x, double y) {
    }

    /** Pointer left the plot. Overriders should call {@code super} to clear the tooltip. */
    protected void onPointerLeave() {
        hideTooltip();
    }

    // ---------------------------------------------------------------------- legend

    /** Registers a legend entry. Call from {@link #draw()}; entries are cleared each redraw. */
    protected void legend(String label, String color) {
        legendItems.add(new LegendItem(label, color));
    }

    protected void legend(String label, String color, String value) {
        legendItems.add(new LegendItem(label, color, value));
    }

    /** Hides the legend strip even when entries are registered. */
    public ChartBase setLegendVisible(boolean visible) {
        this.legendVisible = visible;
        refresh();
        return this;
    }

    private void renderLegend() {
        legendHost.removeAll();
        if (!legendVisible || legendItems.isEmpty()) {
            legendHost.addClassName("hidden");
            return;
        }
        legendHost.removeClassName("hidden");
        for (LegendItem item : legendItems) {
            Div entry = new Div();
            entry.addClassName("flex items-center gap-1.5");
            Div swatch = new Div();
            swatch.addClassName("h-2 w-2 shrink-0 rounded-sm");
            swatch.setStyle("background-color", item.color());
            Div label = new Div(item.label());
            entry.add(swatch, label);
            if (item.value() != null) {
                Div value = new Div(item.value());
                value.addClassName("font-mono text-base-content/50");
                entry.add(value);
            }
            legendHost.add(entry);
        }
    }

    // ----------------------------------------------------------------- empty state

    /** Message shown when {@link #hasData()} is false. */
    public ChartBase setEmptyText(String message) {
        this.emptyText = message;
        refresh();
        return this;
    }

    private void drawEmptyState() {
        add(rect(plotLeft(), plotTop(), plotWidth(), plotHeight(), Palette.BASE_200));
        Element frame = rect(plotLeft(), plotTop(), plotWidth(), plotHeight(), "none");
        frame.setAttribute("stroke", "currentColor");
        frame.setAttribute("stroke-opacity", "0.08");
        frame.setAttribute("rx", "6");
        add(frame);
        add(text(plotLeft() + plotWidth() / 2.0, plotTop() + plotHeight() / 2.0,
            emptyText, "middle", 12, 0.4));
    }
}
