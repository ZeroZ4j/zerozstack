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

import org.teavm.jso.core.JSDate;

/**
 * Axis mathematics and formatting for the chart set.
 *
 * <p>Two things every chart needs and nobody should hand-roll twice: <em>nice</em> tick
 * selection (the 1/2/5 x 10^k rule, the same rule d3-array uses) and number formatting
 * that works under TeaVM. {@code String.format} is not available in the TeaVM classlib,
 * and {@code Double.toString} flips to scientific notation at the extremes, so every
 * number here is rendered by integer arithmetic.</p>
 *
 * <p>Time ticks are chosen from a fixed ladder of human step sizes (1s, 5s, 30s, 1m, 5m,
 * 1h, 6h, 1d, ...) and aligned in <em>local</em> time via {@link JSDate}, so an hourly
 * axis lands on the hour in the viewer's zone rather than on the hour in UTC.</p>
 */
public final class Scales {

    /** Human step sizes for a time axis, ascending, in milliseconds. */
    private static final long[] TIME_STEPS = {
        1000L, 2000L, 5000L, 10_000L, 15_000L, 30_000L,
        60_000L, 120_000L, 300_000L, 600_000L, 900_000L, 1_800_000L,
        3_600_000L, 7_200_000L, 10_800_000L, 21_600_000L, 43_200_000L,
        86_400_000L, 172_800_000L, 604_800_000L
    };

    private static final long MINUTE = 60_000L;
    private static final long HOUR = 3_600_000L;
    private static final long DAY = 86_400_000L;

    private static final String[] MONTHS = {
        "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    };

    private Scales() {
    }

    // ---------------------------------------------------------------- linear ticks

    /**
     * The nicest step that yields roughly {@code count} intervals across the span:
     * always 1, 2, 5 or 10 times a power of ten.
     */
    public static double tickStep(double min, double max, int count) {
        double span = max - min;
        if (span <= 0 || count <= 0 || Double.isNaN(span) || Double.isInfinite(span)) {
            return 1;
        }
        double raw = span / count;
        double magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
        double normalised = raw / magnitude;
        double step = normalised < 1.5 ? 1 : normalised < 3 ? 2 : normalised < 7 ? 5 : 10;
        return step * magnitude;
    }

    /** Tick values inside [min, max] at the nice step, inclusive of both boundaries when they land on one. */
    public static double[] ticks(double min, double max, int count) {
        if (max <= min) {
            return new double[] { min };
        }
        double step = tickStep(min, max, count);
        double first = Math.ceil(min / step) * step;
        // Guard against the float error that would otherwise emit a tick a hair outside the range.
        double epsilon = step * 1e-9;
        int n = (int) Math.floor((max - first) / step + epsilon) + 1;
        if (n < 1) {
            return new double[] { min };
        }
        if (n > 200) {
            n = 200;
        }
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            out[i] = first + i * step;
        }
        return out;
    }

    /**
     * Expands [min, max] outwards to the enclosing tick boundaries, so the axis ends on a
     * round number rather than on whatever the data happened to reach.
     * Returns {@code {niceMin, niceMax}}.
     */
    public static double[] niceBounds(double min, double max, int count) {
        if (Double.isNaN(min) || Double.isNaN(max)) {
            return new double[] { 0, 1 };
        }
        if (max == min) {
            // A flat series still needs a visible band around it.
            double pad = Math.abs(min) < 1e-9 ? 1 : Math.abs(min) * 0.1;
            min -= pad;
            max += pad;
        }
        double step = tickStep(min, max, count);
        return new double[] {
            Math.floor(min / step) * step,
            Math.ceil(max / step) * step
        };
    }

    /** Decimal places a tick label needs so that consecutive ticks stay distinguishable. */
    public static int decimalsFor(double step) {
        if (step <= 0 || Double.isNaN(step) || Double.isInfinite(step)) {
            return 0;
        }
        int decimals = (int) Math.ceil(-Math.log10(step) + 1e-9);
        return decimals < 0 ? 0 : decimals > 6 ? 6 : decimals;
    }

    // ------------------------------------------------------------------ time ticks

    /** The nicest time step for roughly {@code count} intervals, from the human ladder. */
    public static long timeStep(long from, long to, int count) {
        long span = to - from;
        if (span <= 0 || count <= 0) {
            return 1000L;
        }
        long target = span / count;
        for (long candidate : TIME_STEPS) {
            if (candidate >= target) {
                return candidate;
            }
        }
        // Beyond a week, fall back to whole-day multiples.
        long days = Math.max(1, target / DAY);
        return days * DAY;
    }

    /** Time ticks inside [from, to], aligned to the step in the viewer's local time zone. */
    public static long[] timeTicks(long from, long to, int count) {
        if (to <= from) {
            return new long[] { from };
        }
        long step = timeStep(from, to, count);
        long offset = zoneOffsetMillis(from);
        long localFrom = from - offset;
        long first = ceilDiv(localFrom, step) * step + offset;
        int n = (int) ((to - first) / step) + 1;
        if (n < 1) {
            return new long[] { from };
        }
        if (n > 200) {
            n = 200;
        }
        long[] out = new long[n];
        for (int i = 0; i < n; i++) {
            out[i] = first + i * step;
        }
        return out;
    }

    /**
     * Local zone offset at an instant, in milliseconds, positive east of Greenwich.
     * {@code getTimezoneOffset} reports UTC-minus-local in minutes, so the sign flips.
     */
    private static long zoneOffsetMillis(long epochMillis) {
        return -((long) JSDate.create((double) epochMillis).getTimezoneOffset()) * MINUTE;
    }

