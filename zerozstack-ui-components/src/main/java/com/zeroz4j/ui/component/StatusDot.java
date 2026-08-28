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
 * person reads when they hover it. Give both whenever you have words worth reading:</p>
 *
 * <pre>{@code
 * new StatusDot("DISPATCHED", "Sent to a worker");
 * dot.setState("FAILED", "Could not finish");
 * }</pre>
 *
 * <p><b>Given only a state, the dot writes the hover text itself</b>, in ordinary language:
 * {@code DESIGN_REVIEW} hovers as "Design review" rather than as the constant. That is a fallback,
 * not an excuse - it can only reword the name it was given, so a name that means nothing to the
 * reader still means nothing once the underscores are gone. It stops a console full of dots
 * shouting {@code DISPATCHED} at somebody who does not work on the code. Text that already reads
 * like a sentence is left exactly as it was.</p>
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
     * Creates a dot coloured by the given state, with the state reworded into ordinary language
     * for the hover text.
     *
     * @param state the state name, which decides colour and pulse
     */
    public StatusDot(String state) {
        this(state, readableLabel(state));
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
     * Sets the state, rewording it into ordinary language for the hover text. Call
     * {@link #setState(String, String)} instead whenever you have better words than the name.
     *
     * @param state the state name, which decides colour and pulse
     */
    public void setState(String state) {
        setState(state, readableLabel(state));
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

    /**
     * Turns a state name into words a person can read.
     *
     * <p>{@code DESIGN_REVIEW} becomes "Design review". Anything that does not look like a
     * constant - anything with a lower-case letter or a space in it - is already somebody's
     * writing and is handed back untouched.</p>
     *
     * @param state the state name
     * @return the same words, readable; an empty string when there was no state
     */
    public static String readableLabel(String state) {
        if (state == null || state.isEmpty()) {
            return "";
        }
        if (!looksLikeAConstant(state)) {
            return state;
        }
        StringBuilder out = new StringBuilder(state.length());
        boolean first = true;
        for (int i = 0; i < state.length(); i++) {
            char c = state.charAt(i);
            if (c == '_' || c == '-') {
                out.append(' ');
            } else if (first) {
                out.append(Character.toUpperCase(c));
                first = false;
            } else {
                out.append(Character.toLowerCase(c));
            }
        }
        return out.toString().trim();
    }

    /** A constant is upper case, digits, underscores and hyphens - and nothing else. */
    private static boolean looksLikeAConstant(String state) {
        boolean sawLetter = false;
        for (int i = 0; i < state.length(); i++) {
            char c = state.charAt(i);
            if (c == '_' || c == '-' || (c >= '0' && c <= '9')) {
                continue;
            }
            if (Character.isLetter(c) && !Character.isLowerCase(c)) {
                sawLetter = true;
                continue;
            }
            return false;
        }
        return sawLetter;
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

