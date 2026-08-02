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

import com.zeroz4j.ui.chart.BarGauge;
import com.zeroz4j.ui.chart.Threshold;
import com.zeroz4j.ui.chart.ValueFormat;
import com.zeroz4j.ui.layout.Div;
import java.util.ArrayList;
import java.util.List;

public class BarGaugeShowcase extends ComponentShowcase {

    public BarGaugeShowcase() {
        super();
        addTitle("Bar Gauge");
        addDescription("The densest way to show the same measurement across many things: "
            + "utilisation per mount, memory per container, load per core. Built from DOM rather "
            + "than SVG, so labels truncate properly and the whole thing reflows on resize.");

        List<BarGauge.Row> mounts = new ArrayList<>();
        mounts.add(new BarGauge.Row("/", 62));
        mounts.add(new BarGauge.Row("/home", 41));
        mounts.add(new BarGauge.Row("/var/lib/docker", 88));
        mounts.add(new BarGauge.Row("/mnt/nas", 94));

        BarGauge basic = new BarGauge();
        basic.setRange(0, 100);
        basic.setFormat(ValueFormat.PERCENT);
        basic.setThresholds(Threshold.utilisation(75, 90));
        basic.setRows(mounts);
        addSection("Basic — thresholds colour the bar", full(basic));

        BarGauge lcd = new BarGauge();
        lcd.setRange(0, 100);
        lcd.setFormat(ValueFormat.PERCENT);
        lcd.setThresholds(Threshold.utilisation(75, 90));
        lcd.setDisplay(BarGauge.Display.LCD);
        lcd.setRows(mounts);
        addSection("LCD — each segment coloured by the value it represents", full(lcd));

        BarGauge gradient = new BarGauge();
        gradient.setRange(0, 100);
        gradient.setFormat(ValueFormat.PERCENT);
        gradient.setThresholds(Threshold.utilisation(75, 90));
        gradient.setDisplay(BarGauge.Display.GRADIENT);
        gradient.setRows(mounts);
        addSection("Gradient", full(gradient));

        List<BarGauge.Row> cores = new ArrayList<>();
        DemoData data = new DemoData(77L);
        for (int i = 0; i < 10; i++) {
            cores.add(new BarGauge.Row("c" + i, 12 + data.pick() * 82));
        }
        BarGauge vertical = new BarGauge();
        vertical.setRange(0, 100);
        vertical.setFormat(ValueFormat.PERCENT);
        vertical.setThresholds(Threshold.utilisation(70, 90));
        vertical.setOrientation(BarGauge.Orientation.VERTICAL);
        vertical.setVerticalHeight(120);
        vertical.setRows(cores);
        addSection("Vertical — per-core load", full(vertical));

        List<BarGauge.Row> models = new ArrayList<>();
        models.add(new BarGauge.Row("llama-3.3-70b", 140e9));
        models.add(new BarGauge.Row("nemotron-49b", 98e9));
        models.add(new BarGauge.Row("qwen3-32b", 64e9));
        models.add(new BarGauge.Row("whisper-large", 6e9));
        BarGauge ranked = new BarGauge();
        ranked.setFormat(ValueFormat.BYTES);
        ranked.setLabelWidth("9rem");
        ranked.setRows(models);
        ranked.autoRange();
        addSection("Ranked list, auto range", full(ranked));
    }

    private Div full(BarGauge gauge) {
        Div host = new Div();
        host.addClassName("w-full");
        host.add(gauge);
        return host;
    }
}
