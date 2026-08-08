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

import com.zeroz4j.ui.chart.BarChart;
import com.zeroz4j.ui.chart.Series;
import com.zeroz4j.ui.chart.ValueFormat;
import com.zeroz4j.ui.layout.Div;
import java.util.Arrays;
import java.util.List;

public class BarChartShowcase extends ComponentShowcase {

    public BarChartShowcase() {
        super();
        addTitle("Bar Chart");
        addDescription("Categorical bars: grouped or stacked, vertical or horizontal. Vertical "
            + "compares categories against each other; horizontal is for ranked lists and long "
            + "labels, where columns would force the captions to be rotated or clipped.");

        List<String> hours = Arrays.asList("00", "03", "06", "09", "12", "15", "18", "21");
        DemoData data = new DemoData(9001L);

        BarChart simple = new BarChart();
        simple.setYFormat(ValueFormat.INTEGER);
        simple.setValueLabels(true);
        simple.setData(hours, new Series("requests", data.positive(8, 900)));
        addSection("Single series with value labels", full(simple));

        BarChart grouped = new BarChart();
        grouped.setYFormat(ValueFormat.INTEGER);
        grouped.setData(hours,
            new Series("ok", data.positive(8, 800)),
            new Series("4xx", data.positive(8, 180)),
            new Series("5xx", data.positive(8, 60)));
        addSection("Grouped", full(grouped));

        BarChart stacked = new BarChart();
        stacked.setStacked(true);
        stacked.setYFormat(ValueFormat.INTEGER);
        stacked.setData(hours,
            new Series("ok", data.positive(8, 800)),
            new Series("4xx", data.positive(8, 180)),
            new Series("5xx", data.positive(8, 60)));
        addSection("Stacked", full(stacked));

        List<String> images = Arrays.asList(
            "nvcr.io/nim/parakeet", "ghcr.io/searxng", "vllm/vllm-openai",
            "nvcr.io/riva-tts", "postgres:16");
        BarChart horizontal = new BarChart();
        horizontal.setHorizontal(true);
        horizontal.setYFormat(ValueFormat.BYTES);
        horizontal.setValueLabels(true);
        horizontal.setChartHeight(180);
        horizontal.setData(images, new Series("on disk", 18.4e9, 1.2e9, 9.8e9, 14.1e9, 0.42e9));
        addSection("Horizontal — ranked, long labels", full(horizontal));
    }

    private Div full(BarChart chart) {
        Div host = new Div();
        host.addClassName("w-full");
        host.add(chart);
        return host;
    }
}
