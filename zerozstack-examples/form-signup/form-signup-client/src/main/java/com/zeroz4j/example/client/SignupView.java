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

import com.zeroz4j.api.Disposable;
import com.zeroz4j.example.api.RegistrationService;
import com.zeroz4j.example.api.RegistrationService_Stub;
import com.zeroz4j.example.model.Registration;
import com.zeroz4j.example.model.Registration_Rules;
import com.zeroz4j.signals.Computed;
import com.zeroz4j.signals.Effect;
import com.zeroz4j.signals.ValueSignal;
import com.zeroz4j.ui.component.*;
import com.zeroz4j.ui.layout.*;

import java.util.ArrayList;
import java.util.List;

public class SignupView extends Card {

    private final RegistrationService registrationService = new RegistrationService_Stub();

    // Source signals for form fields
    private final ValueSignal<String> fullName = new ValueSignal<>("");
    private final ValueSignal<String> email = new ValueSignal<>("");
    private final ValueSignal<Integer> experienceYears = new ValueSignal<>(0);
    private final ValueSignal<String> tShirtSize = new ValueSignal<>("M");
    private final ValueSignal<Boolean> newsletter = new ValueSignal<>(false);
    private final ValueSignal<String> bio = new ValueSignal<>("");

    // Registrations table list signal
    private final ValueSignal<List<Registration>> registrations = new ValueSignal<>(new ArrayList<>());
    private final ValueSignal<String> statusMessage = new ValueSignal<>("");
    private final ValueSignal<Boolean> statusSuccess = new ValueSignal<>(true);

    private final List<Disposable> disposables = new ArrayList<>();

    public SignupView() {
        super();
        addClassName("w-full");
        addClassName("max-w-4xl");
        addClassName("mx-auto");
        addClassName("flex");
        addClassName("flex-col");

        add(new CardTitle("Developer Conference Signup"));

        // Status Alert / Message container
        Div statusDiv = new Div();
        statusDiv.addClassName("mb-4");
        add(statusDiv);

        disposables.add(Effect.create(() -> {
            String msg = statusMessage.get();
            statusDiv.getElement().setInnerHTML("");
            if (msg != null && !msg.trim().isEmpty()) {
                Alert alert = new Alert(msg);
                if (Boolean.TRUE.equals(statusSuccess.get())) {
                    alert.addClassName("alert-success");
                } else {
                    alert.addClassName("alert-error");
                }
                statusDiv.add(alert);
            }
        }));

        // Form Layout
        FormLayout formLayout = new FormLayout();
        formLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0px", 1),
                new FormLayout.ResponsiveStep("600px", 2)
        );

        // 1. Full Name
        VerticalLayout nameGroup = new VerticalLayout();
        nameGroup.addClassName("gap-1");
        Span nameLabel = new Span("Full Name *");
        nameLabel.addClassName("font-semibold");
        TextField nameField = new TextField("Enter your full name");
        nameField.bindValue(fullName);
        nameField.withRule(Registration_Rules.fullName());
        Div nameError = new Div();
        nameError.addClassName("text-error");
        nameError.addClassName("text-xs");
        nameGroup.add(nameLabel, nameField, nameError);
        disposables.add(Effect.create(() -> {
            fullName.get(); // track dependency
            List<String> violations = nameField.getViolations();
            nameError.setText(violations.isEmpty() ? "" : violations.get(0));
        }));
        formLayout.add(nameGroup);

        // 2. Email Address
        VerticalLayout emailGroup = new VerticalLayout();
        emailGroup.addClassName("gap-1");
        Span emailLabel = new Span("Email Address *");
        emailLabel.addClassName("font-semibold");
        TextField emailField = new TextField("dev@example.com");
        emailField.bindValue(email);
        emailField.withRule(Registration_Rules.email());
        Div emailError = new Div();
        emailError.addClassName("text-error");
        emailError.addClassName("text-xs");
        emailGroup.add(emailLabel, emailField, emailError);
        disposables.add(Effect.create(() -> {
            email.get(); // track dependency
            List<String> violations = emailField.getViolations();
            emailError.setText(violations.isEmpty() ? "" : violations.get(0));
        }));
        formLayout.add(emailGroup);

