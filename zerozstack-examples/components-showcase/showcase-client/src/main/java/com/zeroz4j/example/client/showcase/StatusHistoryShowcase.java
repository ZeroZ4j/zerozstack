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

import com.zeroz4j.ui.chart.StatusHistory;
import com.zeroz4j.ui.layout.Div;
import java.util.ArrayList;
import java.util.List;

public class StatusHistoryShowcase extends ComponentShowcase {

    public StatusHistoryShowcase() {
        super();
        addTitle("Status History");
        addDescription("One mark per sample, one row per subject. Discrete marks show coverage: "
            + "a missed poll leaves a hole, so 'the probe stopped answering' looks different from "
            + "'the value did not change' — which a band chart cannot distinguish.");

        DemoData data = new DemoData(8080L);
        long[] times = DemoData.timestamps(60, 30_000);

        List<StatusHistory.Row> probes = new ArrayList<>();
        probes.add(new StatusHistory.Row("vitals", states(data, 60, 0.05, "ok", "warn", "error")));
        probes.add(new StatusHistory.Row("docker", states(data, 60, 0.12, "ok", "warn", "error")));
        probes.add(new StatusHistory.Row("models", withHoles(states(data, 60, 0.03, "ok", "warn"), 24, 9)));
        probes.add(new StatusHistory.Row("disk", states(data, 60, 0.2, "ok", "warn", "error")));

        StatusHistory history = new StatusHistory();
        history.setData(times, probes);
        addSection("Probe health, last 30 minutes", full(history));

        StatusHistory strip = new StatusHistory();
        strip.setCellGap(0);
        strip.setRounded(false);
        strip.setRowHeight(14);
        strip.setData(times, probes);
        addSection("Continuous strip — no gap, square marks", full(strip));
    }

    private static String[] states(DemoData data, int count, double disturbance, String... vocabulary) {
        String[] out = new String[count];
        for (int i = 0; i < count; i++) {
            out[i] = data.pickState(vocabulary, disturbance);
        }
        return out;
    }

    /** Nulls stand for samples that never arrived. */
    private static String[] withHoles(String[] states, int from, int length) {
        for (int i = from; i < from + length && i < states.length; i++) {
            states[i] = null;
        }
        return states;
    }

    private Div full(StatusHistory chart) {
        Div host = new Div();
        host.addClassName("w-full");
        host.add(chart);
        return host;
    }
}
