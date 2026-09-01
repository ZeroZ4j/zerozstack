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

import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.theme.Emphasis;
import com.zeroz4j.ui.theme.TextStyle;
import java.util.ArrayList;
import java.util.List;

/**
 * A stack of labelled meters against a shared scale — the densest way to show "the same
 * measurement across many things": memory per container, utilisation per GPU, free space
 * per mount, top consumers by size.
 *
 * <p>Built from DOM rather than SVG. Every row is text plus a rectangle, both of which the
 * browser lays out, wraps and truncates better than hand-placed SVG ever will, and the whole
 * thing reflows on resize without a redraw.</p>
 *
 * <pre>{@code
 * BarGauge disks = new BarGauge();
 * disks.setRange(0, 100);
 * disks.setFormat(ValueFormat.PERCENT);
 * disks.setThresholds(Threshold.utilisation(75, 90));
 * disks.setRows(List.of(new BarGauge.Row("/", 62), new BarGauge.Row("/mnt/nas", 91)));
 * }</pre>
 */
public final class BarGauge extends Div {

    /** How the filled portion is drawn. */
    public enum Display {
        /** One solid bar in the threshold colour. */
        BASIC,
        /**
         * Discrete segments, each lit in the colour of the threshold it falls in — the
         * retro-LCD look. Reading a level off segments is quicker than off a bar edge.
         */
        LCD,
        /** A solid bar that fades from the base colour to the reached threshold colour. */
        GRADIENT
    }

    public enum Orientation { HORIZONTAL, VERTICAL }

    /** One meter: a caption and its current value. */
    public record Row(String label, double value) {
    }

    private final List<Row> rows = new ArrayList<>();
    private double min;
    private double max = 100;
    private ValueFormat format = ValueFormat.AUTO;
    private List<Threshold> thresholds;
    private Display display = Display.BASIC;
    private Orientation orientation = Orientation.HORIZONTAL;
    private int segments = 22;
    private String labelWidth = "7rem";
    private String barHeight = "1.15rem";
    private int verticalHeight = 130;

    public BarGauge() {
        addClassName("flex w-full flex-col gap-2 text-base-content");
    }

    // ------------------------------------------------------------------ public API

    public BarGauge setRows(List<Row> newRows) {
        rows.clear();
        if (newRows != null) {
            rows.addAll(newRows);
        }
        render();
        return this;
    }

    public BarGauge addRow(String label, double value) {
        rows.add(new Row(label, value));
        render();
        return this;
    }

    public BarGauge clearRows() {
        rows.clear();
        render();
        return this;
    }

    /** The shared scale every row is measured against. */
    public BarGauge setRange(double newMin, double newMax) {
        this.min = newMin;
        this.max = newMax > newMin ? newMax : newMin + 1;
        render();
        return this;
    }

    /** Scales to the data: zero to the largest row, rounded out to a nice number. */
    public BarGauge autoRange() {
        double largest = 0;
        for (Row row : rows) {
            if (!Double.isNaN(row.value())) {
                largest = Math.max(largest, row.value());
            }
        }
        double[] bounds = Scales.niceBounds(0, largest, 4);
        return setRange(0, bounds[1]);
    }

    public BarGauge setFormat(ValueFormat newFormat) {
        this.format = newFormat == null ? ValueFormat.AUTO : newFormat;
        render();
        return this;
    }

    public BarGauge setThresholds(List<Threshold> steps) {
        this.thresholds = steps;
        render();
        return this;
    }

    public BarGauge setDisplay(Display newDisplay) {
        this.display = newDisplay == null ? Display.BASIC : newDisplay;
        render();
        return this;
    }

    public BarGauge setOrientation(Orientation newOrientation) {
        this.orientation = newOrientation == null ? Orientation.HORIZONTAL : newOrientation;
        render();
        return this;
    }

    /** Segment count in {@link Display#LCD}. Default 22. */
    public BarGauge setSegments(int count) {
        this.segments = Math.max(4, Math.min(80, count));
        render();
        return this;
    }

    /** CSS width of the label gutter in horizontal layout, e.g. {@code "9rem"}. */
    public BarGauge setLabelWidth(String cssWidth) {
        this.labelWidth = cssWidth;
        render();
        return this;
    }

    /** CSS height of each bar in horizontal layout. */
    public BarGauge setBarHeight(String cssHeight) {
        this.barHeight = cssHeight;
        render();
        return this;
    }

    /** Column height in pixels for vertical layout. Default 130. */
    public BarGauge setVerticalHeight(int pixels) {
        this.verticalHeight = Math.max(40, pixels);
        render();
        return this;
    }

