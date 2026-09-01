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

import com.zeroz4j.ui.chart.PanelFrame;
import com.zeroz4j.ui.chart.ValueFormat;
import com.zeroz4j.ui.chart.MetricTable;
import com.zeroz4j.ui.component.Alert;
import com.zeroz4j.ui.component.Button;
import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.component.EmptyState;
import com.zeroz4j.ui.component.Loading;
import com.zeroz4j.ui.component.Skeleton;
import com.zeroz4j.ui.layout.Div;
import java.util.ArrayList;
import java.util.List;
import org.teavm.jso.browser.Window;

/**
 * A panel has four states and most galleries show one. Here they are switched between, so that a
 * reader sees each affordance where it actually belongs rather than in a row next to the others.
 */
public class FourStatesShowcase extends ComponentShowcase {

    /** One row of the demo panel. */
    private record Region(String name, double requests, double errorRate) {
    }

    private static final List<Region> REGIONS = regions();

    /** The hand-built panel's body, swapped whole when the state changes. */
    private final Div body = new Div();

    private final Div stateLabel = new Div("Ready");

    public FourStatesShowcase() {
        super();
        addTitle("The four states of a panel");
        addDescription("A panel that fetches something is in one of four states: nothing asked for "
                + "yet, waiting for an answer, the answer failed, or the answer arrived. Most "
                + "galleries only ever draw the last one.");

        addWhatToCheck("Try this",
                "Press each of the four buttons and read what the panel says.",
                "While it is waiting, check that the wait is announced and not only drawn.",
                "When it fails, check there is a way to try again that the keyboard can reach.",
                "When there is nothing to show, check the panel says why, not just that it is empty.",
                "Broken looks like: an empty grey box with no words, a spinner with no name, or a "
                        + "failure with no way out.");

        addSection("The library's own panel, in each of its four states", builtInPanel());
        addSection("The same four states built by hand, one affordance each", handBuiltPanel());
    }

    // ------------------------------------------------------------------ the built-in panel

    private Component[] builtInPanel() {
        MetricTable<Region> table = new MetricTable<>();
        table.addTextColumn("region", Region::name);
        table.addValueColumn("requests", Region::requests, ValueFormat.AUTO);
        table.addValueColumn("errors", Region::errorRate, ValueFormat.PERCENT_1);
        table.setItems(REGIONS);

        PanelFrame panel = new PanelFrame("Requests by region");
        panel.setId("four-states-panel");
        panel.setSubtitle("last 24 hours");
        panel.setContent(table);
        panel.setNoDataText("Nothing has been asked for yet. Choose a time range above.");
        panel.setState(PanelFrame.State.READY);

        Button nothing = stateButton("Nothing asked for yet", "four-states-nodata", () -> {
            panel.setNoDataText("Nothing has been asked for yet. Choose a time range above.");
            panel.setState(PanelFrame.State.NO_DATA);
        });
        Button waiting = stateButton("Waiting for an answer", "four-states-loading",
                () -> panel.setState(PanelFrame.State.LOADING));
        Button failed = stateButton("The answer failed", "four-states-error", () -> {
            panel.setError("The reporting service did not answer within 30 seconds.");
            panel.setState(PanelFrame.State.ERROR);
        });
        Button ready = stateButton("The answer arrived", "four-states-ready",
                () -> panel.setState(PanelFrame.State.READY));

        Button roundTrip = stateButton("Do the whole round trip", "four-states-roundtrip", () -> {
            panel.setState(PanelFrame.State.LOADING);
            Window.setTimeout(() -> {
                panel.setError("The reporting service did not answer within 30 seconds.");
                panel.setState(PanelFrame.State.ERROR);
            }, 1500);
            Window.setTimeout(() -> panel.setState(PanelFrame.State.READY), 3500);
        });

        Div buttons = new Div();
        buttons.addClassName("flex flex-wrap gap-2 mb-4 w-full");
        buttons.add(nothing, waiting, failed, ready, roundTrip);

        Div host = new Div();
        host.addClassName("w-full");
        host.add(buttons, panel);
        return new Component[] { host };
    }

    // ------------------------------------------------------------------ the hand-built panel

