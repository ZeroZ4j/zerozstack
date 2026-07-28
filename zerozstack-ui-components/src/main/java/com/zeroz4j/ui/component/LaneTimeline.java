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

import com.zeroz4j.ui.component.Button;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.Span;
import com.zeroz4j.signals.ValueSignal;
import org.teavm.jso.browser.Window;

import org.teavm.jso.dom.events.MouseEvent;
import org.teavm.jso.dom.xml.Element;

import java.util.ArrayList;
import java.util.List;
import com.zeroz4j.signals.Effect;

/**
 * Swimlane replay timeline (design Ãƒâ€šÃ‚Â§6.5): one lane per worker session, colored by outcome,
 * with event ticks; a draggable cursor drives the {@code cursor} signal (epoch millis) that
 * the run graph time-travels on. Play at 1ÃƒÆ’Ã¢â‚¬â€ / 4ÃƒÆ’Ã¢â‚¬â€ / 16ÃƒÆ’Ã¢â‚¬â€; "Live" resets to now (cursor null).
 */
public final class LaneTimeline extends Div {

    /** One lane: label + [openedAt, closedAt] + event timestamps (all epoch millis). */
    public record Lane(String label, String outcome, long openedAt, long closedAt, List<Long> events) {}

    /** null = live (no time travel). */
    public final ValueSignal<Long> cursor = new ValueSignal<>(null);

    private static final int LANE_H = 22;
    private static final int LABEL_W = 90;
    private static final int AXIS_H = 18;

    private final Div svgHost = new Div();
    private List<Lane> lanes = List.of();
    private long minTime;
    private long maxTime;
    private int playSpeed; // 0 = paused
    private boolean playing;

    public LaneTimeline() {
        addClassName("flex flex-col border-t border-base-300 bg-base-200/40 shrink-0");
        add(controls());
        svgHost.addClassName("px-3 pb-2 overflow-x-auto");
        add(svgHost);
    }

    public void setLanes(List<Lane> newLanes) {
        this.lanes = newLanes == null ? List.of() : newLanes;
        minTime = Long.MAX_VALUE;
        maxTime = Long.MIN_VALUE;
        for (Lane lane : lanes) {
            if (lane.openedAt() > 0) {
                minTime = Math.min(minTime, lane.openedAt());
            }
            long end = lane.closedAt() > 0 ? lane.closedAt() : lane.openedAt();
            maxTime = Math.max(maxTime, end);
            for (long event : lane.events()) {
                maxTime = Math.max(maxTime, event);
            }
        }
        if (lanes.isEmpty() || minTime == Long.MAX_VALUE) {
            minTime = 0;
            maxTime = 1;
        }
        if (maxTime <= minTime) {
            maxTime = minTime + 1000;
        }
        redraw();
    }

    private Div controls() {
        Div bar = new Div();
        bar.addClassName("flex items-center gap-1.5 px-3 py-1.5 text-xs");
        Span title = new Span("REPLAY");
        title.addClassName("font-bold tracking-wider text-[10px] text-base-content/40 mr-2");
        bar.getElement().appendChild(title.getElement());

        bar.add(speedButton(bar, "ÃƒÂ¢Ã¢â‚¬â€œÃ‚Â¶ 1ÃƒÆ’Ã¢â‚¬â€", 1));
        bar.add(speedButton(bar, "ÃƒÂ¢Ã¢â‚¬â€œÃ‚Â¶ 4ÃƒÆ’Ã¢â‚¬â€", 4));
        bar.add(speedButton(bar, "ÃƒÂ¢Ã¢â‚¬â€œÃ‚Â¶ 16ÃƒÆ’Ã¢â‚¬â€", 16));
        Button pause = new Button("ÃƒÂ¢Ã‚ÂÃ‚Â¸");
        pause.addClassName("btn-ghost btn-xs");
        pause.addClickListener(e -> {
            playing = false;
            playSpeed = 0;
        });
        Button live = new Button("Live");
        live.addClassName("btn-ghost btn-xs text-success");
        live.addClickListener(e -> {
            playing = false;
            playSpeed = 0;
            cursor.set(null);
            redraw();
        });
        bar.add(pause, live);

        Span time = new Span("");
        time.addClassName("ml-auto font-mono text-[10px] text-base-content/50");
        Effect.create(() -> {
            Long at = cursor.get();
            time.setText(at == null ? "live" : offset(at));
        });
        bar.getElement().appendChild(time.getElement());
        return bar;
    }

    private Button speedButton(Div bar, String label, int speed) {
        Button button = new Button(label);
        button.addClassName("btn-ghost btn-xs");
        button.addClickListener(e -> {
            playSpeed = speed;
            if (cursor.get() == null) {
                cursor.set(minTime);
            }
            if (!playing) {
                playing = true;
                tick();
            }
        });
        return button;
    }

    /** ~30fps playback: advances the cursor by speed ÃƒÆ’Ã¢â‚¬â€ frame time until the end. */
    private void tick() {
        if (!playing || playSpeed == 0) {
            return;
        }
        Long at = cursor.get();
        long next = (at == null ? minTime : at) + playSpeed * 33L;
        if (next >= maxTime) {
            cursor.set(maxTime);
            playing = false;
            playSpeed = 0;
        } else {
            cursor.set(next);
            Window.setTimeout(this::tick, 33);
        }
        redraw();
    }

