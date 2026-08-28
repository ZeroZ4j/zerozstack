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

public class RatingShowcase extends ComponentShowcase {

    public RatingShowcase() {
        addTitle("Rating");
        addDescription("Rating component displays a set of stars to rate something.");

        addWhatToCheck("Try this",
                "Tab onto every field below, including the read-only one. The disabled one must be "
                        + "skipped and the read-only one must not.",
                "Read the caption of each field. A field with no caption has no name.",
                "The one marked wrong has to say why, under the field, in words.",
                "The required ones have to show that they are required by something other than colour.",
                "The last one has a caption of 140 characters. It should wrap, not push the page out.",
                "Broken looks like: a red border with no sentence, a required field with no mark, "
                        + "or a disabled field that Tab still stops on.");

        // Basic Rating
        Rating basicRating = new Rating();
        basicRating.withLabel("How did we do?");
        basicRating.setValue(3);
        addSection("Basic Rating", basicRating);

        addSection("Every state a rating really has", allStates());

        // Sizes
        Rating ratingXs = new Rating().setThemeSize(ThemeSize.XS);
        ratingXs.setValue(1);
        Rating ratingSm = new Rating().setThemeSize(ThemeSize.SM);
        ratingSm.setValue(2);
        Rating ratingMd = new Rating().setThemeSize(ThemeSize.MD);
        ratingMd.setValue(3);
        Rating ratingLg = new Rating().setThemeSize(ThemeSize.LG);
        ratingLg.setValue(4);

        addSection("Sizes",
            ratingXs, ratingSm, ratingMd, ratingLg
        );

        // Data Binding Demo
        ValueSignal<Integer> signal = new ValueSignal<>(3);
        Rating component = new Rating();
        component.bindValue(signal);
        Span output = new Span();
        output.bindText(new Computed<>(() -> "Current value: " + signal.get()));
        addSection("Data Binding Demo", component, output);
    }

    private static Div allStates() {
        Rating plain = new Rating();
        plain.withLabel("How did we do?");
        plain.setValue(4);

        Rating helped = new Rating();
        helped.withLabel("How did we do?");
        helped.setHelperText("One star is poor, five is excellent.");
        helped.setValue(3);

        Rating required = new Rating();
        required.withLabel("Rate this article");
        required.setRequiredIndicatorVisible(true);
        required.setValue(0);

        Rating wrong = new Rating();
        wrong.withLabel("Rate this article");
        wrong.setRequiredIndicatorVisible(true);
        wrong.setValue(0);
        wrong.setErrorMessage("Give at least one star.");

        Rating disabled = new Rating();
        disabled.withLabel("Rating (you have already voted)");
        disabled.setValue(5);
        disabled.setEnabled(false);
        disabled.setHelperText("Disabled: Tab skips it and it is not read out.");

        Rating readOnly = new Rating();
        readOnly.withLabel("What everybody else gave it");
        readOnly.setValue(4);
        FieldStates.readOnly(readOnly);
        readOnly.setHelperText("Read only: Tab still reaches it, so it can still be read out.");

        Rating longCaption = new Rating();
        longCaption.withLabel(FieldStates.LONG_CAPTION);
        longCaption.setValue(2);

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
