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

import com.zeroz4j.signals.Computed;
import com.zeroz4j.signals.ValueSignal;
import com.zeroz4j.ui.component.Toggle;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.Span;
import com.zeroz4j.ui.theme.ThemeColor;
import com.zeroz4j.ui.theme.ThemeSize;

/**
 * A switch that is on or off. Every one on this page has a caption, because a switch with no
 * caption is a control nobody can name — and until this page was rewritten, all fourteen of them
 * had none.
 */
public class ToggleShowcase extends ComponentShowcase {

    public ToggleShowcase() {
        super();
        addTitle("Toggle");
        addDescription("A tick box drawn as a switch. It is on or off, and it takes effect at "
                + "once — use a button instead when the change only happens on save.");

        addWhatToCheck("Try this",
                "Tab onto each switch and press Space. It should flip.",
                "Read the caption beside each switch. A switch with no caption cannot be named by "
                        + "anybody who cannot see which row it is in.",
                "The disabled one must be skipped by Tab; the read-only one must not.",
                "The one marked wrong has to say why, in words, under it.",
                "Broken looks like: a switch with no caption, or one whose only sign of being on "
                        + "is its colour.");

        addSection("A switch with a caption", basic());
        addSection("Every state a switch really has", allStates());
        addSection("Colours", colours());
        addSection("Sizes", sizes());
        addSection("Bound to a signal", bound());
    }

    // ------------------------------------------------------------------ sections

    private static Div basic() {
        Toggle toggle = new Toggle();
        toggle.setId("toggle-basic");
        toggle.withLabel("Send me a weekly summary");
        toggle.setValue(true);
        return wrap(toggle);
    }

    private static Div allStates() {
        Toggle plain = new Toggle();
        plain.withLabel("Dark background");

        Toggle helped = new Toggle();
        helped.withLabel("Keep me signed in");
        helped.setHelperText("Only on a computer nobody else uses.");

        Toggle required = new Toggle();
        required.withLabel("I agree to the house rules");
        required.setRequiredIndicatorVisible(true);

        Toggle wrong = new Toggle();
        wrong.withLabel("I agree to the house rules");
        wrong.setRequiredIndicatorVisible(true);
        wrong.setErrorMessage("You have to agree before you can carry on.");

        Toggle disabled = new Toggle();
        disabled.withLabel("Two-factor sign-in (not on this plan)");
        disabled.setEnabled(false);
        disabled.setHelperText("Disabled: Tab skips it and it is not read out.");

        Toggle readOnly = new Toggle();
        readOnly.withLabel("Your account was verified");
        readOnly.setValue(true);
        FieldStates.readOnly(readOnly);
        readOnly.setHelperText("Read only: Tab still reaches it, so it can still be read out.");

        Toggle longCaption = new Toggle();
        longCaption.withLabel(FieldStates.LONG_CAPTION);

        return FieldStates.stack(
                FieldStates.labelled("Caption only", plain),
                FieldStates.labelled("Caption and helper text", helped),
                FieldStates.labelled("Required", required),
                FieldStates.labelled("Wrong, and saying why", wrong),
                FieldStates.labelled("Disabled", disabled),
                FieldStates.labelled("Read only", readOnly),
                FieldStates.labelled("A very long caption", longCaption));
    }

    private static Div colours() {
        ThemeColor[] colours = { ThemeColor.PRIMARY, ThemeColor.SECONDARY, ThemeColor.ACCENT,
            ThemeColor.NEUTRAL, ThemeColor.INFO, ThemeColor.SUCCESS, ThemeColor.WARNING,
            ThemeColor.ERROR };
        String[] names = { "Primary", "Secondary", "Accent", "Neutral", "Info", "Success",
            "Warning", "Error" };
        Div host = new Div();
        host.addClassName("flex flex-wrap gap-6 w-full");
        for (int i = 0; i < colours.length; i++) {
            Toggle toggle = new Toggle();
            toggle.setThemeColor(colours[i]);
            toggle.withLabel(names[i]);
            toggle.setValue(true);
            host.add(toggle);
        }
        return host;
    }

    private static Div sizes() {
        ThemeSize[] sizes = { ThemeSize.XS, ThemeSize.SM, ThemeSize.MD, ThemeSize.LG };
        String[] names = { "Extra small", "Small", "Medium", "Large" };
        Div host = new Div();
        host.addClassName("flex flex-wrap items-end gap-6 w-full");
        for (int i = 0; i < sizes.length; i++) {
            Toggle toggle = new Toggle();
            toggle.setThemeSize(sizes[i]);
            toggle.withLabel(names[i]);
            toggle.setValue(true);
            host.add(toggle);
        }
        return host;
    }

    private static Div bound() {
        ValueSignal<Boolean> signal = new ValueSignal<>(false);
        Toggle toggle = new Toggle();
        toggle.setId("toggle-bound");
        toggle.withLabel("Flip this and watch the line below");
        toggle.bindValue(signal);

        Span output = new Span();
        output.addClassName("text-sm");
        output.bindText(new Computed<>(() -> "Currently: " + (signal.get() ? "on" : "off")));

        Div host = new Div();
        host.addClassName("flex flex-col gap-2 w-full");
        host.add(toggle, output);
        return host;
    }

    private static Div wrap(Toggle toggle) {
        Div host = new Div();
        host.addClassName("w-full max-w-sm");
        host.add(toggle);
        return host;
    }
}
