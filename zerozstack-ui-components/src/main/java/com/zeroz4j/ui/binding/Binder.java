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
package com.zeroz4j.ui.binding;

import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.component.HasValue;

import java.util.ArrayList;
import java.util.List;
import com.zeroz4j.ui.component.HasStyle;

/**
 * Type-safe data binding framework binding domain model objects (beans) to zeroz4j UI components implementing {@link HasValue}.
 *
 * <p>Supports validation, custom converters, required-field checks, automatic write-through on change, and visual error indicators.</p>
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>Data Flow:</b> {@link #readBean(Object)} copies properties from domain bean into UI fields. {@link #writeBean(Object)} validates fields and writes UI values back into domain bean.</li>
 *   <li><b>Live Model Updates:</b> Value change listeners registered in {@code bind()} auto-write back to the active bean if validation passes.</li>
 *   <li><b>Styling Integration:</b> Components implementing {@link HasStyle} receive the CSS class {@code "input-error"} when validation fails.</li>
 * </ul>
 *
 * @param <BEAN> the domain model class type being bound
 */
public class Binder<BEAN> {
    
    private BEAN bean;
    private BEAN lastReadBean;
    private final List<Binding<BEAN, ?>> bindings = new ArrayList<>();

    /**
     * Default constructor creating an unbound {@link Binder} instance.
     */
    public Binder() {
    }

    /**
     * Configures a binding builder for a target UI component implementing {@link HasValue}.
     *
     * @param <FIELDVALUE> the value type of the UI field
     * @param field        the UI component instance
     * @return a new {@link BindingBuilder} for configuring validators and property bindings
     */
    public <FIELDVALUE> BindingBuilder<BEAN, FIELDVALUE> forField(HasValue<FIELDVALUE> field) {
        return new BindingBuilderImpl<>(field);
    }

    /**
     * Binds a target domain bean to this binder and reads its property values into all bound UI fields.
     *
     * @param bean the domain model bean instance to bind
     *
     * <p><b>Under the hood:</b> Sets internal {@code bean} reference and executes {@link #readBean(Object)}.</p>
     */
    public void setBean(BEAN bean) {
        this.bean = bean;
        for (Binding<BEAN, ?> binding : bindings) {
            binding.read(bean);
        }
    }

    /**
     * Retrieves the currently bound domain model bean instance.
     *
     * @return bound bean instance, or {@code null} if unbound
     */
    public BEAN getBean() {
        return bean;
    }

    /**
     * Populates the bound UI fields from the given bean in <b>buffered</b> mode: the binder keeps no
     * reference to it, so subsequent edits stay in the fields until {@link #writeBean(Object)} or
     * {@link #writeBeanIfValid(Object)} is called explicitly.
     *
     * <p>This is the counterpart to {@link #setBean(Object)}, which is write-through. Calling
     * {@code readBean} always switches the binder into buffered mode, releasing any bean previously
     * passed to {@code setBean} — otherwise later edits would keep writing into that old instance.</p>
     *
     * @param bean domain bean to read from, or {@code null} to clear the fields
     */
    public void readBean(BEAN bean) {
        this.bean = null;                       // buffered mode: nothing is written through
        for (Binding<BEAN, ?> binding : bindings) {
            binding.read(bean);
        }
        this.lastReadBean = bean;
    }

    /**
     * Re-reads the fields from whatever was last loaded, discarding uncommitted edits.
     *
     * <p>In write-through mode ({@link #setBean(Object)}) this reloads from the bound bean. In buffered
     * mode it reloads from the bean last passed to {@link #readBean(Object)}, which is how a Cancel
     * button reverts a form. With nothing loaded, the fields are cleared.</p>
     */
    public void refreshFields() {
        BEAN source = bean != null ? bean : lastReadBean;
        for (Binding<BEAN, ?> binding : bindings) {
            binding.read(source);
        }
    }

