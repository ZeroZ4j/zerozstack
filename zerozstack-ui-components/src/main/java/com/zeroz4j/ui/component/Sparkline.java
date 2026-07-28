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
package com.zeroz4j.ui.component;

import com.zeroz4j.ui.layout.Div;
import org.teavm.jso.dom.xml.Element;

/**
 * Tiny inline trend chart (default 100ÃƒÆ’Ã¢â‚¬â€24): a polyline auto-scaled to its data with a
 * soft area fill. For KPI tiles, token burn, and survival trends.
 */
public final class Sparkline extends Div {

    private final int width;
    private final int height;
    private final Element svg;

    public Sparkline() {
        this(100, 24);
    }

    public Sparkline(int width, int height) {
        this.width = width;
        this.height = height;
        addClassName("inline-block align-middle");
        svg = SvgCanvas.el("svg",
            "width", String.valueOf(width),
            "height", String.valueOf(height),
            "viewBox", "0 0 " + width + " " + height);
        getElement().appendChild(svg);
    }

    public void setValues(double[] values) {
        while (svg.getFirstChild() != null) {
            svg.removeChild(svg.getFirstChild());
        }
        if (values == null || values.length < 2) {
            return;
        }
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (double v : values) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        double range = max - min;
        if (range == 0) {
            range = 1;
        }
        StringBuilder points = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            double x = (double) i / (values.length - 1) * (width - 2) + 1;
            double y = height - 2 - (values[i] - min) / range * (height - 4);
            points.append(i == 0 ? "" : " ").append(round1(x)).append(',').append(round1(y));
        }
        // Area fill under the line, then the line itself (currentColor ÃƒÂ¢Ã¢â‚¬Â Ã¢â‚¬â„¢ themes for free).
        Element area = SvgCanvas.el("polygon",
            "points", points + " " + (width - 1) + "," + (height - 1) + " 1," + (height - 1),
            "fill", "currentColor", "opacity", "0.12");
        Element line = SvgCanvas.el("polyline",
            "points", points.toString(),
            "fill", "none", "stroke", "currentColor", "stroke-width", "1.5",
            "stroke-linejoin", "round", "stroke-linecap", "round");
        svg.appendChild(area);
        svg.appendChild(line);
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}

