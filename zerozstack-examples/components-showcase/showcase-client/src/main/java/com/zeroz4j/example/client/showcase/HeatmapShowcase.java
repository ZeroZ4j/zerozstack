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

import com.zeroz4j.ui.chart.Heatmap;
import com.zeroz4j.ui.chart.Palette;
import com.zeroz4j.ui.chart.ValueFormat;
import com.zeroz4j.ui.layout.Div;

public class HeatmapShowcase extends ComponentShowcase {

    public HeatmapShowcase() {
        super();
        addTitle("Heatmap");
        addDescription("Histograms over time: each column is a time bucket, each cell a value "
            + "band, coloured by how many samples landed in it. A p99 line tells you the tail "
            + "moved; a heatmap tells you whether everything got slower or a second mode appeared.");

        DemoData data = new DemoData(31337L);
        long[] columns = DemoData.timestamps(48, 60_000);
        double[] edges = Heatmap.linearEdges(0, 400, 16);

        // Two populations of request latency, the slow one growing in the second half.
        double[][] samples = new double[columns.length][];
        for (int c = 0; c < columns.length; c++) {
            double slowShare = c < columns.length * 0.55 ? 0.08 : 0.34;
            samples[c] = new double[120];
            for (int i = 0; i < samples[c].length; i++) {
                samples[c][i] = data.pick() < slowShare
                    ? 210 + data.pick() * 150
                    : 45 + data.pick() * 60;
            }
        }
        double[][] counts = Heatmap.bucketise(samples, edges);

        Heatmap latency = new Heatmap();
        latency.setYFormat(ValueFormat.DURATION);
        latency.setData(columns, edges, counts);
        addSection("Request latency distribution", full(latency));

        Heatmap viridis = new Heatmap();
        viridis.setYFormat(ValueFormat.DURATION);
        viridis.setRamp(Palette.VIRIDIS);
        viridis.setData(columns, edges, counts);
        addSection("Viridis ramp — perceptually uniform, colour-vision safe", full(viridis));

        Heatmap blues = new Heatmap();
        blues.setYFormat(ValueFormat.DURATION);
        blues.setRamp(Palette.BLUES);
        blues.setHideEmptyCells(false);
        blues.setData(columns, edges, counts);
        addSection("Single hue, empty cells drawn", full(blues));
    }

    private Div full(Heatmap chart) {
        Div host = new Div();
        host.addClassName("w-full");
        host.add(chart);
        return host;
    }
}
