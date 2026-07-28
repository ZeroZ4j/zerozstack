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
import com.zeroz4j.ui.theme.*;
import com.zeroz4j.signals.*;

public class StepsShowcase extends ComponentShowcase {

    public StepsShowcase() {
        addTitle("Steps");
        addDescription("Steps is used to show a progress of steps.");

        // Horizontal steps
        Steps steps = new Steps();
        
        class StepItem extends Component {
            public StepItem(String text, boolean active) {
                super("li");
                addClassName("step");
                if (active) {
                    addClassName("step-primary");
                }
                getElement().setTextContent(text);
            }
        }

        steps.add(
            new StepItem("Register", true),
            new StepItem("Choose plan", true),
            new StepItem("Purchase", false),
            new StepItem("Receive Product", false)
        );

        addSection("Horizontal Steps", steps);

        // Vertical steps
        Steps verticalSteps = new Steps();
        verticalSteps.addClassName("steps-vertical");
        
        verticalSteps.add(
            new StepItem("Step 1", true),
            new StepItem("Step 2", true),
            new StepItem("Step 3", false)
        );

        addSection("Vertical Steps", verticalSteps);
    }
}
