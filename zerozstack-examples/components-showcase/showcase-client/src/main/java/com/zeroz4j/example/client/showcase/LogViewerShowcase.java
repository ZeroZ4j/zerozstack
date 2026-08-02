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

import com.zeroz4j.ui.chart.LogViewer;
import com.zeroz4j.ui.layout.Div;
import java.util.ArrayList;
import java.util.List;
import org.teavm.jso.browser.Window;
import org.teavm.jso.core.JSDate;

public class LogViewerShowcase extends ComponentShowcase {

    private static final String[] LEVELS = {"INFO", "INFO", "INFO", "DEBUG", "WARN", "ERROR"};
    private static final String[] SOURCES = {"vitals", "docker", "models", "archive", "admission"};
    private static final String[] MESSAGES = {
        "sampled /proc/meminfo in 1.4ms",
        "docker top returned 6 containers",
        "scanned ~/.cache/huggingface, 41 entries",
        "admission check passed, 24.6 GB headroom",
        "nvidia-smi reported [N/A] for memory.total, using unified pool",
        "probe timed out after 2000ms, retrying",
        "container agent-asr restarted by the daemon",
        "unified memory still pinned after process exit"
    };

    private final DemoData data = new DemoData(31415L);

    public LogViewerShowcase() {
        super();
        addTitle("Log Viewer");
        addDescription("A live log pane: level-coloured, filterable, and glued to the tail until "
            + "you scroll away. Built on VirtualScroller, so only visible rows exist in the DOM. "
            + "Follow-tail is sticky but not forced — a pane that yanks you back mid-read is "
            + "worse than useless during an incident.");

        LogViewer viewer = new LogViewer();
        viewer.setMaxLines(2000);
        viewer.addClassName("h-72");

        List<LogViewer.Line> seed = new ArrayList<>();
        long now = (long) JSDate.now();
        for (int i = 400; i >= 0; i--) {
            seed.add(line(now - i * 400L));
        }
        viewer.setLines(seed);

        // A live tail, so follow-tail and the filter can be tried against moving data.
        Window.setInterval(() -> viewer.append(line((long) JSDate.now())), 900);

        Div host = new Div();
        host.addClassName("flex h-72 w-full");
        host.add(viewer);
        addSection("Live tail - type in the filter, or pick a level", host);

        LogViewer quiet = new LogViewer();
        quiet.setSourceVisible(false);
        quiet.addClassName("h-40");
        List<LogViewer.Line> few = new ArrayList<>();
        few.add(new LogViewer.Line(now - 4000, "INFO", "server started on port 12000"));
        few.add(new LogViewer.Line(now - 3000, "INFO", "probe selected: LocalVitalsProbe"));
        few.add(new LogViewer.Line(now - 1500, "WARN", "sudo requires a password; skipping drop_caches"));
        few.add(new LogViewer.Line(now - 200, "ERROR", "docker socket: permission denied"));
        quiet.setLines(few);
        Div quietHost = new Div();
        quietHost.addClassName("flex h-40 w-full");
        quietHost.add(quiet);
        addSection("No source column", quietHost);
    }

    private LogViewer.Line line(long at) {
        return new LogViewer.Line(at,
            LEVELS[(int) (data.pick() * LEVELS.length)],
            SOURCES[(int) (data.pick() * SOURCES.length)],
            MESSAGES[(int) (data.pick() * MESSAGES.length)]);
    }
}
