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

import com.zeroz4j.ui.chart.RollingChart;
import com.zeroz4j.ui.chart.Threshold;
import com.zeroz4j.ui.chart.ValueFormat;
import com.zeroz4j.ui.component.Button;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.HorizontalLayout;
import org.teavm.jso.browser.Window;
import org.teavm.jso.core.JSDate;

public class RollingChartShowcase extends ComponentShowcase {

    private final DemoData data = new DemoData(4242L);
    private double memory = 74;
    private double util = 55;

    public RollingChartShowcase() {
        super();
        addTitle("Rolling Chart");
        addDescription("Live telemetry with a fixed window: samples are pushed in, the window "
            + "slides, old samples fall off the left. Redraw is decoupled from data arrival, so "
            + "the trace scrolls smoothly at any sample rate and a stalled feed shows as a "
            + "growing gap at the right edge rather than a frozen chart.");

        RollingChart live = new RollingChart(180, "memory in use");
        live.setWindow(60_000);
        live.setYFormat(ValueFormat.GIGABYTES);
        live.setYBounds(0, 121);
        live.setThresholds(Threshold.utilisation(90, 110));
        live.channel(0).filled();
        live.start();

        RollingChart multi = new RollingChart(180, "gpu", "cpu");
        multi.setWindow(60_000);
        multi.setYFormat(ValueFormat.PERCENT);
        multi.setYBounds(0, 100);
        multi.start();

        // One feed drives both charts, at the 2s cadence the real vitals probe uses.
        Window.setInterval(() -> {
            memory = clamp(memory + data.pick() * 6 - 3, 30, 118);
            util = clamp(util + data.pick() * 18 - 9, 2, 99);
            live.push(memory);
            multi.push(util, clamp(util * 0.6 + data.pick() * 12, 2, 99));
        }, 2000);

        // Seed the window so the panel is not empty on first paint.
        long now = (long) JSDate.now();
        for (int i = 30; i >= 0; i--) {
            memory = clamp(memory + data.pick() * 6 - 3, 30, 118);
            util = clamp(util + data.pick() * 18 - 9, 2, 99);
            live.push(now - i * 2000L, memory);
            multi.push(now - i * 2000L, util, clamp(util * 0.6 + data.pick() * 12, 2, 99));
        }

        addSection("Streaming, one minute window", full(live));
        addSection("Two channels", full(multi));

        Button pause = new Button("Pause");
        pause.addClassName("btn-sm");
        pause.addClickListener(event -> {
            if (live.isStreaming()) {
                live.stop();
                multi.stop();
                pause.setText("Resume");
            } else {
                live.start();
                multi.start();
                pause.setText("Pause");
            }
        });
        Button clear = new Button("Clear");
        clear.addClassName("btn-sm btn-ghost");
        clear.addClickListener(event -> {
            live.clear();
            multi.clear();
        });
        HorizontalLayout controls = new HorizontalLayout(pause, clear);
        controls.addClassName("gap-2");
        addSection("Controls", controls);
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : value > max ? max : value;
    }

    private Div full(RollingChart chart) {
        Div host = new Div();
        host.addClassName("w-full");
        host.add(chart);
        return host;
    }
}
