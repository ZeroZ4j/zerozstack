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

/**
 * Chart colours.
 *
 * <p><b>Categorical series</b> resolve to DaisyUI theme tokens ({@code var(--color-primary)}
 * and friends) rather than literal hex. Written into an SVG {@code stroke} or {@code fill}
 * attribute they are resolved by the browser at paint time, so switching
 * {@code data-theme} recolours every chart on the page with no redraw and no listener.</p>
 *
 * <p><b>Continuous ramps</b> cannot work that way — a heatmap needs a perceptually ordered
 * sequence, and theme tokens are chosen for contrast against the surface, not for ordering.
 * Those are literal sRGB stops interpolated per value, and they look the same in every
 * theme by design: a "hot" cell must read as hot in light mode too.</p>
 */
public final class Palette {

    /**
     * Categorical series colours, in assignment order. Primary first so a single-series
     * chart picks up the app's own accent without any configuration.
     *
     * <p>Each carries a literal fallback. An application that does not load DaisyUI leaves
     * these custom properties undefined, and an undefined {@code var()} with no fallback makes
     * the whole attribute invalid — an SVG stroke would silently disappear. The fallback keeps
     * a chart legible anywhere; define the tokens to make it match your theme.</p>
     */
    public static final String[] SERIES = {
        "var(--color-primary, #4f46e5)",
        "var(--color-secondary, #db2777)",
        "var(--color-accent, #0d9488)",
        "var(--color-info, #0284c7)",
        "var(--color-success, #16a34a)",
        "var(--color-warning, #d97706)",
        "var(--color-error, #dc2626)",
        "var(--color-neutral, #6b7280)"
    };

    public static final String BASE_CONTENT = "var(--color-base-content, currentColor)";
    public static final String BASE_100 = "var(--color-base-100, #ffffff)";
    public static final String BASE_200 = "var(--color-base-200, #f4f4f5)";
    public static final String BASE_300 = "var(--color-base-300, #d4d4d8)";
    public static final String PRIMARY = "var(--color-primary, #4f46e5)";
    public static final String INFO = "var(--color-info, #0284c7)";
    public static final String SUCCESS = "var(--color-success, #16a34a)";
    public static final String WARNING = "var(--color-warning, #d97706)";
    public static final String ERROR = "var(--color-error, #dc2626)";

    /** Cool-to-hot, for density and utilisation heatmaps. Turbo-like, ordered in lightness. */
    public static final int[] HEAT = {
        0x30123B, 0x4145AB, 0x4675ED, 0x39A2FC, 0x1BCFD4,
        0x24ECA6, 0x61FC6C, 0xA4FC3B, 0xD1E834, 0xF3C63A,
        0xFE9B2D, 0xF36315, 0xD93806, 0xB11901, 0x7A0403
    };

    /** Perceptually uniform and colour-vision-safe. The right default for scientific density. */
    public static final int[] VIRIDIS = {
        0x440154, 0x482878, 0x3E4A89, 0x31688E, 0x26828E,
        0x1F9E89, 0x35B779, 0x6DCD59, 0xB4DE2C, 0xFDE725
    };

    /** Single-hue, for "more is darker" without implying a threshold. */
    public static final int[] BLUES = {
        0xF7FBFF, 0xDEEBF7, 0xC6DBEF, 0x9ECAE1, 0x6BAED6,
        0x4292C6, 0x2171B5, 0x08519C, 0x08306B
    };

    private Palette() {
    }

    /** The categorical colour for a series index, cycling when there are more series than colours. */
    public static String series(int index) {
        if (index < 0) {
            index = -index;
        }
        return SERIES[index % SERIES.length];
    }

    /** Resolves a DaisyUI token name ("primary", "base-300", "success") to a CSS colour reference. */
    public static String token(String name) {
        return name == null || name.isEmpty() ? BASE_CONTENT : "var(--color-" + name + ")";
    }

    /**
     * Samples a ramp at {@code t} in 0..1, interpolating between the two nearest stops.
     * Returns a {@code #rrggbb} literal.
     */
    public static String ramp(int[] stops, double t) {
        if (stops == null || stops.length == 0) {
            return "#888888";
        }
        if (stops.length == 1 || Double.isNaN(t)) {
            return hex(stops[0]);
        }
        double position = Scales.clamp(t, 0, 1) * (stops.length - 1);
        int lower = (int) Math.floor(position);
        int upper = Math.min(lower + 1, stops.length - 1);
        return hex(mix(stops[lower], stops[upper], position - lower));
    }

    /** Convenience for the default heat ramp. */
    public static String heat(double t) {
        return ramp(HEAT, t);
    }

    private static int mix(int from, int to, double fraction) {
        int red = channel(from, 16) + (int) Math.round((channel(to, 16) - channel(from, 16)) * fraction);
        int green = channel(from, 8) + (int) Math.round((channel(to, 8) - channel(from, 8)) * fraction);
        int blue = channel(from, 0) + (int) Math.round((channel(to, 0) - channel(from, 0)) * fraction);
        return (red << 16) | (green << 8) | blue;
    }

    private static int channel(int rgb, int shift) {
        return (rgb >> shift) & 0xFF;
    }

    private static String hex(int rgb) {
        StringBuilder out = new StringBuilder("#");
        String digits = Integer.toHexString(rgb & 0xFFFFFF);
        for (int i = digits.length(); i < 6; i++) {
            out.append('0');
        }
        return out.append(digits).toString();
    }
}
