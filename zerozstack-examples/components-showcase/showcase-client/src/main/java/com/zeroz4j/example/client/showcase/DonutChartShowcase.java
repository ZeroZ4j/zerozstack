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

import com.zeroz4j.ui.chart.DonutChart;
import com.zeroz4j.ui.chart.ValueFormat;
import com.zeroz4j.ui.layout.Div;
import java.util.ArrayList;
import java.util.List;

public class DonutChartShowcase extends ComponentShowcase {

    public DonutChartShowcase() {
        super();
        addTitle("Donut Chart");
        addDescription("Composition of a whole, when the total is meaningful and the parts are "
            + "few. Beyond about six slices the small ones become unreadable and a Bar Chart says "
            + "the same thing more precisely. The hole holds the total.");

        List<DonutChart.Slice> disk = new ArrayList<>();
        disk.add(new DonutChart.Slice("model weights", 412e9));
        disk.add(new DonutChart.Slice("docker images", 88e9));
        disk.add(new DonutChart.Slice("nim cache", 41e9));
        disk.add(new DonutChart.Slice("free", 210e9));

        DonutChart donut = new DonutChart();
        donut.setFormat(ValueFormat.BYTES);
        donut.setCenterLabel("on disk");
        donut.setSlices(disk);
        addSection("Disk by category", box(donut));

        DonutChart pie = new DonutChart();
        pie.setFormat(ValueFormat.INTEGER);
        pie.setInnerRadius(0);
        pie.setCenterLabel(null);
        List<DonutChart.Slice> states = new ArrayList<>();
        states.add(new DonutChart.Slice("running", 7));
        states.add(new DonutChart.Slice("exited", 3));
        states.add(new DonutChart.Slice("paused", 1));
        pie.setSlices(states);
        addSection("Pie — inner radius zero", box(pie));

        DonutChart thin = new DonutChart();
        thin.setFormat(ValueFormat.GIGABYTES);
        thin.setInnerRadius(0.82);
        thin.setCenterLabel("of 121 GB");
        thin.setLegendPercent(false);
        List<DonutChart.Slice> memory = new ArrayList<>();
        memory.add(new DonutChart.Slice("resident", 62.4));
        memory.add(new DonutChart.Slice("cached", 21.8));
        memory.add(new DonutChart.Slice("available", 36.8));
        thin.setSlices(memory);
        addSection("Thin ring", box(thin));
    }

    private Div box(DonutChart chart) {
        Div host = new Div();
        host.addClassName("w-72");
        host.add(chart);
        return host;
    }
}
