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

import com.zeroz4j.ui.component.SvgCanvas;
import java.util.List;
import org.teavm.jso.dom.xml.Element;

/**
 * Shared machinery for anything drawn against an x and a y axis: scale computation, grid,
 * tick labels, threshold bands and a crosshair.
 *
 * <p>The x domain is held as two doubles whatever it means — epoch millis for a time chart,
 * a value range for a scatter or histogram — so {@link #xFor(double)} and
 * {@link #valueAtX(double)} serve every subclass. Categorical charts ignore it and place
 * their own bands.</p>
 *
 * <p><b>Axis order matters.</b> {@link #drawYAxis()} sizes the left gutter from the widest
 * tick label it is about to render, so it must run before any geometry is used. The
 * expected shape of a subclass {@code draw()} is: compute the y scale, call
 * {@code drawYAxis()}, call an x axis, then plot.</p>
 */
public abstract class CartesianChart extends ChartBase {

    /** Approximate advance width of the tick font, used to size the left gutter. */
    private static final double CHAR_WIDTH = 6.4;

    private double fixedYMin = Double.NaN;
    private double fixedYMax = Double.NaN;
    private double scaleMin;
    private double scaleMax;
    private double tickStep = 1;

    private double xFrom;
    private double xTo = 1;

    private ValueFormat yFormat = ValueFormat.AUTO;
    private List<Threshold> thresholds;
    private boolean thresholdBands;
    private boolean gridYVisible = true;
    private boolean gridXVisible;
    private boolean yAxisVisible = true;
    private boolean zeroBaseline = true;

    private Element crosshair;
    private Element hoverLayer;

    // ----------------------------------------------------------------- public API

    /**
     * Pins the y axis. Pass {@code Double.NaN} for either end to keep that end automatic.
     * Pinning both is what makes several charts comparable side by side.
     */
    public CartesianChart setYBounds(double min, double max) {
        this.fixedYMin = min;
        this.fixedYMax = max;
        refresh();
        return this;
    }

    /** How y tick labels and tooltip values are rendered. */
    public CartesianChart setYFormat(ValueFormat format) {
        this.yFormat = format == null ? ValueFormat.AUTO : format;
        refresh();
        return this;
    }

    public ValueFormat yFormat() {
        return yFormat;
    }

    /**
     * Threshold steps for this chart. Drawn as dashed marker lines by default; call
     * {@link #setThresholdBands(boolean)} to shade the regions between them instead.
     */
    public CartesianChart setThresholds(List<Threshold> steps) {
        this.thresholds = steps;
        refresh();
        return this;
    }

    public List<Threshold> thresholds() {
        return thresholds;
    }

    /** Shades each threshold region rather than drawing a line at its boundary. */
    public CartesianChart setThresholdBands(boolean shaded) {
        this.thresholdBands = shaded;
        refresh();
        return this;
    }

    public CartesianChart setGridVisible(boolean horizontal, boolean vertical) {
        this.gridYVisible = horizontal;
        this.gridXVisible = vertical;
        refresh();
        return this;
    }

    public CartesianChart setYAxisVisible(boolean visible) {
        this.yAxisVisible = visible;
        refresh();
        return this;
    }

    /**
     * Whether an all-positive series is scaled from zero. On by default, because a chart
     * that crops the baseline exaggerates every wiggle; turn it off for a metric that lives
     * in a narrow band far from zero, such as a temperature.
     */
    public CartesianChart setZeroBaseline(boolean fromZero) {
        this.zeroBaseline = fromZero;
        refresh();
        return this;
    }

    // ------------------------------------------------------------------- y scaling

    /**
     * Establishes the y scale from the data extent, honouring any pinned bound, the
     * zero-baseline rule and the highest threshold, then rounding out to nice ticks.
     */
    protected void computeYScale(double dataMin, double dataMax) {
        double min = dataMin;
        double max = dataMax;
        if (Double.isNaN(min) || Double.isNaN(max) || min > max) {
            min = 0;
            max = 1;
        }
        if (zeroBaseline && min > 0) {
            min = 0;
        }
        if (zeroBaseline && max < 0) {
            max = 0;
        }
        // A threshold the data has not reached yet still needs to be on screen, or the
        // operator cannot see how much headroom is left.
        if (thresholds != null) {
            for (Threshold step : thresholds) {
                if (!Double.isInfinite(step.from())) {
                    max = Math.max(max, step.from());
                }
            }
        }
        double[] nice = Scales.niceBounds(min, max, 5);
        scaleMin = Double.isNaN(fixedYMin) ? nice[0] : fixedYMin;
        scaleMax = Double.isNaN(fixedYMax) ? nice[1] : fixedYMax;
        if (scaleMax <= scaleMin) {
            scaleMax = scaleMin + 1;
        }
        tickStep = Scales.tickStep(scaleMin, scaleMax, 5);
    }

