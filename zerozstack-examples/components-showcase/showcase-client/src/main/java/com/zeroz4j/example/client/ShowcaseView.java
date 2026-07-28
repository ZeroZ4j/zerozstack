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
package com.zeroz4j.example.client;

import com.zeroz4j.ui.component.*;
import com.zeroz4j.ui.layout.*;
import com.zeroz4j.signals.ValueSignal;
import com.zeroz4j.signals.Computed;
import java.util.Arrays;

public class ShowcaseView extends VerticalLayout {

    public ShowcaseView() {
        super();
        addClassName("p-8");
        addClassName("gap-8");

        // 1. Text & Basic State
        ValueSignal<String> nameSignal = new ValueSignal<>("World");
        
        Card introCard = new Card();
        introCard.add(new CardTitle("Signals & Data Binding"));
        
        TextField nameField = new TextField("Enter your name");
        nameField.bindValue(nameSignal);
        
        Span greeting = new Span();
        greeting.addClassName("text-lg");
        greeting.bindText(new Computed<>(() -> "Hello, " + nameSignal.get() + "!"));
        
        introCard.add(nameField, greeting);
        add(introCard);
        
        // 2. Buttons & Actions
        Card actionCard = new Card();
        actionCard.add(new CardTitle("Actions"));
        
        ValueSignal<Integer> countSignal = new ValueSignal<>(0);
        Button clickMeButton = new Button("Click Me");
        clickMeButton.addClickListener(e -> countSignal.update(c -> c + 1));
        
        Badge countBadge = new Badge();
        countBadge.bindText(new Computed<>(() -> "Clicks: " + countSignal.get()));
        
        actionCard.add(new HorizontalLayout(clickMeButton, countBadge));
        add(actionCard);
        
        // 3. Form Inputs
        Card formCard = new Card();
        formCard.add(new CardTitle("Form Inputs"));
        
        TextArea textArea = new TextArea("Some long text...");
        Toggle toggle = new Toggle();
        Checkbox checkbox = new Checkbox();
        
        Select select = new Select();
        select.setItems(Arrays.asList("Option 1", "Option 2", "Option 3"));
        
        RadioButtonGroup radioGroup = new RadioButtonGroup("options");
        radioGroup.setItems(Arrays.asList("A", "B", "C"));
        
        formCard.add(textArea, new HorizontalLayout(new Span("Toggle: "), toggle), new HorizontalLayout(new Span("Checkbox: "), checkbox), select, radioGroup);
        add(formCard);
        
        // 4. Modals and Overlays
        Card overlayCard = new Card();
        overlayCard.add(new CardTitle("Overlays"));
        
        Dialog dialog = new Dialog();
        dialog.add(new CardTitle("Dialog Content"), new Span("This is a modal dialog."));
        Button closeButton = new Button("Close");
        closeButton.addClickListener(e -> dialog.close());
        dialog.addAction(closeButton);
        
        Button openDialogButton = new Button("Open Dialog");
        openDialogButton.addClickListener(e -> dialog.open());
        
        overlayCard.add(openDialogButton, dialog);
        add(overlayCard);
        
        // 5. Alerts
        Alert successAlert = new Alert("Operation successful!", "alert-success");
        Alert errorAlert = new Alert("Operation failed!", "alert-error");
        add(successAlert, errorAlert);
    }
}
