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

import java.util.ArrayList;
import java.util.List;

/**
 * A value band and the colour it paints, shared by every component that turns a number
 * into a judgement: {@code Gauge}, {@code BarGauge}, {@code MetricTable}, and the
 * threshold bands drawn behind a {@code TimeSeriesChart}.
 *
 * <p>A threshold set is a base colour plus ascending steps. The colour for a value is the
 * last step whose {@code from} it has reached, so {@code steps(70 warning, 90 error)} on a
 * base of success paints green to 70, amber to 90, red above.</p>
 */
public final class Threshold {

    private final double from;
    private final String color;
    private final String label;

    public Threshold(double from, String color) {
        this(from, color, null);
    }

    public Threshold(double from, String color, String label) {
        this.from = from;
        this.color = color;
        this.label = label;
    }

    public double from() {
        return from;
    }

    public String color() {
        return color;
    }

    /** Optional legend caption; falls back to the boundary value when absent. */
    public String label() {
        return label != null ? label : Scales.compact(from);
    }

    /**
     * The conventional set for a utilisation metric: green, amber from {@code warnAt},
     * red from {@code errorAt}. Ordered and ready to hand to any threshold-aware component.
     */
    public static List<Threshold> utilisation(double warnAt, double errorAt) {
        List<Threshold> steps = new ArrayList<>();
        steps.add(new Threshold(Double.NEGATIVE_INFINITY, Palette.SUCCESS, "ok"));
        steps.add(new Threshold(warnAt, Palette.WARNING, "warning"));
        steps.add(new Threshold(errorAt, Palette.ERROR, "critical"));
        return steps;
    }

    /** A single flat colour, for components that want thresholds switched off. */
    public static List<Threshold> flat(String color) {
        List<Threshold> steps = new ArrayList<>();
        steps.add(new Threshold(Double.NEGATIVE_INFINITY, color));
        return steps;
    }

    /**
     * The colour for {@code value}: the last step it has reached. Steps are assumed to be in
     * ascending order; {@code fallback} is returned for an empty or null set.
     */
    public static String colorFor(List<Threshold> steps, double value, String fallback) {
        if (steps == null || steps.isEmpty()) {
            return fallback;
        }
        String chosen = fallback;
        for (Threshold step : steps) {
            if (value >= step.from()) {
                chosen = step.color();
            }
        }
        return chosen;
    }

    /** Steps that fall strictly inside (min, max) — the ones worth drawing as a marker line. */
    public static List<Threshold> within(List<Threshold> steps, double min, double max) {
        List<Threshold> visible = new ArrayList<>();
        if (steps == null) {
            return visible;
        }
        for (Threshold step : steps) {
            if (step.from() > min && step.from() < max && !Double.isInfinite(step.from())) {
                visible.add(step);
            }
        }
        return visible;
    }
}
