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

import com.zeroz4j.ui.component.Alert;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.theme.TextStyle;

public class AlertShowcase extends ComponentShowcase {

    /** Shows that pressing an action really did something, rather than looking like it might. */
    private final Div actionLog = new Div("Nothing pressed yet.");

    public AlertShowcase() {
        addTitle("Alert");
        addDescription("The tinted notice. A strip of prose the reader is meant to act on. Pick the tone by "
            + "what you are saying, not by a colour: worth knowing, it worked, be careful, it "
            + "failed.");

        addSection("The four tones",
            column(
                Alert.info("The next scheduled backup runs at 02:00."),
                Alert.success("Your profile has been updated."),
                Alert.caution("This disk is 94% full."),
                Alert.danger("Nothing was saved.")));

        addSection("With a heading and something to do about it",
            column(
                Alert.danger("The connection to the server was refused, so none of the three "
                        + "files were uploaded.")
                     .withHeading("The upload failed")
                     .withAction("Try again", e -> actionLog.setText("Pressed: Try again")),
                Alert.caution("Two people are editing this page. The last one to save wins.")
                     .withHeading("Somebody else is here")
                     .withAction("Reload", e -> actionLog.setText("Pressed: Reload"))));

        actionLog.setId("alert-action-log");
        actionLog.addClassName("text-sm text-base-content/70");
        addSection("What was pressed", actionLog);

        addSection("Long text wraps rather than running off the side",
            column(
                Alert.info("A notice can be as long as it needs to be. This one keeps going well "
                        + "past the width of any sensible column, so that the wrapping is "
                        + "obvious: the words carry on to a second line and a third, the tone "
                        + "mark stays where it is at the top left, and nothing is shortened, "
                        + "clipped or hidden behind a hover.")
                     .withHeading("A notice with rather a lot to say")));

        addSection("Without the tone mark",
            column(
                Alert.success("Saved.").withHeading("Done"),
                plain(Alert.caution("The licence expires in three days."))));

        Div note = new Div();
        note.add(TextStyle.CAPTION.paragraph(
            "setThemeColor and new Alert(text, \"alert-info\") still work and are deprecated: "
                + "they spell out a stylesheet class, which nothing checks and no reader "
                + "understands."));
        addSection("The older way", note);
    }

    private static Alert plain(Alert alert) {
        alert.setIconVisible(false);
        return alert;
    }

    /** The notices are full-width blocks, so they stack rather than sitting side by side. */
    private static Div column(Alert... alerts) {
        Div stack = new Div();
        stack.addClassName("flex flex-col gap-3 w-full");
        stack.add(alerts);
        return stack;
    }
}
