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

import com.zeroz4j.signals.Computed;
import com.zeroz4j.signals.Effect;
import com.zeroz4j.signals.ValueSignal;
import com.zeroz4j.ui.component.Badge;
import com.zeroz4j.ui.component.Button;
import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.component.KeyedList;
import com.zeroz4j.ui.component.TextField;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.Span;
import java.util.ArrayList;
import java.util.List;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.events.EventListener;
import org.teavm.jso.dom.html.HTMLElement;

/**
 * A list that changes underneath the reader. This is where the keyboard gets thrown away, so the
 * page keeps a running report of where the keyboard is and every time it moved on its own.
 */
public class MovingListShowcase extends ComponentShowcase {

    /** One job in the queue. */
    private record Job(String id, String name, String state, int progress) {
    }

    private static final String[] NAMES = {
        "nightly-backup", "invoice-run", "reindex-search", "send-reminders",
        "Rechnungslauf-Dezember", "画像リサイズ", "purge-old-sessions", "warm-the-cache",
        "rebuild-thumbnails", "export-to-accounting", "check-certificates", "rotate-logs",
    };

    private static final String[] STATES = { "queued", "running", "done", "failed" };

    private final ValueSignal<List<Job>> jobs = new ValueSignal<>(new ArrayList<>());
    private final ValueSignal<String> filter = new ValueSignal<>("");

    /** Where the keyboard is now, and how many times it moved without anybody pressing a key. */
    private final Div focusReadout = new Div("The keyboard is nowhere yet.");
    private int stolenCount;
    private String lastFocusId = "";
    private boolean expectingFocusChange;

    private int timerHandle = -1;
    private int tick;
    private int nextId = 1;

    private final DemoData data = new DemoData(2026L);

    public MovingListShowcase() {
        super();
        addTitle("A list that moves while you are reading it");
        addDescription("Jobs arrive, change and finish on a timer. Meanwhile the reader is "
                + "scrolling, typing in the filter box, and has one row's button under the "
                + "keyboard. Something has to give, and this page shows what.");

        addWhatToCheck("Try this",
                "The list is already moving. Tab onto a row's button and leave it there.",
                "Watch the line marked \"the keyboard is on\". If it changes on its own, the list "
                        + "took the keyboard away from you.",
                "Type in the filter box while the list is moving. Your letters must not be lost "
                        + "and the box must keep the keyboard.",
                "Scroll down the list while it moves. It must not jump back to the top.",
                "Compare the two lists. The left one is thrown away and rebuilt every time; the "
                        + "right one keeps the rows it already had.",
                "Broken looks like: the count of \"times the keyboard was taken away\" going up "
                        + "while you are not pressing anything.");

        seed();
        watchFocus();

        focusReadout.setId("moving-list-focus");
        focusReadout.addClassName("font-mono text-sm");

        // The list moves from the moment the page opens. A page about a list that moves, sitting
        // still until somebody presses a button, is the easy example again.
        startTimer();

        addSection("Controls", controls());
        addSection("Where the keyboard is", focusReadout);
        addSection("Rebuilt from scratch on every change", rebuiltList());
        addSection("Patched by key, keeping the rows it already had", keyedList());
    }

    // ------------------------------------------------------------------ controls

    private Component[] controls() {
        TextField filterField = new TextField().withLabel("Filter the list");
        filterField.setId("moving-list-filter");
        filterField.setHelperText("Type part of a job name. The list keeps moving while you type.");
        filterField.addValueChangeListener(e -> filter.set(e.getValue() == null ? "" : e.getValue()));

        Button start = new Button("Start the changes again");
        start.setId("moving-list-start");
        start.addClassName("btn-primary");
        start.addClickListener(e -> startTimer());

        Button stop = new Button("Stop");
        stop.setId("moving-list-stop");
        stop.addClickListener(e -> stopTimer());

        Button once = new Button("Change it once");
        once.setId("moving-list-once");
        once.addClickListener(e -> advance());

        Div row = new Div();
        row.addClassName("flex flex-wrap items-end gap-3 w-full");
        row.add(filterField, start, stop, once);
        return new Component[] { row };
    }

    // ------------------------------------------------------------------ the two lists

    /** The naive one: empty the container, render every row again. */
    private Component[] rebuiltList() {
        Div host = scrollHost("moving-list-rebuilt");
        Computed<List<Job>> visible = new Computed<>(this::visibleJobs);
        Effect.create(() -> {
            List<Job> current = visible.get();
            host.removeAll();
            for (Job job : current) {
                host.add(row(job, "rebuilt"));
            }
        });
        return new Component[] { host };
    }

    /** The careful one: the library's keyed patcher, which only touches what changed. */
    private Component[] keyedList() {
        Div host = scrollHost("moving-list-keyed");
        Computed<List<Job>> visible = new Computed<>(this::visibleJobs);
        new KeyedList<>(host, visible, Job::id, job -> row(job, "keyed"),
                (existing, job) -> {
                    // In place: only the words that changed, so the button keeps the keyboard.
                    HTMLElement state = existing.getElement().querySelector(".job-state");
                    if (state != null) {
                        state.setTextContent(job.state());
                    }
                    HTMLElement progress = existing.getElement().querySelector(".job-progress");
                    if (progress != null) {
                        progress.setTextContent(job.progress() + " %");
                    }
                });
        return new Component[] { host };
    }

