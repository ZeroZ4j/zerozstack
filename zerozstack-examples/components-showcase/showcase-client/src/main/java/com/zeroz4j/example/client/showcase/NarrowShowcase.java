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
import com.zeroz4j.ui.chart.PanelFrame;
import com.zeroz4j.ui.chart.Threshold;
import com.zeroz4j.ui.chart.ValueFormat;
import com.zeroz4j.ui.component.Button;
import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.component.Dialog;
import com.zeroz4j.ui.component.KpiTile;
import com.zeroz4j.ui.component.LaneTimeline;
import com.zeroz4j.ui.component.Select;
import com.zeroz4j.ui.component.SplitPane;
import com.zeroz4j.ui.component.Table;
import com.zeroz4j.ui.component.TextField;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.FormLayout;
import com.zeroz4j.ui.layout.Span;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.teavm.jso.core.JSDate;

/**
 * The densest things the library has, in a box 360 pixels wide. A narrow window can be looked at
 * without anybody resizing the browser, and what pushes out is obvious next to what does not.
 */
public class NarrowShowcase extends ComponentShowcase {

    /** One row of the demo table. */
    private record Node(String name, double memoryGb, double cpuPct, String state) {
    }

    /** The width every box on this page is pinned to. */
    private static final String NARROW = "360px";

    public NarrowShowcase() {
        super();
        addTitle("Everything, 360 pixels wide");
        addDescription("A telephone is about 360 pixels across. Every box on this page is exactly "
                + "that wide, so what a narrow window does to the dense components can be seen "
                + "here, side by side with the page around it.");

        addWhatToCheck("Try this",
                "Look at the edge of each grey box. Nothing should stick out past it.",
                "Where a wide thing does not fit, it should have its own sideways scrollbar inside "
                        + "the box — the page itself must never scroll sideways.",
                "Open the box asked for at 56 rem, which is far wider than 360 pixels. It should "
                        + "shrink to fit the window, not run off the edge of it.",
                "Move the divider between the two panels with the arrow keys.",
                "Broken looks like: a sideways scrollbar on the whole page, a table's last column "
                        + "cut off with no way to reach it, or a dialog whose buttons are off screen.");

        addSection("A table of eight columns", narrowBox(wideTable()));
        addSection("A metric table with bars and sparklines", narrowBox(metricTable()));
        addSection("Two panels either side of a divider", narrowBox(splitPane()));
        addSection("A lane timeline", narrowBox(laneTimeline()));
        addSection("Three tiles that want a row each", narrowBox(tiles()));
        addSection("A panel with a heading, a subtitle and two actions", narrowBox(panel()));
        addSection("A dialog asked for 56 rem", narrowBox(wideDialog()));
    }

    // ------------------------------------------------------------------ the narrow box

    /** The whole point: a container of exactly 360 pixels, marked so the edge is visible. */
    private static Component narrowBox(Component content) {
        Div frame = new Div();
        frame.addClassName("rounded-box border-2 border-dashed border-warning/60 p-2 bg-base-100");
        frame.setWidth(NARROW);
        frame.setStyle("max-width", NARROW);
        frame.add(content);

        Div outer = new Div();
        outer.addClassName("w-full");
        Span caption = new Span("360 pixels wide — nothing may stick out past the dashed edge");
        caption.addClassName("block text-xs text-base-content/50 mb-2");
        outer.add(caption, frame);
        return outer;
    }

    // ------------------------------------------------------------------ the dense things

    private static Component wideTable() {
        Table table = new Table();
        table.setId("narrow-table");
        table.addClassName("table-zebra table-xs");

        String[] headers = { "node", "region", "memory", "cpu", "disk", "state", "since", "owner" };
        Component thead = element("thead");
        Component headRow = element("tr");
        for (String header : headers) {
            headRow.getElement().appendChild(cell("th", header).getElement());
        }
        thead.getElement().appendChild(headRow.getElement());

        Component tbody = element("tbody");
        for (int i = 0; i < 12; i++) {
            Component tr = element("tr");
            tr.getElement().appendChild(cell("td", "node-" + (i + 1)).getElement());
            tr.getElement().appendChild(cell("td", i % 2 == 0 ? "eu-central-1" : "ap-northeast-1")
                    .getElement());
            tr.getElement().appendChild(cell("td", (8 + i * 3) + " GB").getElement());
            tr.getElement().appendChild(cell("td", (12 + i * 6) + " %").getElement());
            tr.getElement().appendChild(cell("td", (140 + i * 11) + " GB").getElement());
            tr.getElement().appendChild(cell("td", i % 5 == 0 ? "unhealthy" : "running").getElement());
            tr.getElement().appendChild(cell("td", (i + 1) + " d").getElement());
            tr.getElement().appendChild(cell("td", "platform-team").getElement());
            tbody.getElement().appendChild(tr.getElement());
        }

        table.getElement().appendChild(thead.getElement());
        table.getElement().appendChild(tbody.getElement());

        // A wide thing that does not fit scrolls inside its own box, never sideways on the page.
        Div scroller = new Div();
        scroller.addClassName("overflow-x-auto w-full");
        scroller.add(table);
        return scroller;
    }

