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
package com.zeroz4j.ui.chart;

import com.zeroz4j.ui.component.Button;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.Span;
import com.zeroz4j.ui.theme.TextStyle;
import java.util.ArrayList;
import java.util.List;
import org.teavm.jso.browser.Window;

/**
 * Manual refresh plus an auto-refresh interval, with the age of the current data on show.
 *
 * <p>The age readout is the point. A dashboard that refreshes silently gives no way to tell
 * "the number has not moved" from "the number has not been fetched", and during an incident
 * those are opposite conclusions. This keeps the last successful update visible and counts
 * up from it.</p>
 *
 * <p>In a ZeroZ4j app most data arrives by shared signal, and no refresh control is needed
 * at all. This is for the rest: an expensive scan, a shell probe, anything the server does
 * not push.</p>
 *
 * <pre>{@code
 * RefreshControl refresh = new RefreshControl();
 * refresh.onRefresh(() -> inventoryService.rescan());
 * refresh.setInterval(30);
 * panel.addAction(refresh);
 * // when the result lands:
 * refresh.markUpdated();
 * }</pre>
 */
public final class RefreshControl extends Div {

    /** Selectable intervals in seconds; 0 is off. */
    private static final int[] INTERVALS = {0, 5, 10, 30, 60, 300};

    private final List<Runnable> listeners = new ArrayList<>();
    private final Button refreshButton = new Button("Refresh");
    private final Button intervalButton = new Button("Off");
    private final Span age = new Span("never");

    private int intervalSeconds;
    private int timerHandle = -1;
    private int ageTickHandle = -1;
    private double lastUpdatedAt = -1;
    private boolean busy;

    public RefreshControl() {
        addClassName("flex items-center gap-1.5");

        age.addClassName("font-mono " + TextStyle.CAPTION.getClassNames());

        intervalButton.addClassName("btn-xs btn-ghost");
        intervalButton.getElement().setAttribute("title", "Auto-refresh interval");
        intervalButton.addClickListener(event -> cycleInterval());

        refreshButton.addClassName("btn-xs btn-ghost");
        refreshButton.addClickListener(event -> refresh());

        add(age, intervalButton, refreshButton);
        startAgeTicker();
    }

    // ------------------------------------------------------------------ public API

    /** Registers a refresh action. Several may be registered; all run on each refresh. */
    public RefreshControl onRefresh(Runnable action) {
        if (action != null) {
            listeners.add(action);
        }
        return this;
    }

    /** Runs the refresh actions now and shows the busy state until {@link #markUpdated()}. */
    public RefreshControl refresh() {
        busy = true;
        renderBusy();
        for (Runnable listener : listeners) {
            listener.run();
        }
        return this;
    }

    /**
     * Records a successful update. Call this when the data actually lands, not when the
     * request is sent — the age readout is only worth showing if it means what it says.
     */
    public RefreshControl markUpdated() {
        lastUpdatedAt = now();
        busy = false;
        renderBusy();
        renderAge();
        return this;
    }

    /** Auto-refresh interval in seconds; 0 turns it off. */
    public RefreshControl setInterval(int seconds) {
        this.intervalSeconds = Math.max(0, seconds);
        intervalButton.setText(intervalSeconds == 0 ? "Off" : intervalSeconds + "s");
        intervalButton.setClassName("btn btn-xs " + (intervalSeconds == 0 ? "btn-ghost" : "btn-active"));
        restartTimer();
        return this;
    }

    public int interval() {
        return intervalSeconds;
    }

    /** Stops auto-refresh and the age ticker. Call when the view is torn down. */
    public void dispose() {
        if (timerHandle >= 0) {
            Window.clearInterval(timerHandle);
            timerHandle = -1;
        }
        if (ageTickHandle >= 0) {
            Window.clearInterval(ageTickHandle);
            ageTickHandle = -1;
        }
    }

    @Override
    protected void onDetach() {
        super.onDetach();
        dispose();
    }

    // -------------------------------------------------------------------- internals

    private void cycleInterval() {
        int position = 0;
        for (int i = 0; i < INTERVALS.length; i++) {
            if (INTERVALS[i] == intervalSeconds) {
                position = i;
                break;
            }
        }
        setInterval(INTERVALS[(position + 1) % INTERVALS.length]);
    }

    private void restartTimer() {
        if (timerHandle >= 0) {
            Window.clearInterval(timerHandle);
            timerHandle = -1;
        }
        if (intervalSeconds > 0) {
            timerHandle = Window.setInterval(this::refresh, intervalSeconds * 1000);
        }
    }

    /** Ticks the age readout once a second so it stays honest between refreshes. */
    private void startAgeTicker() {
        ageTickHandle = Window.setInterval(this::renderAge, 1000);
    }

    private void renderAge() {
        if (lastUpdatedAt < 0) {
            age.setText("never");
            return;
        }
        long elapsed = (long) (now() - lastUpdatedAt);
        age.setText(elapsed < 1500 ? "just now" : Scales.duration(elapsed) + " ago");
    }

    private void renderBusy() {
        refreshButton.setClassName("btn btn-xs btn-ghost");
        refreshButton.setText(busy ? "..." : "Refresh");
        if (busy) {
            refreshButton.addClassName("loading");
        }
    }

    private static double now() {
        return org.teavm.jso.core.JSDate.now();
    }
}
