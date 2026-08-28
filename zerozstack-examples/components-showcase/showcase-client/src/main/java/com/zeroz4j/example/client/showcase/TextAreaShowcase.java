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

public class TextAreaShowcase extends ComponentShowcase {

    public TextAreaShowcase() {
        addTitle("TextArea");
        addDescription("TextArea allows users to enter multi-line text.");

        addWhatToCheck("Try this",
                "Tab onto every field below, including the read-only one. The disabled one must be "
                        + "skipped and the read-only one must not.",
                "Read the caption of each field. A field with no caption has no name.",
                "The one marked wrong has to say why, under the field, in words.",
                "The required ones have to show that they are required by something other than colour.",
                "The last one has a caption of 140 characters. It should wrap, not push the page out.",
                "Broken looks like: a red border with no sentence, a required field with no mark, "
                        + "or a disabled field that Tab still stops on.");

        // Basic TextArea
        TextArea basicTextArea = new TextArea("Write your bio here...").withLabel("About you");
        addSection("Basic TextArea", basicTextArea);

        addSection("Every state a text area really has", allStates());

        // Colors
        TextArea taPrimary = new TextArea("Primary").setThemeColor(ThemeColor.PRIMARY);
        TextArea taSecondary = new TextArea("Secondary").setThemeColor(ThemeColor.SECONDARY);
        TextArea taAccent = new TextArea("Accent").setThemeColor(ThemeColor.ACCENT);
        TextArea taNeutral = new TextArea("Neutral").setThemeColor(ThemeColor.NEUTRAL);
        TextArea taInfo = new TextArea("Info").setThemeColor(ThemeColor.INFO);
        TextArea taSuccess = new TextArea("Success").setThemeColor(ThemeColor.SUCCESS);
        TextArea taWarning = new TextArea("Warning").setThemeColor(ThemeColor.WARNING);
        TextArea taError = new TextArea("Error").setThemeColor(ThemeColor.ERROR);

        addSection("Colors",
            taPrimary, taSecondary, taAccent, taNeutral,
            taInfo, taSuccess, taWarning, taError
        );

        // Sizes
        TextArea taXs = new TextArea("Extra Small").setThemeSize(ThemeSize.XS);
        TextArea taSm = new TextArea("Small").setThemeSize(ThemeSize.SM);
        TextArea taMd = new TextArea("Medium").setThemeSize(ThemeSize.MD);
        TextArea taLg = new TextArea("Large").setThemeSize(ThemeSize.LG);

        addSection("Sizes",
            taXs, taSm, taMd, taLg
        );

        // Data Binding Demo
        ValueSignal<String> signal = new ValueSignal<>("Hello World");
        TextArea component = new TextArea();
        component.bindValue(signal);
        Span output = new Span();
        output.bindText(new Computed<>(() -> "Current value: " + signal.get()));
        addSection("Data Binding Demo", component, output);
    }

    /** The seven states, so none of them is discovered for the first time in an application. */
    private static Div allStates() {
        TextArea plain = new TextArea().withLabel("Notes");

        TextArea helped = new TextArea().withLabel("Delivery instructions")
            .withHelperText("Where to leave the parcel if nobody is in.");

        TextArea required = new TextArea().withLabel("Reason for the return");
        required.setRequiredIndicatorVisible(true);
        required.setHelperText("We cannot process a return without one.");

        TextArea wrong = new TextArea().withLabel("Reason for the return");
        wrong.setRequiredIndicatorVisible(true);
        wrong.setValue("no");
        wrong.setErrorMessage("Tell us a little more than that - at least ten characters.");

        TextArea disabled = new TextArea().withLabel("Internal notes");
        disabled.setValue("Only support staff may edit this.");
        disabled.setEnabled(false);
        disabled.setHelperText("Disabled: Tab skips it and it is not read out.");

        TextArea readOnly = FieldStates.readOnly(new TextArea().withLabel("What you sent us"));
        readOnly.setValue("The parcel arrived with the seal broken.");
        readOnly.setHelperText("Read only: Tab still reaches it, so it can still be read out.");

        TextArea longCaption = new TextArea().withLabel(FieldStates.LONG_CAPTION);
        longCaption.setHelperText("A caption of 140 characters.");

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
