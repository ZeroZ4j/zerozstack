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

import com.zeroz4j.ui.chart.Treemap;
import com.zeroz4j.ui.chart.ValueFormat;
import com.zeroz4j.ui.layout.Div;
import java.util.ArrayList;
import java.util.List;

public class TreemapShowcase extends ComponentShowcase {

    public TreemapShowcase() {
        super();
        addTitle("Treemap");
        addDescription("Proportional area, for 'what is taking up all the space'. With fifty "
            + "model directories a bar chart is fifty rows of scrolling and a pie chart is "
            + "unreadable; a treemap shows all fifty at once. Squarified layout keeps tiles close "
            + "to square, because area is much harder to judge as an aspect ratio grows.");

        List<Treemap.Node> weights = new ArrayList<>();
        weights.add(new Treemap.Node("llama-3.3-70b", 140e9));
        weights.add(new Treemap.Node("nemotron-49b", 98e9));
        weights.add(new Treemap.Node("qwen3-32b", 64e9));
        weights.add(new Treemap.Node("gemma-27b", 54e9));
        weights.add(new Treemap.Node("whisper-large-v3", 6.2e9));
        weights.add(new Treemap.Node("parakeet-tdt", 2.4e9));

        List<Treemap.Node> images = new ArrayList<>();
        images.add(new Treemap.Node("vllm-openai", 18.4e9));
        images.add(new Treemap.Node("riva-tts", 14.1e9));
        images.add(new Treemap.Node("parakeet-nim", 9.8e9));
        images.add(new Treemap.Node("searxng", 1.2e9));

        List<Treemap.Node> flat = new ArrayList<>();
        flat.addAll(weights);
        Treemap single = new Treemap();
        single.setFormat(ValueFormat.BYTES);
        single.setNodes(flat);
        addSection("One level — model weights on disk", full(single));

        List<Treemap.Node> nested = new ArrayList<>();
        nested.add(new Treemap.Node("huggingface cache", 0, weights));
        nested.add(new Treemap.Node("docker images", 0, images));
        nested.add(new Treemap.Node("nim cache", 41e9));
        nested.add(new Treemap.Node("logs and misc", 7.5e9));

        Treemap hierarchy = new Treemap();
        hierarchy.setFormat(ValueFormat.BYTES);
        hierarchy.setChartHeight(320);
        hierarchy.setNodes(nested);
        addSection("Two levels — parents sum their children", full(hierarchy));
    }

    private Div full(Treemap chart) {
        Div host = new Div();
        host.addClassName("w-full");
        host.add(chart);
        return host;
    }
}
