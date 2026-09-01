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

import com.zeroz4j.ui.binding.Binder;
import com.zeroz4j.ui.binding.BinderValidationStatus;
import com.zeroz4j.ui.binding.ValidationResult;
import com.zeroz4j.ui.component.Alert;
import com.zeroz4j.ui.component.Button;
import com.zeroz4j.ui.component.Checkbox;
import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.component.Loading;
import com.zeroz4j.ui.component.RadioButtonGroup;
import com.zeroz4j.ui.component.Range;
import com.zeroz4j.ui.component.Rating;
import com.zeroz4j.ui.component.Select;
import com.zeroz4j.ui.component.TextArea;
import com.zeroz4j.ui.component.TextField;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.FormLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.teavm.jso.browser.Window;

/**
 * A form filled in wrongly on purpose, so the page shows every error at once, then one correction
 * at a time, then what the form looks like while it is saving and when the server says no.
 */
public class HardFormShowcase extends ComponentShowcase {

    /** What the form fills in. */
    private static final class Registration {
        private String fullName;
        private String email;
        private String phone;
        private String country;
        private String plan;
        private String company;
        private String password;
        private String repeatPassword;
        private String notes;
        private Double budget;
        private Integer satisfaction;
        private Boolean acceptedTerms;

        String getFullName() { return fullName; }
        void setFullName(String v) { fullName = v; }
        String getEmail() { return email; }
        void setEmail(String v) { email = v; }
        String getPhone() { return phone; }
        void setPhone(String v) { phone = v; }
        String getCountry() { return country; }
        void setCountry(String v) { country = v; }
        String getPlan() { return plan; }
        void setPlan(String v) { plan = v; }
        String getCompany() { return company; }
        void setCompany(String v) { company = v; }
        String getPassword() { return password; }
        void setPassword(String v) { password = v; }
        String getRepeatPassword() { return repeatPassword; }
        void setRepeatPassword(String v) { repeatPassword = v; }
        String getNotes() { return notes; }
        void setNotes(String v) { notes = v; }
        Double getBudget() { return budget; }
        void setBudget(Double v) { budget = v; }
        Integer getSatisfaction() { return satisfaction; }
        void setSatisfaction(Integer v) { satisfaction = v; }
        Boolean getAcceptedTerms() { return acceptedTerms; }
        void setAcceptedTerms(Boolean v) { acceptedTerms = v; }
    }

    private final TextField fullName = new TextField();
    private final TextField email = new TextField();
    private final TextField phone = new TextField();
    private final Select country = new Select();
    private final RadioButtonGroup plan = new RadioButtonGroup("hard-form-plan");
    private final TextField company = new TextField();
    private final TextField password = new TextField();
    private final TextField repeatPassword = new TextField();
    private final TextArea notes = new TextArea();
    private final Range budget = new Range();
    private final Rating satisfaction = new Rating();
    private final Checkbox acceptedTerms = new Checkbox();

    private final Binder<Registration> binder = new Binder<>();
    private final Registration bean = new Registration();

    /** The banner over the form: the summary of what is wrong, or what happened when it saved. */
    private final Div banner = new Div();

    private final Button save = new Button("Create the account");
    private final Loading savingSpinner = new Loading()
            .withAriaLabel("Creating the account, please wait");
    private final Div savingRow = new Div();

    /** The corrections, applied one at a time by the "Fix the next problem" button. */
    private final List<Runnable> fixes = new ArrayList<>();
    private int nextFix;

    /** The server refuses the first save and accepts the second, so both states can be seen. */
    private boolean serverHasRefusedOnce;