    private void redraw() {
        svgHost.removeAll();
        int width = Math.max(600, getElement().getClientWidth() - 24);
        int plotW = width - LABEL_W - 10;
        int height = AXIS_H + lanes.size() * (LANE_H + 4) + 6;
        Element svg = SvgCanvas.el("svg",
            "width", String.valueOf(width), "height", String.valueOf(height));

        for (int i = 0; i < lanes.size(); i++) {
            Lane lane = lanes.get(i);
            int y = AXIS_H + i * (LANE_H + 4);
            Element label = SvgCanvas.el("text",
                "x", "0", "y", String.valueOf(y + 15), "font-size", "10",
                "fill", "currentColor", "font-family", "ui-monospace, monospace");
            label.appendChild(Window.current().getDocument().createTextNode(
                truncate(lane.label(), 12)));
            svg.appendChild(label);

            long end = lane.closedAt() > 0 ? lane.closedAt() : maxTime;
            int barX = LABEL_W + x(lane.openedAt(), plotW);
            int barW = Math.max(3, x(end, plotW) - x(lane.openedAt(), plotW));
            Element bar = SvgCanvas.el("rect",
                "x", String.valueOf(barX), "y", String.valueOf(y + 4),
                "width", String.valueOf(barW), "height", String.valueOf(LANE_H - 8),
                "rx", "4", "fill", outcomeColor(lane.outcome()), "fill-opacity", "0.25",
                "stroke", outcomeColor(lane.outcome()), "stroke-width", "1");
            svg.appendChild(bar);
            for (long event : lane.events()) {
                Element tickMark = SvgCanvas.el("line",
                    "x1", String.valueOf(LABEL_W + x(event, plotW)),
                    "y1", String.valueOf(y + 6),
                    "x2", String.valueOf(LABEL_W + x(event, plotW)),
                    "y2", String.valueOf(y + LANE_H - 6),
                    "stroke", outcomeColor(lane.outcome()), "stroke-width", "1.5");
                svg.appendChild(tickMark);
            }
        }

        // Cursor line ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â draggable across the plot area.
        Long at = cursor.get();
        int cursorX = LABEL_W + (at == null ? plotW : x(at, plotW));
        Element cursorLine = SvgCanvas.el("line",
            "x1", String.valueOf(cursorX), "y1", "2",
            "x2", String.valueOf(cursorX), "y2", String.valueOf(height - 2),
            "stroke", "#38bdf8", "stroke-width", "2");
        svg.appendChild(cursorLine);
        Element grip = SvgCanvas.el("circle",
            "cx", String.valueOf(cursorX), "cy", "8", "r", "5",
            "fill", "#38bdf8", "style", "cursor:ew-resize");
        svg.appendChild(grip);

        Div wrapper = new Div();
        wrapper.getElement().appendChild(svg);
        final boolean[] dragging = {false};
        wrapper.getElement().addEventListener("mousedown", (org.teavm.jso.dom.events.EventListener<org.teavm.jso.dom.events.MouseEvent>) e -> {
            dragging[0] = true;
            scrub(e, plotW, wrapper);
        });
        wrapper.getElement().addEventListener("mousemove", (org.teavm.jso.dom.events.EventListener<org.teavm.jso.dom.events.MouseEvent>) e -> {
            if (dragging[0]) {
                scrub(e, plotW, wrapper);
            }
        });
        org.teavm.jso.dom.events.EventListener<org.teavm.jso.dom.events.MouseEvent> stop = e -> dragging[0] = false;
        wrapper.getElement().addEventListener("mouseup", stop);
        wrapper.getElement().addEventListener("mouseleave", stop);
        svgHost.add(wrapper);
    }

    private void scrub(MouseEvent e, int plotW, Div wrapper) {
        playing = false;
        playSpeed = 0;
        var rect = wrapper.getElement().getBoundingClientRect();
        int px = e.getClientX() - rect.getLeft() - LABEL_W;
        double fraction = Math.max(0, Math.min(1, (double) px / plotW));
        cursor.set(minTime + (long) (fraction * (maxTime - minTime)));
        redraw();
    }

    private int x(long time, int plotW) {
        return (int) ((double) (time - minTime) / (maxTime - minTime) * plotW);
    }

    private String offset(long at) {
        long seconds = Math.max(0, (at - minTime) / 1000);
        long remainder = seconds % 60;
        return "+" + (seconds / 60) + ":" + (remainder < 10 ? "0" : "") + remainder;
    }

    private static String outcomeColor(String outcome) {
        return switch (outcome == null ? "" : outcome) {
            case "COMPLETED" -> "#22c55e";
            case "KILLED" -> "#f43f5e";
            case "FAILED" -> "#f59e0b";
            case "RUNNING" -> "#38bdf8";
            default -> "#94a3b8";
        };
    }

    private static String truncate(String s, int max) {
        return s == null ? "" : s.length() <= max ? s : s.substring(0, max - 1) + "ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦";
    }
}

