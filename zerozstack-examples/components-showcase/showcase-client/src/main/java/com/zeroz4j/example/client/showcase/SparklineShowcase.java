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

import com.zeroz4j.ui.component.Sparkline;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.Span;
import com.zeroz4j.ui.theme.TextStyle;

public class SparklineShowcase extends ComponentShowcase {

    private final DemoData data = new DemoData(90210L);

    public SparklineShowcase() {
        super();
        addTitle("Sparkline");
        addDescription("A tiny inline trend chart, auto-scaled to its data. It draws in "
            + "currentColor by default, so it inherits the surrounding text colour and follows the "
            + "theme for free. For anything with axes or a tooltip, use Time Series Chart.");

        addSection("Modes",
            captioned("area (default)", new Sparkline(), data.wave(40, 50, 20, 2, 4), "text-primary"),
            captioned("line", new Sparkline().setMode(Sparkline.Mode.LINE),
                data.wave(40, 50, 20, 2, 4), "text-info"),
            captioned("bar", new Sparkline().setMode(Sparkline.Mode.BAR),
                data.positive(24, 100), "text-accent"));

        addSection("Delta coloured — green when it ends above where it started, red below",
            captioned("rising", new Sparkline(140, 32).setDeltaColored(true),
                data.withStep(40, 30, 70, 0.4, 5), null),
            captioned("falling", new Sparkline(140, 32).setDeltaColored(true),
                data.withStep(40, 75, 28, 0.45, 5), null));

        addSection("Markers and baseline",
            captioned("min and max marked", new Sparkline(180, 40).setMarkersVisible(true),
                data.wave(50, 50, 24, 2.2, 6), "text-primary"),
            captioned("baseline at the mean", new Sparkline(180, 40).setBaselineVisible(true),
                data.wave(50, 50, 24, 2.2, 6), "text-secondary"),
            captioned("baseline at 60", new Sparkline(180, 40).setBaseline(60).setMarkersVisible(true),
                data.wave(50, 50, 24, 2.2, 6), "text-warning"));

        addSection("Gap handling — a NaN breaks the line rather than reading as zero",
            captioned("probe outage", new Sparkline(220, 44).setMarkersVisible(true),
                DemoData.withGap(data.wave(60, 50, 18, 2, 4), 22, 10), "text-error"));

        Div inline = new Div();
        inline.addClassName("flex items-center gap-2 text-secondary");
        Span label = new Span("requests/s");
        TextStyle.CAPTION.applyTo(label);
        Sparkline spark = new Sparkline(80, 18);
        spark.setValues(data.wave(30, 40, 15, 2, 3));
        Span value = new Span("412");
        value.addClassName("font-mono text-sm font-semibold text-base-content");
        inline.add(label, spark, value);
        addSection("Inline in a line of text", inline);
    }

    private Div captioned(String caption, Sparkline spark, double[] values, String colorClass) {
        spark.setValues(values);
        Div host = new Div();
        host.addClassName("flex flex-col gap-1");
        Span text = new Span(caption);
        TextStyle.CAPTION.applyTo(text);
        Div sparkHost = new Div();
        if (colorClass != null) {
            sparkHost.addClassName(colorClass);
        }
        sparkHost.add(spark);
        host.add(text, sparkHost);
        return host;
    }
}
