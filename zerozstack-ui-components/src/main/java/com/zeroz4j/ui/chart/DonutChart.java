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

import com.zeroz4j.ui.component.SvgCanvas;
import com.zeroz4j.ui.layout.Div;
import java.util.ArrayList;
import java.util.List;
import org.teavm.jso.dom.xml.Element;

/**
 * Composition of a whole: disk by category, memory by process, containers by state.
 *
 * <p>Reach for it when the total is meaningful and the parts are few — beyond about six
 * slices the small ones become unreadable and a {@link BarChart} or {@link BarGauge} says
 * the same thing more precisely. The hole in the middle earns its place by holding the
 * total, which is usually the number the operator wanted first.</p>
 *
 * <pre>{@code
 * DonutChart disk = new DonutChart();
 * disk.setFormat(ValueFormat.BYTES);
 * disk.setCenterLabel("on disk");
 * disk.setSlices(List.of(
 *     new DonutChart.Slice("models", 412e9),
 *     new DonutChart.Slice("images", 88e9),
 *     new DonutChart.Slice("free",   210e9)));
 * }</pre>
 */
public final class DonutChart extends ChartBase {

    /** One wedge. Leave {@code color} null to take the next palette entry. */
    public record Slice(String label, double value, String color) {

        public Slice(String label, double value) {
            this(label, value, null);
        }
    }

    private record Wedge(int index, double startAngle, double endAngle) {
    }

    private List<Slice> slices = new ArrayList<>();
    private final List<Wedge> wedges = new ArrayList<>();
    private ValueFormat format = ValueFormat.AUTO;
    private double innerRadius = 0.62;
    private String centerLabel = "total";
    private String centerValue;
    private boolean legendPercent = true;
    private int highlighted = -1;

    public DonutChart() {
        setChartHeight(200);
        setMargins(8, 8, 8, 8);
    }

    // ------------------------------------------------------------------ public API

    public DonutChart setSlices(List<Slice> newSlices) {
        this.slices = newSlices == null ? new ArrayList<>() : newSlices;
        refresh();
        return this;
    }

    public DonutChart setFormat(ValueFormat newFormat) {
        this.format = newFormat == null ? ValueFormat.AUTO : newFormat;
        refresh();
        return this;
    }

    /** Hole size as a fraction of the radius. Zero makes it a pie. Default 0.62. */
    public DonutChart setInnerRadius(double fraction) {
        this.innerRadius = Scales.clamp(fraction, 0, 0.9);
        refresh();
        return this;
    }

    /** Caption under the centre figure. */
    public DonutChart setCenterLabel(String label) {
        this.centerLabel = label;
        refresh();
        return this;
    }

    /** Overrides the centre figure; by default it is the sum of the slices. */
    public DonutChart setCenterValue(String value) {
        this.centerValue = value;
        refresh();
        return this;
    }

    /** Adds each slice's share of the total to its legend entry. */
    public DonutChart setLegendPercent(boolean value) {
        this.legendPercent = value;
        refresh();
        return this;
    }

    public double total() {
        double sum = 0;
        for (Slice slice : slices) {
            if (!Double.isNaN(slice.value()) && slice.value() > 0) {
                sum += slice.value();
            }
        }
        return sum;
    }

    // --------------------------------------------------------------------- drawing

    @Override
    protected boolean hasData() {
        return !slices.isEmpty() && total() > 0;
    }

    @Override
    protected void draw() {
        wedges.clear();
        double total = total();
        double centreX = width() / 2.0;
        double centreY = plotTop() + plotHeight() / 2.0;
        double outer = Math.max(16, Math.min(width(), plotHeight()) / 2.0 - 4);
        double inner = outer * innerRadius;

        // Start at twelve o'clock and run clockwise, which is how everyone reads a dial.
        double angle = 90;
        for (int i = 0; i < slices.size(); i++) {
            Slice slice = slices.get(i);
            if (Double.isNaN(slice.value()) || slice.value() <= 0) {
                continue;
            }
            double sweep = slice.value() / total * 360;
            double endAngle = angle - sweep;
            String color = slice.color() != null ? slice.color() : Palette.series(i);

            Element wedge = SvgCanvas.el("path",
                "d", wedgePath(centreX, centreY, outer, inner, angle, endAngle),
                "fill", color,
                "stroke", Palette.BASE_100,
                "stroke-width", "1.5");
            if (highlighted == i) {
                wedge.setAttribute("filter", "brightness(1.15)");
                wedge.setAttribute("stroke-width", "2.5");
            }
            add(wedge);
            wedges.add(new Wedge(i, angle, endAngle));
            angle = endAngle;

            legend(slice.label(), color, legendPercent
                ? format.format(slice.value()) + "  " + Scales.fixed(slice.value() / total * 100, 1) + "%"
                : format.format(slice.value()));
        }

        if (innerRadius > 0.25) {
            String figure = centerValue != null ? centerValue : format.format(total);
            Element figureText = monoText(centreX, centreY - 4, figure, "middle",
                Math.max(12, inner * 0.42), 1);
            figureText.setAttribute("font-weight", "700");
            add(figureText);
            if (centerLabel != null && !centerLabel.isEmpty()) {
                add(text(centreX, centreY + inner * 0.34, centerLabel, "middle",
                    Math.max(9, inner * 0.18), 0.5));
            }
        }
    }

