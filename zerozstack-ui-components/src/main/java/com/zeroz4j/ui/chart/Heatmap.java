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
import org.teavm.jso.dom.xml.Element;

/**
 * Histograms over time: each column is one time bucket, each cell a value band, coloured by
 * how many samples landed in it.
 *
 * <p>This is the instrument for distributions a line chart flattens. A p99 latency line
 * tells you the tail moved; a heatmap tells you whether it moved because everything got
 * slower or because a second mode appeared. Same for GPU utilisation across many processes,
 * or request sizes across a fleet.</p>
 *
 * <pre>{@code
 * Heatmap latency = new Heatmap();
 * latency.setYFormat(ValueFormat.DURATION);
 * latency.setData(columnTimes, bucketEdges, counts);
 * }</pre>
 *
 * <p>The ramp is a literal colour sequence, not a theme token: a density scale has to stay
 * perceptually ordered, and theme colours are picked for contrast, not for ordering. Use
 * {@link Palette#VIRIDIS} where colour-vision safety matters.</p>
 */
public final class Heatmap extends CartesianChart {

    private static final int SCALE_WIDTH = 52;
    private static final int SCALE_STEPS = 24;

    private long[] columnTimes = new long[0];
    private double[] bucketEdges = new double[0];
    private double[][] counts = new double[0][];
    private int[] ramp = Palette.HEAT;
    private boolean scaleVisible = true;
    private boolean hideEmptyCells = true;
    private double peak = 1;

    public Heatmap() {
        setChartHeight(220);
        setLegendVisible(false);
        setGridVisible(false, false);
    }

    // ------------------------------------------------------------------ public API

    /**
     * @param times      start of each column, epoch millis, ascending; length = columns
     * @param edges      bucket boundaries, ascending; length = buckets + 1
     * @param bucketCounts {@code [column][bucket]} sample counts
     */
    public Heatmap setData(long[] times, double[] edges, double[][] bucketCounts) {
        this.columnTimes = times == null ? new long[0] : times;
        this.bucketEdges = edges == null ? new double[0] : edges;
        this.counts = bucketCounts == null ? new double[0][] : bucketCounts;
        refresh();
        return this;
    }

    /** Colour ramp; see {@link Palette#HEAT}, {@link Palette#VIRIDIS}, {@link Palette#BLUES}. */
    public Heatmap setRamp(int[] colourStops) {
        this.ramp = colourStops == null || colourStops.length == 0 ? Palette.HEAT : colourStops;
        refresh();
        return this;
    }

    /** Shows the colour scale in the right gutter. */
    public Heatmap setScaleVisible(boolean visible) {
        this.scaleVisible = visible;
        refresh();
        return this;
    }

    /** Leaves zero-count cells unpainted rather than drawing them at the ramp's floor. */
    public Heatmap setHideEmptyCells(boolean hide) {
        this.hideEmptyCells = hide;
        refresh();
        return this;
    }

    /**
     * Buckets raw samples into a grid ready for {@link #setData}. {@code samples[c]} holds
     * every observation in column {@code c}.
     */
    public static double[][] bucketise(double[][] samples, double[] edges) {
        double[][] grid = new double[samples.length][Math.max(0, edges.length - 1)];
        for (int c = 0; c < samples.length; c++) {
            if (samples[c] == null) {
                continue;
            }
            for (double sample : samples[c]) {
                if (Double.isNaN(sample)) {
                    continue;
                }
                for (int b = 0; b + 1 < edges.length; b++) {
                    boolean lastBucket = b + 2 == edges.length;
                    if (sample >= edges[b] && (sample < edges[b + 1] || (lastBucket && sample <= edges[b + 1]))) {
                        grid[c][b]++;
                        break;
                    }
                }
            }
        }
        return grid;
    }

    /** Evenly spaced bucket edges — the usual starting point. */
    public static double[] linearEdges(double from, double to, int buckets) {
        double[] edges = new double[Math.max(1, buckets) + 1];
        for (int i = 0; i < edges.length; i++) {
            edges[i] = from + (to - from) * i / Math.max(1, buckets);
        }
        return edges;
    }

    // --------------------------------------------------------------------- drawing

    @Override
    protected boolean hasData() {
        return columnTimes.length > 0 && bucketEdges.length > 1 && counts.length > 0;
    }

