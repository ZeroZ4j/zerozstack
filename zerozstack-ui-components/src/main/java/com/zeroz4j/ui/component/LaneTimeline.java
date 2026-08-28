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
import org.teavm.jso.dom.html.HTMLElement;
import org.teavm.jso.dom.xml.Element;

import java.util.ArrayList;
import java.util.List;
import com.zeroz4j.signals.Effect;

/**
 * Swimlane replay timeline (design §6.5): one lane per worker session, colored by outcome,
 * with event ticks; a draggable cursor drives the {@code cursor} signal (epoch millis) that
 * the run graph time-travels on. Play at 1× / 4× / 16×; "Live" resets to now (cursor null).
 */
public final class LaneTimeline extends Div {

    /** One lane: label + [openedAt, closedAt] + event timestamps (all epoch millis). */
    public record Lane(String label, String outcome, long openedAt, long closedAt, List<Long> events) {}

    /** null = live (no time travel). */
    public final ValueSignal<Long> cursor = new ValueSignal<>(null);

    private static final int LANE_H = 22;
    /** Where the automatic label column starts and stops. */
    private static final int LABEL_W_MIN = 90;
    private static final int LABEL_W_MAX = 260;
    /** Advance width of one character at font-size 10 in the monospace face used for labels. */
    private static final double LABEL_CHAR_W = 6.1;
    private static final int LABEL_GAP = 10;
    private static final int AXIS_H = 18;

    private final Div svgHost = new Div();
    private List<Lane> lanes = List.of();
    private long minTime;
    private long maxTime;
    private int playSpeed; // 0 = paused
    private boolean playing;
    private int labelWidth;      // 0 = measured from the lane names
    private int labelColumn = LABEL_W_MIN;
    private boolean labelWrap;   // false = one line, clipped by the browser with an ellipsis

    public LaneTimeline() {
        addClassName("flex flex-col border-t border-base-300 bg-base-200/40 shrink-0");
        add(controls());
        svgHost.addClassName("px-3 pb-2 overflow-x-auto");
        add(svgHost);
    }

    /**
     * Fixes the width of the name column, in pixels. By default the column is measured from the
     * longest lane name and sits between 90 and 260 pixels wide, so a name like
     * {@code worker-0 qwen36-27b} is shown in full instead of being cut after twelve characters.
     * Set a width when several timelines on one page have to line up with each other; pass 0 to go
     * back to measuring. A name too long even for the widest column is shown with its end faded
     * out by the browser, and hovering the lane always shows it whole - or turn on
     * {@link #setLabelWrap(boolean)} and it runs onto a second line instead.
     *
     * @param pixels the fixed width of the name column, or 0 to measure it
     */
    public void setLabelWidth(int pixels) {
        this.labelWidth = Math.max(0, pixels);
        redraw();
    }

    /**
     * Lets a name too long for the column run onto more lines, growing that lane to fit.
     *
     * <p>Off by default, because lanes of the same height are easier to scan and a name that long
     * is unusual. Off, a name too long for the column is cut off <i>visually</i> by the browser,
     * with the last characters faded away - the whole name is still there, still selectable, still
     * shown on hover. Either way nothing is thrown away: this component used to cut the name into
     * a shorter string and draw that, so the rest of it existed nowhere on the page.</p>
     *
     * @param wrap true to let long names run onto more lines
     */
    public void setLabelWrap(boolean wrap) {
        this.labelWrap = wrap;
        redraw();
    }

    /**
     * Says whether long names run onto more lines.
     *
     * @return true when they do
     */
    public boolean isLabelWrap() {
        return labelWrap;
    }