    /**
     * Reports whether any bound field currently differs from the value last read into it.
     *
     * <p>Useful for enabling a Save button, or warning before discarding a buffered edit. In
     * write-through mode this is normally {@code false}, because edits are written to the bean as they
     * happen.</p>
     *
     * @return true when at least one field holds an uncommitted change
     */
    public boolean hasChanges() {
        for (Binding<BEAN, ?> binding : bindings) {
            if (binding instanceof BindingImpl && ((BindingImpl<?>) binding).isModified()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Validates all bound UI fields and writes their values to the target domain bean if all validations pass.
     *
     * @param bean domain bean instance to receive field values
     * @throws ValidationException if one or more field validations fail
     *
     * <p><b>Under the hood:</b> Evaluates {@code binding.validate()} for all bindings. Collects errors into a list.
     * Throws {@link ValidationException} if non-empty; otherwise executes {@code binding.write(bean)} for all bindings.</p>
     */
    public void writeBean(BEAN bean) throws ValidationException {
        List<ValidationResult> errors = new ArrayList<>();
        for (Binding<BEAN, ?> binding : bindings) {
            ValidationResult result = binding.validate();
            if (result.isError()) {
                errors.add(result);
            }
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        for (Binding<BEAN, ?> binding : bindings) {
            binding.write(bean);
        }
        // The buffer is now committed, so the fields are no longer "modified".
        this.lastReadBean = bean;
        for (Binding<BEAN, ?> binding : bindings) {
            if (binding instanceof BindingImpl) {
                ((BindingImpl<?>) binding).markCommitted();
            }
        }
    }

    /**
     * Writes UI field values to the target domain bean if valid, returning a boolean status instead of throwing an exception.
     *
     * @param bean domain bean instance to receive field values
     * @return true if write succeeded; false if validation failed
     */
    public boolean writeBeanIfValid(BEAN bean) {
        try {
            writeBean(bean);
            return true;
        } catch (ValidationException e) {
            return false;
        }
    }

    /**
     * Validates all bound UI fields and returns a {@link BinderValidationStatus} report.
     *
     * @return validation status report containing any validation errors
     */
    public BinderValidationStatus<BEAN> validate() {
        List<ValidationResult> errors = new ArrayList<>();
        for (Binding<BEAN, ?> binding : bindings) {
            ValidationResult result = binding.validate();
            if (result.isError()) {
                errors.add(result);
            }
        }
        return new BinderValidationStatus<>(errors);
    }

    private class BindingBuilderImpl<FIELDVALUE> implements BindingBuilder<BEAN, FIELDVALUE> {
        private final HasValue<FIELDVALUE> field;
        private final List<Validator<? super FIELDVALUE>> validators = new ArrayList<>();
        private boolean isRequired = false;
        private String requiredMessage = "This field is required";

        public BindingBuilderImpl(HasValue<FIELDVALUE> field) {
            this.field = field;
        }

        @Override
        public BindingBuilder<BEAN, FIELDVALUE> withValidator(Validator<? super FIELDVALUE> validator) {
            validators.add(validator);
            return this;
        }

        @Override
        public BindingBuilder<BEAN, FIELDVALUE> asRequired(String errorMessage) {
            this.isRequired = true;
            this.requiredMessage = errorMessage;
            return this;
        }

        @Override
        public BindingBuilder<BEAN, FIELDVALUE> withRule(
                com.zeroz4j.api.validation.FieldRule<? super FIELDVALUE> rule) {
            validators.add((value, context) -> {
                java.util.List<String> violations = rule.validate(value);
                return violations.isEmpty()
                        ? ValidationResult.ok()
                        : ValidationResult.error(violations.get(0));
            });
            return this;
        }

        @Override
        public Binding<BEAN, FIELDVALUE> bind(ValueProvider<BEAN, FIELDVALUE> getter, Setter<BEAN, FIELDVALUE> setter) {
            BindingImpl<FIELDVALUE> binding = new BindingImpl<>(field, getter, setter,
                    new ArrayList<>(validators), isRequired, requiredMessage);
            bindings.add(binding);

            // asRequired said so; the field is where a person can see it.
            if (isRequired && field instanceof com.zeroz4j.ui.component.AbstractField) {
                ((com.zeroz4j.ui.component.AbstractField<?, ?>) field).setRequiredIndicatorVisible(true);
            }

            HasValue.ValueChangeListener<FIELDVALUE> listener = event -> {
                ValidationResult vr = binding.validate();
                if (Binder.this.bean != null && !vr.isError()) {
                    binding.write(Binder.this.bean);
                }
            };
            binding.changeListener = listener;
            field.addValueChangeListener(listener);

            return binding;
        }
    }

    private class BindingImpl<FIELDVALUE> implements Binding<BEAN, FIELDVALUE> {
        private final HasValue<FIELDVALUE> field;
        private final ValueProvider<BEAN, FIELDVALUE> getter;
        private final Setter<BEAN, FIELDVALUE> setter;
        private final List<Validator<? super FIELDVALUE>> validators;
        private final boolean isRequired;
        private final String requiredMessage;
        HasValue.ValueChangeListener<FIELDVALUE> changeListener;
        private FIELDVALUE lastReadValue;

        public BindingImpl(HasValue<FIELDVALUE> field, ValueProvider<BEAN, FIELDVALUE> getter, Setter<BEAN, FIELDVALUE> setter, List<Validator<? super FIELDVALUE>> validators, boolean isRequired, String requiredMessage) {
            this.field = field;
            this.getter = getter;
            this.setter = setter;
            this.validators = validators;
            this.isRequired = isRequired;
            this.requiredMessage = requiredMessage;
        }

        @Override
        public HasValue<FIELDVALUE> getField() {
            return field;
        }

        @Override
        public ValidationResult validate() {
            FIELDVALUE value = field.getValue();
            
            // Check required
            if (isRequired && (value == null || (value instanceof String && ((String) value).trim().isEmpty()))) {
                ValidationResult res = ValidationResult.error(requiredMessage);
                showError(res);
                return res;
            }
            
            ValueContext ctx = new ValueContext(field instanceof Component ? (Component) field : null, field);
            for (Validator<? super FIELDVALUE> validator : validators) {
                ValidationResult result = validator.apply(value, ctx);
                if (result.isError()) {
                    showError(result);
                    return result;
                }
            }
            
            clearError();
            return ValidationResult.ok();
        }

        @Override
        public void read(BEAN bean) {
            FIELDVALUE value = bean == null ? null : getter.apply(bean);
            field.setValue(value);
            lastReadValue = value;
            clearError();
        }

        /** True when the field's current value differs from the value last read into it. */
        boolean isModified() {
            FIELDVALUE current = field.getValue();
            return current == null ? lastReadValue != null : !current.equals(lastReadValue);
        }

        /** Accepts the field's current value as the new baseline, clearing the modified flag. */
        void markCommitted() {
            lastReadValue = field.getValue();
        }

        /** Detaches the write-through listener so an unbound field stops updating the bean. */
        void detach() {
            if (changeListener != null) {
                field.removeValueChangeListener(changeListener);
                changeListener = null;
            }
        }

        @Override
        public void write(BEAN bean) {
            if (setter != null) {
                setter.accept(bean, field.getValue());
            }
        }
        
        private void showError(ValidationResult result) {
            if (field instanceof HasStyle) {
                HasStyle style = (HasStyle) field;
                style.addClassName("input-error");
                // Kept only for applications that wrote a stylesheet rule around this variable
                // before 0.8.0. It is not how the message is shown - see below - and it is
                // scheduled to go.
                style.setStyle("--error-message",
                        "'" + result.getErrorMessage().replace("'", "\\'") + "'");
            }
            // Until 0.8.0 the message went only into a stylesheet variable, which nothing displayed
            // unless the application had written a rule for it. A field can now show it itself.
            if (field instanceof com.zeroz4j.ui.component.AbstractField) {
                ((com.zeroz4j.ui.component.AbstractField<?, ?>) field)
                        .setErrorMessage(result.getErrorMessage());
            }
        }

        private void clearError() {
            if (field instanceof HasStyle) {
                HasStyle style = (HasStyle) field;
                style.removeClassName("input-error");
                // Left set, this outlived the error it described: an application displaying the
                // variable kept showing the old sentence after the value was corrected.
                style.setStyle("--error-message", "''");
            }
            if (field instanceof com.zeroz4j.ui.component.AbstractField) {
                ((com.zeroz4j.ui.component.AbstractField<?, ?>) field).setErrorMessage(null);
            }
        }
    }

    /**
     * Removes a specific field binding from this binder instance.
     *
     * @param binding the binding handle to remove
     */
    public void removeBinding(Binding<BEAN, ?> binding) {
        if (binding instanceof BindingImpl) {
            ((BindingImpl<?>) binding).detach();
        }
        bindings.remove(binding);
    }

    /**
     * Removes all registered field bindings from this binder instance.
     */
    public void removeAllBindings() {
        for (Binding<BEAN, ?> binding : bindings) {
            if (binding instanceof BindingImpl) {
                ((BindingImpl<?>) binding).detach();
            }
        }
        bindings.clear();
    }
}
