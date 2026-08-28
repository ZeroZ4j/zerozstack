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

public class RangeShowcase extends ComponentShowcase {

    public RangeShowcase() {
        addTitle("Range");
        addDescription("Range slider is used to select a value from a range.");

        addWhatToCheck("Try this",
                "Tab onto every field below, including the read-only one. The disabled one must be "
                        + "skipped and the read-only one must not.",
                "Read the caption of each field. A field with no caption has no name.",
                "The one marked wrong has to say why, under the field, in words.",
                "The required ones have to show that they are required by something other than colour.",
                "The last one has a caption of 140 characters. It should wrap, not push the page out.",
                "Broken looks like: a red border with no sentence, a required field with no mark, "
                        + "or a disabled field that Tab still stops on.");

        // Basic Range
        Range basicRange = new Range();
        basicRange.withLabel("Volume");
        basicRange.setValue(40.0);
        addSection("Basic Range", basicRange);

        addSection("Every state a slider really has", allStates());

        // Colors
        Range rangePrimary = new Range().setThemeColor(ThemeColor.PRIMARY);
        rangePrimary.setValue(20.0);
        Range rangeSecondary = new Range().setThemeColor(ThemeColor.SECONDARY);
        rangeSecondary.setValue(30.0);
        Range rangeAccent = new Range().setThemeColor(ThemeColor.ACCENT);
        rangeAccent.setValue(40.0);
        Range rangeNeutral = new Range().setThemeColor(ThemeColor.NEUTRAL);
        rangeNeutral.setValue(50.0);
        Range rangeInfo = new Range().setThemeColor(ThemeColor.INFO);
        rangeInfo.setValue(60.0);
        Range rangeSuccess = new Range().setThemeColor(ThemeColor.SUCCESS);
        rangeSuccess.setValue(70.0);
        Range rangeWarning = new Range().setThemeColor(ThemeColor.WARNING);
        rangeWarning.setValue(80.0);
        Range rangeError = new Range().setThemeColor(ThemeColor.ERROR);
        rangeError.setValue(90.0);
        
        addSection("Colors", 
            rangePrimary, rangeSecondary, rangeAccent, rangeNeutral, 
            rangeInfo, rangeSuccess, rangeWarning, rangeError
        );

        // Sizes
        Range rangeXs = new Range().setThemeSize(ThemeSize.XS);
        rangeXs.setValue(10.0);
        Range rangeSm = new Range().setThemeSize(ThemeSize.SM);
        rangeSm.setValue(30.0);
        Range rangeMd = new Range().setThemeSize(ThemeSize.MD);
        rangeMd.setValue(50.0);
        Range rangeLg = new Range().setThemeSize(ThemeSize.LG);
        rangeLg.setValue(70.0);

        addSection("Sizes",
            rangeXs, rangeSm, rangeMd, rangeLg
        );

        // Data Binding Demo
        ValueSignal<Double> signal = new ValueSignal<>(50.0);
        Range component = new Range();
        component.bindValue(signal);
        Span output = new Span();
        output.bindText(new Computed<>(() -> "Current value: " + signal.get()));
        addSection("Data Binding Demo", component, output);
    }

    private static Div allStates() {
        Range plain = new Range();
        plain.withLabel("Brightness");
        plain.setValue(60.0);

        Range helped = new Range();
        helped.withLabel("Monthly budget");
        helped.setHelperText("Between nothing and five hundred euros.");
        helped.setValue(180.0);

        Range required = new Range();
        required.withLabel("How likely are you to recommend us?");
        required.setRequiredIndicatorVisible(true);
        required.setValue(0.0);

        Range wrong = new Range();
        wrong.withLabel("Monthly budget");
        wrong.setValue(0.0);
        wrong.setErrorMessage("Move the slider above zero.");

        Range disabled = new Range();
        disabled.withLabel("Bandwidth cap (fixed by your plan)");
        disabled.setValue(75.0);
        disabled.setEnabled(false);
        disabled.setHelperText("Disabled: Tab skips it and it is not read out.");

        Range readOnly = new Range();
        readOnly.withLabel("How full the disk is");
        readOnly.setValue(88.0);
        FieldStates.readOnly(readOnly);
        readOnly.setHelperText("Read only: Tab still reaches it, so it can still be read out.");

        Range longCaption = new Range();
        longCaption.withLabel(FieldStates.LONG_CAPTION);
        longCaption.setValue(50.0);

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
