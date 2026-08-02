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

import com.zeroz4j.ui.chart.Threshold;
import com.zeroz4j.ui.chart.ValueFormat;
import com.zeroz4j.ui.component.Badge;
import com.zeroz4j.ui.component.PropertyGrid;
import com.zeroz4j.ui.component.StatusDot;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.Span;

public class PropertyGridShowcase extends ComponentShowcase {

    public PropertyGridShowcase() {
        super();
        addTitle("Property Grid");
        addDescription("Key-value pairs in an aligned two-column grid. The right side takes a "
            + "string or any component, so a property can carry a status dot, a badge or a meter "
            + "rather than being flattened to text.");

        PropertyGrid host = new PropertyGrid();
        host.row("hostname", "gx10-8c8b");
        host.row("architecture", "aarch64");
        host.row("gpu", "NVIDIA GB10");
        host.row("driver", "580.173.02");
        host.row("unified memory", ValueFormat.GIGABYTES.format(121));
        host.row("os", "Ubuntu 24.04");
        addSection("Plain values", full(host));

        PropertyGrid rich = new PropertyGrid();
        rich.row("probe", withDot("RUNNING", "LocalVitalsProbe"));
        rich.row("sample interval", "2s");
        rich.row("cuda processes", badge("6", "badge-primary"));
        rich.row("memory pressure", coloured("94 %", Threshold.utilisation(75, 90), 94));
        rich.row("last error", coloured("none", Threshold.utilisation(75, 90), 0));
        addSection("Component values", full(rich));
    }

    private Div withDot(String state, String text) {
        Div row = new Div();
        row.addClassName("flex items-center gap-2");
        Span caption = new Span(text);
        row.add(new StatusDot(state), caption);
        return row;
    }

    private Badge badge(String text, String variant) {
        Badge badge = new Badge(text);
        badge.addClassName(variant);
        return badge;
    }

    private Span coloured(String text, java.util.List<Threshold> thresholds, double value) {
        Span span = new Span(text);
        span.addClassName("font-mono font-semibold");
        span.setStyle("color", Threshold.colorFor(thresholds, value, "currentColor"));
        return span;
    }

    private Div full(PropertyGrid grid) {
        Div box = new Div();
        box.addClassName("w-full max-w-md");
        box.add(grid);
        return box;
    }
}
