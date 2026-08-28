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
import org.teavm.jso.dom.xml.Element;

/**
 * The workhorse panel: one or more metrics over time, as lines, filled areas or a stack.
 *
 * <p>Data is <em>aligned</em> — one shared timestamp array plus one value array per series,
 * indexed the same way. That is what makes the shared crosshair and the live legend cheap:
 * a hover resolves to a single index and every series reads its value from it.</p>
 *
 * <pre>{@code
 * TimeSeriesChart chart = new TimeSeriesChart();
 * chart.setYFormat(ValueFormat.GIGABYTES);
 * chart.setData(timestamps,
 *     new Series("available", available).filled(),
 *     new Series("resident", resident));
 * }</pre>
 *
 * <p>A {@code NaN} is a gap, not a zero: the line breaks and the fill leaves a hole, so a
 * probe outage reads as missing rather than as a plunge to the floor.</p>
 */
public class TimeSeriesChart extends CartesianChart {

    private long[] timestamps = new long[0];
    private List<Series> series = new ArrayList<>();
    private boolean stacked;
    private boolean legendValues = true;
    private long fixedFrom = -1;
    private long fixedTo = -1;

    /** Pixel x of each sample, cached at draw time so hover can resolve without recomputing. */
    private double[] sampleX = new double[0];
    private double[][] plottedY = new double[0][];

    public TimeSeriesChart() {
        setChartHeight(200);
    }

    // ------------------------------------------------------------------ public API

    /** Replaces the data. {@code timestamps} is epoch millis, ascending. */
    public TimeSeriesChart setData(long[] newTimestamps, List<Series> newSeries) {
        this.timestamps = newTimestamps == null ? new long[0] : newTimestamps;
        this.series = newSeries == null ? new ArrayList<>() : newSeries;
        refresh();
        return this;
    }

    public TimeSeriesChart setData(long[] newTimestamps, Series... newSeries) {
        List<Series> list = new ArrayList<>();
        if (newSeries != null) {
            for (Series item : newSeries) {
                list.add(item);
            }
        }
        return setData(newTimestamps, list);
    }

    protected long[] timestamps() {
        return timestamps;
    }

    protected List<Series> series() {
        return series;
    }

    /**
     * Stacks the series, each drawn on top of the sum below it. Use for parts of a whole —
     * memory by process, requests by status class — never for independent metrics.
     */
    public TimeSeriesChart setStacked(boolean value) {
        this.stacked = value;
        refresh();
        return this;
    }

    /** Shows each series' latest value in the legend. */
    public TimeSeriesChart setLegendValues(boolean value) {
        this.legendValues = value;
        refresh();
        return this;
    }

    /**
     * Pins the visible time window. Without this the window is the extent of the data, which
     * makes a chart appear to zoom as samples accumulate.
     */
    public TimeSeriesChart setTimeRange(long from, long to) {
        this.fixedFrom = from;
        this.fixedTo = to;
        refresh();
        return this;
    }

    /** Reverts to auto-fitting the window to the data. */
    public TimeSeriesChart clearTimeRange() {
        this.fixedFrom = -1;
        this.fixedTo = -1;
        refresh();
        return this;
    }

    // -------------------------------------------------------------------- drawing