    public HardFormShowcase() {
        super();
        addTitle("A form that fails, then recovers");
        addDescription("Twelve fields, filled in wrongly to begin with. Press save and every "
                + "problem appears at once. Then fix them one at a time and watch the messages go.");

        addWhatToCheck("Try this",
                "Press save straight away. Every field that is wrong should say so, in words.",
                "Check the summary at the top says how many are wrong and can be reached by keyboard.",
                "The repeat-password field is eight characters long, so it is fine on its own — it "
                        + "is only wrong because it does not match the password above it.",
                "Choose the business plan and the company field becomes required. Nothing else changes.",
                "Press \"Fix the next problem\" over and over and watch the messages disappear one by one.",
                "When it saves, the first attempt is refused by the server. Read what it says and "
                        + "press save again.",
                "Broken looks like: only the first error shown, a message that stays after the "
                        + "value is corrected, a save button that stays pressable while saving, or "
                        + "an error you cannot reach with the keyboard.");

        buildFields();
        bindFields();
        fillInWronglyOnPurpose();

        banner.setId("hard-form-banner");
        banner.addClassName("w-full");

        addSection("The banner", banner);
        addSection("The form", form());
    }

    // ------------------------------------------------------------------ the fields

    private void buildFields() {
        fullName.setLabel("Full name");
        fullName.setId("hard-form-fullname");
        fullName.setHelperText("As it appears on your invoice.");

        email.setLabel("Email address");
        email.setId("hard-form-email");
        email.setHelperText("We send the receipt here.");

        phone.setLabel("Telephone");
        phone.setId("hard-form-phone");
        phone.setHelperText("Digits, spaces and a leading plus. At least seven digits.");

        country.setLabel("Country");
        country.setId("hard-form-country");
        country.setItems(Arrays.asList("", "Germany", "Austria", "Switzerland", "Japan",
                "United Arab Emirates"));
        country.setHelperText("Where the invoice is sent.");

        plan.setLabel("Plan");
        plan.setId("hard-form-plan-group");
        plan.setItems(Arrays.asList("Personal", "Business"));
        plan.setHelperText("Business needs a company name.");

        company.setLabel("Company");
        company.setId("hard-form-company");
        company.setHelperText("Only needed on the business plan.");

        password.setLabel("Password");
        password.setId("hard-form-password");
        password.getElement().setAttribute("type", "password");
        password.setHelperText("At least eight characters.");

        repeatPassword.setLabel("Repeat the password");
        repeatPassword.setId("hard-form-repeat");
        repeatPassword.getElement().setAttribute("type", "password");
        repeatPassword.setHelperText("It has to be the same as the one above. This is the field "
                + "that is fine on its own and wrong in combination.");

        notes.setLabel("Anything we should know");
        notes.setId("hard-form-notes");
        notes.setHelperText("Up to 200 characters.");

        budget.setLabel("Monthly budget, in euros");
        budget.setId("hard-form-budget");
        budget.getElement().setAttribute("min", "0");
        budget.getElement().setAttribute("max", "500");
        budget.setHelperText("Anything above zero.");

        satisfaction.setLabel("How well did the sign-up go?");
        satisfaction.setId("hard-form-satisfaction");
        satisfaction.setHelperText("At least one star.");

        acceptedTerms.setLabel("I accept the terms");
        acceptedTerms.setId("hard-form-terms");
        acceptedTerms.setHelperText("The account cannot be created without this.");
    }