    /**
     * Returns the fixed width of the name column, or 0 when it is measured from the names.
     *
     * @return the configured label width in pixels
     */
    public int getLabelWidth() {
        return labelWidth;
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

        bar.add(speedButton(bar, "▶ 1×", 1));
        bar.add(speedButton(bar, "▶ 4×", 4));
        bar.add(speedButton(bar, "▶ 16×", 16));
        Button pause = new Button("⏸");
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

    /** ~30fps playback: advances the cursor by speed × frame time until the end. */
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
        labelColumn = measureLabelColumn();
        int width = Math.max(600, getElement().getClientWidth() - 24);
        int plotW = width - labelColumn - LABEL_GAP;
        int[] tops = new int[lanes.size()];
        int[] heights = new int[lanes.size()];
        int cursorY = AXIS_H;
        for (int i = 0; i < lanes.size(); i++) {
            heights[i] = laneHeight(lanes.get(i));
            tops[i] = cursorY;
            cursorY += heights[i] + 4;
        }
        int height = cursorY + 6;
        Element svg = SvgCanvas.el("svg",
            "width", String.valueOf(width), "height", String.valueOf(height));

        for (int i = 0; i < lanes.size(); i++) {
            Lane lane = lanes.get(i);
            int y = tops[i];
            int laneH = heights[i];
            String name = lane.label() == null ? "" : lane.label();
            svg.appendChild(labelBox(name, y, laneH));

            long end = lane.closedAt() > 0 ? lane.closedAt() : maxTime;
            int barX = labelColumn + x(lane.openedAt(), plotW);
            int barW = Math.max(3, x(end, plotW) - x(lane.openedAt(), plotW));
            int barH = LANE_H - 8;
            int barY = y + (laneH - barH) / 2;
            Element bar = SvgCanvas.el("rect",
                "x", String.valueOf(barX), "y", String.valueOf(barY),
                "width", String.valueOf(barW), "height", String.valueOf(barH),
                "rx", "4", "fill", outcomeColor(lane.outcome()), "fill-opacity", "0.25",
                "stroke", outcomeColor(lane.outcome()), "stroke-width", "1");
            svg.appendChild(bar);
            for (long event : lane.events()) {
                Element tickMark = SvgCanvas.el("line",
                    "x1", String.valueOf(labelColumn + x(event, plotW)),
                    "y1", String.valueOf(barY - 2),
                    "x2", String.valueOf(labelColumn + x(event, plotW)),
                    "y2", String.valueOf(barY + barH + 2),
                    "stroke", outcomeColor(lane.outcome()), "stroke-width", "1.5");
                svg.appendChild(tickMark);
            }
        }

        // Cursor line — draggable across the plot area.
        Long at = cursor.get();
        int cursorX = labelColumn + (at == null ? plotW : x(at, plotW));
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
        int px = e.getClientX() - rect.getLeft() - labelColumn;
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

    /** The column is as wide as the longest name needs, within the two bounds. */
    private int measureLabelColumn() {
        if (labelWidth > 0) {
            return labelWidth;
        }
        int longest = 0;
        for (Lane lane : lanes) {
            if (lane.label() != null) {
                longest = Math.max(longest, lane.label().length());
            }
        }
        int needed = (int) Math.ceil(longest * LABEL_CHAR_W) + LABEL_GAP;
        return Math.max(LABEL_W_MIN, Math.min(LABEL_W_MAX, needed));
    }

    /**
     * The lane name, whole, as ordinary HTML inside the drawing.
     *
     * <p>The name is put on the page in full and the browser decides what to do when the column is
     * too narrow for it: fade the end away on one line, or run onto more lines when wrapping is
     * on. Either way every character is in the page, so it can be selected, searched for and read
     * out - which is what drawing a shortened copy of it could never manage.</p>
     */
    private Element labelBox(String name, int top, int laneH) {
        Element host = SvgCanvas.el("foreignObject",
            "x", "0", "y", String.valueOf(top),
            "width", String.valueOf(Math.max(10, labelColumn - LABEL_GAP)),
            "height", String.valueOf(laneH));
        HTMLElement text = Window.current().getDocument().createElement("div");
        String common = "font-size:10px;font-family:ui-monospace, monospace;color:currentColor;";
        text.setAttribute("style", labelWrap
            ? common + "height:100%;display:flex;align-items:center;line-height:12px;"
              + "white-space:normal;overflow-wrap:anywhere;"
            : common + "height:" + laneH + "px;line-height:" + laneH + "px;white-space:nowrap;"
              + "overflow:hidden;text-overflow:ellipsis;");
        text.setAttribute("title", name);
        text.setTextContent(name);
        host.appendChild(text);
        return host;
    }

    /** A lane is one row tall unless a wrapped name needs more room. */
    private int laneHeight(Lane lane) {
        if (!labelWrap) {
            return LANE_H;
        }
        int lines = wrappedLineCount(lane.label(), Math.max(10, labelColumn - LABEL_GAP));
        return Math.max(LANE_H, lines * 12 + 8);
    }

    /**
     * How many lines the name will take, worked out the same way the browser does it: whole words
     * first, and a word longer than the column broken across lines.
     *
     * <p>Package-private so a test can check the arithmetic without a browser.</p>
     */
    static int wrappedLineCount(String name, int columnPx) {
        if (name == null || name.isEmpty()) {
            return 1;
        }
        int perLine = Math.max(1, (int) (columnPx / LABEL_CHAR_W));
        int lines = 1;
        int used = 0;
        for (String word : name.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            int wordLength = word.length();
            int needed = used == 0 ? wordLength : wordLength + 1;
            if (used + needed <= perLine) {
                used += needed;
                continue;
            }
            if (used > 0) {
                lines++;
                used = 0;
            }
            while (wordLength > perLine) {
                lines++;
                wordLength -= perLine;
            }
            used = wordLength;
        }
        return lines;
    }
}

