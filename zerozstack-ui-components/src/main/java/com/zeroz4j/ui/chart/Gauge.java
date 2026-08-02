/*
 * Copyright 2026 Franz Schoning
 * Project: https://www.zeroz4j.com
 * Author: Franz Schoning - Principal Enterprise Architect (https://www.franzschoning.com)
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

import com.zeroz4j.ui.component.SvgCanvas;
import java.util.List;
import org.teavm.jso.dom.xml.Element;

/**
 * A radial gauge: one value against a range, coloured by threshold.
 *
 * <p>Where {@code RadialProgress} shows a percentage, a gauge shows a <em>reading</em> — it
 * carries a min and a max, a unit, and threshold arcs that say whether the number is fine,
 * worth watching, or a problem. That judgement is the reason to spend the space.</p>
 *
 * <pre>{@code
 * Gauge memory = new Gauge("Unified memory", 0, 121);
 * memory.setFormat(ValueFormat.GIGABYTES);
 * memory.setThresholds(Threshold.utilisation(85, 110));
 * memory.setValue(96.4);
 * }</pre>
 *
 * <p>The dial spans 270 degrees, opening at the bottom. The gap is doing work: a full circle
 * gives no visual anchor for "empty", so a near-zero reading is ambiguous.</p>
 */
public final class Gauge extends ChartBase {

    private static final double START_ANGLE = 225;
    private static final double SWEEP = 270;

    private String label;
    private double value = Double.NaN;
    private double min;
    private double max = 100;
    private ValueFormat format = ValueFormat.AUTO;
    private List<Threshold> thresholds;
    private boolean showRange = true;
    private boolean thresholdArc = true;
    private double thickness = 0.18;

    public Gauge(String label) {
        this(label, 0, 100);
    }

    public Gauge(String label, double min, double max) {
        this.label = label;
        this.min = min;
        this.max = max;
        setChartHeight(170);
        setMargins(6, 6, 6, 6);
        setLegendVisible(false);
    }

    // ------------------------------------------------------------------ public API

    public Gauge setValue(double newValue) {
        this.value = newValue;
        refresh();
        return this;
    }

    public double value() {
        return value;
    }

    public Gauge setRange(double newMin, double newMax) {
        this.min = newMin;
        this.max = newMax > newMin ? newMax : newMin + 1;
        refresh();
        return this;
    }

    public Gauge setLabel(String newLabel) {
        this.label = newLabel;
        refresh();
        return this;
    }

    public Gauge setFormat(ValueFormat newFormat) {
        this.format = newFormat == null ? ValueFormat.AUTO : newFormat;
        refresh();
        return this;
    }

    /** Threshold steps; the arc and the reading both take their colour from these. */
    public Gauge setThresholds(List<Threshold> steps) {
        this.thresholds = steps;
        refresh();
        return this;
    }

    /** Shows the min and max at the ends of the dial. */
    public Gauge setRangeVisible(boolean visible) {
        this.showRange = visible;
        refresh();
        return this;
    }

    /**
     * Draws a thin threshold band outside the dial, so you can see where the limits sit even
     * when the needle is nowhere near them. Off leaves only the value's own colour.
     */
    public Gauge setThresholdArcVisible(boolean visible) {
        this.thresholdArc = visible;
        refresh();
        return this;
    }

    /** Arc thickness as a fraction of the radius. Default 0.18. */
    public Gauge setThickness(double fraction) {
        this.thickness = Scales.clamp(fraction, 0.05, 0.5);
        refresh();
        return this;
    }

    // --------------------------------------------------------------------- drawing

    @Override
    protected boolean hasData() {
        return true;
    }

