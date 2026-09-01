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

import com.zeroz4j.ui.component.StatusDot;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.Span;
import com.zeroz4j.ui.theme.TextStyle;

public class StatusDotShowcase extends ComponentShowcase {

    public StatusDotShowcase() {
        super();
        addTitle("Status Dot");
        addDescription("A coloured state dot that pulses while the state is active. It knows a "
            + "fixed state vocabulary, so every surface in an application colours the same state "
            + "identically — the point is consistency, not decoration.");

        addSection("Active states — these pulse",
            labelled("RUNNING"), labelled("EXECUTING"), labelled("DISPATCHED"), labelled("OPEN"));

        addSection("Settled states",
            labelled("COMPLETED"), labelled("APPROVED"), labelled("DELIVERED"), labelled("SUPERSEDED"));

        addSection("Trouble",
            labelled("FAILED"), labelled("REJECTED"), labelled("KILLED"), labelled("ERROR"));

        addSection("Waiting",
            labelled("PENDING"), labelled("READY"), labelled("INTAKE"), labelled("SOMETHING_ELSE"));

        addSection("Given only a state, the dot writes the hover text itself - hover these",
            labelled("DESIGN_REVIEW"), labelled("FINAL_INTEGRATION"), labelled("TEST_AUTHORING"));

        addSection("Better still, give it the words - hover each of these",
            described("DISPATCHED", "Sent to a worker"),
            described("EXECUTING", "Working on it now"),
            described("SUPERSEDED", "Replaced by a newer attempt"),
            described("KILLED", "Stopped before it finished"));
    }

    /** Coloured by an internal state name, but hovered and announced in words a person reads. */
    private Div described(String state, String label) {
        Div row = new Div();
        row.addClassName("flex items-center gap-2");
        Span caption = TextStyle.CAPTION.span(label);
        row.add(new StatusDot(state, label), caption);
        return row;
    }

    private Div labelled(String state) {
        Div row = new Div();
        row.addClassName("flex items-center gap-2");
        Span caption = TextStyle.CAPTION.span(state);
        caption.addClassName("font-mono");
        row.add(new StatusDot(state), caption);
        return row;
    }
}
