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
 * A grid of periodic state samples: one column per poll, one row per subject.
 *
 * <p>Where {@link StateTimeline} draws continuous bands between transitions, this draws one
 * mark per sample. The difference matters: discrete marks show <em>coverage</em>. A missed
 * poll leaves a hole, so "the probe stopped answering" looks different from "the value did
 * not change" — which a band chart cannot distinguish.</p>
 *
 * <pre>{@code
 * StatusHistory probes = new StatusHistory();
 * probes.setData(sampleTimes, List.of(
 *     new StatusHistory.Row("vitals", vitalsStates),
 *     new StatusHistory.Row("docker", dockerStates)));
 * }</pre>
 */
public final class StatusHistory extends CartesianChart {

    /** One subject and its state at each sample time. A {@code null} entry is a missed sample. */
    public record Row(String label, String[] states) {
    }

    private record Hit(double x, double y, double size, int rowIndex, int sampleIndex) {
    }

    private long[] times = new long[0];
    private List<Row> rows = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private StateColor stateColor = StateColor.DEFAULT;
    private int rowHeight = 20;
    private int rowGap = 4;
    private double cellGap = 2;
    private boolean rounded = true;
    private boolean autoHeight = true;

    public StatusHistory() {
        setGridVisible(false, false);
        setMargins(8, 14, 26, 90);
    }

    // ------------------------------------------------------------------ public API

    public StatusHistory setData(long[] sampleTimes, List<Row> newRows) {
        this.times = sampleTimes == null ? new long[0] : sampleTimes;
        this.rows = newRows == null ? new ArrayList<>() : newRows;
        beginBatch();
        if (autoHeight) {
            setChartHeight(marginTop + marginBottom + Math.max(1, rows.size()) * (rowHeight + rowGap));
        }
        endBatch();
        return this;
    }

    public StatusHistory setStateColor(StateColor mapping) {
        this.stateColor = mapping == null ? StateColor.DEFAULT : mapping;
        refresh();
        return this;
    }

    public StatusHistory setRowHeight(int pixels) {
        this.rowHeight = Math.max(6, pixels);
        return setData(times, rows);
    }

    /** Gap between marks in pixels. Zero makes the row read as a continuous strip. */
    public StatusHistory setCellGap(double pixels) {
        this.cellGap = Math.max(0, pixels);
        refresh();
        return this;
    }

    /** Rounded marks read as samples; square marks read as a filled band. */
    public StatusHistory setRounded(boolean value) {
        this.rounded = value;
        refresh();
        return this;
    }

    public StatusHistory setAutoHeight(boolean value) {
        this.autoHeight = value;
        return this;
    }

    // --------------------------------------------------------------------- drawing

    @Override
    protected boolean hasData() {
        return times.length > 0 && !rows.isEmpty();
    }

    @Override
    protected void draw() {
        hits.clear();

        int widest = 0;
        for (Row row : rows) {
            widest = Math.max(widest, row.label() == null ? 0 : row.label().length());
        }
        marginLeft = Math.max(40, Math.min(width() / 3, (int) Math.ceil(widest * 6.2) + 10));

        long from = times[0];
        // The last sample occupies a slot of its own, so the window runs one step past it.
        long step = times.length > 1 ? times[times.length - 1] - times[times.length - 2] : 60_000;
        long to = times[times.length - 1] + step;
        drawTimeAxis(from, to);

        double slotWidth = (double) plotWidth() / times.length;
        double markWidth = Math.max(1, slotWidth - cellGap);
        double markHeight = Math.max(1, rowHeight - cellGap);

        Set<String> seenStates = new LinkedHashSet<>();
        for (int r = 0; r < rows.size(); r++) {
            Row row = rows.get(r);
            double top = plotTop() + r * (rowHeight + rowGap);
            add(text(marginLeft - 8, top + rowHeight / 2.0, row.label(), "end", 10, 0.65));

            for (int i = 0; i < times.length; i++) {
                String state = row.states() != null && i < row.states().length ? row.states()[i] : null;
                double left = plotLeft() + i * slotWidth + cellGap / 2;
                if (state == null) {
                    // A missed sample is an outline, not a colour — absence has to look
                    // different from any state, including "unknown".
                    Element hole = rect(left, top + cellGap / 2, markWidth, markHeight, "none");
                    hole.setAttribute("stroke", "currentColor");
                    hole.setAttribute("stroke-opacity", "0.15");
                    hole.setAttribute("stroke-dasharray", "2 2");
                    hole.setAttribute("rx", rounded ? "3" : "0");
                    add(hole);
                    continue;
                }
                Element mark = rect(left, top + cellGap / 2, markWidth, markHeight,
                    stateColor.colorFor(state));
                mark.setAttribute("rx", rounded ? "3" : "0");
                add(mark);
                seenStates.add(state);
                hits.add(new Hit(left, top + cellGap / 2, markWidth, r, i));
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
            if (x >= hit.x() && x <= hit.x() + hit.size()
                && y >= hit.y() && y <= hit.y() + rowHeight) {
                Row row = rows.get(hit.rowIndex());
                String state = row.states()[hit.sampleIndex()];
                Div content = new Div();
                content.addClassName("flex flex-col gap-0.5");
                Div header = new Div();
                header.addClassName("flex items-center gap-2");
                Div swatch = new Div();
                swatch.addClassName("h-2 w-2 shrink-0 rounded-sm");
                swatch.setStyle("background-color", stateColor.colorFor(state));
                Div name = new Div(row.label() + " · " + state);
                name.addClassName("font-semibold");
                header.add(swatch, name);
                Div when = new Div(Scales.timestamp(times[hit.sampleIndex()]));
                when.addClassName("font-mono text-[10px] text-base-content/50");
                content.add(header, when);
                showTooltip(x, y, content);
                return;
            }
        }
        hideTooltip();
    }
}
