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

import com.zeroz4j.ui.component.TokenMeter;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.Span;

public class TokenMeterShowcase extends ComponentShowcase {

    public TokenMeterShowcase() {
        super();
        addTitle("Token Meter");
        addDescription("A budget burn bar with a 'used / cap' label: green under 75 percent, "
            + "amber to 90, red above. Built for LLM token budgets, but it fits any consumable "
            + "quota. A cap of zero renders as uncapped.");

        addSection("Burn levels",
            labelled("comfortable", 120_000, 1_000_000),
            labelled("watch it", 800_000, 1_000_000),
            labelled("nearly out", 960_000, 1_000_000));

        addSection("Edge cases",
            labelled("uncapped", 2_400_000, 0),
            labelled("untouched", 0, 500_000),
            labelled("over cap", 1_200_000, 1_000_000));
    }

    private Div labelled(String caption, long used, long cap) {
        Div row = new Div();
        row.addClassName("flex flex-col gap-1");
        Span label = new Span(caption);
        label.addClassName("text-xs text-base-content/50");
        TokenMeter meter = new TokenMeter();
        meter.set(used, cap);
        row.add(label, meter);
        return row;
    }
}