    @Override
    protected void draw() {
        marginRight = scaleVisible ? SCALE_WIDTH : 14;

        peak = 0;
        for (double[] column : counts) {
            if (column == null) {
                continue;
            }
            for (double count : column) {
                peak = Math.max(peak, count);
            }
        }
        if (peak <= 0) {
            peak = 1;
        }

        // The axis has to land exactly on the bucket edges, so no nice-rounding here.
        setYScaleExact(bucketEdges[0], bucketEdges[bucketEdges.length - 1]);
        drawYAxis();

        long from = columnTimes[0];
        long to = columnEnd();
        drawTimeAxis(from, to);

        for (int c = 0; c < columnTimes.length && c < counts.length; c++) {
            double left = xFor(columnTimes[c]);
            double right = xFor(c + 1 < columnTimes.length ? columnTimes[c + 1] : to);
            double cellWidth = Math.max(1, right - left);
            for (int b = 0; b + 1 < bucketEdges.length && counts[c] != null && b < counts[c].length; b++) {
                double count = counts[c][b];
                if (count <= 0 && hideEmptyCells) {
                    continue;
                }
                double top = yFor(bucketEdges[b + 1]);
                double cellHeight = Math.max(1, yFor(bucketEdges[b]) - top);
                Element cell = rect(left, top, cellWidth, cellHeight, Palette.ramp(ramp, count / peak));
                cell.setAttribute("shape-rendering", "crispEdges");
                add(cell);
            }
        }

        if (scaleVisible) {
            drawColourScale();
        }
    }

    /**
     * A stepped gradient in the right gutter. Drawn as discrete rectangles rather than an
     * SVG {@code linearGradient} so there is no {@code defs} id to collide with when several
     * heatmaps share a page.
     */
    private void drawColourScale() {
        double barLeft = plotRight() + 12;
        double barWidth = 10;
        double stepHeight = (double) plotHeight() / SCALE_STEPS;
        for (int i = 0; i < SCALE_STEPS; i++) {
            double t = 1 - (i + 0.5) / SCALE_STEPS;
            Element swatch = rect(barLeft, plotTop() + i * stepHeight, barWidth, stepHeight + 0.5,
                Palette.ramp(ramp, t));
            swatch.setAttribute("shape-rendering", "crispEdges");
            add(swatch);
        }
        add(monoText(barLeft + barWidth + 4, plotTop() + 4, Scales.compact(peak), "start", 9, 0.5));
        add(monoText(barLeft + barWidth + 4, plotBottom() - 4, "0", "start", 9, 0.5));
    }

    private long columnEnd() {
        if (columnTimes.length < 2) {
            return columnTimes[0] + 60_000;
        }
        long lastStep = columnTimes[columnTimes.length - 1] - columnTimes[columnTimes.length - 2];
        return columnTimes[columnTimes.length - 1] + lastStep;
    }

    // ----------------------------------------------------------------------- hover

    @Override
    protected void onPointerMove(double x, double y) {
        if (!hasData() || !isDrawn() || !inPlot(x, y)) {
            hideTooltip();
            return;
        }
        long at = (long) valueAtX(x);
        int column = -1;
        for (int c = 0; c < columnTimes.length; c++) {
            long end = c + 1 < columnTimes.length ? columnTimes[c + 1] : columnEnd();
            if (at >= columnTimes[c] && at < end) {
                column = c;
                break;
            }
        }
        int bucket = -1;
        for (int b = 0; b + 1 < bucketEdges.length; b++) {
            if (y <= yFor(bucketEdges[b]) && y >= yFor(bucketEdges[b + 1])) {
                bucket = b;
                break;
            }
        }
        if (column < 0 || bucket < 0 || column >= counts.length
            || counts[column] == null || bucket >= counts[column].length) {
            hideTooltip();
            return;
        }

        Div content = new Div();
        content.addClassName("flex flex-col gap-0.5");
        Div when = new Div(Scales.timestamp(columnTimes[column]));
        when.addClassName("font-mono text-[10px] text-base-content/50");
        Div band = new Div(yFormat().format(bucketEdges[bucket])
            + " to " + yFormat().format(bucketEdges[bucket + 1]));
        band.addClassName("text-base-content/70");
        Div count = new Div(Scales.fixed(counts[column][bucket], 0) + " samples");
        count.addClassName("font-mono font-semibold");
        content.add(when, band, count);
        showTooltip(x, y, content);
    }
}
