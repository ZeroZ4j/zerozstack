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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.teavm.jso.dom.xml.Element;

/**
 * Discrete state over time, one lane per subject: which containers were up, which models
 * were loaded, when a service was degraded.
 *
 * <p>A line chart cannot show this honestly — interpolating between "running" and "exited"
 * has no meaning. State is drawn as bands whose edges are the transitions, which is what an
 * operator is actually looking for.</p>
 *
 * <pre>{@code
 * StateTimeline containers = new StateTimeline();
 * containers.setRows(List.of(
 *     new StateTimeline.Row("agent-asr", List.of(
 *         new StateTimeline.Band("running", t0, t1),
 *         new StateTimeline.Band("exited",  t1, t2)))));
 * }</pre>
 *
 * <p>Distinct from {@code LaneTimeline}, which is a replay control with a scrub cursor and a
 * fixed session vocabulary. This one is a read-only visualisation of arbitrary states.</p>
 */
public final class StateTimeline extends CartesianChart {

    /** One state occupying a time interval. {@code to} may be in the future for an open band. */
    public record Band(String state, long from, long to) {
    }

    /** One lane: a caption and its bands, in time order. */
    public record Row(String label, List<Band> bands) {
    }

    private record Hit(double x, double y, double w, double h, int rowIndex, Band band) {
    }

    private List<Row> rows = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private StateColor stateColor = StateColor.DEFAULT;
    private int rowHeight = 24;
    private int rowGap = 4;
    private long fixedFrom = -1;
    private long fixedTo = -1;
    private boolean bandLabels = true;
    private boolean autoHeight = true;

    public StateTimeline() {
        setLegendVisible(true);
        setGridVisible(false, true);
        setMargins(8, 14, 26, 90);
    }

    // ------------------------------------------------------------------ public API

    public StateTimeline setRows(List<Row> newRows) {
        this.rows = newRows == null ? new ArrayList<>() : newRows;
        beginBatch();
        if (autoHeight) {
            setChartHeight(marginTop + marginBottom + Math.max(1, rows.size()) * (rowHeight + rowGap));
        }
        endBatch();
        return this;
    }

    public StateTimeline setStateColor(StateColor mapping) {
        this.stateColor = mapping == null ? StateColor.DEFAULT : mapping;
        refresh();
        return this;
    }

    /** Lane height in pixels. The chart resizes itself to fit unless the height was pinned. */
    public StateTimeline setRowHeight(int pixels) {
        this.rowHeight = Math.max(8, pixels);
        return setRows(rows);
    }

    /**
     * Pins the visible window. Without it the window spans the data, which makes an open
     * band appear to end at the last transition rather than at "now".
     */
    public StateTimeline setTimeRange(long from, long to) {
        this.fixedFrom = from;
        this.fixedTo = to;
        refresh();
        return this;
    }

    /** Writes the state name into bands wide enough to hold it. */
    public StateTimeline setBandLabels(boolean visible) {
        this.bandLabels = visible;
        refresh();
        return this;
    }

    /** Stops the chart sizing itself to its row count. */
    public StateTimeline setAutoHeight(boolean value) {
        this.autoHeight = value;
        return this;
    }

    // --------------------------------------------------------------------- drawing

    @Override
    protected boolean hasData() {
        for (Row row : rows) {
            if (row.bands() != null && !row.bands().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void draw() {
        hits.clear();

        long from = fixedFrom >= 0 ? fixedFrom : Long.MAX_VALUE;
        long to = fixedTo >= 0 ? fixedTo : Long.MIN_VALUE;
        if (fixedFrom < 0 || fixedTo < 0) {
            for (Row row : rows) {
                for (Band band : row.bands()) {
                    if (fixedFrom < 0) {
                        from = Math.min(from, band.from());
                    }
                    if (fixedTo < 0) {
                        to = Math.max(to, band.to());
                    }
                }
            }
        }
        if (from == Long.MAX_VALUE) {
            from = 0;
        }
        if (to <= from) {
            to = from + 60_000;
        }

        int widest = 0;
        for (Row row : rows) {
            widest = Math.max(widest, row.label() == null ? 0 : row.label().length());
        }
        marginLeft = Math.max(40, Math.min(width() / 3, (int) Math.ceil(widest * 6.2) + 10));

        drawTimeAxis(from, to);

        Set<String> seenStates = new LinkedHashSet<>();
        for (int r = 0; r < rows.size(); r++) {
            Row row = rows.get(r);
            double top = plotTop() + r * (rowHeight + rowGap);
            add(text(marginLeft - 8, top + rowHeight / 2.0, row.label(), "end", 10, 0.65));

            // The lane's own background, so a gap in coverage is visibly a gap.
            Element lane = rect(plotLeft(), top, plotWidth(), rowHeight, Palette.BASE_300);
            lane.setAttribute("fill-opacity", "0.35");
            lane.setAttribute("rx", "3");
            add(lane);

            for (Band band : row.bands()) {
                double left = Math.max(plotLeft(), xFor(Math.max(band.from(), from)));
                double right = Math.min(plotRight(), xFor(Math.min(band.to(), to)));
                double bandWidth = right - left;
                if (bandWidth <= 0) {
                    continue;
                }
                String color = stateColor.colorFor(band.state());
                Element block = rect(left, top, bandWidth, rowHeight, color);
                block.setAttribute("rx", "3");
                add(block);
                seenStates.add(band.state());
                hits.add(new Hit(left, top, bandWidth, rowHeight, r, band));

                if (bandLabels && bandWidth > (band.state().length() * 6.4 + 12)) {
                    Element caption = text(left + bandWidth / 2, top + rowHeight / 2.0,
                        band.state(), "middle", 9, 1);
                    caption.setAttribute("fill", Palette.BASE_100);
                    caption.setAttribute("fill-opacity", "0.9");
                    add(caption);
                }
            }
        }

        for (String state : seenStates) {
            legend(state, stateColor.colorFor(state));
        }
    }

    // ----------------------------------------------------------------------- hover

    @Override
    protected void onPointerMove(double x, double y) {
        for (Hit hit : hits) {
            if (x >= hit.x() && x <= hit.x() + hit.w() && y >= hit.y() && y <= hit.y() + hit.h()) {
                showTooltip(x, y, tooltipFor(hit));
                return;
            }
        }
        hideTooltip();
    }

    private Div tooltipFor(Hit hit) {
        Band band = hit.band();
        Div content = new Div();
        content.addClassName("flex flex-col gap-0.5");

        Div header = new Div();
        header.addClassName("flex items-center gap-2");
        Div swatch = new Div();
        swatch.addClassName("h-2 w-2 shrink-0 rounded-sm");
        swatch.setStyle("background-color", stateColor.colorFor(band.state()));
        Div name = new Div(rows.get(hit.rowIndex()).label() + " · " + band.state());
        name.addClassName("font-semibold");
        header.add(swatch, name);

        Div span = new Div(Scales.timestamp(band.from()) + " → " + Scales.timestamp(band.to()));
        span.addClassName("font-mono text-[10px] text-base-content/50");
        Div held = new Div("held for " + Scales.duration(band.to() - band.from()));
        held.addClassName("text-base-content/70");

        content.add(header, span, held);
        return content;
    }
}
