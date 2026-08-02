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

import com.zeroz4j.ui.chart.Threshold;
import com.zeroz4j.ui.component.KpiTile;
import com.zeroz4j.ui.component.Sparkline;
import com.zeroz4j.ui.layout.Div;

public class KpiTileShowcase extends ComponentShowcase {

    public KpiTileShowcase() {
        super();
        addTitle("KPI Tile");
        addDescription("A dashboard stat tile: label, big value, an optional movement line and a "
            + "trend sparkline. The row of these across the top of a console is what an operator "
            + "reads first, before any chart.");

        DemoData data = new DemoData(1024L);

        KpiTile memory = new KpiTile("Unified memory available");
        memory.value("24.6", "GB");
        memory.setDirection(KpiTile.Direction.UP_IS_GOOD);
        memory.setDelta(24.6, 32.8, " GB");
        memory.trend(data.walk(30, 30, 2, 18, 44));

        KpiTile resident = new KpiTile("Held by CUDA processes");
        resident.value("96.4", "GB");
        resident.setDirection(KpiTile.Direction.DOWN_IS_GOOD);
        resident.setDelta(96.4, 88.2, " GB");
        resident.trend(data.walk(30, 90, 3, 70, 110));

        KpiTile util = new KpiTile("GPU utilisation");
        util.value("43", "%");
        util.setDelta(43, 31, " pts");
        util.trend(data.wave(30, 45, 20, 2, 4));

        KpiTile temp = new KpiTile("GPU temperature");
        temp.value("78", "°C");
        temp.setDirection(KpiTile.Direction.DOWN_IS_GOOD);
        temp.setDelta(78, 74, " °C");
        temp.setValueColor(Threshold.colorFor(Threshold.utilisation(75, 85), 78, null));
        temp.trend(data.wave(30, 74, 5, 1.4, 1));

        Div row = new Div();
        row.addClassName("flex w-full flex-wrap gap-3");
        row.add(memory, resident, util, temp);
        addSection("A KPI row — direction decides the colour, not the sign", row);

        KpiTile neutral = new KpiTile("Open connections");
        neutral.value("1,284");
        neutral.setDirection(KpiTile.Direction.NEUTRAL);
        neutral.setDelta(1284, 1102);
        neutral.trend(data.walk(30, 1200, 60, 900, 1500));

        KpiTile bars = new KpiTile("Requests / minute");
        bars.value("8.2", "K");
        bars.setDelta(8200, 7400);
        bars.sparkline().setMode(Sparkline.Mode.BAR);
        bars.trend(data.positive(20, 9000));

        KpiTile marked = new KpiTile("p95 latency");
        marked.value("212", "ms");
        marked.setDirection(KpiTile.Direction.DOWN_IS_GOOD);
        marked.setDelta(212, 180, " ms");
        marked.sparkline().setMarkersVisible(true).setBaselineVisible(true);
        marked.trend(data.wave(30, 200, 40, 2, 12));

        Div variants = new Div();
        variants.addClassName("flex w-full flex-wrap gap-3");
        variants.add(neutral, bars, marked);
        addSection("Neutral direction, bar trend, markers and baseline", variants);

        KpiTile bare = new KpiTile("Containers running");
        bare.value("6");
        bare.setTrendVisible(false);

        KpiTile unknown = new KpiTile("Probe status");
        unknown.value("—");
        unknown.setDelta(Double.NaN, Double.NaN);
        unknown.setTrendVisible(false);

        addSection("No history to show", bare, unknown);
    }
}