    private static Component metricTable() {
        List<Node> nodes = new ArrayList<>();
        DemoData data = new DemoData(99L);
        nodes.add(new Node("agent-asr-frankfurt", 18.4, 62, "running"));
        nodes.add(new Node("vllm-qwen3-tokyo", 64.8, 96, "unhealthy"));
        nodes.add(new Node("postgres-primary", 1.2, 7, "running"));
        nodes.add(new Node("old-agent", 0, 0, "exited"));

        MetricTable<Node> table = new MetricTable<>();
        table.setId("narrow-metric-table");
        table.addTextColumn("node", Node::name);
        table.addValueColumn("memory", Node::memoryGb, ValueFormat.GIGABYTES,
                Threshold.utilisation(30, 60));
        table.addBarColumn("cpu", Node::cpuPct, 0, 100, ValueFormat.PERCENT,
                Threshold.utilisation(70, 90));
        table.addSparklineColumn("trend", node -> data.wave(24, node.memoryGb(), 3, 1.5, 1));
        table.addStateColumn("state", Node::state);
        table.setItems(nodes);

        Div scroller = new Div();
        scroller.addClassName("overflow-x-auto w-full");
        scroller.add(table);
        return scroller;
    }

    private static Component splitPane() {
        SplitPane split = SplitPane.horizontal("narrow-demo", 150, 60, 280);
        split.setId("narrow-split");
        split.setAriaLabel("Move the divider between the file list and the details");

        Div files = new Div();
        files.addClassName("p-2 text-xs overflow-auto");
        for (int i = 0; i < 10; i++) {
            Div file = new Div("very-long-file-name-" + i + ".java");
            file.addClassName("truncate py-0.5");
            files.add(file);
        }

        Div details = new Div();
        details.addClassName("p-2 text-xs overflow-auto");
        details.add(new Div("The details of whichever file is chosen appear here. On a narrow "
                + "screen this side is the one that gets squeezed."));

        split.setFirst(files);
        split.setSecond(details);

        Div host = new Div();
        host.addClassName("h-48 w-full rounded border border-base-300 overflow-hidden");
        host.add(split);
        return host;
    }

    private static Component laneTimeline() {
        long now = (long) JSDate.now();
        long start = now - 8 * 60_000L;
        DemoData data = new DemoData(1717L);

        List<LaneTimeline.Lane> lanes = new ArrayList<>();
        lanes.add(lane("worker-0 qwen36-27b", "COMPLETED", start, start + 5 * 60_000L, data, 6));
        lanes.add(lane("worker-1 sonnet-4-6", "RUNNING", start + 60_000L, 0, data, 4));
        lanes.add(lane("worker-2 gpt-oss-120b", "FAILED", start + 30_000L,
                start + 3 * 60_000L, data, 3));

        LaneTimeline timeline = new LaneTimeline();
        timeline.setId("narrow-lane-timeline");
        timeline.setLabelWidth(90);
        timeline.setLanes(lanes);

        Div host = new Div();
        host.addClassName("w-full overflow-x-auto");
        host.add(timeline);
        return host;
    }

    private static Component tiles() {
        Div host = new Div();
        host.addClassName("grid grid-cols-1 gap-2 w-full");
        host.add(new KpiTile("Requests per second").value("18.204", "req/s")
                .delta("+ 12 % on last week", true));
        host.add(new KpiTile("Errors").value("0,4", "%").delta("- 0,2 points", true));
        host.add(new KpiTile("Longest response").value("4.812", "ms")
                .delta("+ 1.100 ms", false));
        return host;
    }

    private static Component panel() {
        FormLayout form = new FormLayout();
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        TextField search = new TextField().withLabel("Search");
        search.setId("narrow-search");
        Select region = new Select().withLabel("Region");
        region.setId("narrow-region");
        region.setItems(Arrays.asList("eu-central-1 Frankfurt", "ap-northeast-1 Tokyo",
                "us-east-1 Northern Virginia"));
        form.add(search, region);

        PanelFrame panel = new PanelFrame("Nodes in the selected region");
        panel.setId("narrow-panel");
        panel.setSubtitle("updated a moment ago");
        panel.addAction(smallButton("Refresh", "narrow-panel-refresh"));
        panel.addAction(smallButton("Export", "narrow-panel-export"));
        panel.setContent(form);
        panel.setFooter("Three of eleven nodes shown");
        return panel;
    }

    private static Component wideDialog() {
        Dialog dialog = new Dialog("A panel asked for 56 rem in a 360-pixel window");
        dialog.setId("narrow-wide-dialog");
        dialog.setWidth("56rem");
        dialog.add(new Div("This box was asked for 56 rem — about 900 pixels. The window is far "
                + "narrower than that. It should shrink to fit and keep its buttons on screen."));
        dialog.add(wideTable());
        Button close = new Button("Close", e -> dialog.close());
        close.addClassName("btn-primary");
        dialog.addAction(close);

        Button open = new Button("Open the too-wide box");
        open.setId("narrow-wide-dialog-open");
        open.addClickListener(e -> dialog.open());

        Div host = new Div();
        host.addClassName("w-full");
        host.add(open, dialog);
        return host;
    }

    // ------------------------------------------------------------------ helpers

    private static Button smallButton(String label, String id) {
        Button button = new Button(label);
        button.setId(id);
        button.addClassName("btn-xs btn-ghost");
        return button;
    }

    private static LaneTimeline.Lane lane(String label, String outcome, long openedAt,
                                          long closedAt, DemoData data, int eventCount) {
        long end = closedAt > 0 ? closedAt : openedAt + 6 * 60_000L;
        List<Long> events = new ArrayList<>();
        for (int i = 0; i < eventCount; i++) {
            events.add(openedAt + (long) (data.pick() * (end - openedAt)));
        }
        return new LaneTimeline.Lane(label, outcome, openedAt, closedAt, events);
    }

    private static Component cell(String tag, String text) {
        Component cell = element(tag);
        cell.getElement().setTextContent(text);
        return cell;
    }

    private static Component element(String tag) {
        return new Component(tag) {
        };
    }
}
