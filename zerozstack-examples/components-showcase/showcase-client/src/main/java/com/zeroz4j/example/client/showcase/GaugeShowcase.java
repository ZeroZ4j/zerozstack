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

import com.zeroz4j.ui.chart.Gauge;
import com.zeroz4j.ui.chart.Threshold;
import com.zeroz4j.ui.chart.ValueFormat;
import com.zeroz4j.ui.layout.Div;

public class GaugeShowcase extends ComponentShowcase {

    public GaugeShowcase() {
        super();
        addTitle("Gauge");
        addDescription("One value against a range, coloured by threshold. Where RadialProgress "
            + "shows a percentage, a Gauge shows a reading: it carries a min, a max, a unit and "
            + "threshold arcs that say whether the number is fine, worth watching, or a problem.");

        Gauge memory = new Gauge("unified memory", 0, 121);
        memory.setFormat(ValueFormat.GIGABYTES);
        memory.setThresholds(Threshold.utilisation(85, 110));
        memory.setValue(96.4);

        Gauge util = new Gauge("gpu utilisation", 0, 100);
        util.setFormat(ValueFormat.PERCENT);
        util.setThresholds(Threshold.utilisation(70, 90));
        util.setValue(43);

        Gauge temp = new Gauge("gpu temperature", 20, 95);
        temp.setFormat(ValueFormat.CELSIUS);
        temp.setThresholds(Threshold.utilisation(75, 85));
        temp.setValue(78);

        addSection("Thresholds", box(memory), box(util), box(temp));

        Gauge plain = new Gauge("requests / s", 0, 500);
        plain.setThresholdArcVisible(false);
        plain.setValue(312);

        Gauge thick = new Gauge("cache hit", 0, 100);
        thick.setFormat(ValueFormat.PERCENT);
        thick.setThickness(0.34);
        thick.setThresholdArcVisible(false);
        thick.setValue(91);

        Gauge bare = new Gauge("queue depth", 0, 64);
        bare.setRangeVisible(false);
        bare.setThresholdArcVisible(false);
        bare.setValue(7);

        addSection("Without threshold arcs", box(plain), box(thick), box(bare));

        Gauge unknown = new Gauge("probe unavailable", 0, 100);
        unknown.setThresholdArcVisible(false);
        addSection("No reading", box(unknown));
    }

    private Div box(Gauge gauge) {
        Div host = new Div();
        host.addClassName("w-56");
        host.add(gauge);
        return host;
    }
}
