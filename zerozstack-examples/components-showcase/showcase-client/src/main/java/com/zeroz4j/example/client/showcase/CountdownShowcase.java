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

import com.zeroz4j.ui.component.*;
import com.zeroz4j.ui.layout.*;

public class CountdownShowcase extends ComponentShowcase {

    public CountdownShowcase() {
        addTitle("Countdown");
        addDescription("Countdown displays a numerical countdown timer with custom styles.");

        // Simple Countdown
        Countdown countdown = new Countdown();
        countdown.addClassName("font-mono text-5xl");

        Component valHours = new Component("span") {};
        valHours.getElement().getStyle().setProperty("--value", "10");

        Component valMins = new Component("span") {};
        valMins.getElement().getStyle().setProperty("--value", "24");

        Component valSecs = new Component("span") {};
        valSecs.getElement().getStyle().setProperty("--value", "45");

        countdown.getElement().appendChild(valHours.getElement());
        countdown.getElement().appendChild(valMins.getElement());
        countdown.getElement().appendChild(valSecs.getElement());

        addSection("Countdown Example", countdown);
    }
}
