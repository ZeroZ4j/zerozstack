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

import com.zeroz4j.ui.theme.TextStyle;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.Span;

/**
 * Budget burn bar: colored progress with a "used / cap" label. Green under 75%,
 * warning to 90%, error above. A cap of 0 renders as "uncapped".
 *
 * <p>The bar is two coloured boxes and means nothing on its own, so the meter carries
 * {@code role="progressbar"} with a range of 0 to 100 and a reading that follows the numbers it is
 * given. The hover title says the same thing in full - "1200 of 5000 tokens (24%)" - and that is
 * also what names the meter aloud. Give it a name of its own with {@link #setAriaLabel(String)}
 * where "Token budget" or "Monthly spend" would be clearer.</p>
 */
public final class TokenMeter extends Div {

    private final Div bar = new Div();
    private final Span label = new Span();

    public TokenMeter() {
        addClassName("flex items-center gap-2 min-w-0");
        getElement().setAttribute("role", "progressbar");
        getElement().setAttribute("aria-valuemin", "0");
        getElement().setAttribute("aria-valuemax", "100");
        Div track = new Div();
        track.addClassName("h-1.5 w-24 rounded-full bg-base-300 overflow-hidden shrink-0");
        bar.addClassName("h-full rounded-full bg-success transition-all duration-300");
        bar.setStyle("width", "0%");
        track.add(bar);
        label.addClassName(TextStyle.CAPTION.getClassNames()
                + " font-mono whitespace-nowrap");
        add(track, label);
    }

    public void set(long used, long cap) {
        if (cap <= 0) {
            bar.setStyle("width", "100%");
            bar.setClassName("h-full rounded-full bg-base-content/20");
            label.setText(compact(used) + " / uncapped");
            // No cap means there is no percentage to report. Both the reading and the hover text
            // have to be replaced, not merely left alone: a title from an earlier call that did
            // have a cap would otherwise still be sitting here claiming one.
            getElement().removeAttribute("aria-valuenow");
            getElement().setAttribute("title", used + " tokens used, no cap");
            return;
        }
        int pct = (int) Math.min(100, used * 100 / cap);
        String color = pct >= 90 ? "bg-error" : pct >= 75 ? "bg-warning" : "bg-success";
        bar.setClassName("h-full rounded-full transition-all duration-300 " + color);
        bar.setStyle("width", pct + "%");
        label.setText(compact(used) + " / " + compact(cap));
        getElement().setAttribute("aria-valuenow", String.valueOf(pct));
        getElement().setAttribute("title", used + " of " + cap + " tokens (" + pct + "%)");
    }

    /**
     * Names the meter for anybody who cannot see it - "Token budget", "Monthly spend".
     *
     * @param label the words, or null to take the name away again
     * @return this meter
     */
    public TokenMeter withAriaLabel(String label) {
        setAriaLabel(label);
        return this;
    }

    static String compact(long n) {
        if (n >= 1_000_000) {
            return (n / 100_000) / 10.0 + "M";
        }
        if (n >= 1_000) {
            return (n / 100) / 10.0 + "K";
        }
        return String.valueOf(n);
    }
}