    // --------------------------------------------------------------------- render

    private void render() {
        removeAll();
        if (rows.isEmpty()) {
            Div empty = new Div("No data");
            empty.addClassName("py-4 text-center " + TextStyle.SECONDARY.getClassNames());
            add(empty);
            return;
        }
        if (orientation == Orientation.VERTICAL) {
            setClassName("flex w-full items-end gap-3 text-base-content");
            for (Row row : rows) {
                add(verticalRow(row));
            }
        } else {
            setClassName("flex w-full flex-col gap-2 text-base-content");
            for (Row row : rows) {
                add(horizontalRow(row));
            }
        }
    }

    private Div horizontalRow(Row row) {
        Div line = new Div();
        line.addClassName("flex items-center gap-3");

        Div label = new Div(row.label());
        label.addClassName("shrink-0 truncate text-right " + TextStyle.CAPTION.getClassNames());
        label.setStyle("width", labelWidth);
        label.getElement().setAttribute("title", row.label());

        Div track = new Div();
        track.addClassName("relative flex-1 overflow-hidden rounded bg-base-300/60");
        track.setStyle("height", barHeight);
        fill(track, row.value(), false);

        Div value = new Div(Double.isNaN(row.value()) ? "-" : format.format(row.value()));
        value.addClassName("w-20 shrink-0 text-right font-mono font-semibold " + TextStyle.CAPTION.getClassNames(Emphasis.FULL));
        value.setStyle("color", Threshold.colorFor(thresholds, row.value(), Palette.BASE_CONTENT));

        line.add(label, track, value);
        return line;
    }

    private Div verticalRow(Row row) {
        Div column = new Div();
        column.addClassName("flex min-w-0 flex-1 flex-col items-center gap-1");

        Div value = new Div(Double.isNaN(row.value()) ? "-" : format.format(row.value()));
        value.addClassName("font-mono font-semibold " + TextStyle.CAPTION.getClassNames(Emphasis.FULL));
        value.setStyle("color", Threshold.colorFor(thresholds, row.value(), Palette.BASE_CONTENT));

        Div track = new Div();
        track.addClassName("relative w-full overflow-hidden rounded bg-base-300/60");
        track.setStyle("height", verticalHeight + "px");
        fill(track, row.value(), true);

        Div label = new Div(row.label());
        label.addClassName("w-full truncate text-center " + TextStyle.CAPTION.getClassNames());
        label.getElement().setAttribute("title", row.label());

        column.add(value, track, label);
        return column;
    }

    private void fill(Div track, double value, boolean vertical) {
        double fraction = Double.isNaN(value) ? 0 : Scales.normalise(value, min, max);
        String color = Threshold.colorFor(thresholds, value, Palette.PRIMARY);

        if (display == Display.LCD) {
            Div cells = new Div();
            cells.addClassName(vertical
                ? "absolute inset-0 flex flex-col-reverse gap-px p-px"
                : "absolute inset-0 flex gap-px p-px");
            int lit = (int) Math.round(fraction * segments);
            for (int i = 0; i < segments; i++) {
                Div cell = new Div();
                cell.addClassName("flex-1 rounded-[1px]");
                if (i < lit) {
                    // Each segment takes the colour of the value it represents, so the bar
                    // shows where it crossed a threshold, not just where it ended up.
                    double atSegment = min + (max - min) * ((i + 0.5) / segments);
                    cell.setStyle("background-color", Threshold.colorFor(thresholds, atSegment, color));
                } else {
                    cell.addClassName("bg-base-content/10");
                }
                cells.add(cell);
            }
            track.add(cells);
            return;
        }

        Div bar = new Div();
        bar.addClassName(vertical
            ? "absolute bottom-0 left-0 w-full rounded transition-all duration-300"
            : "absolute left-0 top-0 h-full rounded transition-all duration-300");
        String extent = Scales.fixed(fraction * 100, 2) + "%";
        if (vertical) {
            bar.setStyle("height", extent);
        } else {
            bar.setStyle("width", extent);
        }
        if (display == Display.GRADIENT) {
            String base = thresholds == null || thresholds.isEmpty()
                ? Palette.PRIMARY
                : thresholds.get(0).color();
            bar.setStyle("background",
                "linear-gradient(" + (vertical ? "to top" : "to right") + ", " + base + ", " + color + ")");
        } else {
            bar.setStyle("background-color", color);
        }
        track.add(bar);
    }
}
