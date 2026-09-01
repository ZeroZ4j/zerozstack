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
import com.zeroz4j.ui.theme.TextStyle;
import java.util.ArrayList;
import java.util.List;

/**
 * The key for a colour-encoded chart: either a continuous ramp with end labels, or a set of
 * discrete threshold swatches.
 *
 * <p>A heatmap or treemap without one is a picture, not a measurement — the reader can see
 * that a cell is hotter, but not by how much. Kept as its own component so a panel can place
 * it once for several charts that share a scale.</p>
 *
 * <pre>{@code
 * ColorScaleLegend key = new ColorScaleLegend();
 * key.setRamp(Palette.VIRIDIS).setRange(0, peak).setFormat(ValueFormat.INTEGER);
 * }</pre>
 */
public final class ColorScaleLegend extends Div {

    private static final int GRADIENT_STEPS = 40;

    public enum Orientation { HORIZONTAL, VERTICAL }

    private int[] ramp = Palette.HEAT;
    private List<Threshold> thresholds;
    private double min;
    private double max = 100;
    private ValueFormat format = ValueFormat.AUTO;
    private Orientation orientation = Orientation.HORIZONTAL;
    private String caption;
    private int thickness = 10;
    private int length = 160;

    public ColorScaleLegend() {
        render();
    }

    // ------------------------------------------------------------------ public API

    /** Switches to continuous mode with this ramp. */
    public ColorScaleLegend setRamp(int[] colourStops) {
        this.ramp = colourStops == null || colourStops.length == 0 ? Palette.HEAT : colourStops;
        this.thresholds = null;
        render();
        return this;
    }

    /** Switches to discrete mode: one swatch per threshold step. */
    public ColorScaleLegend setThresholds(List<Threshold> steps) {
        this.thresholds = steps;
        render();
        return this;
    }

    public ColorScaleLegend setRange(double newMin, double newMax) {
        this.min = newMin;
        this.max = newMax > newMin ? newMax : newMin + 1;
        render();
        return this;
    }

    public ColorScaleLegend setFormat(ValueFormat newFormat) {
        this.format = newFormat == null ? ValueFormat.AUTO : newFormat;
        render();
        return this;
    }

    public ColorScaleLegend setOrientation(Orientation newOrientation) {
        this.orientation = newOrientation == null ? Orientation.HORIZONTAL : newOrientation;
        render();
        return this;
    }

    /** A caption before the scale, e.g. the unit being encoded. */
    public ColorScaleLegend setCaption(String text) {
        this.caption = text;
        render();
        return this;
    }

    /** Bar thickness in pixels. */
    public ColorScaleLegend setThickness(int pixels) {
        this.thickness = Math.max(4, pixels);
        render();
        return this;
    }

    /** Bar length in pixels along its orientation. */
    public ColorScaleLegend setLength(int pixels) {
        this.length = Math.max(40, pixels);
        render();
        return this;
    }

    // --------------------------------------------------------------------- render

    private void render() {
        removeAll();
        boolean vertical = orientation == Orientation.VERTICAL;
        setClassName((vertical
            ? "flex flex-col items-center gap-1.5 "
            : "flex items-center gap-2 ") + TextStyle.CAPTION.getClassNames());

        if (caption != null && !caption.isEmpty()) {
            Div label = new Div(caption);
            label.addClassName("shrink-0");
            add(label);
        }

        if (thresholds != null && !thresholds.isEmpty()) {
            renderThresholds(vertical);
        } else {
            renderRamp(vertical);
        }
    }

    private void renderRamp(boolean vertical) {
        Div low = new Div(format.format(min));
        low.addClassName("shrink-0 font-mono");

        Div bar = new Div();
        bar.addClassName(vertical ? "flex flex-col-reverse overflow-hidden rounded"
            : "flex overflow-hidden rounded");
        if (vertical) {
            bar.setStyle("width", thickness + "px");
            bar.setStyle("height", length + "px");
        } else {
            bar.setStyle("width", length + "px");
            bar.setStyle("height", thickness + "px");
        }
        // Discrete steps rather than a CSS gradient: the ramp is interpolated in sRGB by
        // Palette, and letting CSS interpolate instead would give a visibly different scale
        // from the one the chart painted.
        for (int i = 0; i < GRADIENT_STEPS; i++) {
            Div step = new Div();
            step.addClassName("flex-1");
            step.setStyle("background-color", Palette.ramp(ramp, (i + 0.5) / GRADIENT_STEPS));
            bar.add(step);
        }

        Div high = new Div(format.format(max));
        high.addClassName("shrink-0 font-mono");

        if (vertical) {
            add(high, bar, low);
        } else {
            add(low, bar, high);
        }
    }

    private void renderThresholds(boolean vertical) {
        Div swatches = new Div();
        swatches.addClassName(vertical ? "flex flex-col gap-1" : "flex flex-wrap items-center gap-3");
        List<Threshold> steps = new ArrayList<>(thresholds);
        for (int i = 0; i < steps.size(); i++) {
            Threshold step = steps.get(i);
            Div entry = new Div();
            entry.addClassName("flex items-center gap-1.5");
            Div swatch = new Div();
            swatch.addClassName("h-2.5 w-2.5 shrink-0 rounded-sm");
            swatch.setStyle("background-color", step.color());
            String bound = Double.isInfinite(step.from())
                ? "< " + format.format(steps.size() > 1 ? steps.get(1).from() : max)
                : format.format(step.from()) + " +";
            Div text = new Div(step.label() != null && !step.label().equals(Scales.compact(step.from()))
                ? step.label() + "  " + bound
                : bound);
            entry.add(swatch, text);
            swatches.add(entry);
        }
        add(swatches);
    }
}