    private static long ceilDiv(long value, long divisor) {
        long quotient = value / divisor;
        return (value % divisor != 0 && value > 0) ? quotient + 1 : quotient;
    }

    // ------------------------------------------------------------------ formatting

    /**
     * Fixed-decimal rendering built from integer arithmetic, because {@code String.format}
     * does not exist under TeaVM and {@code Double.toString} produces {@code 1.0E-4}.
     */
    public static String fixed(double value, int decimals) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "-";
        }
        if (decimals < 0) {
            decimals = 0;
        }
        boolean negative = value < 0;
        double magnitude = Math.abs(value);
        long scaled = Math.round(magnitude * pow10(decimals));
        String digits = String.valueOf(scaled);
        String rendered;
        if (decimals == 0) {
            rendered = digits;
        } else {
            while (digits.length() <= decimals) {
                digits = "0" + digits;
            }
            int split = digits.length() - decimals;
            rendered = digits.substring(0, split) + "." + digits.substring(split);
        }
        return negative && scaled != 0 ? "-" + rendered : rendered;
    }

    /** A tick label with just enough precision for the given step. */
    public static String tickLabel(double value, double step) {
        return fixed(value, decimalsFor(step));
    }

    /** 1.2K / 3.4M / 1.1G — one decimal, dropped when it would read {@code .0}. */
    public static String compact(double value) {
        double magnitude = Math.abs(value);
        if (magnitude >= 1e12) {
            return trimZero(fixed(value / 1e12, 1)) + "T";
        }
        if (magnitude >= 1e9) {
            return trimZero(fixed(value / 1e9, 1)) + "G";
        }
        if (magnitude >= 1e6) {
            return trimZero(fixed(value / 1e6, 1)) + "M";
        }
        if (magnitude >= 1000) {
            return trimZero(fixed(value / 1000, 1)) + "K";
        }
        if (magnitude >= 10 || magnitude == 0) {
            return fixed(value, 0);
        }
        return trimZero(fixed(value, magnitude >= 1 ? 1 : 2));
    }

    /** Binary sizes: 4.2 GB, 610 MB, 12 KB. */
    public static String bytes(double byteCount) {
        double magnitude = Math.abs(byteCount);
        if (magnitude >= 1024.0 * 1024 * 1024 * 1024) {
            return trimZero(fixed(byteCount / (1024.0 * 1024 * 1024 * 1024), 1)) + " TB";
        }
        if (magnitude >= 1024.0 * 1024 * 1024) {
            return trimZero(fixed(byteCount / (1024.0 * 1024 * 1024), 1)) + " GB";
        }
        if (magnitude >= 1024.0 * 1024) {
            return trimZero(fixed(byteCount / (1024.0 * 1024), 1)) + " MB";
        }
        if (magnitude >= 1024) {
            return trimZero(fixed(byteCount / 1024.0, 1)) + " KB";
        }
        return fixed(byteCount, 0) + " B";
    }

    private static String trimZero(String rendered) {
        return rendered.endsWith(".0") ? rendered.substring(0, rendered.length() - 2) : rendered;
    }

    /**
     * A clock label whose resolution follows the visible span: seconds under an hour,
     * minutes under a day, day-and-month beyond that.
     */
    public static String clock(long epochMillis, long spanMillis) {
        JSDate date = JSDate.create((double) epochMillis);
        if (spanMillis >= 7 * DAY) {
            return date.getDate() + " " + MONTHS[date.getMonth()];
        }
        if (spanMillis >= DAY) {
            return date.getDate() + " " + MONTHS[date.getMonth()] + " " + pad2(date.getHours()) + ":" + pad2(date.getMinutes());
        }
        if (spanMillis >= HOUR) {
            return pad2(date.getHours()) + ":" + pad2(date.getMinutes());
        }
        return pad2(date.getHours()) + ":" + pad2(date.getMinutes()) + ":" + pad2(date.getSeconds());
    }

    /** Full local timestamp, for tooltips where the axis label is too terse. */
    public static String timestamp(long epochMillis) {
        JSDate date = JSDate.create((double) epochMillis);
        return date.getDate() + " " + MONTHS[date.getMonth()] + " "
            + pad2(date.getHours()) + ":" + pad2(date.getMinutes()) + ":" + pad2(date.getSeconds());
    }

    /** Elapsed time as 2d 4h / 3h 12m / 5m 08s / 900ms. */
    public static String duration(long millis) {
        long magnitude = Math.abs(millis);
        if (magnitude < 1000) {
            return millis + "ms";
        }
        long seconds = magnitude / 1000;
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m " + pad2((int) (seconds % 60)) + "s";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + "h " + pad2((int) (minutes % 60)) + "m";
        }
        return (hours / 24) + "d " + (hours % 24) + "h";
    }

    private static String pad2(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    private static double pow10(int exponent) {
        double result = 1;
        for (int i = 0; i < exponent; i++) {
            result *= 10;
        }
        return result;
    }

    // ------------------------------------------------------------------- mapping

    /** Position of {@code value} within [min, max] as 0..1, clamped. */
    public static double normalise(double value, double min, double max) {
        if (max == min) {
            return 0;
        }
        return clamp((value - min) / (max - min), 0, 1);
    }

    public static double clamp(double value, double min, double max) {
        return value < min ? min : value > max ? max : value;
    }

    /** Rounds to one decimal — SVG coordinates do not need more, and shorter attributes redraw faster. */
    public static double px(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
