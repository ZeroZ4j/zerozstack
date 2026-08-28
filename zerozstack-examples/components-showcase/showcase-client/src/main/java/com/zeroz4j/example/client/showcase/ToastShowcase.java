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
import com.zeroz4j.ui.component.Button;
import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.component.Dialog;
import com.zeroz4j.ui.component.Toast;
import com.zeroz4j.ui.layout.Div;
import org.teavm.jso.browser.Window;

/**
 * A short message about something that has already happened. This page raises real ones onto the
 * page rather than drawing a picture of one, because everything interesting about a toast — where
 * it goes, what covers it, whether Escape takes it away — only happens when it is really there.
 */
public class ToastShowcase extends ComponentShowcase {

    private int raised;

    public ToastShowcase() {
        super();
        addTitle("Toast");
        addDescription("A short message that appears in the corner, says what just happened, and "
                + "goes away again. It never takes the keyboard, so it never interrupts typing.");

        addWhatToCheck("Try this",
                "Raise one. It should appear in the corner and go away on its own after six seconds.",
                "Raise five at once. They should stack, not sit on top of each other.",
                "Press Escape while one is showing. It should go.",
                "Tab while one is showing. The keyboard must not jump into the message.",
                "Raise one while the box is open. A message about what you just did in a box is "
                        + "no use if the box hides it — but note that a box the browser owns is "
                        + "above every message, so this is the case to look at hardest.",
                "Broken looks like: messages piled on top of each other, one that never goes away, "
                        + "or the keyboard landing inside one.");

        addSection("One message", one());
        addSection("Five at once", many());
        addSection("The four tones", tones());
        addSection("One that interrupts, for something that cannot wait", urgent());
        addSection("One that Escape does not take away", stubborn());
        addSection("Raised while a box is open", overADialog());
        addSection("What it looks like sitting still, for a screenshot", stationary());
    }

    // ------------------------------------------------------------------ sections

    private Component one() {
        Button button = new Button("Say it was saved");
        button.setId("toast-one");
        button.addClassName("btn-primary");
        button.addClickListener(e -> raise(Alert.success("Your changes were saved."), true, true));
        return button;
    }

    private Component many() {
        Button button = new Button("Raise five");
        button.setId("toast-many");
        button.addClickListener(e -> {
            raise(Alert.info("The export has started."), true, true);
            raise(Alert.info("Three files were read."), true, true);
            raise(Alert.caution("One file was skipped: it was empty."), true, true);
            raise(Alert.info("The export finished."), true, true);
            raise(Alert.success("The file is ready to download."), true, true);
        });
        return button;
    }

    private Component tones() {
        Div host = new Div();
        host.addClassName("flex flex-wrap gap-2 w-full");
        host.add(toneButton("Something happened", () -> Alert.info("The nightly job has started.")));
        host.add(toneButton("It worked", () -> Alert.success("The invoice was sent.")));
        host.add(toneButton("Careful", () -> Alert.caution("Two rows were skipped.")));
        host.add(toneButton("It failed", () -> Alert.danger("The invoice could not be sent.")));
        return host;
    }

    private Button toneButton(String label, java.util.function.Supplier<Alert> alert) {
        Button button = new Button(label);
        button.addClassName("btn-sm");
        button.addClickListener(e -> raise(alert.get(), true, true));
        return button;
    }

    private Component urgent() {
        Button button = new Button("Interrupt whatever is being read out");
        button.setId("toast-urgent");
        button.addClickListener(e -> raise(
                Alert.danger("Your session ends in one minute. Save your work."), true, false));
        return button;
    }

    private Component stubborn() {
        Button button = new Button("Raise one that Escape will not take away");
        button.setId("toast-stubborn");
        button.addClickListener(e -> raise(
                Alert.caution("This one has to be closed with its own button."), false, true));
        return button;
    }

    private Component overADialog() {
        Dialog dialog = new Dialog("Rename the folder");
        dialog.setId("toast-dialog");
        dialog.add(new Div("Press the button below and watch where the message goes. The browser "
                + "owns this box, so it sits above everything a stacking number can reach."));

        Button raiseFromInside = new Button("Say it was renamed");
        raiseFromInside.setId("toast-from-dialog");
        raiseFromInside.addClickListener(e -> raise(Alert.success("The folder was renamed."),
                true, true));
        dialog.addAction(raiseFromInside);

        Button close = new Button("Close", e -> dialog.close());
        close.addClassName("btn-primary");
        dialog.addAction(close);

        Button open = new Button("Open the box");
        open.setId("toast-open-dialog");
        open.addClickListener(e -> dialog.open());

        Div host = new Div();
        host.addClassName("flex gap-3 w-full");
        host.add(open, dialog);
        return host;
    }

    /** The same component, parked in the page, so a screenshot can be taken of it. */
    private static Component stationary() {
        Div host = new Div();
        host.addClassName("flex flex-col gap-3 w-full");

        Toast plain = new Toast("Message sent successfully.");
        plain.addClassName("relative");

        Toast withAlert = new Toast();
        withAlert.addClassName("relative");
        withAlert.add(Alert.info("A new message arrived while you were away."));

        host.add(plain, withAlert);
        return host;
    }

    // ------------------------------------------------------------------ raising one

    /**
     * Puts a real message on the page. The corner and the stacking are the component's own; nothing
     * here picks a position or a stacking number.
     */
    private void raise(Alert alert, boolean closeOnEsc, boolean polite) {
        raised++;
        Toast toast = new Toast();
        toast.setId("toast-live-" + raised);
        toast.addClassName("toast-top toast-end");
        toast.setCloseOnEsc(closeOnEsc);
        toast.setUrgent(!polite);

        if (!closeOnEsc) {
            alert.setAction("Close", e -> toast.close());
        }
        toast.add(alert);

        toast.show();
        if (closeOnEsc) {
            Window.setTimeout(toast::close, 6000);
        }
    }
}