    @Override
    protected boolean hasData() {
        if (timestamps.length == 0 || series.isEmpty()) {
            return false;
        }
        for (Series item : series) {
            if (!item.isHidden() && !item.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void draw() {
        long from = fixedFrom >= 0 ? fixedFrom : timestamps[0];
        long to = fixedTo >= 0 ? fixedTo : timestamps[timestamps.length - 1];
        if (to <= from) {
            to = from + 1000;
        }

        double[][] tops = stacked ? cumulative() : rawValues();
        computeYScale(minOf(tops), maxOf(tops));
        drawYAxis();
        drawTimeAxis(from, to);

        sampleX = new double[timestamps.length];
        for (int i = 0; i < timestamps.length; i++) {
            sampleX[i] = xFor(timestamps[i]);
        }
        plottedY = new double[series.size()][];

        // Stacked areas are painted top band first so the lower bands overlay their edges
        // cleanly; unstacked series keep declaration order so the first is the most legible.
        for (int s = stacked ? series.size() - 1 : 0;
             stacked ? s >= 0 : s < series.size();
             s += stacked ? -1 : 1) {
            Series item = series.get(s);
            double[] ys = new double[timestamps.length];
            for (int i = 0; i < timestamps.length; i++) {
                double value = tops[s][i];
                ys[i] = Double.isNaN(value) ? Double.NaN : yFor(value);
            }
            plottedY[s] = ys;
            if (!item.isHidden()) {
                drawSeries(item, s, ys, tops);
            }
        }

        enableCrosshair();
        buildLegend(tops);
    }

    private void drawSeries(Series item, int index, double[] ys, double[][] tops) {
        String color = item.colorOr(index);

        if (stacked || item.isFilled()) {
            String areaData = stacked
                ? stackedAreaPath(index, ys, tops)
                : areaPath(sampleX, ys, yFor(Math.max(yScaleMin(), 0)), item.isStepped());
            if (!areaData.isEmpty()) {
                Element area = com.zeroz4j.ui.component.SvgCanvas.el("path",
                    "d", areaData, "fill", color, "stroke", "none");
                area.setAttribute("fill-opacity", String.valueOf(stacked ? 0.55 : item.fillOpacity()));
                add(area);
            }
        }

        Element stroke = path(linePath(sampleX, ys, item.isStepped()), color, item.strokeWidth());
        if (item.isDashed()) {
            stroke.setAttribute("stroke-dasharray", "5 4");
        }
        add(stroke);

        if (item.hasPoints()) {
            for (int i = 0; i < ys.length; i++) {
                if (!Double.isNaN(ys[i])) {
                    add(circle(sampleX[i], ys[i], 2.2, color));
                }
            }
        }
    }

    /** The band between this series' cumulative top and the one below it. */
    private String stackedAreaPath(int index, double[] topYs, double[][] tops) {
        double baseline = yFor(Math.max(yScaleMin(), 0));
        double[] bottomYs = new double[topYs.length];
        for (int i = 0; i < topYs.length; i++) {
            if (index == 0) {
                bottomYs[i] = baseline;
            } else {
                double below = tops[index - 1][i];
                bottomYs[i] = Double.isNaN(below) ? baseline : yFor(below);
            }
        }
        StringBuilder path = new StringBuilder();
        int runStart = -1;
        for (int i = 0; i <= topYs.length; i++) {
            boolean gap = i == topYs.length || Double.isNaN(topYs[i]);
            if (gap) {
                if (runStart >= 0 && i - runStart > 1) {
                    for (int j = i - 1; j >= runStart; j--) {
                        path.append('L').append(num(sampleX[j])).append(' ').append(num(bottomYs[j]));
                    }
                    path.append('Z');
                }
                runStart = -1;
                continue;
            }
            if (runStart < 0) {
                runStart = i;
                path.append('M').append(num(sampleX[i])).append(' ').append(num(topYs[i]));
            } else {
                path.append('L').append(num(sampleX[i])).append(' ').append(num(topYs[i]));
            }
        }
        return path.toString();
    }

    private void buildLegend(double[][] tops) {
        for (int s = 0; s < series.size(); s++) {
            Series item = series.get(s);
            String value = null;
            if (legendValues) {
                double latest = lastKnown(item.values());
                value = Double.isNaN(latest) ? "-" : yFormat().format(latest);
            }
            legend(item.name(), item.colorOr(s), value);
        }
    }

    // ---------------------------------------------------------------------- hover

    @Override
    protected void onPointerMove(double x, double y) {
        if (!hasData() || !isDrawn() || sampleX.length == 0 || !inPlot(x, y)) {
            hideCrosshair();
            hideTooltip();
            return;
        }
        int index = nearestIndex(x);
        if (index < 0) {
            return;
        }
        moveCrosshair(sampleX[index]);
        clearHoverMarkers();
        for (int s = 0; s < series.size(); s++) {
            Series item = series.get(s);
            if (item.isHidden() || plottedY[s] == null || Double.isNaN(plottedY[s][index])) {
                continue;
            }
            Element marker = circle(sampleX[index], plottedY[s][index], 3.5, item.colorOr(s));
            marker.setAttribute("stroke", Palette.BASE_100);
            marker.setAttribute("stroke-width", "1.5");
            addHoverMarker(marker);
        }
        showTooltip(sampleX[index], y, tooltipFor(index));
    }

    private Div tooltipFor(int index) {
        Div content = new Div();
        content.addClassName("flex flex-col gap-1");
        Div when = new Div(Scales.timestamp(timestamps[index]));
        when.addClassName("font-mono " + TextStyle.CAPTION.getClassNames());
        content.add(when);
        for (int s = 0; s < series.size(); s++) {
            Series item = series.get(s);
            if (item.isHidden()) {
                continue;
            }
            double value = item.valueAt(index);
            Div row = new Div();
            row.addClassName("flex items-center gap-2");
            Div swatch = new Div();
            swatch.addClassName("h-2 w-2 shrink-0 rounded-sm");
            swatch.setStyle("background-color", item.colorOr(s));
            Div name = new Div(item.name());
            name.addClassName(TextStyle.CAPTION.getClassNames());
            Div rendered = new Div(Double.isNaN(value) ? "no data" : yFormat().format(value));
            rendered.addClassName("ml-auto pl-3 font-mono font-semibold");
            row.add(swatch, name, rendered);
            content.add(row);
        }
        return content;
    }

    private int nearestIndex(double pixelX) {
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < sampleX.length; i++) {
            double distance = Math.abs(sampleX[i] - pixelX);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------- scaling

    private double[][] rawValues() {
        double[][] out = new double[series.size()][];
        for (int s = 0; s < series.size(); s++) {
            out[s] = padded(series.get(s).values());
        }
        return out;
    }

    /** Running totals across visible series, so each band sits on the sum of those below. */
    private double[][] cumulative() {
        double[][] out = new double[series.size()][];
        double[] running = new double[timestamps.length];
        for (int s = 0; s < series.size(); s++) {
            double[] values = padded(series.get(s).values());
            double[] totals = new double[timestamps.length];
            for (int i = 0; i < timestamps.length; i++) {
                if (series.get(s).isHidden() || Double.isNaN(values[i])) {
                    totals[i] = Double.isNaN(values[i]) ? Double.NaN : running[i];
                } else {
                    running[i] += values[i];
                    totals[i] = running[i];
                }
            }
            out[s] = totals;
        }
        return out;
    }

    /** Aligns a series to the timestamp array, padding a short series with gaps. */
    private double[] padded(double[] values) {
        if (values.length == timestamps.length) {
            return values;
        }
        double[] out = new double[timestamps.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = i < values.length ? values[i] : Double.NaN;
        }
        return out;
    }

    private double minOf(double[][] grid) {
        double min = Double.MAX_VALUE;
        for (int s = 0; s < grid.length; s++) {
            if (series.get(s).isHidden()) {
                continue;
            }
            for (double value : grid[s]) {
                if (!Double.isNaN(value)) {
                    min = Math.min(min, value);
                }
            }
        }
        return min == Double.MAX_VALUE ? 0 : min;
    }

    private double maxOf(double[][] grid) {
        double max = -Double.MAX_VALUE;
        for (int s = 0; s < grid.length; s++) {
            if (series.get(s).isHidden()) {
                continue;
            }
            for (double value : grid[s]) {
                if (!Double.isNaN(value)) {
                    max = Math.max(max, value);
                }
            }
        }
        return max == -Double.MAX_VALUE ? 1 : max;
    }

    private static double lastKnown(double[] values) {
        for (int i = values.length - 1; i >= 0; i--) {
            if (!Double.isNaN(values[i])) {
                return values[i];
            }
        }
        return Double.NaN;
    }
}