    private void bindFields() {
        binder.forField(fullName)
                .asRequired("Type your full name.")
                .withValidator(v -> v != null && v.trim().length() >= 2,
                        "A name needs at least two letters.")
                .bind(Registration::getFullName, Registration::setFullName);

        binder.forField(email)
                .asRequired("Type your email address.")
                .withValidator(v -> v != null && v.contains("@") && v.indexOf('.', v.indexOf('@')) > 0,
                        "An email address needs an @ and a dot after it, like anna@example.com.")
                .bind(Registration::getEmail, Registration::setEmail);

        binder.forField(phone)
                .asRequired("Type a telephone number.")
                .withValidator(HardFormShowcase::looksLikeAPhoneNumber,
                        "Use digits, spaces and a leading plus only, and at least seven digits.")
                .bind(Registration::getPhone, Registration::setPhone);

        binder.forField(country)
                .asRequired("Choose a country.")
                .bind(Registration::getCountry, Registration::setCountry);

        binder.forField(plan)
                .asRequired("Choose a plan.")
                .bind(Registration::getPlan, Registration::setPlan);

        // Wrong only in combination with the plan above it.
        binder.forField(company)
                .withValidator(v -> !"Business".equals(plan.getValue())
                                || (v != null && !v.trim().isEmpty()),
                        "The business plan needs a company name.")
                .bind(Registration::getCompany, Registration::setCompany);

        binder.forField(password)
                .asRequired("Choose a password.")
                .withValidator(v -> v != null && v.length() >= 8,
                        "A password needs at least eight characters.")
                .bind(Registration::getPassword, Registration::setPassword);

        // Long enough on its own; wrong because of the field above it.
        binder.forField(repeatPassword)
                .asRequired("Type the password a second time.")
                .withValidator(v -> v != null && v.length() >= 8,
                        "A password needs at least eight characters.")
                .withValidator(v -> v != null && v.equals(password.getValue()),
                        "The two passwords are not the same.")
                .bind(Registration::getRepeatPassword, Registration::setRepeatPassword);

        binder.forField(notes)
                .withValidator(v -> v == null || v.length() <= 200,
                        "That is longer than 200 characters. Shorten it.")
                .bind(Registration::getNotes, Registration::setNotes);

        binder.forField(budget)
                .withValidator(v -> v != null && v > 0,
                        "Move the slider above zero.")
                .bind(Registration::getBudget, Registration::setBudget);

        binder.forField(satisfaction)
                .withValidator(v -> v != null && v >= 1, "Give at least one star.")
                .bind(Registration::getSatisfaction, Registration::setSatisfaction);

        binder.forField(acceptedTerms)
                .withValidator(v -> v != null && v, "You have to accept the terms.")
                .bind(Registration::getAcceptedTerms, Registration::setAcceptedTerms);

        binder.setBean(bean);
    }

    /** Every field wrong, in a different way, so one press of save lights the whole form up. */
    private void fillInWronglyOnPurpose() {
        fullName.setValue("A");
        email.setValue("anna at example");
        phone.setValue("call me");
        country.setValue("");
        plan.setValue("Business");
        company.setValue("");
        password.setValue("short");
        repeatPassword.setValue("password");
        notes.setValue(repeat("This note is far too long. ", 12));
        budget.setValue(0.0);
        satisfaction.setValue(0);
        acceptedTerms.setValue(false);

        fixes.add(() -> fullName.setValue("Anna Bergström"));
        fixes.add(() -> email.setValue("anna@example.com"));
        fixes.add(() -> phone.setValue("+49 30 1234567"));
        fixes.add(() -> country.setValue("Germany"));
        fixes.add(() -> company.setValue("Bergström Handel GmbH"));
        fixes.add(() -> password.setValue("correct-horse"));
        fixes.add(() -> repeatPassword.setValue("correct-horse"));
        fixes.add(() -> notes.setValue("Please invoice quarterly."));
        fixes.add(() -> budget.setValue(180.0));
        fixes.add(() -> satisfaction.setValue(4));
        fixes.add(() -> acceptedTerms.setValue(true));
    }

    // ------------------------------------------------------------------ the layout

    private Component[] form() {
        FormLayout layout = new FormLayout();
        layout.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("40rem", 2));
        layout.add(fullName, email, phone, country, plan, company, password, repeatPassword,
                budget, satisfaction, acceptedTerms);
        layout.add(notes);
        layout.setColSpan(notes, 2);

        save.setId("hard-form-save");
        save.addClassName("btn-primary");
        save.addClickListener(e -> attemptSave());

        Button fixOne = new Button("Fix the next problem", e -> applyNextFix());
        fixOne.setId("hard-form-fix-one");

        Button fixAll = new Button("Fix everything", e -> {
            while (nextFix < fixes.size()) {
                applyNextFix();
            }
        });
        fixAll.setId("hard-form-fix-all");

        Button breakAgain = new Button("Break it again", e -> {
            nextFix = 0;
            serverHasRefusedOnce = false;
            fillInWronglyOnPurpose();
            banner.removeAll();
            save.setText("Create the account");
        });
        breakAgain.setId("hard-form-break");

        savingRow.addClassName("hidden items-center gap-2 text-sm text-base-content/70");
        savingRow.add(savingSpinner, new Div("Creating the account. Nothing can be changed until "
                + "the server answers."));

