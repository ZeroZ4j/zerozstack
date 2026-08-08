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

import com.zeroz4j.ui.chart.Palette;
import com.zeroz4j.ui.component.Button;
import com.zeroz4j.ui.component.SvgCanvas;
import com.zeroz4j.ui.layout.Div;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.xml.Element;

public class SvgCanvasShowcase extends ComponentShowcase {

    public SvgCanvasShowcase() {
        super();
        addTitle("SVG Canvas");
        addDescription("An interactive SVG surface that pans on drag and zooms on the wheel, "
            + "anchored at the cursor. It is the foundation the chart set is built on; use it "
            + "directly for a node graph, a topology map, or any diagram that needs its own "
            + "geometry. Note that createElement silently fails for SVG — always use "
            + "SvgCanvas.el, which uses the right namespace.");

        SvgCanvas canvas = new SvgCanvas();
        canvas.addClassName("rounded-lg border border-base-300 bg-base-200/40");
        drawTopology(canvas);

        Div host = new Div();
        host.addClassName("h-72 w-full");
        host.add(canvas);
        addSection("Drag to pan, wheel to zoom", host);

        Button fit = new Button("Fit");
        fit.addClassName("btn-sm btn-ghost");
        fit.addClickListener(event -> canvas.fit(420, 240));
        Button reset = new Button("Reset view");
        reset.addClassName("btn-sm btn-ghost");
        reset.addClickListener(event -> canvas.setView(0, 0, 1));
        addSection("View controls", fit, reset);

        // The surface measures zero until the browser has laid it out.
        Window.setTimeout(() -> canvas.fit(420, 240), 60);
    }

    /** A small container topology, drawn straight into the viewport group. */
    private void drawTopology(SvgCanvas canvas) {
        Element viewport = canvas.viewport();
        String[] names = {"agent-asr", "agent-tts", "searxng", "vllm", "postgres"};
        double centreX = 210;
        double centreY = 120;

        for (int i = 0; i < names.length; i++) {
            double angle = Math.PI * 2 * i / names.length - Math.PI / 2;
            double x = centreX + Math.cos(angle) * 130;
            double y = centreY + Math.sin(angle) * 80;

            viewport.appendChild(SvgCanvas.el("line",
                "x1", String.valueOf(centreX), "y1", String.valueOf(centreY),
                "x2", String.valueOf(x), "y2", String.valueOf(y),
                "stroke", "currentColor", "stroke-opacity", "0.25", "stroke-width", "1.5"));

            viewport.appendChild(SvgCanvas.el("circle",
                "cx", String.valueOf(x), "cy", String.valueOf(y), "r", "26",
                "fill", Palette.series(i), "fill-opacity", "0.85"));

            Element label = SvgCanvas.el("text",
                "x", String.valueOf(x), "y", String.valueOf(y + 42),
                "text-anchor", "middle", "font-size", "10",
                "fill", "currentColor", "fill-opacity", "0.7");
            label.appendChild(Window.current().getDocument().createTextNode(names[i]));
            viewport.appendChild(label);
        }

        viewport.appendChild(SvgCanvas.el("circle",
            "cx", String.valueOf(centreX), "cy", String.valueOf(centreY), "r", "34",
            "fill", "var(--color-base-300)",
            "stroke", "currentColor", "stroke-opacity", "0.3"));
        Element hub = SvgCanvas.el("text",
            "x", String.valueOf(centreX), "y", String.valueOf(centreY + 4),
            "text-anchor", "middle", "font-size", "11", "fill", "currentColor");
        hub.appendChild(Window.current().getDocument().createTextNode("gx10"));
        viewport.appendChild(hub);
    }
}
