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
import com.zeroz4j.ui.layout.FormLayout;
import com.zeroz4j.ui.layout.*;
import com.zeroz4j.ui.theme.*;
import com.zeroz4j.signals.*;

public class TextFieldShowcase extends ComponentShowcase {

    public TextFieldShowcase() {
        addTitle("TextField");
        addDescription("TextField is a standard text input component.");

        addWhatToCheck("Try this",
                "Tab onto every field, including the read-only one. The disabled one must be "
                        + "skipped and the read-only one must not.",
                "Click the words of a caption. The keyboard should land in the field they name.",
                "The one marked wrong has to say why, under the field, in words.",
                "The last one has a caption of 140 characters. It should wrap, not push the page out.",
                "Type in the field that only has a placeholder and watch its only name disappear.",
                "Broken looks like: a red border with no sentence, a required field with no mark, "
                        + "or a disabled field that Tab still stops on.");

        // Basic TextField
        TextField basicTextField = new TextField("Enter text...");
        addSection("Basic TextField", basicTextField);

        // Caption vs placeholder
        TextField placeholderOnly = new TextField("Primary folder path");
        TextField captioned = new TextField().withLabel("Primary folder path");
        TextField both = new TextField("/home/me/projects").withLabel("Primary folder path");
        Div captionRow = new Div();
        captionRow.addClassName("w-full grid gap-4 md:grid-cols-3");
        captionRow.add(placeholderOnly, captioned, both);
        addSection("A caption is not a placeholder - type in the first one and it stops saying "
            + "what it is. Click the words on the second one and it takes the focus.", captionRow);

        // A whole form: captions, required marks, explanations and messages
        TextField name = new TextField().withLabel("Your name");
        name.setRequiredIndicatorVisible(true);
        TextField email = new TextField("you@example.com").withLabel("Email address")
            .withHelperText("We only use this to send the receipt.");
        email.setRequiredIndicatorVisible(true);
        TextField folder = new TextField().withLabel("Primary folder path")
            .withHelperText("An absolute path. It is created if it does not exist yet.");
        TextField rejected = new TextField().withLabel("Port number");
        rejected.setValue("http://8080");
        rejected.setErrorMessage("A port is a number between 1 and 65535.");
        TextArea notes = new TextArea("Anything else we should know?").withLabel("Notes");
        Checkbox terms = new Checkbox();
        terms.withLabel("Send me the occasional release note");
        Select region = new Select();
        region.setItems(java.util.List.of("Europe", "North America", "Asia"));
        region.withLabel("Region").withHelperText("Where your data is stored.");

        FormLayout form = new FormLayout();
        form.add(name, email, folder, rejected, region, notes, terms);
        form.setColSpan(notes, 2);
        form.setColSpan(terms, 2);
        Div formHost = new Div();
        formHost.addClassName("w-full");
        formHost.add(form);
        addSection("A form: captions, required marks, explanations and a message", formHost);

        addSection("Every state a text field really has", allStates());

        // Colors
        TextField tfPrimary = new TextField("Primary").setThemeColor(ThemeColor.PRIMARY);
        TextField tfSecondary = new TextField("Secondary").setThemeColor(ThemeColor.SECONDARY);
        TextField tfAccent = new TextField("Accent").setThemeColor(ThemeColor.ACCENT);
        TextField tfNeutral = new TextField("Neutral").setThemeColor(ThemeColor.NEUTRAL);
        TextField tfInfo = new TextField("Info").setThemeColor(ThemeColor.INFO);
        TextField tfSuccess = new TextField("Success").setThemeColor(ThemeColor.SUCCESS);
        TextField tfWarning = new TextField("Warning").setThemeColor(ThemeColor.WARNING);
        TextField tfError = new TextField("Error").setThemeColor(ThemeColor.ERROR);

        addSection("Colors",
            tfPrimary, tfSecondary, tfAccent, tfNeutral,
            tfInfo, tfSuccess, tfWarning, tfError
        );

        // Sizes
        TextField tfXs = new TextField("Extra Small").setThemeSize(ThemeSize.XS);
        TextField tfSm = new TextField("Small").setThemeSize(ThemeSize.SM);
        TextField tfMd = new TextField("Medium").setThemeSize(ThemeSize.MD);
        TextField tfLg = new TextField("Large").setThemeSize(ThemeSize.LG);

        addSection("Sizes",
            tfXs, tfSm, tfMd, tfLg
        );

        // A caption can be given to a field that is already on the page: the control moves into
        // its group where it stands, keeping its place among its siblings.
        TextField late = new TextField("Type here");
        Button giveCaption = new Button("Give it a caption");
        giveCaption.addClassName("btn-primary");
        giveCaption.addClickListener(e -> late.setLabel("Added after the field was on the page"));
        Div lateHost = new Div();
        lateHost.addClassName("w-full flex items-end gap-4");
        lateHost.add(late, giveCaption);
        addSection("A caption can arrive later", lateHost);

        // Data Binding Demo
        ValueSignal<String> signal = new ValueSignal<>("Hello");
        TextField component = new TextField();
        component.bindValue(signal);
        Span output = new Span();
        output.bindText(new Computed<>(() -> "Current value: " + signal.get()));
        addSection("Data Binding Demo", component, output);
    }

    /** The seven states, so none of them is met for the first time inside an application. */
    private static Div allStates() {
        TextField plain = new TextField().withLabel("Town");

        TextField helped = new TextField().withLabel("Post code")
            .withHelperText("Five digits, no spaces.");

        TextField required = new TextField().withLabel("Street and number");
        required.setRequiredIndicatorVisible(true);

        TextField wrong = new TextField().withLabel("Post code");
        wrong.setValue("ABC");
        wrong.setRequiredIndicatorVisible(true);
        wrong.setErrorMessage("A post code is five digits, like 10827.");

        TextField disabled = new TextField().withLabel("Customer number (we set this)");
        disabled.setValue("KD-4711-2026");
        disabled.setEnabled(false);
        disabled.setHelperText("Disabled: Tab skips it and it is not read out.");

        TextField readOnly = FieldStates.readOnly(new TextField().withLabel("Your account number"));
        readOnly.setValue("DE89 3704 0044 0532 0130 00");
        readOnly.setHelperText("Read only: Tab still reaches it, so it can still be read out.");

        TextField longCaption = new TextField().withLabel(FieldStates.LONG_CAPTION);
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