    private static Div scrollHost(String id) {
        Div host = new Div();
        host.setId(id);
        host.addClassName("flex flex-col gap-1 w-full h-72 overflow-y-auto rounded-box "
                + "border border-base-300 bg-base-100 p-2");
        return host;
    }

    private Component row(Job job, String listName) {
        Div row = new Div();
        row.addClassName("flex items-center gap-3 px-2 py-1 rounded hover:bg-base-200");

        Span name = new Span(job.name());
        name.addClassName("flex-1 truncate text-sm");

        Badge state = new Badge(job.state());
        state.addClassName("job-state badge-outline");

        Span progress = new Span(job.progress() + " %");
        progress.addClassName("job-progress font-mono text-xs w-12 text-right");

        Button acknowledge = new Button("Acknowledge");
        acknowledge.setId(listName + "-ack-" + job.id());
        acknowledge.addClassName("btn-xs");
        acknowledge.withAriaLabel("Acknowledge " + job.name());
        acknowledge.addClickListener(e -> {
            expectingFocusChange = true;
            jobs.update(list -> {
                List<Job> next = new ArrayList<>(list);
                next.removeIf(j -> j.id().equals(job.id()));
                return next;
            });
        });

        row.add(name, state, progress, acknowledge);
        return row;
    }

    // ------------------------------------------------------------------ the data moving

    private void seed() {
        List<Job> list = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            list.add(newJob());
        }
        jobs.set(list);
    }

    private Job newJob() {
        String name = NAMES[(int) (data.pick() * NAMES.length) % NAMES.length];
        return new Job("j" + (nextId++), name + "-" + nextId,
                STATES[(int) (data.pick() * STATES.length) % STATES.length],
                (int) (data.pick() * 100));
    }

    /** One tick: a few rows change, one leaves, one arrives at the top. */
    private void advance() {
        tick++;
        List<Job> next = new ArrayList<>(jobs.get());
        for (int i = 0; i < next.size(); i++) {
            if ((i + tick) % 4 == 0) {
                Job job = next.get(i);
                next.set(i, new Job(job.id(), job.name(),
                        STATES[(int) (data.pick() * STATES.length) % STATES.length],
                        Math.min(100, job.progress() + 7)));
            }
        }
        if (next.size() > 8 && tick % 2 == 0) {
            next.remove(next.size() / 2);
        }
        next.add(0, newJob());
        jobs.set(next);
    }

    private void startTimer() {
        if (timerHandle >= 0) {
            return;
        }
        timerHandle = Window.setInterval(this::advance, 1500);
    }

    private void stopTimer() {
        if (timerHandle >= 0) {
            Window.clearInterval(timerHandle);
            timerHandle = -1;
        }
    }

    private List<Job> visibleJobs() {
        String needle = filter.get() == null ? "" : filter.get().trim().toLowerCase();
        List<Job> all = jobs.get();
        if (needle.isEmpty()) {
            return all;
        }
        List<Job> result = new ArrayList<>();
        for (Job job : all) {
            if (job.name().toLowerCase().contains(needle)) {
                result.add(job);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ the focus report

    /**
     * The whole point of the page. Every move of the keyboard is recorded; a move nobody asked for
     * is counted separately, because that is the fault.
     */
    private void watchFocus() {
        EventListener<Event> listener = evt -> report();
        Window.current().getDocument().addEventListener("focusin", listener);
        Window.current().getDocument().addEventListener("focusout", listener);
        Window.setInterval(this::report, 500);
    }

    private void report() {
        HTMLElement active = Window.current().getDocument().getActiveElement();
        String id = active == null ? "nothing" : describe(active);
        if (!id.equals(lastFocusId)) {
            boolean lostToTheBody = "the page body (the keyboard was thrown away)".equals(id);
            if (lostToTheBody && !expectingFocusChange && !lastFocusId.isEmpty()
                    && !"nothing".equals(lastFocusId)) {
                stolenCount++;
            }
            expectingFocusChange = false;
            lastFocusId = id;
        }
        focusReadout.setText("The keyboard is on: " + id
                + "   |   times it was taken away on its own: " + stolenCount);
    }

    private static String describe(HTMLElement element) {
        String tag = element.getTagName() == null ? "?" : element.getTagName().toLowerCase();
        if ("body".equals(tag)) {
            return "the page body (the keyboard was thrown away)";
        }
        String id = element.getAttribute("id");
        String text = element.getAttribute("aria-label");
        if (text == null || text.isEmpty()) {
            text = element.getTextContent();
        }
        if (text != null && text.length() > 40) {
            text = text.substring(0, 40) + "…";
        }
        return tag + (id == null || id.isEmpty() ? "" : "#" + id)
                + (text == null || text.isEmpty() ? "" : " \"" + text.trim() + "\"");
    }
}
