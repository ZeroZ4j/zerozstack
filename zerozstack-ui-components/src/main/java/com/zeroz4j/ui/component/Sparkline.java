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
import org.teavm.jso.dom.xml.Element;

/**
 * Tiny inline trend chart (default 100x24), auto-scaled to its data.
 *
 * <p>Draws in {@code currentColor} by default, so it inherits the surrounding text colour and
 * follows the theme for free. For KPI tiles, table cells, token burn and survival trends — for
 * anything needing axes, a tooltip or a legend, use {@code com.zeroz4j.ui.chart.TimeSeriesChart}.</p>
 *
 * <pre>{@code
 * Sparkline trend = new Sparkline(120, 28);
 * trend.setMode(Sparkline.Mode.BAR);
 * trend.setDeltaColored(true);      // green when it ends above where it started, red below
 * trend.setValues(samples);
 * }</pre>
 *
 * <p>A {@code NaN} is a gap: the line breaks rather than interpolating across it, so a missing
 * sample does not read as a value.</p>
 */
public final class Sparkline extends Div {

    /** How the series is drawn. */
    public enum Mode {
        /** A line with a soft area fill beneath it. The default. */
        AREA,
        /** A line only — less ink, better when several are stacked in a table column. */
        LINE,
        /** One bar per sample. Correct for counts and anything discrete. */
        BAR
    }

    private static final double PAD = 1;

    private final int width;
    private final int height;
    private final Element svg;

    private Mode mode = Mode.AREA;
    private double[] values = new double[0];
    private boolean deltaColored;
    private boolean markersVisible;
    private boolean baselineVisible;
    private double baseline = Double.NaN;
    private String color;

    public Sparkline() {
        this(100, 24);
    }

    public Sparkline(int width, int height) {
        this.width = width;
        this.height = height;
        addClassName("inline-block align-middle");
        svg = SvgCanvas.el("svg",
            "width", String.valueOf(width),
            "height", String.valueOf(height),
            "viewBox", "0 0 " + width + " " + height);
        getElement().appendChild(svg);
    }

    // ------------------------------------------------------------------ public API

    public Sparkline setValues(double[] newValues) {
        this.values = newValues == null ? new double[0] : newValues;
        redraw();
        return this;
    }

    public Sparkline setMode(Mode newMode) {
        this.mode = newMode == null ? Mode.AREA : newMode;
        redraw();
        return this;
    }

    /**
     * Colours the trend by its own direction: success when the last known value is above the first,
     * error when below. Only meaningful where up is good — leave it off for a metric like latency,
     * or set an explicit {@link #setColor}.
     */
    public Sparkline setDeltaColored(boolean value) {
        this.deltaColored = value;
        redraw();
        return this;
    }

    /** Dots on the lowest and highest samples, so the range is readable without an axis. */
    public Sparkline setMarkersVisible(boolean value) {
        this.markersVisible = value;
        redraw();
        return this;
    }

    /** A hairline at {@code value}. Without an argument it sits at the series mean. */
    public Sparkline setBaseline(double value) {
        this.baseline = value;
        this.baselineVisible = true;
        redraw();
        return this;
    }

    public Sparkline setBaselineVisible(boolean visible) {
        this.baselineVisible = visible;
        redraw();
        return this;
    }

    /** An explicit CSS colour. Unset inherits {@code currentColor}. */
    public Sparkline setColor(String cssColor) {
        this.color = cssColor;
        redraw();
        return this;
    }

    /** Last known value minus first known value; {@code NaN} when there is nothing to compare. */
    public double delta() {
        double first = Double.NaN;
        double last = Double.NaN;
        for (double value : values) {
            if (!Double.isNaN(value)) {
                if (Double.isNaN(first)) {
                    first = value;
                }
                last = value;
            }
        }
        return Double.isNaN(first) || Double.isNaN(last) ? Double.NaN : last - first;
    }

    // --------------------------------------------------------------------- drawing

    private void redraw() {
        while (svg.getFirstChild() != null) {
            svg.removeChild(svg.getFirstChild());
        }
        if (values.length < 2) {
            return;
        }

        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        double sum = 0;
        int known = 0;
        for (double value : values) {
            if (Double.isNaN(value)) {
                continue;
            }
            min = Math.min(min, value);
            max = Math.max(max, value);
            sum += value;
            known++;
        }
        if (known < 2) {
            return;
        }
        double range = max - min;
        if (range == 0) {
            // A flat series would otherwise divide by zero and collapse onto one edge.
            range = 1;
            min -= 0.5;
        }

        String stroke = resolveColor();
        if (baselineVisible) {
            double at = Double.isNaN(baseline) ? sum / known : baseline;
            double y = yFor(at, min, range);
            if (y >= 0 && y <= height) {
                Element line = SvgCanvas.el("line",
                    "x1", "0", "y1", round1(y), "x2", String.valueOf(width), "y2", round1(y),
                    "stroke", "currentColor", "stroke-opacity", "0.25",
                    "stroke-width", "1", "stroke-dasharray", "2 2");
                svg.appendChild(line);
            }
        }

        if (mode == Mode.BAR) {
            drawBars(min, range, stroke);
        } else {
            drawLine(min, range, stroke);
        }

        if (markersVisible) {
            drawMarkers(min, range, stroke);
        }
    }

