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

/**
 * Maps a discrete state name to a CSS colour, for {@link StateTimeline} and
 * {@link StatusHistory}.
 *
 * <p>{@link #DEFAULT} recognises the vocabulary system components actually emit — up/down,
 * running/exited, healthy/unhealthy, ok/warn/error — and assigns anything it does not
 * recognise a stable palette colour derived from the name, so an unknown state is at least
 * consistent between rows and between redraws.</p>
 */
@FunctionalInterface
public interface StateColor {

    String colorFor(String state);

    /** Semantic where the name is recognised, stable-by-hash where it is not. */
    StateColor DEFAULT = state -> {
        if (state == null || state.isEmpty()) {
            return Palette.BASE_300;
        }
        switch (state.toLowerCase()) {
            case "up", "ok", "running", "healthy", "active", "ready", "success",
                 "completed", "online", "loaded", "true", "pass":
                return Palette.SUCCESS;
            case "warn", "warning", "degraded", "pending", "starting", "restarting", "throttled":
                return Palette.WARNING;
            case "down", "error", "failed", "unhealthy", "crashed", "critical", "offline", "fail":
                return Palette.ERROR;
            case "exited", "stopped", "paused", "idle", "unloaded", "false":
                return Palette.BASE_300;
            case "unknown", "n/a", "":
                return "var(--color-neutral, #6b7280)";
            default:
                return Palette.series(Math.abs(state.hashCode()) % Palette.SERIES.length);
        }
    };

    /** Every state one colour — for a timeline where only the transitions matter. */
    static StateColor flat(String color) {
        return state -> color;
    }
}
