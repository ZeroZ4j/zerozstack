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

/**
 * One named line of numbers plus how to draw it.
 *
 * <p>The chart set uses the <em>aligned</em> data model: a chart holds one shared x axis
 * (timestamps or categories) and every series carries a {@code values} array indexed the
 * same way. This is how uPlot models a chart, and it is what makes a shared crosshair, a
 * live legend and stacking cheap — no per-point objects, no per-series x lookup.</p>
 *
 * <p>A {@code NaN} value is a gap: the line breaks rather than interpolating across it,
 * so a probe outage reads as missing data instead of a straight line through it.</p>
 */
public final class Series {

    private final String name;
    private double[] values;
    private String color;
    private boolean filled;
    private boolean dashed;
    private boolean stepped;
    private boolean points;
    private boolean hidden;
    private double strokeWidth = 1.75;
    private double fillOpacity = 0.14;

    public Series(String name, double... values) {
        this.name = name;
        this.values = values == null ? new double[0] : values;
    }

    public String name() {
        return name;
    }

    public double[] values() {
        return values;
    }

    public Series values(double... newValues) {
        this.values = newValues == null ? new double[0] : newValues;
        return this;
    }

    public double valueAt(int index) {
        return index >= 0 && index < values.length ? values[index] : Double.NaN;
    }

    public int size() {
        return values.length;
    }

    /** Explicit colour; leave unset to take the next colour from {@link Palette}. */
    public Series color(String cssColor) {
        this.color = cssColor;
        return this;
    }

    /** Resolves to the explicit colour, or the palette entry for this series' position. */
    public String colorOr(int index) {
        return color != null ? color : Palette.series(index);
    }

    /** Draws a translucent area between the line and the baseline. */
    public Series filled(boolean value) {
        this.filled = value;
        return this;
    }

    public Series filled() {
        return filled(true);
    }

    public boolean isFilled() {
        return filled;
    }

    public Series fillOpacity(double value) {
        this.fillOpacity = value;
        return this;
    }

    public double fillOpacity() {
        return fillOpacity;
    }

    public Series dashed(boolean value) {
        this.dashed = value;
        return this;
    }

    /** A dashed line reads as "derived" — a limit, a forecast, a target. */
    public Series dashed() {
        return dashed(true);
    }

    public boolean isDashed() {
        return dashed;
    }

    /**
     * Holds each value until the next sample instead of sloping between them. Correct for
     * anything that changes discretely — a queue depth, a replica count, a setting.
     */
    public Series stepped(boolean value) {
        this.stepped = value;
        return this;
    }

    public Series stepped() {
        return stepped(true);
    }

    public boolean isStepped() {
        return stepped;
    }

    /** Marks each sample with a dot. Worth it under about 40 points, noise above it. */
    public Series points(boolean value) {
        this.points = value;
        return this;
    }

    public Series points() {
        return points(true);
    }

    public boolean hasPoints() {
        return points;
    }

    /** Hidden series keep their palette slot and their legend entry, but are not drawn. */
    public Series hidden(boolean value) {
        this.hidden = value;
        return this;
    }

    public boolean isHidden() {
        return hidden;
    }

    public Series strokeWidth(double value) {
        this.strokeWidth = value;
        return this;
    }

    public double strokeWidth() {
        return strokeWidth;
    }

    /** True when every value is {@code NaN} — nothing to draw and nothing to scale to. */
    public boolean isEmpty() {
        for (double value : values) {
            if (!Double.isNaN(value)) {
                return false;
            }
        }
        return true;
    }
}