    /**
     * An annulus segment: outer arc clockwise, then the inner arc back. Angles are degrees
     * counter-clockwise from east, so a clockwise sweep decreases.
     */
    private static String wedgePath(double cx, double cy, double outer, double inner,
                                    double startAngle, double endAngle) {
        double startRad = Math.toRadians(startAngle);
        double endRad = Math.toRadians(endAngle);
        int largeArc = Math.abs(startAngle - endAngle) > 180 ? 1 : 0;

        double outerStartX = cx + outer * Math.cos(startRad);
        double outerStartY = cy - outer * Math.sin(startRad);
        double outerEndX = cx + outer * Math.cos(endRad);
        double outerEndY = cy - outer * Math.sin(endRad);

        StringBuilder path = new StringBuilder()
            .append('M').append(num(outerStartX)).append(' ').append(num(outerStartY))
            .append('A').append(num(outer)).append(' ').append(num(outer))
            .append(" 0 ").append(largeArc).append(" 1 ")
            .append(num(outerEndX)).append(' ').append(num(outerEndY));

        if (inner <= 0.5) {
            return path.append('L').append(num(cx)).append(' ').append(num(cy)).append('Z').toString();
        }
        double innerEndX = cx + inner * Math.cos(endRad);
        double innerEndY = cy - inner * Math.sin(endRad);
        double innerStartX = cx + inner * Math.cos(startRad);
        double innerStartY = cy - inner * Math.sin(startRad);
        return path
            .append('L').append(num(innerEndX)).append(' ').append(num(innerEndY))
            .append('A').append(num(inner)).append(' ').append(num(inner))
            .append(" 0 ").append(largeArc).append(" 0 ")
            .append(num(innerStartX)).append(' ').append(num(innerStartY))
            .append('Z')
            .toString();
    }

    // ----------------------------------------------------------------------- hover

    @Override
    protected void onPointerMove(double x, double y) {
        if (!hasData() || !isDrawn()) {
            return;
        }
        double centreX = width() / 2.0;
        double centreY = plotTop() + plotHeight() / 2.0;
        double outer = Math.max(16, Math.min(width(), plotHeight()) / 2.0 - 4);
        double distance = Math.sqrt((x - centreX) * (x - centreX) + (y - centreY) * (y - centreY));
        if (distance > outer || distance < outer * innerRadius) {
            clearHighlight();
            return;
        }

        // atan2 with the y axis flipped back to maths orientation, normalised to 0..360.
        double degrees = Math.toDegrees(Math.atan2(centreY - y, x - centreX));
        int found = -1;
        for (Wedge wedge : wedges) {
            if (containsAngle(wedge, degrees)) {
                found = wedge.index();
                break;
            }
        }
        if (found < 0) {
            clearHighlight();
            return;
        }
        if (found != highlighted) {
            highlighted = found;
            refresh();
        }
        Slice slice = slices.get(found);
        Div content = new Div();
        content.addClassName("flex flex-col gap-0.5");
        Div name = new Div(slice.label());
        name.addClassName("font-semibold");
        Div value = new Div(format.format(slice.value())
            + "  ·  " + Scales.fixed(slice.value() / total() * 100, 1) + "%");
        value.addClassName("font-mono text-base-content/70");
        content.add(name, value);
        showTooltip(x, y, content);
    }

    /** Wedges run clockwise from {@code startAngle}, wrapping past -180 degrees. */
    private static boolean containsAngle(Wedge wedge, double degrees) {
        double offsetFromStart = normalise(wedge.startAngle() - degrees);
        double sweep = normalise(wedge.startAngle() - wedge.endAngle());
        return offsetFromStart >= 0 && offsetFromStart <= sweep;
    }

    private static double normalise(double degrees) {
        double value = degrees % 360;
        return value < 0 ? value + 360 : value;
    }

    private void clearHighlight() {
        hideTooltip();
        if (highlighted != -1) {
            highlighted = -1;
            refresh();
        }
    }

    @Override
    protected void onPointerLeave() {
        super.onPointerLeave();
        clearHighlight();
    }
}