    /**
     * Sets the y scale to exactly these bounds, skipping the zero-baseline and nice-rounding
     * rules. For charts whose y extent is defined by the data structure rather than inferred
     * from it — a heatmap's bucket edges have to line up with the axis exactly.
     */
    protected void setYScaleExact(double min, double max) {
        scaleMin = min;
        scaleMax = max > min ? max : min + 1;
        tickStep = Scales.tickStep(scaleMin, scaleMax, 5);
    }

    protected double yScaleMin() {
        return scaleMin;
    }

    protected double yScaleMax() {
        return scaleMax;
    }

    /** Pixel y for a data value. Values outside the scale are not clamped — clip if it matters. */
    protected double yFor(double value) {
        double fraction = (value - scaleMin) / (scaleMax - scaleMin);
        return plotBottom() - fraction * plotHeight();
    }

    // ------------------------------------------------------------------- x scaling

    /** Sets the x domain. Units are the subclass's business; time charts use epoch millis. */
    protected void setXDomain(double from, double to) {
        this.xFrom = from;
        this.xTo = to > from ? to : from + 1;
    }

    protected double xDomainFrom() {
        return xFrom;
    }

    protected double xDomainTo() {
        return xTo;
    }

    protected double xFor(double value) {
        return plotLeft() + (value - xFrom) / (xTo - xFrom) * plotWidth();
    }

    /** Inverse of {@link #xFor(double)} — the domain value under a pixel position. */
    protected double valueAtX(double pixelX) {
        return xFrom + (pixelX - plotLeft()) / plotWidth() * (xTo - xFrom);
    }

    // ----------------------------------------------------------------- axis drawing

    /**
     * Draws the y grid, tick labels and threshold marks, after widening the left gutter to
     * fit the labels. Call this first in {@code draw()}.
     */
    protected void drawYAxis() {
        double[] ticks = Scales.ticks(scaleMin, scaleMax, 5);
        int decimals = Scales.decimalsFor(tickStep);

        if (yAxisVisible) {
            int widest = 0;
            for (double tick : ticks) {
                widest = Math.max(widest, label(tick, decimals).length());
            }
            marginLeft = Math.max(28, (int) Math.ceil(widest * CHAR_WIDTH) + 12);
        } else {
            marginLeft = 8;
        }

        if (thresholdBands) {
            drawThresholdBands();
        }

        for (double tick : ticks) {
            double y = yFor(tick);
            if (y < plotTop() - 1 || y > plotBottom() + 1) {
                continue;
            }
            if (gridYVisible) {
                Element grid = line(plotLeft(), y, plotRight(), y, "currentColor", 1);
                grid.setAttribute("stroke-opacity", tick == 0 ? "0.22" : "0.09");
                add(grid);
            }
            if (yAxisVisible) {
                add(monoText(PlotText.LABEL, marginLeft - 8, y, label(tick, decimals), "end"));
            }
        }

        if (!thresholdBands) {
            drawThresholdLines();
        }
    }

    private String label(double tick, int decimals) {
        return yFormat == ValueFormat.AUTO ? Scales.fixed(tick, decimals) : yFormat.format(tick);
    }

    /**
     * Time axis along the bottom: ticks at human intervals, labelled at a resolution that
     * follows the visible span.
     */
    protected void drawTimeAxis(long from, long to) {
        setXDomain(from, to);
        long span = to - from;
        int targetTicks = Math.max(2, plotWidth() / 90);
        long[] ticks = Scales.timeTicks(from, to, targetTicks);
        for (long tick : ticks) {
            double x = xFor(tick);
            if (x < plotLeft() - 1 || x > plotRight() + 1) {
                continue;
            }
            if (gridXVisible) {
                Element grid = line(x, plotTop(), x, plotBottom(), "currentColor", 1);
                grid.setAttribute("stroke-opacity", "0.07");
                add(grid);
            }
            addEdgeAwareLabel(x, plotBottom() + 13, Scales.clock(tick, span));
        }
        drawAxisLine();
    }

