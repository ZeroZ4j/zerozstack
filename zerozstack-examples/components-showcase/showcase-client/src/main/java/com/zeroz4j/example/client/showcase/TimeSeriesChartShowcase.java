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
package com.zeroz4j.example.client.showcase;

import com.zeroz4j.ui.chart.Series;
import com.zeroz4j.ui.chart.Threshold;
import com.zeroz4j.ui.chart.TimeSeriesChart;
import com.zeroz4j.ui.chart.ValueFormat;
import com.zeroz4j.ui.layout.Div;

public class TimeSeriesChartShowcase extends ComponentShowcase {

    public TimeSeriesChartShowcase() {
        super();
        addTitle("Time Series Chart");
        addDescription("The workhorse dashboard panel: one or more metrics over time, as lines, "
            + "filled areas or a stack. Hover anywhere for a shared crosshair and every series' "
            + "value at that instant. NaN is a gap, not a zero.");

        DemoData data = new DemoData(20260801L);
        long[] times = DemoData.timestamps(90, 2000);

        TimeSeriesChart lines = new TimeSeriesChart();
        lines.setYFormat(ValueFormat.PERCENT);
        lines.setData(times,
            new Series("gpu", data.wave(90, 62, 24, 2.2, 4)),
            new Series("cpu", data.wave(90, 38, 14, 3.1, 3)));
        addSection("Multiple series", full(lines));

        TimeSeriesChart area = new TimeSeriesChart();
        area.setYFormat(ValueFormat.GIGABYTES);
        area.setData(times, new Series("memory in use", data.walk(90, 74, 2.4, 40, 118)).filled());
        addSection("Filled area", full(area));

        TimeSeriesChart stacked = new TimeSeriesChart();
        stacked.setStacked(true);
        stacked.setYFormat(ValueFormat.GIGABYTES);
        stacked.setData(times,
            new Series("agent-asr", data.wave(90, 18, 4, 1.4, 1)),
            new Series("agent-tts", data.wave(90, 26, 6, 0.9, 1)),
            new Series("searxng", data.wave(90, 8, 2, 2.6, 0.5)));
        addSection("Stacked — parts of a whole", full(stacked));

        TimeSeriesChart thresholds = new TimeSeriesChart();
        thresholds.setYFormat(ValueFormat.PERCENT);
        thresholds.setThresholds(Threshold.utilisation(70, 90));
        thresholds.setThresholdBands(true);
        thresholds.setData(times, new Series("disk", data.withStep(90, 55, 93, 0.6, 3)).filled());
        addSection("Threshold bands", full(thresholds));

        TimeSeriesChart gaps = new TimeSeriesChart();
        gaps.setYFormat(ValueFormat.CELSIUS);
        gaps.setZeroBaseline(false);
        gaps.setData(times,
            new Series("gpu temp", DemoData.withGap(data.wave(90, 61, 6, 1.7, 1.2), 34, 12)).points());
        addSection("Gap handling — the probe stopped answering", full(gaps));

        TimeSeriesChart stepped = new TimeSeriesChart();
        stepped.setYFormat(ValueFormat.INTEGER);
        stepped.setData(times,
            new Series("loaded models", data.withStep(90, 2, 4, 0.45, 0)).stepped().filled(),
            new Series("licence cap", data.withStep(90, 6, 6, 0.5, 0)).dashed());
        addSection("Stepped and dashed — discrete values and limits", full(stepped));
    }

    private Div full(TimeSeriesChart chart) {
        Div host = new Div();
        host.addClassName("w-full");
        host.add(chart);
        return host;
    }
}
