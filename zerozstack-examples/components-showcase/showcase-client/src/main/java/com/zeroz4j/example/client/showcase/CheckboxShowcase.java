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
import com.zeroz4j.signals.ValueSignal;
import com.zeroz4j.signals.Computed;

public class CheckboxShowcase extends ComponentShowcase {

    public CheckboxShowcase() {
        addTitle("Checkbox");
        addDescription("Checkboxes allow users to select one or more options from a set.");

        addWhatToCheck("Try this",
                "Tab onto every field below, including the read-only one. The disabled one must be "
                        + "skipped and the read-only one must not.",
                "Read the caption of each field. A field with no caption has no name.",
                "The one marked wrong has to say why, under the field, in words.",
                "The required ones have to show that they are required by something other than colour.",
                "The last one has a caption of 140 characters. It should wrap, not push the page out.",
                "Broken looks like: a red border with no sentence, a required field with no mark, "
                        + "or a disabled field that Tab still stops on.");

        addSection("Every state a checkbox really has", allStates());

        // Color variants
        Checkbox primary = new Checkbox();
        primary.withLabel("Primary");
        primary.setValue(true);
        primary.setThemeColor(ThemeColor.PRIMARY);

        Checkbox secondary = new Checkbox();
        secondary.withLabel("Secondary");
        secondary.setValue(true);
        secondary.setThemeColor(ThemeColor.SECONDARY);

        Checkbox accent = new Checkbox();
        accent.withLabel("Accent");
        accent.setValue(true);
        accent.setThemeColor(ThemeColor.ACCENT);

        Checkbox neutral = new Checkbox();
        neutral.withLabel("Neutral");
        neutral.setValue(true);
        neutral.setThemeColor(ThemeColor.NEUTRAL);

        Checkbox info = new Checkbox();
        info.withLabel("Info");
        info.setValue(true);
        info.setThemeColor(ThemeColor.INFO);

        Checkbox success = new Checkbox();
        success.withLabel("Success");
        success.setValue(true);
        success.setThemeColor(ThemeColor.SUCCESS);

        Checkbox warning = new Checkbox();
        warning.withLabel("Warning");
        warning.setValue(true);
        warning.setThemeColor(ThemeColor.WARNING);

        Checkbox error = new Checkbox();
        error.withLabel("Error");
        error.setValue(true);
        error.setThemeColor(ThemeColor.ERROR);

        addSection("Checkbox Colors", primary, secondary, accent, neutral, info, success, warning, error);

        // Size variants
        Checkbox xs = new Checkbox();
        xs.withLabel("Extra small");
        xs.setValue(true);
        xs.setThemeSize(ThemeSize.XS);

        Checkbox sm = new Checkbox();
        sm.withLabel("Small");
        sm.setValue(true);
        sm.setThemeSize(ThemeSize.SM);

        Checkbox md = new Checkbox();
        md.withLabel("Medium");
        md.setValue(true);
        md.setThemeSize(ThemeSize.MD);

        Checkbox lg = new Checkbox();
        lg.withLabel("Large");
        lg.setValue(true);
        lg.setThemeSize(ThemeSize.LG);

        addSection("Checkbox Sizes", xs, sm, md, lg);

        // Data Binding Demo
        ValueSignal<Boolean> signal = new ValueSignal<>(false);
        Checkbox component = new Checkbox();
        component.withLabel("Tick this and watch the line below");
        component.bindValue(signal);
        Span output = new Span();
        output.bindText(new Computed<>(() -> "Current value: " + signal.get()));
        addSection("Data Binding Demo", component, output);
    }

    private static Div allStates() {
        Checkbox plain = new Checkbox();
        plain.withLabel("Send me the occasional release note");

        Checkbox helped = new Checkbox();
        helped.withLabel("Keep me signed in");
        helped.setHelperText("Only do this on a computer nobody else uses.");

        Checkbox required = new Checkbox();
        required.withLabel("I accept the terms");
        required.setRequiredIndicatorVisible(true);

        Checkbox wrong = new Checkbox();
        wrong.withLabel("I accept the terms");
        wrong.setRequiredIndicatorVisible(true);
        wrong.setErrorMessage("The account cannot be created until you accept the terms.");

        Checkbox disabled = new Checkbox();
        disabled.withLabel("Two-factor sign-in (not on this plan)");
        disabled.setEnabled(false);
        disabled.setHelperText("Disabled: Tab skips it and it is not read out.");

        Checkbox readOnly = new Checkbox();
        readOnly.withLabel("Your account was verified");
        readOnly.setValue(true);
        FieldStates.readOnly(readOnly);
        readOnly.setHelperText("Read only: Tab still reaches it, so it can still be read out.");

        Checkbox longCaption = new Checkbox();
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
}