    /**
     * A centred tick label, re-anchored when it would run off the surface.
     *
     * <p>The last tick usually sits at the very edge of the plot, so a centred label loses its
     * right half to the {@code <svg>} boundary — a clipped "12:50:0" reads as a rendering bug.</p>
     */
    private void addEdgeAwareLabel(double x, double y, String rendered) {
        double halfWidth = rendered.length() * CHAR_WIDTH / 2;
        if (x + halfWidth > width() - 2) {
            add(monoText(PlotText.LABEL, width() - 2, y, rendered, "end"));
        } else if (x - halfWidth < 2) {
            add(monoText(PlotText.LABEL, 2, y, rendered, "start"));
        } else {
            add(monoText(PlotText.LABEL, x, y, rendered, "middle"));
        }
    }

    /** Numeric axis along the bottom, for scatter and histogram. */
    protected void drawValueAxis(double from, double to, ValueFormat format) {
        setXDomain(from, to);
        int targetTicks = Math.max(2, plotWidth() / 70);
        double[] ticks = Scales.ticks(from, to, targetTicks);
        double step = Scales.tickStep(from, to, targetTicks);
        for (double tick : ticks) {
            double x = xFor(tick);
            if (x < plotLeft() - 1 || x > plotRight() + 1) {
                continue;
            }
            if (gridXVisible) {
                Element grid = line(x, plotTop(), x, plotBottom(), "currentColor", 1);
                grid.setAttribute("stroke-opacity", "0.07");
                add(grid);
            }
            String rendered = format != null ? format.format(tick) : Scales.tickLabel(tick, step);
            addEdgeAwareLabel(x, plotBottom() + 13, rendered);
        }
        drawAxisLine();
    }