        // 3. Years of Java Experience
        VerticalLayout expGroup = new VerticalLayout();
        expGroup.addClassName("gap-1");
        Span expLabel = new Span("Years of Java Experience *");
        expLabel.addClassName("font-semibold");
        IntegerSelect expField = new IntegerSelect();
        expField.setItems(List.of(0, 1, 2, 3, 5, 10, 15, 20, 25, 30, 50));
        expField.bindValue(experienceYears);
        expField.withRule(Registration_Rules.experienceYears());
        Div expError = new Div();
        expError.addClassName("text-error");
        expError.addClassName("text-xs");
        expGroup.add(expLabel, expField, expError);
        disposables.add(Effect.create(() -> {
            experienceYears.get(); // track dependency
            List<String> violations = expField.getViolations();
            expError.setText(violations.isEmpty() ? "" : violations.get(0));
        }));
        formLayout.add(expGroup);

        // 4. T-Shirt Size
        VerticalLayout tShirtGroup = new VerticalLayout();
        tShirtGroup.addClassName("gap-1");
        Span tShirtLabel = new Span("T-Shirt Size *");
        tShirtLabel.addClassName("font-semibold");
        Select tShirtField = new Select();
        tShirtField.setItems(List.of("S", "M", "L", "XL"));
        tShirtField.bindValue(tShirtSize);
        tShirtField.withRule(Registration_Rules.tShirtSize());
        Div tShirtError = new Div();
        tShirtError.addClassName("text-error");
        tShirtError.addClassName("text-xs");
        tShirtGroup.add(tShirtLabel, tShirtField, tShirtError);
        disposables.add(Effect.create(() -> {
            tShirtSize.get(); // track dependency
            List<String> violations = tShirtField.getViolations();
            tShirtError.setText(violations.isEmpty() ? "" : violations.get(0));
        }));
        formLayout.add(tShirtGroup);

        // 5. Newsletter Toggle
        VerticalLayout newsGroup = new VerticalLayout();
        newsGroup.addClassName("gap-1");
        Span newsLabel = new Span("Newsletter");
        newsLabel.addClassName("font-semibold");
        HorizontalLayout newsRow = new HorizontalLayout();
        newsRow.addClassName("items-center");
        newsRow.addClassName("gap-2");
        Toggle newsToggle = new Toggle();
        newsToggle.bindValue(newsletter);
        Span newsText = new Span("Subscribe to conference updates");
        newsRow.add(newsToggle, newsText);
        newsGroup.add(newsLabel, newsRow);
        formLayout.add(newsGroup);

        // 6. Bio (TextArea - full width span 2)
        VerticalLayout bioGroup = new VerticalLayout();
        bioGroup.addClassName("gap-1");
        Span bioLabel = new Span("Short Bio (optional)");
        bioLabel.addClassName("font-semibold");
        TextArea bioField = new TextArea("Tell us a bit about your work...");
        bioField.bindValue(bio);
        bioField.withRule(Registration_Rules.bio());
        Div bioError = new Div();
        bioError.addClassName("text-error");
        bioError.addClassName("text-xs");
        bioGroup.add(bioLabel, bioField, bioError);
        disposables.add(Effect.create(() -> {
            bio.get(); // track dependency
            List<String> violations = bioField.getViolations();
            bioError.setText(violations.isEmpty() ? "" : violations.get(0));
        }));
        formLayout.setColSpan(bioGroup, 2);
        formLayout.add(bioGroup);

        add(formLayout);

        // Computed form validity combining field validation
        Computed<Boolean> formValid = new Computed<>(() -> {
            fullName.get();
            email.get();
            experienceYears.get();
            tShirtSize.get();
            newsletter.get();
            bio.get();

            return nameField.isValid()
                    && emailField.isValid()
                    && expField.isValid()
                    && tShirtField.isValid()
                    && bioField.isValid();
        });

        // Submit button row
        HorizontalLayout buttonRow = new HorizontalLayout();
        buttonRow.addClassName("mt-4");
        buttonRow.addClassName("justify-end");