    private Component[] handBuiltPanel() {
        body.addClassName("min-h-64 w-full rounded-box border border-base-300 bg-base-100 p-4");
        stateLabel.addClassName("text-sm text-base-content/60 mb-2");
        stateLabel.setId("four-states-hand-label");

        Button nothing = stateButton("Nothing asked for yet", "hand-nodata", this::showEmpty);
        Button waiting = stateButton("Waiting for an answer", "hand-loading", this::showLoading);
        Button failed = stateButton("The answer failed", "hand-error", this::showError);
        Button ready = stateButton("The answer arrived", "hand-ready", this::showReady);

        Div buttons = new Div();
        buttons.addClassName("flex flex-wrap gap-2 mb-4 w-full");
        buttons.add(nothing, waiting, failed, ready);

        showReady();

        Div host = new Div();
        host.addClassName("w-full");
        host.add(buttons, stateLabel, body);
        return new Component[] { host };
    }

    /** Nothing asked for yet: an EmptyState, which says why it is empty and offers the next step. */
    private void showEmpty() {
        body.removeAll();
        stateLabel.setText("Nothing asked for yet — EmptyState, with a way forward");
        body.add(new EmptyState("inbox", "No regions selected",
                "Pick at least one region and the numbers for it will appear here.")
                .withAction("Select every region", this::showLoading));
    }

    /** Waiting: skeletons where the rows will be, and a named spinner so it is announced. */
    private void showLoading() {
        body.removeAll();
        stateLabel.setText("Waiting — Skeleton for the shape, Loading for the announcement");
        Div rows = new Div();
        rows.addClassName("flex flex-col gap-3");
        for (int i = 0; i < 5; i++) {
            Div row = new Div();
            row.addClassName("flex items-center gap-3");
            Skeleton name = new Skeleton();
            name.addClassName("h-4 w-40");
            Skeleton value = new Skeleton();
            value.addClassName("h-4 w-24");
            Skeleton bar = new Skeleton();
            bar.addClassName("h-4 flex-1");
            row.add(name, value, bar);
            rows.add(row);
        }
        Div spinnerRow = new Div();
        spinnerRow.addClassName("flex items-center gap-3 mt-4 text-sm text-base-content/70");
        Loading spinner = new Loading().withAriaLabel("Fetching requests by region");
        spinnerRow.add(spinner, new Div("Fetching requests by region"));
        body.add(rows, spinnerRow);
    }

    /** Failed: an Alert in its danger tone, carrying the one action that gets the reader out. */
    private void showError() {
        body.removeAll();
        stateLabel.setText("Failed — Alert in its danger tone, with the way out attached");
        Alert alert = Alert.danger("The reporting service did not answer within 30 seconds. "
                + "Nothing was lost; the numbers below are from the last successful fetch.")
                .withHeading("Could not fetch the latest numbers")
                .withAction("Try again", e -> showLoading());
        alert.setId("four-states-hand-alert");
        body.add(alert);
    }

    /** Ready: the actual thing the panel is for. */
    private void showReady() {
        body.removeAll();
        stateLabel.setText("Ready — the table the panel exists to show");
        MetricTable<Region> table = new MetricTable<>();
        table.addTextColumn("region", Region::name);
        table.addValueColumn("requests", Region::requests, ValueFormat.AUTO);
        table.addValueColumn("errors", Region::errorRate, ValueFormat.PERCENT_1);
        table.setItems(REGIONS);
        body.add(table);
    }

    // ------------------------------------------------------------------ helpers

    private static Button stateButton(String label, String id, Runnable action) {
        Button button = new Button(label, e -> action.run());
        button.setId(id);
        button.addClassName("btn-sm");
        return button;
    }

    private static List<Region> regions() {
        List<Region> list = new ArrayList<>();
        list.add(new Region("eu-central-1 Frankfurt", 184_320, 0.4));
        list.add(new Region("eu-west-1 Dublin", 96_112, 1.9));
        list.add(new Region("us-east-1 Virginia", 412_880, 0.7));
        list.add(new Region("ap-northeast-1 東京", 58_004, 2.6));
        list.add(new Region("sa-east-1 São Paulo", 12_450, 5.1));
        return list;
    }
}
