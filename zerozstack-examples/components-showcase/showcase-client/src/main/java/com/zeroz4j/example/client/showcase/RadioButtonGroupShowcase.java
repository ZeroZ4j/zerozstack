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
import com.zeroz4j.ui.component.RadioButtonGroup;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.Span;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * One choice out of several. A group is a {@code div} holding several controls, so its caption
 * names the whole group rather than any one button — which is the part that is easy to get wrong.
 */
public class RadioButtonGroupShowcase extends ComponentShowcase {

    public RadioButtonGroupShowcase() {
        super();
        addTitle("RadioButtonGroup");
        addDescription("A set of round buttons where exactly one can be chosen. Use it when there "
                + "are few enough choices to show them all at once.");

        addWhatToCheck("Try this",
                "Tab into a group. It should take one stop, not one stop per button.",
                "Once you are inside, the arrow keys should move between the choices.",
                "Read the caption. It has to name the whole group, not sit beside one button.",
                "The group marked wrong has to say why, once, under the whole group.",
                "The disabled group must be skipped by Tab altogether.",
                "The long group has fifteen choices with long names. It should stack, not overflow.",
                "Broken looks like: Tab stopping on every button in turn, a group with no caption, "
                        + "or an error message repeated beside each choice.");

        addSection("Three choices", threeChoices());
        addSection("Every state a group really has", allStates());
        addSection("Fifteen choices with long names", manyChoices());
        addSection("Bound to a signal", bound());
    }

    // ------------------------------------------------------------------ sections

    private static Div threeChoices() {
        RadioButtonGroup group = new RadioButtonGroup("simple-options");
        group.withLabel("How would you like to be contacted?");
        group.setItems(Arrays.asList("By email", "By telephone", "By post"));
        group.setValue("By email");
        return wrap(group);
    }

    private static Div allStates() {
        RadioButtonGroup plain = new RadioButtonGroup("states-plain");
        plain.withLabel("Delivery");
        plain.setItems(Arrays.asList("Standard", "Express"));

        RadioButtonGroup helped = new RadioButtonGroup("states-helped");
        helped.withLabel("Delivery");
        helped.setHelperText("Express arrives the next working day and costs nine euros more.");
        helped.setItems(Arrays.asList("Standard", "Express"));

        RadioButtonGroup required = new RadioButtonGroup("states-required");
        required.withLabel("Plan");
        required.setRequiredIndicatorVisible(true);
        required.setItems(Arrays.asList("Personal", "Business"));

        RadioButtonGroup wrong = new RadioButtonGroup("states-wrong");
        wrong.withLabel("Plan");
        wrong.setRequiredIndicatorVisible(true);
        wrong.setItems(Arrays.asList("Personal", "Business"));
        wrong.setErrorMessage("Choose a plan before carrying on.");

        RadioButtonGroup disabled = new RadioButtonGroup("states-disabled");
        disabled.withLabel("Region (fixed for your account)");
        disabled.setItems(Arrays.asList("Europe", "North America"));
        disabled.setValue("Europe");
        disabled.setEnabled(false);
        disabled.setHelperText("Disabled: Tab skips it and it is not read out.");

        RadioButtonGroup readOnly = new RadioButtonGroup("states-readonly");
        readOnly.withLabel("What you chose last time");
        readOnly.setItems(Arrays.asList("Standard", "Express"));
        readOnly.setValue("Express");
        FieldStates.readOnly(readOnly);
        readOnly.setHelperText("Read only: Tab still reaches it, so it can still be read out.");

        RadioButtonGroup longCaption = new RadioButtonGroup("states-long");
        longCaption.withLabel(FieldStates.LONG_CAPTION);
        longCaption.setItems(Arrays.asList("Yes", "No"));

        return FieldStates.stack(
                FieldStates.labelled("Caption only", plain),
                FieldStates.labelled("Caption and helper text", helped),
                FieldStates.labelled("Required", required),
                FieldStates.labelled("Wrong, and saying why", wrong),
                FieldStates.labelled("Disabled", disabled),
                FieldStates.labelled("Read only", readOnly),
                FieldStates.labelled("A very long caption", longCaption));
    }

    private static Div manyChoices() {
        List<String> reasons = new ArrayList<>(Arrays.asList(
                "The parcel never arrived",
                "The parcel arrived damaged",
                "The wrong thing was sent",
                "It does not fit",
                "It is not what the photograph showed",
                "It stopped working within the first week",
                "I changed my mind",
                "I was charged twice",
                "Es wurde eine falsche Rechnungsadresse verwendet",
                "商品が説明と異なっていました",
                "The delivery was later than promised",
                "The packaging could not be recycled",
                "I ordered it by mistake",
                "Somebody else in the household already ordered one",
                "Something else — I will explain below"));
        RadioButtonGroup group = new RadioButtonGroup("many-reasons");
        group.withLabel("Why are you sending it back?");
        group.setItems(reasons);
        group.setHelperText("Fifteen choices, some of them long, some in other languages.");
        return wrap(group);
    }

    private static Div bound() {
        ValueSignal<String> signal = new ValueSignal<>("Banana");
        RadioButtonGroup group = new RadioButtonGroup("demo-fruits");
        group.withLabel("Pick one");
        group.setItems(Arrays.asList("Apple", "Banana", "Orange"));
        group.bindValue(signal);

        Span output = new Span();
        output.addClassName("text-sm");
        output.bindText(new Computed<>(() -> "Currently chosen: " + signal.get()));

        Div host = new Div();
        host.addClassName("flex flex-col gap-2 w-full");
        host.add(group, output);
        return host;
    }

    private static Div wrap(RadioButtonGroup group) {
        Div host = new Div();
        host.addClassName("w-full max-w-md");
        host.add(group);
        return host;
    }
}
