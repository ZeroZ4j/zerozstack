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

import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.Span;

/**
 * The chrome around a dashboard panel: title, subtitle, header actions, footer — and the
 * four states every panel actually has.
 *
 * <p>A chart on its own is only the happy path. In a console the same panel also has to say
 * "still loading", "the probe failed", and "queried fine, nothing came back" — and those
 * three are different facts an operator needs to tell apart. Left to each view, they get
 * skipped, and a failed probe renders as an empty chart that looks like an idle system.</p>
 *
 * <pre>{@code
 * PanelFrame panel = new PanelFrame("Unified memory");
 * panel.setSubtitle("last 2 minutes");
 * panel.setContent(chart);
 * panel.addAction(refreshControl);
 * // later:
 * panel.setError(vitals.probeError);
 * }</pre>
 */
public final class PanelFrame extends Div {

    public enum State {
        /** Content is shown. */
        READY,
        /** A skeleton is shown over the content area. */
        LOADING,
        /** An error message replaces the content. */
        ERROR,
        /** The query worked and returned nothing. */
        NO_DATA
    }

    private final Div header = new Div();
    private final Div titleBox = new Div();
    private final Span title = new Span();
    private final Span subtitle = new Span();
    private final Div actions = new Div();
    private final Div body = new Div();
    private final Div overlay = new Div();
    private final Div footer = new Div();

    private Component content;
    private State state = State.READY;
    private String errorText = "";
    private String noDataText = "No data for the selected range";

    public PanelFrame() {
        this(null);
    }

    public PanelFrame(String titleText) {
        addClassName("flex min-w-0 flex-col rounded-xl border border-base-300 bg-base-100 shadow-sm");

        header.addClassName("flex items-start gap-3 border-b border-base-300/70 px-4 py-2.5");
        titleBox.addClassName("flex min-w-0 flex-col");
        title.addClassName("truncate text-sm font-semibold text-base-content");
        subtitle.addClassName("truncate text-xs text-base-content/50");
        subtitle.addClassName("hidden");
        titleBox.add(title, subtitle);
        actions.addClassName("ml-auto flex shrink-0 items-center gap-1.5");
        header.add(titleBox, actions);

        body.addClassName("relative min-w-0 flex-1 p-4");
        overlay.addClassName("absolute inset-0 z-10 hidden flex-col items-center justify-center "
            + "gap-2 rounded-b-xl bg-base-100/80 px-4 text-center backdrop-blur-[1px]");
        body.add(overlay);

        footer.addClassName("hidden border-t border-base-300/70 px-4 py-1.5 text-xs text-base-content/50");

        add(header, body, footer);
        setTitle(titleText);
    }

    // ------------------------------------------------------------------ public API

    public PanelFrame setTitle(String text) {
        title.setText(text == null ? "" : text);
        return this;
    }

    public PanelFrame setSubtitle(String text) {
        subtitle.setText(text == null ? "" : text);
        if (text == null || text.isEmpty()) {
            subtitle.addClassName("hidden");
        } else {
            subtitle.removeClassName("hidden");
        }
        return this;
    }

    /** The panel's content — normally a chart, but anything works. */
    public PanelFrame setContent(Component newContent) {
        if (content != null) {
            body.remove(content);
        }
        content = newContent;
        if (content != null) {
            // Insert before the overlay so the overlay keeps painting on top.
            body.getElement().insertBefore(content.getElement(), overlay.getElement());
        }
        return this;
    }

    public Component content() {
        return content;
    }

    /** Adds a control to the header — a refresh control, a time picker, a menu. */
    public PanelFrame addAction(Component action) {
        actions.add(action);
        return this;
    }

    public PanelFrame clearActions() {
        actions.removeAll();
        return this;
    }

    /** A note along the bottom: sample interval, source, last update. */
    public PanelFrame setFooter(String text) {
        footer.removeAll();
        if (text == null || text.isEmpty()) {
            footer.addClassName("hidden");
        } else {
            footer.removeClassName("hidden");
            footer.setText(text);
        }
        return this;
    }

    /** Tightens the padding, for panels in a dense grid. */
    public PanelFrame setDense(boolean dense) {
        body.setClassName("relative min-w-0 flex-1 " + (dense ? "p-2" : "p-4"));
        header.setClassName("flex items-start gap-3 border-b border-base-300/70 px-4 "
            + (dense ? "py-1.5" : "py-2.5"));
        return this;
    }

    // ---------------------------------------------------------------------- state

    public PanelFrame setState(State newState) {
        this.state = newState == null ? State.READY : newState;
        renderState();
        return this;
    }

    public State state() {
        return state;
    }

    public PanelFrame setLoading(boolean loading) {
        return setState(loading ? State.LOADING : State.READY);
    }

    /** Shows the error state. A null or empty message clears it back to ready. */
    public PanelFrame setError(String message) {
        this.errorText = message == null ? "" : message;
        return setState(errorText.isEmpty() ? State.READY : State.ERROR);
    }

    /** Message for {@link State#NO_DATA}. */
    public PanelFrame setNoDataText(String message) {
        this.noDataText = message;
        if (state == State.NO_DATA) {
            renderState();
        }
        return this;
    }

    private void renderState() {
        overlay.removeAll();
        if (state == State.READY) {
            overlay.addClassName("hidden");
            overlay.removeClassName("flex");
            return;
        }
        overlay.removeClassName("hidden");
        overlay.addClassName("flex");

        switch (state) {
            case LOADING -> {
                Div spinner = new Div();
                spinner.addClassName("loading loading-spinner loading-md text-primary");
                Div caption = new Div("Loading");
                caption.addClassName("text-xs text-base-content/50");
                overlay.add(spinner, caption);
            }
            case ERROR -> {
                Div badge = new Div("Error");
                badge.addClassName("badge badge-error badge-sm");
                Div message = new Div(errorText);
                message.addClassName("max-w-md text-xs text-base-content/70");
                overlay.add(badge, message);
            }
            case NO_DATA -> {
                Div message = new Div(noDataText);
                message.addClassName("text-xs text-base-content/40");
                overlay.add(message);
            }
            default -> {
            }
        }
    }
}