        Button submitButton = new Button("Register");
        submitButton.addClassName("btn-primary");
        buttonRow.add(submitButton);
        add(buttonRow);

        // Submit button is disabled while formValid is false — driven by Effect
        disposables.add(Effect.create(() -> {
            boolean valid = Boolean.TRUE.equals(formValid.get());
            submitButton.setEnabled(valid);
        }));

        submitButton.addClickListener(e -> {
            if (!Boolean.TRUE.equals(formValid.get())) {
                return;
            }
            Registration r = new Registration(
                    System.currentTimeMillis(),
                    fullName.get(),
                    email.get(),
                    experienceYears.get() != null ? experienceYears.get() : 0,
                    tShirtSize.get(),
                    Boolean.TRUE.equals(newsletter.get()),
                    bio.get()
            );

            try {
                registrationService.register(r);
                statusMessage.set("Registration successful for " + r.getFullName() + "!");
                statusSuccess.set(true);

                // Reset form signals
                fullName.set("");
                email.set("");
                experienceYears.set(0);
                tShirtSize.set("M");
                newsletter.set(false);
                bio.set("");

                // Refetch registrations table
                loadRegistrations();
            } catch (Exception ex) {
                statusMessage.set("Registration failed: " + ex.getMessage());
                statusSuccess.set(false);
            }
        });

        // Registration Table Section
        Span tableTitle = new Span("Registered Attendees");
        tableTitle.addClassName("text-lg");
        tableTitle.addClassName("font-bold");
        tableTitle.addClassName("mt-8");
        tableTitle.addClassName("mb-2");
        add(tableTitle);

        Div tableContainer = new Div();
        tableContainer.addClassName("overflow-x-auto");
        add(tableContainer);

        disposables.add(Effect.create(() -> renderTable(tableContainer)));

        loadRegistrations();
    }

    private void renderTable(Div container) {
        container.getElement().setInnerHTML("");
        List<Registration> list = registrations.get();
        if (list == null || list.isEmpty()) {
            Span emptySpan = new Span("No registrations yet.");
            emptySpan.addClassName("text-sm");
            emptySpan.addClassName("opacity-70");
            container.add(emptySpan);
            return;
        }

        Table table = new Table();
        table.addClassName("table-zebra");
        table.addClassName("w-full");

        Component thead = new Component("thead") {};
        Component headerRow = new Component("tr") {};

        for (String col : new String[]{"#", "Name", "Email", "Experience", "T-Shirt", "Newsletter", "Bio"}) {
            Component th = new Component("th") {};
            th.getElement().setTextContent(col);
            headerRow.getElement().appendChild(th.getElement());
        }
        thead.getElement().appendChild(headerRow.getElement());
        table.getElement().appendChild(thead.getElement());

        Component tbody = new Component("tbody") {};
        int idx = 1;
        for (Registration reg : list) {
            Component tr = new Component("tr") {};

            addTd(tr, String.valueOf(idx++));
            addTd(tr, reg.getFullName());
            addTd(tr, reg.getEmail());
            addTd(tr, reg.getExperienceYears() + " yrs");
            addTd(tr, reg.getTShirtSize());
            addTd(tr, reg.isNewsletter() ? "Yes" : "No");
            addTd(tr, reg.getBio() != null ? reg.getBio() : "");

            tbody.getElement().appendChild(tr.getElement());
        }
        table.getElement().appendChild(tbody.getElement());
        container.add(table);
    }

    private void addTd(Component tr, String text) {
        Component td = new Component("td") {};
        td.getElement().setTextContent(text != null ? text : "");
        tr.getElement().appendChild(td.getElement());
    }

    private void loadRegistrations() {
        try {
            List<Registration> list = registrationService.listRegistrations();
            registrations.set(new ArrayList<>(list));
        } catch (Exception ex) {
            statusMessage.set("Failed to fetch registrations: " + ex.getMessage());
            statusSuccess.set(false);
        }
    }

    public void dispose() {
        for (Disposable disposable : disposables) {
            disposable.dispose();
        }
        disposables.clear();
    }
}
