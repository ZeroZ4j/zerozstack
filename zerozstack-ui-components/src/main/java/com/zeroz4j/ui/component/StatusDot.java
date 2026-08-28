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

import com.zeroz4j.ui.layout.Span;

/**
 * Coloured state dot with an optional pulse while active.
 *
 * <p>A dot has <b>two</b> pieces of text, and they are rarely the same one. The <i>state</i> is an
 * internal name that decides the colour and whether the dot pulses; the <i>label</i> is what a
 * person reads when they hover it. Given only a state, the dot uses it for both - which is how
 * every dot in one application ended up hovering as {@code DISPATCHED}. Give both whenever the
 * state is a name from your code rather than a word for the reader:</p>
 *
 * <pre>{@code
 * new StatusDot("DISPATCHED", "Sent to a worker");
 * dot.setState("FAILED", "Could not finish");
 * }</pre>
 *
 * <p>The label is also what assistive technology announces; a dot is a picture with no text in it,
 * so without a label there is nothing to announce.</p>
 */
public final class StatusDot extends Span {

    private final Span dot = new Span();
    private final Span ping = new Span();
    private String state;
    private String label;

    /**
     * Creates a dot coloured by the given state, using that same state as the hover text.
     *
     * @param state the state name, which decides colour and pulse
     */
    public StatusDot(String state) {
        this(state, state);
    }

    /**
     * Creates a dot coloured by one string and read by another.
     *
     * @param state the state name, which decides colour and pulse
     * @param label the words shown on hover and announced by a screen reader
     */
    public StatusDot(String state, String label) {
        addClassName("relative inline-flex w-2.5 h-2.5 shrink-0");
        ping.addClassName("absolute inline-flex w-full h-full rounded-full opacity-60 animate-ping");
        dot.addClassName("relative inline-flex w-2.5 h-2.5 rounded-full");
        getElement().appendChild(ping.getElement());
        getElement().appendChild(dot.getElement());
        getElement().setAttribute("role", "img");
        setState(state, label);
    }

    /**
     * Sets the state, and uses it as the hover text as well. Call
     * {@link #setState(String, String)} instead whenever the state is an internal name.
     *
     * @param state the state name, which decides colour and pulse
     */
    public void setState(String state) {
        setState(state, state);
    }

    /**
     * Sets the state that decides the colour and the words a person reads, separately.
     *
     * @param state the state name, which decides colour and pulse
     * @param label the words shown on hover and announced by a screen reader
     */
    public void setState(String state, String label) {
        this.state = state;
        String color = colorFor(state);
        dot.setClassName("relative inline-flex w-2.5 h-2.5 rounded-full " + color);
        boolean active = isActive(state);
        ping.setClassName("absolute inline-flex w-full h-full rounded-full opacity-60 "
            + color + (active ? " animate-ping" : " hidden"));
        setLabel(label);
    }

    /**
     * Sets only the words a person reads, leaving the colour where it is.
     *
     * @param label the words shown on hover and announced by a screen reader
     */
    public void setLabel(String label) {
        this.label = label;
        String text = label == null ? "" : label;
        getElement().setAttribute("title", text);
        getElement().setAttribute("aria-label", text);
    }

    /**
     * Returns the state that decides this dot's colour.
     *
     * @return the state name
     */
    public String getState() {
        return state;
    }

    /**
     * Returns the words a person reads on hover.
     *
     * @return the label
     */
    public String getLabel() {
        return label;
    }

    /** DaisyUI background class for any state string used across the domain. */
    public static String colorFor(String state) {
        if (state == null) {
            return "bg-base-300";
        }
        return switch (state) {
            case "RUNNING", "EXECUTING", "DISPATCHED", "OPEN" -> "bg-info";
            case "SURVIVED", "SELECTED", "APPROVAL", "DELIVERED", "COMPLETED", "APPROVED" -> "bg-success";
            case "FAILED", "REJECTED" -> "bg-warning";
            case "KILLED", "ABORTED", "ERROR" -> "bg-error";
            case "SUPERSEDED" -> "bg-base-300";
            case "PENDING", "READY", "INTAKE" -> "bg-base-content/30";
            default -> "bg-primary";
        };
    }

    private static boolean isActive(String state) {
        return state != null && switch (state) {
            case "RUNNING", "EXECUTING", "DISPATCHED", "OPEN", "DESIGN", "DESIGN_REVIEW",
                 "PLAN", "TEST_AUTHORING", "FINAL_INTEGRATION" -> true;
            default -> false;
        };
    }
}

