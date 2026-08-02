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
package com.zeroz4j.example.client.showcase;

import com.zeroz4j.ui.component.StatusDot;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.Span;

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
    }

    private Div labelled(String state) {
        Div row = new Div();
        row.addClassName("flex items-center gap-2");
        Span caption = new Span(state);
        caption.addClassName("font-mono text-xs text-base-content/70");
        row.add(new StatusDot(state), caption);
        return row;
    }
}
