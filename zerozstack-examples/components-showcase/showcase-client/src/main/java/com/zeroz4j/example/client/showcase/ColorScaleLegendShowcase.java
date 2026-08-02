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

import com.zeroz4j.ui.chart.ColorScaleLegend;
import com.zeroz4j.ui.chart.Palette;
import com.zeroz4j.ui.chart.Threshold;
import com.zeroz4j.ui.chart.ValueFormat;
import com.zeroz4j.ui.layout.Div;

public class ColorScaleLegendShowcase extends ComponentShowcase {

    public ColorScaleLegendShowcase() {
        super();
        addTitle("Color Scale Legend");
        addDescription("The key for a colour-encoded chart. A heatmap or treemap without one is "
            + "a picture, not a measurement — the reader can see that a cell is hotter, but not "
            + "by how much. Kept separate so one key can serve several charts sharing a scale.");

        ColorScaleLegend heat = new ColorScaleLegend();
        heat.setCaption("samples");
        heat.setRange(0, 240);
        heat.setFormat(ValueFormat.INTEGER);
        addSection("Continuous ramp", heat);

        ColorScaleLegend viridis = new ColorScaleLegend();
        viridis.setRamp(Palette.VIRIDIS);
        viridis.setCaption("density");
        viridis.setRange(0, 1000);
        ColorScaleLegend blues = new ColorScaleLegend();
        blues.setRamp(Palette.BLUES);
        blues.setCaption("depth");
        blues.setRange(0, 64);
        blues.setFormat(ValueFormat.INTEGER);
        addSection("Other ramps", viridis, blues);

        ColorScaleLegend thresholds = new ColorScaleLegend();
        thresholds.setThresholds(Threshold.utilisation(75, 90));
        thresholds.setFormat(ValueFormat.PERCENT);
        thresholds.setRange(0, 100);
        addSection("Discrete thresholds", thresholds);

        ColorScaleLegend vertical = new ColorScaleLegend();
        vertical.setOrientation(ColorScaleLegend.Orientation.VERTICAL);
        vertical.setRange(0, 500);
        vertical.setLength(120);
        Div host = new Div();
        host.addClassName("h-40");
        host.add(vertical);
        addSection("Vertical, for a chart gutter", host);
    }
}
