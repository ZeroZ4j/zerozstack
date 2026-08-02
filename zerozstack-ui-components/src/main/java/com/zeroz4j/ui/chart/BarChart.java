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
import java.util.List;
import org.teavm.jso.dom.xml.Element;

/**
 * Categorical bars: grouped or stacked, vertical columns or horizontal rows.
 *
 * <p>Vertical is for comparing categories against each other. Horizontal is for ranked
 * lists and long labels, where columns would force the captions to be rotated or clipped.</p>
 *
 * <pre>{@code
 * BarChart images = new BarChart();
 * images.setYFormat(ValueFormat.BYTES);
 * images.setHorizontal(true);
 * images.setData(names, new Series("on disk", sizes));
 * }</pre>
 *
 * <p>For a single ranked measurement against a shared scale, {@link BarGauge} is usually the
 * better instrument — it carries thresholds and reads more densely.</p>
 */
public final class BarChart extends CartesianChart {

    /** A drawn bar, kept for hit testing on hover. */
    private record Hit(double x, double y, double w, double h, int seriesIndex, int categoryIndex) {
    }

    private List<String> categories = new ArrayList<>();
    private List<Series> series = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();

    private boolean stacked;
    private boolean horizontal;
    private boolean valueLabels;
    private double bandPadding = 0.28;

    public BarChart() {
        setChartHeight(220);
        setZeroBaseline(true);
    }

    // ------------------------------------------------------------------ public API

    public BarChart setData(List<String> newCategories, List<Series> newSeries) {
        this.categories = newCategories == null ? new ArrayList<>() : newCategories;
        this.series = newSeries == null ? new ArrayList<>() : newSeries;
        refresh();
        return this;
    }

    public BarChart setData(List<String> newCategories, Series... newSeries) {
        List<Series> list = new ArrayList<>();
        if (newSeries != null) {
            for (Series item : newSeries) {
                list.add(item);
            }
        }
        return setData(newCategories, list);
    }

    /** Stacks series within each category instead of placing them side by side. */
    public BarChart setStacked(boolean value) {
        this.stacked = value;
        refresh();
        return this;
    }

    /** Bars grow to the right, categories run down the left gutter. */
    public BarChart setHorizontal(boolean value) {
        this.horizontal = value;
        setGridVisible(!value, value);
        refresh();
        return this;
    }

    /** Prints each bar's value on it. Only sensible with few bars. */
    public BarChart setValueLabels(boolean value) {
        this.valueLabels = value;
        refresh();
        return this;
    }

    /** Fraction of each category band left as gap. 0 butts the bars together. */
    public BarChart setBandPadding(double fraction) {
        this.bandPadding = Scales.clamp(fraction, 0, 0.9);
        refresh();
        return this;
    }

    // --------------------------------------------------------------------- drawing

    @Override
    protected boolean hasData() {
        return !categories.isEmpty() && !series.isEmpty();
    }

    @Override
    protected void draw() {
        hits.clear();
        double[][] values = valueGrid();
        computeYScale(extent(values, false), extent(values, true));
        if (horizontal) {
            drawHorizontal(values);
        } else {
            drawVertical(values);
        }
        for (int s = 0; s < series.size(); s++) {
            legend(series.get(s).name(), series.get(s).colorOr(s));
        }
    }

    private void drawVertical(double[][] values) {
        drawYAxis();
        drawCategoryAxis(categories);

        double bandWidth = (double) plotWidth() / categories.size();
        double barArea = bandWidth * (1 - bandPadding);
        double barWidth = stacked ? barArea : barArea / Math.max(1, series.size());
        double zeroY = yFor(Math.max(0, yScaleMin()));

        for (int c = 0; c < categories.size(); c++) {
            double bandLeft = plotLeft() + c * bandWidth + (bandWidth - barArea) / 2;
            double stackTop = zeroY;
            for (int s = 0; s < series.size(); s++) {
                Series item = series.get(s);
                if (item.isHidden()) {
                    continue;
                }
                double value = values[s][c];
                if (Double.isNaN(value)) {
                    continue;
                }
                double x = stacked ? bandLeft : bandLeft + s * barWidth;
                double y;
                double barHeight;
                if (stacked) {
                    barHeight = Math.abs(zeroY - yFor(value)) ;
                    y = stackTop - barHeight;
                    stackTop = y;
                } else {
                    double valueY = yFor(value);
                    y = Math.min(valueY, zeroY);
                    barHeight = Math.abs(valueY - zeroY);
                }
                emit(x, y, Math.max(1, barWidth - 1.5), barHeight, item.colorOr(s), s, c);
                if (valueLabels && barHeight > 12) {
                    add(monoText(x + barWidth / 2, y - 7, yFormat().format(value), "middle", 9, 0.65));
                }
            }
        }
    }