    private void drawLine(double min, double range, String stroke) {
        StringBuilder points = new StringBuilder();
        boolean penDown = false;
        int firstDrawn = -1;
        int lastDrawn = -1;
        for (int i = 0; i < values.length; i++) {
            if (Double.isNaN(values[i])) {
                penDown = false;
                continue;
            }
            double x = xFor(i);
            double y = yFor(values[i], min, range);
            points.append(penDown ? "L" : "M").append(round1(x)).append(' ').append(round1(y));
            penDown = true;
            if (firstDrawn < 0) {
                firstDrawn = i;
            }
            lastDrawn = i;
        }
        if (points.length() == 0) {
            return;
        }

        if (mode == Mode.AREA && firstDrawn >= 0 && lastDrawn > firstDrawn) {
            // Closed down to the bottom edge. A gap leaves the fill open across it, which reads
            // as missing rather than as zero.
            String area = points + "L" + round1(xFor(lastDrawn)) + " " + (height - PAD)
                + "L" + round1(xFor(firstDrawn)) + " " + (height - PAD) + "Z";
            svg.appendChild(SvgCanvas.el("path",
                "d", area, "fill", stroke, "fill-opacity", "0.14", "stroke", "none"));
        }

        svg.appendChild(SvgCanvas.el("path",
            "d", points.toString(),
            "fill", "none", "stroke", stroke, "stroke-width", "1.5",
            "stroke-linejoin", "round", "stroke-linecap", "round"));
    }

    private void drawBars(double min, double range, String fill) {
        double slot = (double) width / values.length;
        double barWidth = Math.max(1, slot - Math.min(2, slot * 0.3));
        double floor = height - PAD;
        for (int i = 0; i < values.length; i++) {
            if (Double.isNaN(values[i])) {
                continue;
            }
            double y = yFor(values[i], min, range);
            svg.appendChild(SvgCanvas.el("rect",
                "x", round1(i * slot + (slot - barWidth) / 2), "y", round1(y),
                "width", round1(barWidth), "height", round1(Math.max(1, floor - y)),
                "fill", fill, "rx", "0.5"));
        }
    }

    private void drawMarkers(double min, double range, String fill) {
        int lowest = -1;
        int highest = -1;
        for (int i = 0; i < values.length; i++) {
            if (Double.isNaN(values[i])) {
                continue;
            }
            if (lowest < 0 || values[i] < values[lowest]) {
                lowest = i;
            }
            if (highest < 0 || values[i] > values[highest]) {
                highest = i;
            }
        }
        if (lowest >= 0) {
            svg.appendChild(dot(xFor(lowest), yFor(values[lowest], min, range), fill, "0.55"));
        }
        if (highest >= 0 && highest != lowest) {
            svg.appendChild(dot(xFor(highest), yFor(values[highest], min, range), fill, "1"));
        }
    }

    /**
     * A marker sits on the line and often inside the area fill, both of which are the same colour,
     * so it needs a ring in the surface colour to be visible at all.
     */
    private static Element dot(double x, double y, String fill, String opacity) {
        return SvgCanvas.el("circle",
            "cx", round1(x), "cy", round1(y), "r", "2.2",
            "fill", fill, "fill-opacity", opacity,
            "stroke", "var(--color-base-100, #ffffff)", "stroke-width", "1");
    }

    private double xFor(int index) {
        return (double) index / (values.length - 1) * (width - 2 * PAD) + PAD;
    }

    private double yFor(double value, double min, double range) {
        return height - PAD - (value - min) / range * (height - 2 * PAD);
    }

    /**
     * Explicit colour first, then the delta rule, then {@code currentColor} — which is what makes
     * an unconfigured sparkline inherit its surroundings and theme for free.
     */
    private String resolveColor() {
        if (color != null) {
            return color;
        }
        if (deltaColored) {
            double delta = delta();
            if (!Double.isNaN(delta) && delta != 0) {
                return delta > 0
                    ? "var(--color-success, #16a34a)"
                    : "var(--color-error, #dc2626)";
            }
        }
        return "currentColor";
    }

    private static String round1(double value) {
        double rounded = Math.round(value * 10) / 10.0;
        return rounded == Math.floor(rounded)
            ? String.valueOf((long) rounded)
            : String.valueOf(rounded);
    }
}
