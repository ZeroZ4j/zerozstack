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
import org.teavm.jso.dom.xml.Element;

/**
 * Distribution of a set of samples: how many fell in each value band.
 *
 * <p>The companion to {@link Heatmap} — same question, no time dimension. Where an average
 * hides a bimodal distribution, a histogram shows the two modes, which is usually the whole
 * finding: two populations of request, two classes of disk, a fast path and a slow one.</p>
 *
 * <pre>{@code
 * Histogram latency = new Histogram();
 * latency.setXFormat(ValueFormat.DURATION);
 * latency.setValues(samples);        // buckets chosen automatically
 * }</pre>
 */
public final class Histogram extends CartesianChart {

    private double[] edges = new double[0];
    private double[] counts = new double[0];
    private int bucketCount = 20;
    private ValueFormat xFormat = ValueFormat.AUTO;
    private String color;
    private double barGap = 1;

    public Histogram() {
        setChartHeight(200);
        setYFormat(ValueFormat.INTEGER);
        setGridVisible(true, false);
    }

    // ------------------------------------------------------------------ public API

    /** Buckets the samples into {@link #setBucketCount} bands spanning their extent. */
    public Histogram setValues(double[] samples) {
        if (samples == null || samples.length == 0) {
            this.edges = new double[0];
            this.counts = new double[0];
            refresh();
            return this;
        }
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (double sample : samples) {
            if (!Double.isNaN(sample)) {
                min = Math.min(min, sample);
                max = Math.max(max, sample);
            }
        }
        if (min > max) {
            this.edges = new double[0];
            this.counts = new double[0];
            refresh();
            return this;
        }
        // Round the extent out to nice numbers so bucket boundaries are readable values,
        // not artefacts of whichever sample happened to be the smallest.
        double[] bounds = Scales.niceBounds(min, max, bucketCount);
        double[] newEdges = Heatmap.linearEdges(bounds[0], bounds[1], bucketCount);
        double[] newCounts = new double[bucketCount];
        for (double sample : samples) {
            if (Double.isNaN(sample)) {
                continue;
            }
            int bucket = (int) ((sample - bounds[0]) / (bounds[1] - bounds[0]) * bucketCount);
            newCounts[Math.max(0, Math.min(bucketCount - 1, bucket))]++;
        }
        return setBuckets(newEdges, newCounts);
    }

    /** Pre-computed buckets: {@code edges} has one more entry than {@code bucketCounts}. */
    public Histogram setBuckets(double[] bucketEdges, double[] bucketCounts) {
        this.edges = bucketEdges == null ? new double[0] : bucketEdges;
        this.counts = bucketCounts == null ? new double[0] : bucketCounts;
        refresh();
        return this;
    }

    /** Bucket count used by {@link #setValues}. Default 20. */
    public Histogram setBucketCount(int count) {
        this.bucketCount = Math.max(2, Math.min(200, count));
        return this;
    }

    /** How bucket boundaries are labelled on the x axis. */
    public Histogram setXFormat(ValueFormat format) {
        this.xFormat = format == null ? ValueFormat.AUTO : format;
        refresh();
        return this;
    }

    public Histogram setColor(String cssColor) {
        this.color = cssColor;
        refresh();
        return this;
    }

    public Histogram setBarGap(double pixels) {
        this.barGap = Math.max(0, pixels);
        refresh();
        return this;
    }

    // --------------------------------------------------------------------- drawing

    @Override
    protected boolean hasData() {
        return edges.length > 1 && counts.length > 0;
    }

    @Override
    protected void draw() {
        double peak = 0;
        for (double count : counts) {
            peak = Math.max(peak, count);
        }
        computeYScale(0, Math.max(1, peak));
        drawYAxis();
        drawValueAxis(edges[0], edges[edges.length - 1], xFormat);

        String fill = color != null ? color : Palette.PRIMARY;
        double zeroY = yFor(0);
        for (int b = 0; b < counts.length && b + 1 < edges.length; b++) {
            if (counts[b] <= 0) {
                continue;
            }
            double left = xFor(edges[b]);
            double right = xFor(edges[b + 1]);
            double top = yFor(counts[b]);
            Element bar = rect(left + barGap / 2, top,
                Math.max(1, right - left - barGap), zeroY - top, fill);
            bar.setAttribute("rx", "2");
            add(bar);
        }
    }

    // ----------------------------------------------------------------------- hover

    @Override
    protected void onPointerMove(double x, double y) {
        if (!hasData() || !isDrawn() || !inPlot(x, y)) {
            hideTooltip();
            return;
        }
        double value = valueAtX(x);
        for (int b = 0; b < counts.length && b + 1 < edges.length; b++) {
            if (value >= edges[b] && value < edges[b + 1]) {
                Div content = new Div();
                content.addClassName("flex flex-col gap-0.5");
                Div band = new Div(xFormat.format(edges[b]) + " to " + xFormat.format(edges[b + 1]));
                band.addClassName(TextStyle.CAPTION.getClassNames());
                Div count = new Div(Scales.fixed(counts[b], 0) + " samples");
                count.addClassName("font-mono font-semibold");
                content.add(band, count);
                showTooltip(x, y, content);
                return;
            }
        }
        hideTooltip();
    }
}
