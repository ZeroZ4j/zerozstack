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
 * Colored state dot with an optional pulse while active. Knows the SwarmCoder state
 * vocabulary (RunState / CandidateState / session outcomes) so every surface colors
 * states identically.
 */
public final class StatusDot extends Span {

    private final Span dot = new Span();
    private final Span ping = new Span();

    public StatusDot(String state) {
        addClassName("relative inline-flex w-2.5 h-2.5 shrink-0");
        ping.addClassName("absolute inline-flex w-full h-full rounded-full opacity-60 animate-ping");
        dot.addClassName("relative inline-flex w-2.5 h-2.5 rounded-full");
        getElement().appendChild(ping.getElement());
        getElement().appendChild(dot.getElement());
        setState(state);
    }

    public void setState(String state) {
        String color = colorFor(state);
        dot.setClassName("relative inline-flex w-2.5 h-2.5 rounded-full " + color);
        boolean active = isActive(state);
        ping.setClassName("absolute inline-flex w-full h-full rounded-full opacity-60 "
            + color + (active ? " animate-ping" : " hidden"));
        getElement().setAttribute("title", state == null ? "" : state);
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