    @Override
    protected void draw() {
        boolean showArc = thresholdArc && thresholds != null && !thresholds.isEmpty();

        // A 270-degree dial opening at the bottom spans the full 2r horizontally, but only
        // r + r*sin(45) vertically. Both bounds have to include the ring's half-stroke — and the
        // threshold band outside it — or the dial paints over the edge of its own box.
        double outerFactor = 1 + thickness * (showArc ? 0.89 : 0.5);
        double labelSpace = showRange ? 16 : 4;
        double availableWidth = Math.max(40, width() - 8);
        double availableHeight = Math.max(40, height() - marginTop - marginBottom - labelSpace);

        double radius = Math.max(16, Math.min(
            availableWidth / (2 * outerFactor),
            availableHeight / (outerFactor + 0.7071)));
        double centreX = width() / 2.0;
        double centreY = marginTop + radius * outerFactor;
        double stroke = radius * thickness;

        Element track = arc(centreX, centreY, radius, START_ANGLE, START_ANGLE - SWEEP, Palette.BASE_300, stroke);
        track.setAttribute("stroke-opacity", "0.55");
        add(track);

        if (showArc) {
            drawThresholdArc(centreX, centreY, radius + stroke * 0.75, stroke * 0.28);
        }

        String color = Threshold.colorFor(thresholds, value, Palette.PRIMARY);
        boolean known = !Double.isNaN(value);
        if (known) {
            double fraction = Scales.normalise(value, min, max);
            if (fraction > 0.0005) {
                Element fill = arc(centreX, centreY, radius,
                    START_ANGLE, START_ANGLE - SWEEP * fraction, color, stroke);
                fill.setAttribute("stroke-linecap", "round");
                add(fill);
            }
        }

        // The reading has to fit the hole, so the font is capped by the text's own length —
        // "96.4 GB" needs a smaller size than "43 %" in the same dial.
        String reading = known ? format.format(value) : "-";
        double innerDiameter = 2 * (radius - stroke / 2) * 0.92;
        double fontSize = Math.max(10, Math.min(radius * 0.40,
            innerDiameter / Math.max(1, reading.length() * 0.60)));
        Element readingText = monoText(centreX, centreY - radius * 0.04, reading, "middle", fontSize, 1);
        readingText.setAttribute("fill", known ? color : "currentColor");
        readingText.setAttribute("font-weight", "700");
        add(readingText);

        if (label != null && !label.isEmpty()) {
            add(text(centreX, centreY + radius * 0.34, label, "middle",
                Math.max(9, radius * 0.15), 0.55));
        }

        if (showRange) {
            // Both dial ends sit at 45 degrees below the centre line.
            double labelRadius = radius * outerFactor + 2;
            double offsetX = labelRadius * 0.7071;
            double labelY = centreY + labelRadius * 0.7071 + 8;
            add(monoText(centreX - offsetX, labelY, format.format(min), "middle", 9, 0.4));
            add(monoText(centreX + offsetX, labelY, format.format(max), "middle", 9, 0.4));
        }
    }

    private void drawThresholdArc(double cx, double cy, double radius, double stroke) {
        for (int i = 0; i < thresholds.size(); i++) {
            Threshold step = thresholds.get(i);
            double from = Double.isInfinite(step.from()) ? min : Math.max(step.from(), min);
            double to = i + 1 < thresholds.size() ? Math.min(thresholds.get(i + 1).from(), max) : max;
            if (to <= from) {
                continue;
            }
            double startAngle = START_ANGLE - SWEEP * Scales.normalise(from, min, max);
            double endAngle = START_ANGLE - SWEEP * Scales.normalise(to, min, max);
            Element band = arc(cx, cy, radius, startAngle, endAngle, step.color(), stroke);
            band.setAttribute("stroke-opacity", "0.85");
            add(band);
        }
    }

    /**
     * A stroked arc. Angles are in degrees, measured counter-clockwise from east, and the
     * gauge sweeps clockwise, so {@code endAngle} is the smaller number.
     */
    private static Element arc(double cx, double cy, double radius,
                               double startAngle, double endAngle, String color, double strokeWidth) {
        double startRad = Math.toRadians(startAngle);
        double endRad = Math.toRadians(endAngle);
        double x1 = cx + radius * Math.cos(startRad);
        double y1 = cy - radius * Math.sin(startRad);
        double x2 = cx + radius * Math.cos(endRad);
        double y2 = cy - radius * Math.sin(endRad);
        int largeArc = Math.abs(startAngle - endAngle) > 180 ? 1 : 0;
        String d = "M" + num(x1) + " " + num(y1)
            + "A" + num(radius) + " " + num(radius) + " 0 " + largeArc + " 1 "
            + num(x2) + " " + num(y2);
        return SvgCanvas.el("path",
            "d", d, "fill", "none", "stroke", color,
            "stroke-width", num(strokeWidth), "stroke-linecap", "butt");
    }
}
