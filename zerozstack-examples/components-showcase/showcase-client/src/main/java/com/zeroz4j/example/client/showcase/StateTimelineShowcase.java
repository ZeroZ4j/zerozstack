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

import com.zeroz4j.ui.chart.StateTimeline;
import com.zeroz4j.ui.layout.Div;
import java.util.ArrayList;
import java.util.List;
import org.teavm.jso.core.JSDate;

public class StateTimelineShowcase extends ComponentShowcase {

    public StateTimelineShowcase() {
        super();
        addTitle("State Timeline");
        addDescription("Discrete state over time, one lane per subject. A line chart cannot show "
            + "this honestly — interpolating between 'running' and 'exited' has no meaning. State "
            + "is drawn as bands whose edges are the transitions.");

        long now = (long) JSDate.now();
        long from = now - 60 * 60_000L;
        DemoData data = new DemoData(555L);

        List<StateTimeline.Row> containers = new ArrayList<>();
        containers.add(new StateTimeline.Row("agent-asr", lane(from, now, data,
            new String[] {"running", "restarting", "running"})));
        containers.add(new StateTimeline.Row("agent-tts-nim", lane(from, now, data,
            new String[] {"running"})));
        containers.add(new StateTimeline.Row("spark-searxng", lane(from, now, data,
            new String[] {"running", "exited", "running", "unhealthy"})));
        containers.add(new StateTimeline.Row("vllm-qwen3", lane(from, now, data,
            new String[] {"starting", "running", "failed", "running"})));

        StateTimeline containerStates = new StateTimeline();
        containerStates.setRows(containers);
        containerStates.setTimeRange(from, now);
        addSection("Container state, last hour", full(containerStates));

        List<StateTimeline.Row> services = new ArrayList<>();
        services.add(new StateTimeline.Row("api", twoState(from, now, "up", "down", 0.88, data)));
        services.add(new StateTimeline.Row("scheduler", twoState(from, now, "up", "degraded", 0.7, data)));
        services.add(new StateTimeline.Row("store", twoState(from, now, "up", "down", 0.97, data)));

        StateTimeline compact = new StateTimeline();
        compact.setRowHeight(14);
        compact.setBandLabels(false);
        compact.setRows(services);
        compact.setTimeRange(from, now);
        addSection("Compact, no band labels", full(compact));
    }

    /** A lane that walks through the given vocabulary in order, with uneven durations. */
    private static List<StateTimeline.Band> lane(long from, long to, DemoData data, String[] states) {
        List<StateTimeline.Band> bands = new ArrayList<>();
        long cursor = from;
        int index = 0;
        while (cursor < to) {
            long duration = (long) ((to - from) * (0.08 + data.pick() * 0.3));
            long end = Math.min(to, cursor + duration);
            bands.add(new StateTimeline.Band(states[index % states.length], cursor, end));
            cursor = end;
            index++;
        }
        return bands;
    }

    /** Mostly the healthy state, occasionally the other one. */
    private static List<StateTimeline.Band> twoState(long from, long to, String good, String bad,
                                                     double healthShare, DemoData data) {
        List<StateTimeline.Band> bands = new ArrayList<>();
        long cursor = from;
        while (cursor < to) {
            long duration = (long) ((to - from) * (0.04 + data.pick() * 0.18));
            long end = Math.min(to, cursor + duration);
            bands.add(new StateTimeline.Band(data.pick() < healthShare ? good : bad, cursor, end));
            cursor = end;
        }
        return bands;
    }

    private Div full(StateTimeline chart) {
        Div host = new Div();
        host.addClassName("w-full");
        host.add(chart);
        return host;
    }
}
