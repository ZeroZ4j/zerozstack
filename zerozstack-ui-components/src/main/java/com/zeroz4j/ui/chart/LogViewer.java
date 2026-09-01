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
import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.component.TextField;
import com.zeroz4j.ui.component.VirtualScroller;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.Span;
import com.zeroz4j.ui.theme.Emphasis;
import com.zeroz4j.ui.theme.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A live log pane: level-coloured, filterable, and glued to the tail until you scroll away.
 *
 * <p>Built on {@link VirtualScroller}, so only the visible rows exist in the DOM and a
 * console can hold tens of thousands of lines without the browser slowing down. A bounded
 * ring ({@link #setMaxLines}) caps memory too — a log pane left open for a week must not
 * become the reason the tab dies.</p>
 *
 * <pre>{@code
 * LogViewer logs = new LogViewer();
 * logs.setMaxLines(5000);
 * // from a server event topic:
 * logs.append(new LogViewer.Line(event.at(), "ERROR", "vitals", event.message()));
 * }</pre>
 *
 * <p>Follow-tail is <em>sticky but not forced</em>: scrolling up to read something detaches
 * it, returning to the bottom re-arms it. A pane that yanks you back to the tail mid-read is
 * worse than useless during an incident.</p>
 */
public final class LogViewer extends Div {

    /** One log record. {@code level} and {@code source} may be null. */
    public record Line(long time, String level, String source, String message) {

        public Line(long time, String level, String message) {
            this(time, level, null, message);
        }
    }

    private static final int ROW_HEIGHT = 22;

    private final List<Line> lines = new ArrayList<>();
    private final Set<String> knownLevels = new LinkedHashSet<>();
    private final VirtualScroller<Line> scroller;
    private final Div levelBar = new Div();
    private final Span counter = new Span();
    private final Button followButton = new Button("Follow");

    private String textFilter = "";
    private String levelFilter = "";
    private int maxLines = 5000;
    private boolean following = true;
    private boolean showSource = true;

    public LogViewer() {
        addClassName("flex min-h-0 w-full flex-col rounded-lg border border-base-300 bg-base-200/40");
        scroller = new VirtualScroller<>(ROW_HEIGHT, this::renderLine);
        scroller.addClassName("font-mono " + TextStyle.CAPTION.getClassNames(Emphasis.FULL));
        add(toolbar(), scroller);
        scroller.followTail();
        applyFilter();
    }

    // ------------------------------------------------------------------ public API

    /** Replaces the buffer. */
    public LogViewer setLines(List<Line> newLines) {
        lines.clear();
        if (newLines != null) {
            lines.addAll(newLines);
        }
        trim();
        rebuildLevels();
        applyFilter();
        return this;
    }

    /** Appends one line, trimming the oldest once the cap is reached. */
    public LogViewer append(Line line) {
        if (line == null) {
            return this;
        }
        lines.add(line);
        if (line.level() != null && knownLevels.add(line.level().toUpperCase())) {
            renderLevelBar();
        }
        trim();
        applyFilter();
        return this;
    }

    public LogViewer clear() {
        lines.clear();
        knownLevels.clear();
        renderLevelBar();
        applyFilter();
        return this;
    }

    public int size() {
        return lines.size();
    }

    /** Buffer cap. Older lines are dropped once it is exceeded. Default 5000. */
    public LogViewer setMaxLines(int max) {
        this.maxLines = Math.max(50, max);
        trim();
        applyFilter();
        return this;
    }

    /** Substring filter, matched against the message and the source, case-insensitively. */
    public LogViewer setTextFilter(String filter) {
        this.textFilter = filter == null ? "" : filter.toLowerCase();
        applyFilter();
        return this;
    }

    /** Shows only this level. Empty or null shows every level. */
    public LogViewer setLevelFilter(String level) {
        this.levelFilter = level == null ? "" : level.toUpperCase();
        renderLevelBar();
        applyFilter();
        return this;
    }

    public LogViewer setFollowTail(boolean value) {
        this.following = value;
        if (value) {
            scroller.followTail();
        }
        updateFollowButton();
        return this;
    }

    /** Hides the source column, for logs that only have one origin. */
    public LogViewer setSourceVisible(boolean visible) {
        this.showSource = visible;
        applyFilter();
        return this;
    }

    // -------------------------------------------------------------------- toolbar

    private Div toolbar() {
        Div bar = new Div();
        bar.addClassName("flex flex-wrap items-center gap-2 border-b border-base-300 px-2 py-1.5");

        TextField filter = new TextField("Filter");
        filter.addClassName("input-xs input-bordered w-40");
        filter.addValueChangeListener(event -> setTextFilter(event.getValue()));

        levelBar.addClassName("flex items-center gap-1");
        renderLevelBar();

        counter.addClassName("ml-auto font-mono " + TextStyle.CAPTION.getClassNames());

        followButton.addClassName("btn-xs");
        followButton.addClickListener(event -> setFollowTail(!following));
        updateFollowButton();

        Button clear = new Button("Clear");
        clear.addClassName("btn-xs btn-ghost");
        clear.addClickListener(event -> clear());

        bar.add(filter, levelBar, counter, followButton, clear);
        return bar;
    }

    private void renderLevelBar() {
        levelBar.removeAll();
        if (knownLevels.size() < 2) {
            return;
        }
        levelBar.add(levelChip("ALL", ""));
        for (String level : knownLevels) {
            levelBar.add(levelChip(level, level));
        }
    }

    private Button levelChip(String caption, String level) {
        Button chip = new Button(caption);
        boolean active = levelFilter.equals(level);
        chip.addClassName("btn-xs " + (active ? "btn-active" : "btn-ghost"));
        if (!level.isEmpty()) {
            chip.setStyle("color", levelColor(level));
        }
        chip.addClickListener(event -> setLevelFilter(level));
        return chip;
    }

    private void updateFollowButton() {
        followButton.setClassName("btn btn-xs " + (following ? "btn-primary" : "btn-ghost"));
        followButton.setText(following ? "Following" : "Follow");
    }

    // --------------------------------------------------------------------- filter

    private void applyFilter() {
        List<Line> visible = new ArrayList<>();
        for (Line line : lines) {
            if (matches(line)) {
                visible.add(line);
            }
        }
        scroller.setItems(visible);
        counter.setText(visible.size() == lines.size()
            ? lines.size() + " lines"
            : visible.size() + " of " + lines.size());
        if (following) {
            scroller.scrollToBottom();
        }
    }

    private boolean matches(Line line) {
        if (!levelFilter.isEmpty()
            && (line.level() == null || !line.level().equalsIgnoreCase(levelFilter))) {
            return false;
        }
        if (textFilter.isEmpty()) {
            return true;
        }
        if (line.message() != null && line.message().toLowerCase().contains(textFilter)) {
            return true;
        }
        return line.source() != null && line.source().toLowerCase().contains(textFilter);
    }

    private void trim() {
        while (lines.size() > maxLines) {
            lines.remove(0);
        }
    }

    private void rebuildLevels() {
        knownLevels.clear();
        for (Line line : lines) {
            if (line.level() != null) {
                knownLevels.add(line.level().toUpperCase());
            }
        }
        renderLevelBar();
    }

    // ---------------------------------------------------------------------- render

    private Component renderLine(Line line) {
        Div row = new Div();
        row.addClassName("flex items-center gap-2 px-2 leading-[22px] hover:bg-base-content/5");

        Span time = new Span(Scales.clock(line.time(), 0));
        time.addClassName("shrink-0 " + TextStyle.CAPTION.getClassNames());
        row.add(time);

        if (line.level() != null) {
            Span level = new Span(line.level().toUpperCase());
            level.addClassName("w-12 shrink-0 font-semibold");
            level.setStyle("color", levelColor(line.level()));
            row.add(level);
        }
        if (showSource && line.source() != null) {
            Span source = new Span(line.source());
            source.addClassName("shrink-0 " + TextStyle.CAPTION.getClassNames());
            row.add(source);
        }

        Span message = new Span(line.message());
        message.addClassName("min-w-0 flex-1 truncate");
        message.getElement().setAttribute("title", line.message() == null ? "" : line.message());
        row.add(message);
        return row;
    }

    /** Level colours follow severity, so scanning for red does not need the text read. */
    public static String levelColor(String level) {
        if (level == null) {
            return Palette.BASE_CONTENT;
        }
        return switch (level.toUpperCase()) {
            case "ERROR", "FATAL", "SEVERE", "CRITICAL" -> Palette.ERROR;
            case "WARN", "WARNING" -> Palette.WARNING;
            case "INFO" -> Palette.INFO;
            case "DEBUG" -> "var(--color-accent, #0d9488)";
            default -> Palette.BASE_CONTENT;
        };
    }
}
