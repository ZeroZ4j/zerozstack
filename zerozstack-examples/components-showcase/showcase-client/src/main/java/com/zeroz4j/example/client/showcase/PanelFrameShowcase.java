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
import com.zeroz4j.ui.chart.Series;
import com.zeroz4j.ui.chart.TimeRangePicker;
import com.zeroz4j.ui.chart.TimeSeriesChart;
import com.zeroz4j.ui.chart.ValueFormat;
import com.zeroz4j.ui.component.Button;
import com.zeroz4j.ui.layout.Div;

public class PanelFrameShowcase extends ComponentShowcase {

    public PanelFrameShowcase() {
        super();
        addTitle("Panel Frame");
        addDescription("The chrome around a dashboard panel — title, subtitle, header actions, "
            + "footer — and the four states every panel actually has. A chart on its own is only "
            + "the happy path; a console also has to say 'still loading', 'the probe failed' and "
            + "'nothing came back', and those are three different facts.");

        DemoData data = new DemoData(2468L);
        long[] times = DemoData.timestamps(60, 2000);

        TimeSeriesChart chart = new TimeSeriesChart();
        chart.setYFormat(ValueFormat.GIGABYTES);
        chart.setChartHeight(150);
        chart.setData(times, new Series("in use", data.walk(60, 74, 2.2, 40, 118)).filled());

        PanelFrame ready = new PanelFrame("Unified memory");
        ready.setSubtitle("gx10-8c8b, sampled every 2s");
        ready.addAction(new TimeRangePicker(1));
        ready.setContent(chart);
        ready.setFooter("LocalVitalsProbe via /proc/meminfo");
        addSection("A complete panel", full(ready));

        Div states = new Div();
        states.addClassName("grid w-full gap-4 md:grid-cols-2");

        PanelFrame loading = new PanelFrame("Model inventory");
        loading.setSubtitle("scanning ~/.cache/huggingface");
        loading.setContent(placeholder());
        loading.setState(PanelFrame.State.LOADING);

        PanelFrame error = new PanelFrame("Docker containers");
        error.setSubtitle("docker top");
        error.setContent(placeholder());
        error.setError("permission denied talking to the docker socket");

        PanelFrame noData = new PanelFrame("GPU processes");
        noData.setContent(placeholder());
        noData.setNoDataText("No CUDA processes - the GPU pool is idle");
        noData.setState(PanelFrame.State.NO_DATA);

        PanelFrame dense = new PanelFrame("Compact");
        dense.setDense(true);
        dense.setContent(placeholder());
        Button action = new Button("Rescan");
        action.addClassName("btn-xs btn-ghost");
        dense.addAction(action);

        states.add(loading, error, noData, dense);
        addSection("Loading, error, no data, dense", states);
    }

    private Div placeholder() {
        Div box = new Div();
        box.addClassName("h-24 w-full rounded-lg bg-base-200");
        return box;
    }

    private Div full(PanelFrame panel) {
        Div host = new Div();
        host.addClassName("w-full");
        host.add(panel);
        return host;
    }
}
