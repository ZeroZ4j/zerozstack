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
package com.zeroz4j.ui.chart;

import com.zeroz4j.signals.ValueSignal;
import com.zeroz4j.ui.component.Button;
import com.zeroz4j.ui.layout.Div;
import java.util.ArrayList;
import java.util.List;
import org.teavm.jso.core.JSDate;

/**
 * The quick-range selector every dashboard needs: 5m, 15m, 1h, 6h, 24h, 7d.
 *
 * <p>The selection is published as a {@link ValueSignal}, so panels bind to it with an
 * {@link com.zeroz4j.signals.Effect} instead of being wired up by hand — one picker can
 * drive every chart on the page, and a chart added later picks up the current range with no
 * extra plumbing.</p>
 *
 * <pre>{@code
 * TimeRangePicker picker = new TimeRangePicker();
 * Effect.create(() -> {
 *     TimeRangePicker.Range range = picker.range.get();
 *     chart.setTimeRange(range.from(), range.to());
 * });
 * }</pre>
 *
 * <p>A range is a <em>duration</em>, resolved against the clock when it is read — so "last
 * hour" keeps meaning the last hour as time passes, rather than freezing at the instant it
 * was clicked.</p>
 */
public final class TimeRangePicker extends Div {

    /** A named window ending now. {@code from()} and {@code to()} resolve against the clock. */
    public record Range(String label, long durationMillis) {

        public long to() {
            return (long) JSDate.now();
        }

        public long from() {
            return to() - durationMillis;
        }
    }

    /** The ranges an infrastructure console wants by default. */
    public static List<Range> defaultRanges() {
        List<Range> ranges = new ArrayList<>();
        ranges.add(new Range("5m", 5 * 60_000L));
        ranges.add(new Range("15m", 15 * 60_000L));
        ranges.add(new Range("1h", 60 * 60_000L));
        ranges.add(new Range("6h", 6 * 60 * 60_000L));
        ranges.add(new Range("24h", 24 * 60 * 60_000L));
        ranges.add(new Range("7d", 7 * 24 * 60 * 60_000L));
        return ranges;
    }

    /** The selected range. Bind panels to this rather than listening for clicks. */
    public final ValueSignal<Range> range;

    private List<Range> ranges = defaultRanges();

    /**
     * The selection, mirrored outside the signal.
     *
     * <p>{@link #render()} must not call {@code range.get()}. A signal read registers a dependency
     * on whichever {@link com.zeroz4j.signals.Effect} is running at that moment — and this component
     * is typically constructed <em>inside</em> the effect that swaps views. That effect would then
     * subscribe to this picker's own signal, so the picker writing its own selection would
     * invalidate the view, rebuild the picker, and loop until the stack blew. Reading a plain field
     * keeps the dependency where it belongs: on consumers that deliberately read {@link #range}.</p>
     */
    private Range selected;

    public TimeRangePicker() {
        this(2);
    }

    /** @param initialIndex which of the default ranges starts selected */
    public TimeRangePicker(int initialIndex) {
        addClassName("join");
        selected = ranges.get(Math.max(0, Math.min(ranges.size() - 1, initialIndex)));
        range = new ValueSignal<>(selected);
        render();
    }

    // ------------------------------------------------------------------ public API

    /** Replaces the offered ranges. The first becomes selected. */
    public TimeRangePicker setRanges(List<Range> newRanges) {
        if (newRanges != null && !newRanges.isEmpty()) {
            this.ranges = newRanges;
            choose(newRanges.get(0));
        }
        return this;
    }

    public List<Range> ranges() {
        return ranges;
    }

    /** Selects by label; unknown labels are ignored. */
    public TimeRangePicker select(String label) {
        for (Range candidate : ranges) {
            if (candidate.label().equals(label)) {
                choose(candidate);
                break;
            }
        }
        return this;
    }

    /** The current selection, without registering a signal dependency. */
    public Range selected() {
        return selected;
    }

    /** Start of the selected window, resolved now. */
    public long from() {
        return selected.from();
    }

    /** End of the selected window, resolved now. */
    public long to() {
        return selected.to();
    }

    /** Moves the selection: own state first, then the signal consumers watch, then repaint. */
    private void choose(Range candidate) {
        selected = candidate;
        range.set(candidate);
        render();
    }

    // --------------------------------------------------------------------- render

    private void render() {
        removeAll();
        for (Range candidate : ranges) {
            boolean active = selected != null && selected.label().equals(candidate.label());
            Button button = new Button(candidate.label());
            button.setClassName("btn join-item btn-xs " + (active ? "btn-primary" : "btn-ghost"));
            button.addClickListener(event -> choose(candidate));
            add(button);
        }
    }
}
