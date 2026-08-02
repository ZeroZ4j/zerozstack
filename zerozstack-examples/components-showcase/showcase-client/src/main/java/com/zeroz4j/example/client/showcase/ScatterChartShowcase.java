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

import com.zeroz4j.ui.chart.ScatterChart;
import com.zeroz4j.ui.chart.ValueFormat;
import com.zeroz4j.ui.layout.Div;
import java.util.ArrayList;
import java.util.List;

public class ScatterChartShowcase extends ComponentShowcase {

    public ScatterChartShowcase() {
        super();
        addTitle("Scatter Chart");
        addDescription("Two measurements plotted against each other, to find out whether they "
            + "are related — 'is the slowdown actually about memory pressure?', a question two "
            + "time series side by side can only hint at. Point size and colour add two more "
            + "dimensions.");

        DemoData data = new DemoData(606L);

        List<ScatterChart.Point> plain = new ArrayList<>();
        for (int i = 0; i < 160; i++) {
            double memory = 30 + data.pick() * 85;
            // Latency climbs sharply once memory pressure passes about 90 GB.
            double latency = 40 + Math.max(0, memory - 88) * 14 + data.pick() * 40;
            plain.add(new ScatterChart.Point(memory, latency));
        }
        ScatterChart correlation = new ScatterChart();
        correlation.setXFormat(ValueFormat.GIGABYTES);
        correlation.setYFormat(ValueFormat.DURATION);
        correlation.setAxisLabels("memory in use", "p95 latency");
        correlation.setPoints(plain);
        addSection("Correlation", full(correlation));

        String[] models = {"llama-70b", "nemotron-49b", "qwen3-32b"};
        List<ScatterChart.Point> categorised = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            String model = models[(int) (data.pick() * models.length)];
            double base = model.equals("llama-70b") ? 88 : model.equals("nemotron-49b") ? 62 : 40;
            categorised.add(new ScatterChart.Point(
                base + data.pick() * 20,
                60 + data.pick() * 180,
                data.pick() * 900,
                model));
        }
        ScatterChart bubbles = new ScatterChart();
        bubbles.setXFormat(ValueFormat.GIGABYTES);
        bubbles.setYFormat(ValueFormat.DURATION);
        bubbles.setAxisLabels("memory", "latency");
        bubbles.setRadiusRange(3, 14);
        bubbles.setOpacity(0.6);
        bubbles.setPoints(categorised);
        addSection("Category colour and bubble size — size scales by area, not radius", full(bubbles));
    }

    private Div full(ScatterChart chart) {
        Div host = new Div();
        host.addClassName("w-full");
        host.add(chart);
        return host;
    }
}
