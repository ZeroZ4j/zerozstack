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

import com.zeroz4j.ui.chart.Histogram;
import com.zeroz4j.ui.chart.Palette;
import com.zeroz4j.ui.chart.ValueFormat;
import com.zeroz4j.ui.layout.Div;

public class HistogramShowcase extends ComponentShowcase {

    public HistogramShowcase() {
        super();
        addTitle("Histogram");
        addDescription("Distribution of a set of samples. The companion to Heatmap — same "
            + "question, no time dimension. Where an average hides a bimodal distribution, a "
            + "histogram shows both modes, which is usually the whole finding.");

        DemoData data = new DemoData(112233L);

        Histogram bimodal = new Histogram();
        bimodal.setXFormat(ValueFormat.DURATION);
        bimodal.setBucketCount(28);
        bimodal.setValues(data.bimodal(2400, 70, 260, 38));
        addSection("Bimodal latency — a fast path and a slow one", full(bimodal));

        Histogram sizes = new Histogram();
        sizes.setXFormat(ValueFormat.BYTES);
        sizes.setBucketCount(16);
        sizes.setColor(Palette.INFO);
        sizes.setValues(data.positive(1500, 4.5e6));
        addSection("Response sizes, custom colour", full(sizes));

        Histogram coarse = new Histogram();
        coarse.setXFormat(ValueFormat.PERCENT);
        coarse.setBucketCount(10);
        coarse.setBarGap(4);
        coarse.setValues(data.wave(600, 55, 30, 9, 12));
        addSection("Ten buckets, wider gaps", full(coarse));
    }

    private Div full(Histogram chart) {
        Div host = new Div();
        host.addClassName("w-full");
        host.add(chart);
        return host;
    }
}
