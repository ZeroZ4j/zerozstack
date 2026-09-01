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
import com.zeroz4j.ui.component.Dialog;
import com.zeroz4j.ui.component.TextField;
import com.zeroz4j.ui.component.Tooltip;
import com.zeroz4j.ui.component.mixin.HasPositionVariant.Position;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.theme.ThemeColor;

/**
 * A short note attached to a control. The hard questions are the ones a row of coloured examples
 * never asks: does it show for the keyboard as well as the pointer, does it fit when the note is
 * a paragraph, and does it appear at all inside a box.
 */
public class TooltipShowcase extends ComponentShowcase {

    public TooltipShowcase() {
        super();
        addTitle("Tooltip");
        addDescription("A few words that appear beside a control when you point at it. It is the "
                + "top of the stacking order, because it can be attached to anything.");

        addWhatToCheck("Try this",
                "Tab onto each button without touching the mouse. The note should appear for the "
                        + "keyboard too — if it only appears on pointing, half your users never see it.",
                "Press Escape while a note is showing. It should go away.",
                "Look at the long one. It should wrap onto more lines, not run off the side.",
                "Point at the one at the right-hand edge. It should stay on the screen.",
                "Open the box and point at the control inside it. The note has to appear above the box.",
                "Read what the buttons are called with a screen reader. A note that is the only "
                        + "name a button has is a fault: the button needs its own name as well.",
                "Broken looks like: a note only the mouse can summon, one that runs off the edge, "
                        + "or a note that is the whole of a button's name.");

        addSection("The plain case", plain());
        addSection("Which side it appears on", positions());
        addSection("Colours", colours());
        addSection("A note that is a paragraph", longNote());
        addSection("At the right-hand edge, where there is no room", atTheEdge());
        addSection("On a field rather than a button", onAField());
        addSection("Inside a box", insideADialog());
        addSection("A button whose only name is its note — the case to avoid", theBadCase());
    }

    // ------------------------------------------------------------------ sections

    private static Component plain() {
        Tooltip tip = new Tooltip("Saves without closing the window.");
        Button button = new Button("Apply");
        button.setId("tooltip-plain-target");
        tip.add(button);
        return tip;
    }

    private static Component positions() {
        Div host = new Div();
        host.addClassName("flex flex-wrap gap-6 w-full py-8 justify-center");
        host.add(positioned("Above", Position.TOP), positioned("Below", Position.BOTTOM),
                positioned("To the left", Position.LEFT), positioned("To the right", Position.RIGHT));
        return host;
    }

    private static Component positioned(String label, Position position) {
        Tooltip tip = new Tooltip("It appears " + label.toLowerCase() + " the button.");
        tip.setPosition(position);
        tip.add(new Button(label));
        return tip;
    }

    private static Component colours() {
        Div host = new Div();
        host.addClassName("flex flex-wrap gap-4 w-full");
        ThemeColor[] colours = { ThemeColor.PRIMARY, ThemeColor.SECONDARY, ThemeColor.ACCENT,
            ThemeColor.INFO, ThemeColor.SUCCESS, ThemeColor.WARNING, ThemeColor.ERROR };
        String[] names = { "Primary", "Secondary", "Accent", "Info", "Success", "Warning", "Error" };
        for (int i = 0; i < colours.length; i++) {
            Tooltip tip = new Tooltip(names[i] + " note");
            tip.setThemeColor(colours[i]);
            tip.add(new Button(names[i]));
            host.add(tip);
        }
        return host;
    }

    private static Component longNote() {
        Tooltip tip = new Tooltip("This folder is cleared every night at two in the morning. "
                + "Anything left in it is deleted and cannot be got back, so move what you want "
                + "to keep before you go home.");
        tip.addClassName("before:max-w-xs before:whitespace-normal");
        Button button = new Button("Nightly clear-out");
        button.setId("tooltip-long-target");
        tip.add(button);
        return tip;
    }

    private static Component atTheEdge() {
        Div host = new Div();
        host.addClassName("flex justify-end w-full");
        Tooltip tip = new Tooltip("There is no room to the right of this one.");
        tip.setPosition(Position.RIGHT);
        Button button = new Button("Right at the edge");
        button.setId("tooltip-edge-target");
        tip.add(button);
        host.add(tip);
        return host;
    }

    private static Component onAField() {
        // The note explains the rule; the caption still names the field. Both are needed.
        Tooltip tip = new Tooltip("Between 8 and 64 characters, and not one you use anywhere else.");
        TextField field = new TextField().withLabel("Password");
        field.setId("tooltip-field");
        field.setHelperText("At least eight characters.");
        tip.add(field);
        return tip;
    }

    private static Component insideADialog() {
        Dialog dialog = new Dialog("A note inside a box");
        dialog.setId("tooltip-dialog");
        Tooltip tip = new Tooltip("This note is attached to a button inside a box.");
        Button inside = new Button("Point at me");
        inside.setId("tooltip-in-dialog");
        tip.add(inside);
        dialog.add(new Div("A note is the top of the stacking order precisely so this works."), tip);
        Button close = new Button("Close", e -> dialog.close());
        close.addClassName("btn-primary");
        dialog.addAction(close);

        Button open = new Button("Open the box");
        open.setId("tooltip-open-dialog");
        open.addClickListener(e -> dialog.open());

        Div host = new Div();
        host.addClassName("flex gap-3 w-full");
        host.add(open, dialog);
        return host;
    }

    /**
     * Kept here on purpose. The button has an icon and nothing else, so the only words describing
     * it are in a note that appears on pointing — which means somebody who cannot point never
     * learns what it does.
     */
    private static Component theBadCase() {
        Div host = new Div();
        host.addClassName("flex flex-wrap items-center gap-6 w-full");

        Tooltip bad = new Tooltip("Delete this folder");
        Button noName = new Button("🗑");
        noName.setId("tooltip-no-name");
        bad.add(noName);

        Tooltip good = new Tooltip("Delete this folder");
        Button named = new Button("🗑");
        named.setId("tooltip-with-name");
        named.withAriaLabel("Delete this folder");
        good.add(named);

        Div explanation = new Div("The left button has no name of its own. The right one has the "
                + "same note and a name as well, so it can be read out.");
        explanation.addClassName("text-sm text-base-content/60 w-full");

        host.add(bad, good, explanation);
        return host;
    }
}
