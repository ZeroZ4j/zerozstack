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
package com.zeroz4j.ui.component;

import com.zeroz4j.api.validation.FieldRule;
import com.zeroz4j.signals.Signal;
import com.zeroz4j.signals.ValueSignal;
import com.zeroz4j.signals.Effect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public abstract class AbstractField<C extends Component, T> extends Component implements HasValue<T>, HasStyle, HasEnabled, HasSize, Focusable {

    private T value;
    private final T emptyValue;
    private final List<ValueChangeListener<T>> listeners = new ArrayList<>();
    private ValueSignal<T> modelSignal;
    private boolean signalUpdating = false;
    private FieldRule<T> rule;
    private List<String> violations = Collections.emptyList();
    private boolean touched = false;
    
    public AbstractField(String tagName, T emptyValue) {
        super(tagName);
        this.emptyValue = emptyValue;
        this.value = emptyValue;
        addClassName("form-control");
    }

    @Override
    public Component getComponent() {
        return this;
    }
    
    public T getEmptyValue() {
        return emptyValue;
    }
    
    @Override
    public T getValue() {
        return value;
    }
    
    @Override
    public void setValue(T value) {
        setModelValue(value, false);
    }

    @Override
    public void addValueChangeListener(ValueChangeListener<T> listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeValueChangeListener(ValueChangeListener<T> listener) {
        listeners.remove(listener);
    }

    @Override
    public com.zeroz4j.api.Disposable bindValue(Signal<T> signal) {
        if (!(signal instanceof ValueSignal)) {
            // Silently degrading to a read-only binding here made "my edits do not reach the signal"
            // a mystery. A one-way binding is legitimate, but it has to be asked for.
            throw new IllegalArgumentException(
                    "bindValue requires a ValueSignal to write user edits back to. "
                    + signal.getClass().getSimpleName() + " cannot be written, so this binding would "
                    + "be silently one-way. Use bindValueReadOnly(signal) if that is what you want.");
        }
        this.modelSignal = (ValueSignal<T>) signal;
        return Effect.create(() -> {
            signalUpdating = true;
            try {
                setValue(this.modelSignal.get());
            } finally {
                signalUpdating = false;
            }
        });
    }

    @Override
    public com.zeroz4j.api.Disposable bindValueReadOnly(Signal<T> signal) {
        return Effect.create(() -> {
            signalUpdating = true;
            try {
                setValue(signal.get());
            } finally {
                signalUpdating = false;
            }
        });
    }
    
    /**
     * Attaches a validation rule, typically from an APT-generated {@code <Model>_Rules}
     * class so the field enforces the same annotations the server does:
     * <pre>{@code
     * nameField.withRule(Registration_Rules.fullName());
     * }</pre>
     *
     * <p>The rule runs on every value change. Once the user has touched the field, the
     * component carries the {@code input-error} style class while invalid; violations are
     * available via {@link #getViolations()} regardless of touch state, so form-level
     * validity ({@link #isValid()}) is accurate from the start.</p>
     *
     * @param rule the field rule
     * @return this field, for chaining
     */
    @SuppressWarnings("unchecked")
    public C withRule(FieldRule<T> rule) {
        this.rule = rule;
        revalidate(false);
        return (C) this;
    }

    /**
     * Returns whether the current value satisfies the attached rule (true when no rule
     * is attached).
     *
     * @return true if valid
     */
    public boolean isValid() {
        return violations.isEmpty();
    }

    /**
     * Returns the current violation messages (empty when valid or no rule attached).
     *
     * @return violation messages
     */
    public List<String> getViolations() {
        return violations;
    }

    private void revalidate(boolean fromClient) {
        if (rule == null) {
            return;
        }
        violations = rule.validate(value);
        if (fromClient) {
            touched = true;
        }
        if (touched) {
            if (violations.isEmpty()) {
                removeClassName("input-error");
            } else {
                addClassName("input-error");
            }
        }
    }

    protected void setModelValue(T value, boolean isFromClient) {
        T oldValue = this.value;
        this.value = value;
        if (!Objects.equals(oldValue, value)) {
            if (!isFromClient) {
                setPresentationValue(value);
            }
            revalidate(isFromClient);

            ValueChangeEvent<T> event = new ValueChangeEvent<>(this, oldValue, value, isFromClient);
            for (ValueChangeListener<T> listener : listeners) {
                listener.valueChanged(event);
            }

            if (this.modelSignal != null && isFromClient && !signalUpdating) {
                this.modelSignal.set(value);
            }
        }
    }

    protected abstract void setPresentationValue(T newPresentationValue);
}
