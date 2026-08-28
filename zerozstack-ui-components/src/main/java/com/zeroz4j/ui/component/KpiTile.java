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

import com.zeroz4j.ui.theme.Emphasis;
import com.zeroz4j.ui.theme.TextStyle;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.Span;

/**
 * Dashboard stat tile: label, big value, an optional movement line and a trend sparkline.
 *
 * <p>A row of these across the top of a console is what an operator reads first, before any chart.
 * The movement line is the part that earns its space — a number on its own says where you are, and
 * the delta says which way you are going, which is usually the actual question.</p>
 *
 * <pre>{@code
 * KpiTile memory = new KpiTile("Unified memory available");
 * memory.value("24.6", "GB");
 * memory.setDirection(KpiTile.Direction.UP_IS_GOOD);
 * memory.setDelta(24.6, 32.8, " GB");   // "8.2 GB (25.0%)" with a down arrow, in red
 * memory.trend(history);
 * }</pre>
 *
 * <p>Direction is a judgement, not arithmetic: falling free memory is bad, falling latency is good.
 * {@link #setDirection} says which, and defaults to up-is-good.</p>
 */
public final class KpiTile extends Div {

    /** Whether a rise in this metric is good news, bad news, or neither. */
    public enum Direction {
        /** A rise is coloured success, a fall error. The default. */
        UP_IS_GOOD,
        /** A rise is coloured error, a fall success — free space, latency, error rate. */
        DOWN_IS_GOOD,
        /** Movement is shown with an arrow but never coloured. */
        NEUTRAL
    }

    private static final String UP = "▲";
    private static final String DOWN = "▼";

    private final Div valueRow = new Div();
    private final Span valueText = new Span();
    private final Span unitText = new Span();
    private final Div delta = new Div();
    private final Sparkline trend = new Sparkline(120, 28);
    private final Div trendWrap = new Div();

    private Direction direction = Direction.UP_IS_GOOD;

    public KpiTile(String label) {
        addClassName("rounded-xl border border-base-300 bg-base-200/50 p-4 flex flex-col gap-1 "
            + "min-w-[10rem]");
        // A metric's name and its value both break rather than setting the tile's width. A metric
        // named after a path or an identifier has no spaces in it, and without this one tile made
        // the whole page scroll sideways.
        getElement().getStyle().setProperty("overflow-wrap", "anywhere");
        getElement().getStyle().setProperty("max-width", "100%");
        Div labelDiv = new Div(label);
        labelDiv.addClassName(TextStyle.CAPTION.getClassNames() + " uppercase tracking-wide");

        valueRow.addClassName("flex items-baseline gap-1");
        valueText.addClassName("text-2xl font-bold font-mono");
        unitText.addClassName(TextStyle.SECONDARY.getClassNames(Emphasis.FAINT));
        unitText.addClassName("hidden");
        valueRow.add(valueText, unitText);

        delta.addClassName(TextStyle.CAPTION.getClassNames());
        trendWrap.addClassName("text-primary mt-1");
        trendWrap.add(trend);
        add(labelDiv, valueRow, delta, trendWrap);
    }

    // ------------------------------------------------------------------ public API

    public KpiTile value(String text) {
        valueText.setText(text);
        return this;
    }

    /** The value with a unit set in smaller, dimmer type beside it. */
    public KpiTile value(String text, String unit) {
        valueText.setText(text);
        return setUnit(unit);
    }

    public KpiTile setUnit(String unit) {
        unitText.setText(unit == null ? "" : unit);
        if (unit == null || unit.isEmpty()) {
            unitText.addClassName("hidden");
        } else {
            unitText.removeClassName("hidden");
        }
        return this;
    }

    /** Colours the figure itself — pair with {@code Threshold.colorFor(...)}. */
    public KpiTile setValueColor(String cssColor) {
        if (cssColor == null || cssColor.isEmpty()) {
            valueText.removeStyle("color");
        } else {
            valueText.setStyle("color", cssColor);
        }
        return this;
    }

    /** Movement text you have already composed. {@code positive} chooses success or error. */
    public KpiTile delta(String text, boolean positive) {
        delta.setText(text);
        delta.setClassName(TextStyle.CAPTION.getClassNames(Emphasis.FULL) + " "
                + (positive ? "text-success" : "text-error"));
        return this;
    }

    /**
     * Computes the movement from {@code current} against {@code previous}: absolute change, the
     * percentage, and a direction arrow, coloured according to {@link #setDirection}.
     */
    public KpiTile setDelta(double current, double previous, String unit) {
        if (Double.isNaN(current) || Double.isNaN(previous)) {
            return clearDelta();
        }
        double change = current - previous;
        StringBuilder text = new StringBuilder()
            .append(change >= 0 ? UP : DOWN)
            .append(' ')
            .append(fixed(Math.abs(change), 1))
            .append(unit == null ? "" : unit);
        if (previous != 0) {
            text.append(" (").append(fixed(Math.abs(change / previous) * 100, 1)).append("%)");
        }
        delta.setText(text.toString());
        delta.setClassName(TextStyle.CAPTION.getClassNames(emphasisFor(change)) + " "
                + colorClassFor(change));
        return this;
    }

    public KpiTile setDelta(double current, double previous) {
        return setDelta(current, previous, "");
    }

    /** Removes the movement line — for a tile with no comparison point yet. */
    public KpiTile clearDelta() {
        delta.setText("");
        delta.setClassName(TextStyle.CAPTION.getClassNames());
        return this;
    }

    /** Whether a rise in this metric is good, bad or neither. Default {@link Direction#UP_IS_GOOD}. */
    public KpiTile setDirection(Direction newDirection) {
        this.direction = newDirection == null ? Direction.UP_IS_GOOD : newDirection;
        return this;
    }

    public KpiTile trend(double[] values) {
        trend.setValues(values);
        return this;
    }

    /** The trend chart, so its mode, markers and colour can be configured. */
    public Sparkline sparkline() {
        return trend;
    }

    /** Hides the trend chart, for a tile that has no history to show. */
    public KpiTile setTrendVisible(boolean visible) {
        trendWrap.setVisible(visible);
        return this;
    }

    // -------------------------------------------------------------------- internals

    private String colorClassFor(double change) {
        if (change == 0 || direction == Direction.NEUTRAL) {
            return "";
        }
        boolean good = direction == Direction.UP_IS_GOOD ? change > 0 : change < 0;
        return good ? "text-success" : "text-error";
    }

    /** Movement with no direction to it is quiet; a rise or a fall the reader should see is not. */
    private Emphasis emphasisFor(double change) {
        return change == 0 || direction == Direction.NEUTRAL ? Emphasis.FAINT : Emphasis.FULL;
    }

    /** {@code String.format} does not exist under TeaVM, so round by hand. */
    private static String fixed(double value, int decimals) {
        double scale = Math.pow(10, decimals);
        long scaled = Math.round(value * scale);
        String digits = String.valueOf(scaled);
        if (decimals == 0) {
            return digits;
        }
        while (digits.length() <= decimals) {
            digits = "0" + digits;
        }
        int split = digits.length() - decimals;
        return digits.substring(0, split) + "." + digits.substring(split);
    }
}