    /** Evenly spaced category labels along the bottom, centred under their band. */
    protected void drawCategoryAxis(List<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return;
        }
        double bandWidth = (double) plotWidth() / categories.size();
        // Drop labels that would collide rather than overlapping them into mush.
        int stride = Math.max(1, (int) Math.ceil(52.0 / bandWidth));
        for (int i = 0; i < categories.size(); i++) {
            if (i % stride != 0) {
                continue;
            }
            double centre = plotLeft() + (i + 0.5) * bandWidth;
            add(text(PlotText.LABEL, centre, plotBottom() + 13, categories.get(i), "middle"));
        }
        drawAxisLine();
    }

    private void drawAxisLine() {
        Element axis = line(plotLeft(), plotBottom(), plotRight(), plotBottom(), "currentColor", 1);
        axis.setAttribute("stroke-opacity", "0.2");
        add(axis);
    }

    // ------------------------------------------------------------------ thresholds

    private void drawThresholdLines() {
        for (Threshold step : Threshold.within(thresholds, scaleMin, scaleMax)) {
            double y = yFor(step.from());
            Element marker = line(plotLeft(), y, plotRight(), y, step.color(), 1.25);
            marker.setAttribute("stroke-dasharray", "5 4");
            marker.setAttribute("stroke-opacity", "0.75");
            add(marker);
        }
    }

    private void drawThresholdBands() {
        if (thresholds == null || thresholds.isEmpty()) {
            return;
        }
        for (int i = 0; i < thresholds.size(); i++) {
            Threshold step = thresholds.get(i);
            double bandFrom = Double.isInfinite(step.from()) ? scaleMin : Math.max(step.from(), scaleMin);
            double bandTo = i + 1 < thresholds.size()
                ? Math.min(thresholds.get(i + 1).from(), scaleMax)
                : scaleMax;
            if (bandTo <= bandFrom) {
                continue;
            }
            double top = yFor(bandTo);
            Element band = rect(plotLeft(), top, plotWidth(), yFor(bandFrom) - top, step.color());
            band.setAttribute("fill-opacity", "0.07");
            add(band);
        }
    }

    // ------------------------------------------------------------------- crosshair

    /**
     * Creates the crosshair and hover-marker layers. Both are created once per draw and
     * then only moved, so hovering never triggers a full repaint.
     */
    protected void enableCrosshair() {
        crosshair = line(0, plotTop(), 0, plotBottom(), "currentColor", 1);
        crosshair.setAttribute("stroke-opacity", "0.35");
        crosshair.setAttribute("stroke-dasharray", "3 3");
        crosshair.setAttribute("visibility", "hidden");
        add(crosshair);
        hoverLayer = SvgCanvas.el("g");
        add(hoverLayer);
    }

    protected void moveCrosshair(double x) {
        if (crosshair != null) {
            crosshair.setAttribute("x1", num(x));
            crosshair.setAttribute("x2", num(x));
            crosshair.setAttribute("visibility", "visible");
        }
    }

    protected void hideCrosshair() {
        if (crosshair != null) {
            crosshair.setAttribute("visibility", "hidden");
        }
        clearHoverMarkers();
    }

    /** The layer for per-hover marks. Clear it before repopulating. */
    protected Element hoverLayer() {
        return hoverLayer;
    }

    protected void clearHoverMarkers() {
        if (hoverLayer != null) {
            while (hoverLayer.getFirstChild() != null) {
                hoverLayer.removeChild(hoverLayer.getFirstChild());
            }
        }
    }

    protected void addHoverMarker(Element element) {
        if (hoverLayer != null && element != null) {
            hoverLayer.appendChild(element);
        }
    }

    /** True when the pointer is inside the plotting area. */
    protected boolean inPlot(double x, double y) {
        return x >= plotLeft() && x <= plotRight() && y >= plotTop() - 4 && y <= plotBottom() + 4;
    }

    @Override
    protected void onPointerLeave() {
        super.onPointerLeave();
        hideCrosshair();
    }

    // ------------------------------------------------------------------- utilities

    /** Builds an SVG path for a run of points, breaking the line at {@code NaN} gaps. */
    protected static String linePath(double[] xs, double[] ys, boolean stepped) {
        StringBuilder path = new StringBuilder();
        boolean penDown = false;
        double previousY = 0;
        for (int i = 0; i < xs.length; i++) {
            if (Double.isNaN(ys[i])) {
                penDown = false;
                continue;
            }
            if (!penDown) {
                path.append('M').append(num(xs[i])).append(' ').append(num(ys[i]));
                penDown = true;
            } else if (stepped) {
                path.append('L').append(num(xs[i])).append(' ').append(num(previousY))
                    .append('L').append(num(xs[i])).append(' ').append(num(ys[i]));
            } else {
                path.append('L').append(num(xs[i])).append(' ').append(num(ys[i]));
            }
            previousY = ys[i];
        }
        return path.toString();
    }

    /**
     * The same run of points closed down to {@code baselineY} so it can be filled. Each
     * unbroken run becomes its own closed subpath, so a gap leaves a hole in the fill too.
     */
    protected static String areaPath(double[] xs, double[] ys, double baselineY, boolean stepped) {
        StringBuilder path = new StringBuilder();
        int runStart = -1;
        double previousY = 0;
        for (int i = 0; i <= xs.length; i++) {
            boolean gap = i == xs.length || Double.isNaN(ys[i]);
            if (gap) {
                if (runStart >= 0 && i - runStart > 1) {
                    path.append('L').append(num(xs[i - 1])).append(' ').append(num(baselineY))
                        .append('L').append(num(xs[runStart])).append(' ').append(num(baselineY))
                        .append('Z');
                } else if (runStart >= 0) {
                    // A single-sample run has no area; drop the open subpath.
                    path.setLength(path.lastIndexOf("M") < 0 ? 0 : path.lastIndexOf("M"));
                }
                runStart = -1;
                continue;
            }
            if (runStart < 0) {
                runStart = i;
                path.append('M').append(num(xs[i])).append(' ').append(num(ys[i]));
            } else if (stepped) {
                path.append('L').append(num(xs[i])).append(' ').append(num(previousY))
                    .append('L').append(num(xs[i])).append(' ').append(num(ys[i]));
            } else {
                path.append('L').append(num(xs[i])).append(' ').append(num(ys[i]));
            }
            previousY = ys[i];
        }
        return path.toString();
    }
}
