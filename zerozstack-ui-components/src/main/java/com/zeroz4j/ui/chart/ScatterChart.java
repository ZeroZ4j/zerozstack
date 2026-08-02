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

import com.zeroz4j.ui.layout.Div;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.teavm.jso.dom.xml.Element;

/**
 * Two measurements plotted against each other, to find out whether they are related.
 *
 * <p>This is the panel for "is the slowdown actually about memory pressure?" — a question
 * two time series side by side can only hint at. A fourth and fifth dimension are available
 * through point size and category colour.</p>
 *
 * <pre>{@code
 * ScatterChart correlation = new ScatterChart();
 * correlation.setXFormat(ValueFormat.GIGABYTES).setYFormat(ValueFormat.DURATION);
 * correlation.setPoints(points);   // Point(memoryUsed, latency, requests, model)
 * }</pre>
 */
public final class ScatterChart extends CartesianChart {

    /** One observation. {@code size} and {@code category} are optional; pass NaN and null. */
    public record Point(double x, double y, double size, String category) {

        public Point(double x, double y) {
            this(x, y, Double.NaN, null);
        }

        public Point(double x, double y, String category) {
            this(x, y, Double.NaN, category);
        }
    }

    private List<Point> points = new ArrayList<>();
    private final Map<String, String> categoryColors = new LinkedHashMap<>();
    private ValueFormat xFormat = ValueFormat.AUTO;
    private String xLabel;
    private String yLabel;
    private double minRadius = 2.5;
    private double maxRadius = 9;
    private double opacity = 0.75;
    private double[] pixelX = new double[0];
    private double[] pixelY = new double[0];

    public ScatterChart() {
        setChartHeight(230);
        setZeroBaseline(false);
        setGridVisible(true, true);
    }

    // ------------------------------------------------------------------ public API

    public ScatterChart setPoints(List<Point> newPoints) {
        this.points = newPoints == null ? new ArrayList<>() : newPoints;
        categoryColors.clear();
        for (Point point : this.points) {
            if (point.category() != null && !categoryColors.containsKey(point.category())) {
                categoryColors.put(point.category(), Palette.series(categoryColors.size()));
            }
        }
        refresh();
        return this;
    }

    public ScatterChart setXFormat(ValueFormat format) {
        this.xFormat = format == null ? ValueFormat.AUTO : format;
        refresh();
        return this;
    }

    /** Axis captions, drawn in the margins. */
    public ScatterChart setAxisLabels(String x, String y) {
        this.xLabel = x;
        this.yLabel = y;
        refresh();
        return this;
    }

    /** Radius range in pixels when points carry a size. */
    public ScatterChart setRadiusRange(double min, double max) {
        this.minRadius = Math.max(1, min);
        this.maxRadius = Math.max(this.minRadius, max);
        refresh();
        return this;
    }

    /** Point opacity. Below 1 so overlapping points read as denser. */
    public ScatterChart setOpacity(double value) {
        this.opacity = Scales.clamp(value, 0.05, 1);
        refresh();
        return this;
    }

    /** Overrides the colour assigned to a category. */
    public ScatterChart setCategoryColor(String category, String cssColor) {
        categoryColors.put(category, cssColor);
        refresh();
        return this;
    }

    // --------------------------------------------------------------------- drawing

    @Override
    protected boolean hasData() {
        return !points.isEmpty();
    }

    @Override
    protected void draw() {
        double xMin = Double.MAX_VALUE;
        double xMax = -Double.MAX_VALUE;
        double yMin = Double.MAX_VALUE;
        double yMax = -Double.MAX_VALUE;
        double sizeMin = Double.MAX_VALUE;
        double sizeMax = -Double.MAX_VALUE;
        for (Point point : points) {
            if (Double.isNaN(point.x()) || Double.isNaN(point.y())) {
                continue;
            }
            xMin = Math.min(xMin, point.x());
            xMax = Math.max(xMax, point.x());
            yMin = Math.min(yMin, point.y());
            yMax = Math.max(yMax, point.y());
            if (!Double.isNaN(point.size())) {
                sizeMin = Math.min(sizeMin, point.size());
                sizeMax = Math.max(sizeMax, point.size());
            }
        }
        if (xMin > xMax) {
            return;
        }

        computeYScale(yMin, yMax);
        if (yLabel != null) {
            marginTop = 22;
        }
        drawYAxis();

        double[] xBounds = Scales.niceBounds(xMin, xMax, 5);
        drawValueAxis(xBounds[0], xBounds[1], xFormat);

        if (yLabel != null) {
            add(text(plotLeft(), plotTop() - 10, yLabel, "start", 10, 0.5));
        }
        if (xLabel != null) {
            add(text(plotRight(), plotBottom() + 24, xLabel, "end", 10, 0.5));
        }

        pixelX = new double[points.size()];
        pixelY = new double[points.size()];
        for (int i = 0; i < points.size(); i++) {
            Point point = points.get(i);
            if (Double.isNaN(point.x()) || Double.isNaN(point.y())) {
                pixelX[i] = Double.NaN;
                continue;
            }
            double cx = xFor(point.x());
            double cy = yFor(point.y());
            pixelX[i] = cx;
            pixelY[i] = cy;

            double radius = minRadius;
            if (!Double.isNaN(point.size()) && sizeMax > sizeMin) {
                // Area, not radius, scales with the value — radius would exaggerate it
                // by the square.
                double t = Scales.normalise(point.size(), sizeMin, sizeMax);
                radius = Math.sqrt(minRadius * minRadius
                    + t * (maxRadius * maxRadius - minRadius * minRadius));
            }
            String color = point.category() != null
                ? categoryColors.getOrDefault(point.category(), Palette.PRIMARY)
                : Palette.PRIMARY;
            Element dot = circle(cx, cy, radius, color);
            dot.setAttribute("fill-opacity", String.valueOf(opacity));
            add(dot);
        }

        for (Map.Entry<String, String> entry : categoryColors.entrySet()) {
            legend(entry.getKey(), entry.getValue());
        }
    }

    // ----------------------------------------------------------------------- hover

    @Override
    protected void onPointerMove(double x, double y) {
        if (!hasData() || !isDrawn() || !inPlot(x, y)) {
            hideTooltip();
            return;
        }
        int nearest = -1;
        double nearestDistance = 14;
        for (int i = 0; i < pixelX.length; i++) {
            if (Double.isNaN(pixelX[i])) {
                continue;
            }
            double distance = Math.sqrt((pixelX[i] - x) * (pixelX[i] - x)
                + (pixelY[i] - y) * (pixelY[i] - y));
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = i;
            }
        }
        if (nearest < 0) {
            hideTooltip();
            return;
        }
        Point point = points.get(nearest);
        Div content = new Div();
        content.addClassName("flex flex-col gap-0.5");
        if (point.category() != null) {
            Div category = new Div(point.category());
            category.addClassName("font-semibold");
            content.add(category);
        }
        Div coordinates = new Div((xLabel != null ? xLabel + " " : "")
            + xFormat.format(point.x()) + "   "
            + (yLabel != null ? yLabel + " " : "") + yFormat().format(point.y()));
        coordinates.addClassName("font-mono text-base-content/70");
        content.add(coordinates);
        if (!Double.isNaN(point.size())) {
            Div size = new Div(Scales.compact(point.size()));
            size.addClassName("font-mono text-[10px] text-base-content/50");
            content.add(size);
        }
        showTooltip(x, y, content);
    }
}
