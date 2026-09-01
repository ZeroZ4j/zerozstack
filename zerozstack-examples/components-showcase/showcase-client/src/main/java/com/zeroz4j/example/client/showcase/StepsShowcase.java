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

import com.zeroz4j.ui.component.Button;
import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.component.Steps;
import com.zeroz4j.ui.layout.Div;

/**
 * How far through something you are. Eight steps rather than four, with long names, a step that
 * failed, and a step counter that says in words where you are rather than only colouring it in.
 */
public class StepsShowcase extends ComponentShowcase {

    private static final String[] NAMES = {
        "Create the account",
        "Confirm your email address",
        "Choose a plan",
        "Enter your billing address",
        "Umsatzsteuer-Identifikationsnummer prüfen",
        "Set up two-factor sign-in",
        "Invite the rest of your team",
        "Finish",
    };

    private int current = 3;

    private final Steps steps = new Steps();
    private final Div readout = new Div();

    public StepsShowcase() {
        super();
        addTitle("Steps");
        addDescription("A line of steps showing how far through something somebody is. This one "
                + "has eight of them, one with a very long German name, and one that failed.");

        addWhatToCheck("Try this",
                "Press forwards and backwards and watch the line fill in.",
                "Read the line under the steps. It has to say where you are in words, because "
                        + "colour alone tells a colour-blind reader nothing.",
                "The failed step should be marked as failed, not just left uncoloured.",
                "Make the window narrow and check the eight steps do not push the page sideways.",
                "Broken looks like: a page that scrolls sideways, or the only sign of where you "
                        + "are being a change of colour.");

        steps.setId("steps-horizontal");
        steps.addClassName("w-full");
        readout.setId("steps-readout");
        readout.addClassName("text-sm text-base-content/70");

        render();

        addSection("Eight steps", steps);
        addSection("Where you are, in words", readout);
        addSection("Move", controls());
        addSection("Stacked, for a narrow window", vertical());
        addSection("One step failed", withFailure());
    }

    // ------------------------------------------------------------------ the control

    private void render() {
        steps.removeAll();
        // The whole line is one thing with a name, so it is not read out as eight loose words.
        steps.getElement().setAttribute("role", "list");
        steps.setAriaLabel("Setting up your account, step " + (current + 1) + " of " + NAMES.length);
        for (int i = 0; i < NAMES.length; i++) {
            steps.add(step(NAMES[i], i <= current, i == current, false));
        }
        readout.setText("Step " + (current + 1) + " of " + NAMES.length + ": " + NAMES[current]
                + ". " + current + " done, " + (NAMES.length - current - 1) + " to go.");
    }

    private Component controls() {
        Button back = new Button("Back", e -> {
            current = Math.max(0, current - 1);
            render();
        });
        back.setId("steps-back");
        Button forwards = new Button("Forwards", e -> {
            current = Math.min(NAMES.length - 1, current + 1);
            render();
        });
        forwards.setId("steps-forwards");
        forwards.addClassName("btn-primary");

        Div host = new Div();
        host.addClassName("flex gap-2 w-full");
        host.add(back, forwards);
        return host;
    }

    private static Component vertical() {
        Steps stacked = new Steps();
        stacked.setId("steps-vertical");
        stacked.addClassName("steps-vertical");
        stacked.getElement().setAttribute("role", "list");
        stacked.setAriaLabel("The same eight steps, stacked");
        for (int i = 0; i < NAMES.length; i++) {
            stacked.add(step(NAMES[i], i <= 3, i == 3, false));
        }
        return stacked;
    }

    private static Component withFailure() {
        Steps failed = new Steps();
        failed.setId("steps-failed");
        failed.addClassName("w-full");
        failed.getElement().setAttribute("role", "list");
        failed.setAriaLabel("Setting up your account, stopped at step 4 of 8");
        for (int i = 0; i < NAMES.length; i++) {
            failed.add(step(NAMES[i], i <= 3, false, i == 3));
        }
        return failed;
    }

    // ------------------------------------------------------------------ one step

    /**
     * One step. The state is written into the words as well as the colour, because a step whose
     * only difference is its shade says nothing to somebody who cannot see the difference.
     */
    private static Component step(String name, boolean done, boolean here, boolean failed) {
        Component item = new Component("li") {
        };
        String classes = "step";
        String state;
        if (failed) {
            classes += " step-error";
            state = "could not be finished";
        } else if (here) {
            classes += " step-primary";
            state = "you are here";
        } else if (done) {
            classes += " step-primary";
            state = "done";
        } else {
            state = "still to do";
        }
        item.getElement().setClassName(classes);
        item.getElement().setTextContent(name);
        item.getElement().setAttribute("role", "listitem");
        item.setAriaLabel(name + " — " + state);
        item.getElement().setAttribute("title", name + " — " + state);
        return item;
    }
}