        Div buttons = new Div();
        buttons.addClassName("flex flex-wrap items-center gap-3 mt-4 w-full");
        buttons.add(save, fixOne, fixAll, breakAgain);

        Div host = new Div();
        host.addClassName("w-full");
        host.add(layout, buttons, savingRow);
        return new Component[] { host };
    }

    // ------------------------------------------------------------------ behaviour

    private void applyNextFix() {
        if (nextFix >= fixes.size()) {
            return;
        }
        fixes.get(nextFix).run();
        nextFix++;
        showRemaining();
    }

    private void showRemaining() {
        BinderValidationStatus<Registration> status = binder.validate();
        banner.removeAll();
        if (status.isOk()) {
            banner.add(Alert.success("Everything is filled in. Press save.")
                    .withHeading("Ready to save"));
        } else {
            banner.add(problemSummary(status.getValidationErrors()));
        }
    }

    private void attemptSave() {
        BinderValidationStatus<Registration> status = binder.validate();
        banner.removeAll();
        if (!status.isOk()) {
            banner.add(problemSummary(status.getValidationErrors()));
            return;
        }

        setSaving(true);
        Window.setTimeout(() -> {
            setSaving(false);
            if (!serverHasRefusedOnce) {
                // A refusal the browser could not have known about: only the server holds the
                // list of addresses already registered.
                serverHasRefusedOnce = true;
                email.setErrorMessage("This address already has an account.");
                banner.removeAll();
                banner.add(Alert.danger("An account already exists for anna@example.com. Sign in "
                                + "instead, or use a different address.")
                        .withHeading("The server would not create the account")
                        .withAction("Use anna+new@example.com", e -> {
                            email.setValue("anna+new@example.com");
                            email.setErrorMessage(null);
                            banner.removeAll();
                        }));
                return;
            }
            binder.writeBeanIfValid(bean);
            banner.removeAll();
            banner.add(Alert.success("The account for " + bean.getFullName() + " was created.")
                    .withHeading("Saved"));
            save.setText("Saved");
        }, 1600);
    }

    /** While saving, nothing is changeable and the button does not look pressable. */
    private void setSaving(boolean saving) {
        save.setEnabled(!saving);
        save.setText(saving ? "Creating the account…" : "Create the account");
        if (saving) {
            savingRow.removeClassName("hidden");
            savingRow.addClassName("flex");
        } else {
            savingRow.removeClassName("flex");
            savingRow.addClassName("hidden");
        }
        for (Component field : new Component[] { fullName, email, phone, country, plan, company,
                password, repeatPassword, notes, budget, satisfaction, acceptedTerms }) {
            if (saving) {
                field.getElement().setAttribute("aria-busy", "true");
            } else {
                field.getElement().removeAttribute("aria-busy");
            }
        }
        fullName.setEnabled(!saving);
        email.setEnabled(!saving);
        phone.setEnabled(!saving);
        country.setEnabled(!saving);
        company.setEnabled(!saving);
        password.setEnabled(!saving);
        repeatPassword.setEnabled(!saving);
        notes.setEnabled(!saving);
        budget.setEnabled(!saving);
        acceptedTerms.setEnabled(!saving);
    }

    private static Alert problemSummary(List<ValidationResult> errors) {
        StringBuilder sentence = new StringBuilder();
        for (ValidationResult error : errors) {
            sentence.append(error.getErrorMessage()).append(' ');
        }
        Alert alert = Alert.caution(sentence.toString().trim())
                .withHeading(errors.size() == 1
                        ? "One field still needs fixing"
                        : errors.size() + " fields still need fixing");
        alert.setId("hard-form-problems");
        return alert;
    }

    private static boolean looksLikeAPhoneNumber(String value) {
        if (value == null) {
            return false;
        }
        int digits = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '0' && c <= '9') {
                digits++;
            } else if (c != ' ' && !(c == '+' && i == 0)) {
                return false;
            }
        }
        return digits >= 7;
    }

    private static String repeat(String text, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) {
            sb.append(text);
        }
        return sb.toString();
    }
}