    private void drawHorizontal(double[][] values) {
        // The left gutter holds category captions here, not tick labels.
        int widest = 0;
        for (String category : categories) {
            widest = Math.max(widest, category == null ? 0 : category.length());
        }
        marginLeft = Math.max(40, Math.min(width() / 3, (int) Math.ceil(widest * 6.2) + 10));

        drawValueAxis(yScaleMin(), yScaleMax(), yFormat());

        double bandHeight = (double) plotHeight() / categories.size();
        double barArea = bandHeight * (1 - bandPadding);
        double barHeight = stacked ? barArea : barArea / Math.max(1, series.size());
        double zeroX = xFor(Math.max(0, yScaleMin()));

        for (int c = 0; c < categories.size(); c++) {
            double bandTop = plotTop() + c * bandHeight + (bandHeight - barArea) / 2;
            add(text(marginLeft - 8, bandTop + barArea / 2, categories.get(c), "end", 10, 0.6));

            double stackLeft = zeroX;
            for (int s = 0; s < series.size(); s++) {
                Series item = series.get(s);
                if (item.isHidden()) {
                    continue;
                }
                double value = values[s][c];
                if (Double.isNaN(value)) {
                    continue;
                }
                double y = stacked ? bandTop : bandTop + s * barHeight;
                double x;
                double barWidth;
                if (stacked) {
                    barWidth = Math.abs(xFor(value) - zeroX);
                    x = stackLeft;
                    stackLeft += barWidth;
                } else {
                    double valueX = xFor(value);
                    x = Math.min(valueX, zeroX);
                    barWidth = Math.abs(valueX - zeroX);
                }
                emit(x, y, barWidth, Math.max(1, barHeight - 1.5), item.colorOr(s), s, c);
                if (valueLabels && barWidth > 24) {
                    add(monoText(x + barWidth + 5, y + barHeight / 2,
                        yFormat().format(value), "start", 9, 0.65));
                }
            }
        }
    }

    private void emit(double x, double y, double w, double h, String color, int seriesIndex, int categoryIndex) {
        Element bar = rect(x, y, w, h, color);
        bar.setAttribute("rx", "2");
        add(bar);
        hits.add(new Hit(x, y, w, h, seriesIndex, categoryIndex));
    }

    // ------------------------------------------------------------------------ hover

    @Override
    protected void onPointerMove(double x, double y) {
        for (Hit hit : hits) {
            // Bars can be thin; widen the hit box slightly so hovering is not a precision task.
            if (x >= hit.x() - 2 && x <= hit.x() + hit.w() + 2
                && y >= hit.y() - 2 && y <= hit.y() + hit.h() + 2) {
                showTooltip(x, y, tooltipFor(hit));
                return;
            }
        }
        hideTooltip();
    }

    private Div tooltipFor(Hit hit) {
        Series item = series.get(hit.seriesIndex());
        Div content = new Div();
        content.addClassName("flex flex-col gap-0.5");
        Div category = new Div(categories.get(hit.categoryIndex()));
        category.addClassName("text-[10px] text-base-content/50");
        Div row = new Div();
        row.addClassName("flex items-center gap-2");
        Div swatch = new Div();
        swatch.addClassName("h-2 w-2 shrink-0 rounded-sm");
        swatch.setStyle("background-color", item.colorOr(hit.seriesIndex()));
        Div name = new Div(item.name());
        name.addClassName("text-base-content/70");
        Div value = new Div(yFormat().format(item.valueAt(hit.categoryIndex())));
        value.addClassName("ml-auto pl-3 font-mono font-semibold");
        row.add(swatch, name, value);
        content.add(category, row);
        return content;
    }

    // ----------------------------------------------------------------------- scale

    /** Values per series, padded to the category count. */
    private double[][] valueGrid() {
        double[][] grid = new double[series.size()][categories.size()];
        for (int s = 0; s < series.size(); s++) {
            double[] values = series.get(s).values();
            for (int c = 0; c < categories.size(); c++) {
                grid[s][c] = c < values.length ? values[c] : Double.NaN;
            }
        }
        return grid;
    }

    /**
     * The extent the scale must cover. Stacking sums each category, because the tallest
     * stack — not the tallest single bar — is what has to fit.
     */
    private double extent(double[][] grid, boolean wantMax) {
        double result = wantMax ? -Double.MAX_VALUE : Double.MAX_VALUE;
        for (int c = 0; c < categories.size(); c++) {
            if (stacked) {
                double total = 0;
                for (int s = 0; s < series.size(); s++) {
                    if (!series.get(s).isHidden() && !Double.isNaN(grid[s][c])) {
                        total += grid[s][c];
                    }
                }
                result = wantMax ? Math.max(result, total) : Math.min(result, Math.min(0, total));
            } else {
                for (int s = 0; s < series.size(); s++) {
                    if (series.get(s).isHidden() || Double.isNaN(grid[s][c])) {
                        continue;
                    }
                    result = wantMax ? Math.max(result, grid[s][c]) : Math.min(result, grid[s][c]);
                }
            }
        }
        if (result == Double.MAX_VALUE || result == -Double.MAX_VALUE) {
            return wantMax ? 1 : 0;
        }
        return result;
    }
}
