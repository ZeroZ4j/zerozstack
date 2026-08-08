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
package com.zeroz4j.example.client.showcase;

import com.zeroz4j.ui.chart.PanelFrame;
import com.zeroz4j.ui.chart.RefreshControl;
import com.zeroz4j.ui.chart.Series;
import com.zeroz4j.ui.chart.TimeSeriesChart;
import com.zeroz4j.ui.chart.ValueFormat;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.Span;
import org.teavm.jso.browser.Window;

public class RefreshControlShowcase extends ComponentShowcase {

    private int scans;

    public RefreshControlShowcase() {
        super();
        addTitle("Refresh Control");
        addDescription("Manual refresh plus an auto-refresh interval, with the age of the current "
            + "data on show. The age readout is the point: a dashboard that refreshes silently "
            + "gives no way to tell 'the number has not moved' from 'the number has not been "
            + "fetched', and during an incident those are opposite conclusions.");

        Span log = new Span("no scan yet");
        log.addClassName("font-mono text-xs text-base-content/60");

        RefreshControl manual = new RefreshControl();
        manual.onRefresh(() -> {
            scans++;
            // Real work is asynchronous, so markUpdated lands when the result does — never
            // at the moment the request was sent.
            Window.setTimeout(() -> {
                log.setText("scan #" + scans + " complete");
                manual.markUpdated();
            }, 700);
        });
        Div manualRow = new Div();
        manualRow.addClassName("flex w-full items-center gap-4");
        manualRow.add(manual, log);
        addSection("Manual — click the interval to cycle 5s / 10s / 30s / 1m / 5m", manualRow);

        DemoData data = new DemoData(864L);
        TimeSeriesChart chart = new TimeSeriesChart();
        chart.setYFormat(ValueFormat.INTEGER);
        chart.setChartHeight(140);
        chart.setData(DemoData.timestamps(40, 5000),
            new Series("open files", data.walk(40, 320, 22, 100, 600)).filled());

        RefreshControl auto = new RefreshControl();
        auto.onRefresh(() -> {
            chart.setData(DemoData.timestamps(40, 5000),
                new Series("open files", data.walk(40, 320, 22, 100, 600)).filled());
            auto.markUpdated();
        });
        auto.setInterval(5);
        auto.markUpdated();

        PanelFrame panel = new PanelFrame("Open file descriptors");
        panel.setSubtitle("re-queried every 5 seconds");
        panel.addAction(auto);
        panel.setContent(chart);
        Div host = new Div();
        host.addClassName("w-full");
        host.add(panel);
        addSection("In a panel header, auto-refreshing", host);
    }
}
