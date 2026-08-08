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

import com.zeroz4j.ui.chart.MetricTable;
import com.zeroz4j.ui.chart.Threshold;
import com.zeroz4j.ui.chart.ValueFormat;
import com.zeroz4j.ui.layout.Div;
import java.util.ArrayList;
import java.util.List;

public class MetricTableShowcase extends ComponentShowcase {

    /** One row of the demo: a container and what it is consuming. */
    private record ContainerStat(String name, String image, double memoryGb, double cpuPct,
                                 double[] trend, String state) {
    }

    public MetricTableShowcase() {
        super();
        addTitle("Metric Table");
        addDescription("A table whose cells are measurements: threshold-coloured numbers, inline "
            + "trend sparklines, in-cell bars and state pills, sortable by clicking a header. A "
            + "row of numbers does not tell you which one is a problem, and a chart of forty "
            + "series is unreadable — this gives every row its own miniature chart.");

        DemoData data = new DemoData(4711L);
        List<ContainerStat> stats = new ArrayList<>();
        stats.add(new ContainerStat("agent-asr", "nvcr.io/nim/parakeet", 18.4, 62,
            data.wave(24, 18, 3, 1.5, 1), "running"));
        stats.add(new ContainerStat("agent-tts-nim", "nvcr.io/riva-tts", 26.1, 41,
            data.wave(24, 26, 4, 0.8, 1), "running"));
        stats.add(new ContainerStat("spark-searxng", "ghcr.io/searxng", 0.9, 4,
            data.wave(24, 0.9, 0.2, 2.4, 0.05), "running"));
        stats.add(new ContainerStat("vllm-qwen3", "vllm/vllm-openai", 64.8, 96,
            data.wave(24, 60, 9, 1.1, 3), "unhealthy"));
        stats.add(new ContainerStat("postgres", "postgres:16", 1.2, 7,
            data.wave(24, 1.2, 0.3, 3.2, 0.08), "running"));
        stats.add(new ContainerStat("old-agent", "nvcr.io/legacy", 0, 0,
            data.wave(24, 0, 0, 1, 0), "exited"));

        MetricTable<ContainerStat> table = new MetricTable<>();
        table.addTextColumn("container", ContainerStat::name);
        table.addTextColumn("image", ContainerStat::image);
        table.addValueColumn("memory", ContainerStat::memoryGb, ValueFormat.GIGABYTES,
            Threshold.utilisation(30, 60));
        table.addBarColumn("cpu", ContainerStat::cpuPct, 0, 100, ValueFormat.PERCENT,
            Threshold.utilisation(70, 90));
        table.addSparklineColumn("trend", ContainerStat::trend);
        table.addStateColumn("state", ContainerStat::state);
        table.setItems(stats);
        table.sortBy(2, true);
        addSection("Containers by memory — click any header to re-sort", full(table));

        MetricTable<ContainerStat> plain = new MetricTable<>();
        plain.addTextColumn("container", ContainerStat::name);
        plain.addValueColumn("cpu", ContainerStat::cpuPct, ValueFormat.PERCENT);
        plain.setZebra(false);
        plain.setSortable(false);
        plain.setItems(stats);
        addSection("Minimal — no zebra, not sortable", full(plain));

        MetricTable<ContainerStat> empty = new MetricTable<>();
        empty.addTextColumn("container", ContainerStat::name);
        empty.addValueColumn("memory", ContainerStat::memoryGb, ValueFormat.GIGABYTES);
        empty.setEmptyText("No containers are running");
        addSection("Empty state", full(empty));
    }

    private Div full(MetricTable<ContainerStat> table) {
        Div host = new Div();
        host.addClassName("w-full");
        host.add(table);
        return host;
    }
}
