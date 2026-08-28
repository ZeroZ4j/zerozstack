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

public class DialogShowcase extends ComponentShowcase {

    /** Counts every close, so the page can show that one close means one notification. */
    private int closes;

    public DialogShowcase() {
        addTitle("Dialog");
        addDescription("A dialog takes over the page until it is answered. Escape closes it, a click "
                + "on the dimmed area outside the panel closes it, and the page behind it stops "
                + "responding until it does.");

        Div log = new Div("Nothing closed yet.");
        log.setId("close-log");
        log.addClassName("text-sm text-base-content/70");

        addSection("Default", defaultDialog(log));
        addSection("A wider panel", wideDialog(log));
        addSection("A question that must be answered", mustAnswerDialog(log));
        addSection("The 0.7.0 behaviour", appearanceOnlyDialog(log));
        addSection("Close events", log);
    }

    /** Escape, a click outside and the buttons all close it, and each fires one close event. */
    private Component[] defaultDialog(Div log) {
        Dialog dialog = new Dialog();
        dialog.setId("default-dialog");

        Div title = new Div("Confirm Action");
        title.addClassName("text-lg font-bold mb-4");

        Div message = new Div("Are you sure you want to perform this operation? "
                + "This action cannot be undone.");
        message.addClassName("py-4 text-base-content/85");

        dialog.add(title, message);
        dialog.addAction(new Button("Close", e -> dialog.close()));

        Button confirm = new Button("Confirm", e -> dialog.close());
        confirm.addClassName("btn-primary");
        dialog.addAction(confirm);

        dialog.addCloseListener(e -> {
            closes++;
            log.getElement().setTextContent("Closed " + closes + " time(s); last close "
                    + (e.isFromClient() ? "came from the user." : "was asked for by the code."));
        });

        Button open = new Button("Open Dialog", e -> dialog.open());
        open.setId("open-default");
        open.addClassName("btn-primary");
        return new Component[] { open, dialog };
    }

    /** setWidth sizes the visible panel, not the full-window overlay it sits in. */
    private Component[] wideDialog(Div log) {
        Dialog dialog = new Dialog();
        dialog.setId("wide-dialog");
        dialog.setWidth("56rem");

        Div title = new Div("Review changes");
        title.addClassName("text-lg font-bold mb-4");
        dialog.add(title, new Div("One call — setWidth(\"56rem\") — and the panel is as wide as "
                + "the content needs, without the application knowing that the panel is a separate "
                + "element or what its stylesheet class is called."));
        dialog.addAction(new Button("Close", e -> dialog.close()));

        dialog.addCloseListener(e -> {
            closes++;
            log.getElement().setTextContent("Closed " + closes + " time(s).");
        });

        Button open = new Button("Open Wide Dialog", e -> dialog.open());
        open.setId("open-wide");
        return new Component[] { open, dialog };
    }

    /** Neither exit is available, so the only way out is the button. */
    private Component[] mustAnswerDialog(Div log) {
        Dialog dialog = new Dialog();
        dialog.setId("must-answer-dialog");
        dialog.setCloseOnEsc(false);
        dialog.setCloseOnOutsideClick(false);

        Div title = new Div("Delete the account?");
        title.addClassName("text-lg font-bold mb-4");
        dialog.add(title, new Div("Escape does nothing here and neither does clicking outside. "
                + "When you take those away, leave the user a button."));

        Button cancel = new Button("Keep it", e -> dialog.close());
        cancel.setId("must-answer-cancel");
        cancel.addClassName("btn-primary");
        dialog.addAction(cancel);

        dialog.addCloseListener(e -> {
            closes++;
            log.getElement().setTextContent("Closed " + closes + " time(s).");
        });

        Button open = new Button("Open Blocking Dialog", e -> dialog.open());
        open.setId("open-must-answer");
        return new Component[] { open, dialog };
    }

    /** setModal(false) restores the dialog of 0.7.0 and earlier: an appearance, nothing more. */
    private Component[] appearanceOnlyDialog(Div log) {
        Dialog dialog = new Dialog();
        dialog.setId("legacy-dialog");
        dialog.setModal(false);
        dialog.setCloseOnOutsideClick(false);

        Div title = new Div("The old behaviour");
        title.addClassName("text-lg font-bold mb-4");
        dialog.add(title, new Div("The browser does not own this one. Escape does nothing and the "
                + "button below is the only way out. This is what every dialog did before 0.8.0, "
                + "kept for applications that were relying on it. It takes both calls: turning the "
                + "native behaviour off, and turning off closing on a click outside."));

        Button close = new Button("Close", e -> dialog.close());
        close.setId("legacy-close");
        close.addClassName("btn-primary");
        dialog.addAction(close);

        dialog.addCloseListener(e -> {
            closes++;
            log.getElement().setTextContent("Closed " + closes + " time(s).");
        });

        Button open = new Button("Open Old-Style Dialog", e -> dialog.open());
        open.setId("open-legacy");
        return new Component[] { open, dialog };
    }
}
