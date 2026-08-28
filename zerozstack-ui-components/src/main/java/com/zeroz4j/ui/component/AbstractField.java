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

import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.html.HTMLElement;
import org.teavm.jso.dom.xml.Node;

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

    private static final java.util.concurrent.atomic.AtomicInteger fieldIdCounter =
            new java.util.concurrent.atomic.AtomicInteger();

    private HTMLElement wrapper;
    private HTMLElement controlRow;
    private HTMLElement labelElement;
    private HTMLElement captionElement;
    private HTMLElement requiredMark;
    private HTMLElement helperElement;
    private HTMLElement errorElement;
    private String label;
    private String helperText;
    private String errorMessage;
    private boolean requiredIndicatorVisible;
    
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
            // setErrorMessage carries both halves of "this is wrong": the red control and the
            // sentence under it.
            setErrorMessage(violations.isEmpty() ? null : violations.get(0));
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

    // ---------------------------------------------------------------------
    // Caption, helper text, required marking and the message line
    // ---------------------------------------------------------------------

    /**
     * Sets the caption shown above this field (beside it, for a checkbox or a toggle) and returns
     * the field, so it can be written in the same expression that creates it:
     *
     * <pre>{@code
     * TextField folder = new TextField().withLabel("Primary folder path");
     * }</pre>
     *
     * <p>The caption is a real {@code <label>} tied to the control, so clicking the words focuses
     * the field and a screen reader announces them. It is not the same thing as the placeholder
     * that {@code new TextField("...")} sets: a placeholder disappears the moment somebody types,
     * which is why it cannot be the only name a field has.</p>
     *
     * @param label the caption, or null to remove it
     * @return this field, for chaining
     */
    @SuppressWarnings("unchecked")
    public C withLabel(String label) {
        setLabel(label);
        return (C) this;
    }

    /**
     * Sets the caption shown above this field, or beside it for a checkbox or a toggle.
     *
     * @param label the caption, or null to remove it
     */
    public void setLabel(String label) {
        this.label = label;
        if (label == null || label.isEmpty()) {
            if (labelElement != null) {
                labelElement.getStyle().setProperty("display", "none");
            }
            return;
        }
        ensureWrapper();
        captionElement.setTextContent(label);
        labelElement.getStyle().removeProperty("display");
    }

    /**
     * Returns the caption, or null when the field has none.
     *
     * @return the caption
     */
    public String getLabel() {
        return label;
    }

    /**
     * Sets a quiet line of explanation under the field - the units a number is in, the shape a
     * path should have. It is announced together with the field rather than read as separate text.
     *
     * @param helperText the explanation, or null to remove it
     */
    public void setHelperText(String helperText) {
        this.helperText = helperText;
        if (helperText == null || helperText.isEmpty()) {
            if (helperElement != null) {
                helperElement.getStyle().setProperty("display", "none");
                updateDescribedBy();
            }
            return;
        }
        ensureWrapper();
        helperElement.setTextContent(helperText);
        helperElement.getStyle().removeProperty("display");
        updateDescribedBy();
    }

    /**
     * Sets a quiet line of explanation under the field and returns the field, for chaining.
     *
     * @param helperText the explanation, or null to remove it
     * @return this field, for chaining
     */
    @SuppressWarnings("unchecked")
    public C withHelperText(String helperText) {
        setHelperText(helperText);
        return (C) this;
    }

    /**
     * Returns the explanation shown under the field, or null when it has none.
     *
     * @return the helper text
     */
    public String getHelperText() {
        return helperText;
    }

    /**
     * Marks the caption as required, with the usual asterisk after the words, and tells assistive
     * technology the same thing. It changes appearance only - a {@code Binder} still decides what
     * an empty value means, and the server still decides whether it is accepted.
     *
     * <p>{@code binder.forField(f).asRequired("...")} turns this on by itself, so a form built with
     * a binder rarely needs to call it.</p>
     *
     * @param visible true to mark the field required
     */
    public void setRequiredIndicatorVisible(boolean visible) {
        this.requiredIndicatorVisible = visible;
        if (!visible) {
            if (requiredMark != null) {
                requiredMark.getStyle().setProperty("display", "none");
            }
            getElement().removeAttribute("aria-required");
            return;
        }
        ensureWrapper();
        requiredMark.getStyle().removeProperty("display");
        getElement().setAttribute("aria-required", "true");
    }

    /**
     * Returns whether the caption is marked as required.
     *
     * @return true when the field is marked required
     */
    public boolean isRequiredIndicatorVisible() {
        return requiredIndicatorVisible;
    }

    /**
     * Shows a message under the field, in the error colour, colours the control to match, and
     * marks it invalid for assistive technology. Passing null clears all three.
     *
     * <p>A {@code Binder} and {@link #withRule(com.zeroz4j.api.validation.FieldRule)} both write
     * here, so a validation message is shown by the field that failed rather than having to be
     * placed by hand.</p>
     *
     * @param errorMessage the message, or null to clear it
     */
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        if (errorMessage == null || errorMessage.isEmpty()) {
            if (errorElement != null) {
                errorElement.getStyle().setProperty("display", "none");
                updateDescribedBy();
            }
            removeClassName("input-error");
            getElement().removeAttribute("aria-invalid");
            return;
        }
        ensureWrapper();
        errorElement.setTextContent(errorMessage);
        errorElement.getStyle().removeProperty("display");
        addClassName("input-error");
        getElement().setAttribute("aria-invalid", "true");
        updateDescribedBy();
    }

    /**
     * Returns the message currently shown under the field, or null when there is none.
     *
     * @return the message
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Returns the element a container inserts to place this field. Once the field has a caption,
     * helper text or a message, that is a wrapper holding all of them; until then it is the
     * control itself, and the page is exactly what it was before.
     *
     * @return the outermost element of this field
     */
    @Override
    public HTMLElement getOuterElement() {
        return wrapper != null ? wrapper : getElement();
    }

    @Override
    public void setId(String id) {
        super.setId(id);
        if (labelElement != null) {
            if (labelTargetsControl()) {
                labelElement.setAttribute("for", id);
            } else {
                labelElement.setAttribute("id", id + "-label");
                getElement().setAttribute("aria-labelledby", id + "-label");
            }
        }
        if (helperElement != null) {
            helperElement.setAttribute("id", id + "-help");
        }
        if (errorElement != null) {
            errorElement.setAttribute("id", id + "-error");
        }
        updateDescribedBy();
    }

    /**
     * Whether the caption belongs after the control rather than above it. True for a checkbox and
     * a toggle, where the words sit to the right of the box and the pair reads as one line.
     *
     * @return true to place the caption after the control
     */
    protected boolean labelFollowsControl() {
        return false;
    }

    /**
     * Whether the caption may be a {@code <label for="...">} pointing at this field's element.
     * False for a field whose element is not itself a form control - a radio group is a
     * {@code <div>} of several controls, and is captioned as a named group instead.
     *
     * @return true when the control can be the target of a label
     */
    protected boolean labelTargetsControl() {
        return true;
    }

    private String ensureId() {
        String id = getElement().getAttribute("id");
        if (id == null || id.isEmpty()) {
            id = "zeroz-field-" + fieldIdCounter.incrementAndGet();
            getElement().setAttribute("id", id);
        }
        return id;
    }

    private void updateDescribedBy() {
        String id = getElement().getAttribute("id");
        if (id == null || id.isEmpty()) {
            return;
        }
        StringBuilder described = new StringBuilder();
        if (isShown(helperElement)) {
            described.append(id).append("-help");
        }
        if (isShown(errorElement)) {
            if (described.length() > 0) {
                described.append(' ');
            }
            described.append(id).append("-error");
        }
        if (described.length() == 0) {
            getElement().removeAttribute("aria-describedby");
        } else {
            getElement().setAttribute("aria-describedby", described.toString());
        }
    }

    private static boolean isShown(HTMLElement element) {
        return element != null && !"none".equals(element.getStyle().getPropertyValue("display"));
    }

    /**
     * Builds the caption / control / message group around the control, once, the first time one of
     * those parts is asked for. A field that never gets a caption never grows one.
     */
    private void ensureWrapper() {
        if (wrapper != null) {
            return;
        }
        String id = ensureId();
        HTMLElement box = Window.current().getDocument().createElement("div");
        box.setClassName("zeroz-field");
        box.getStyle().setProperty("display", "flex");
        box.getStyle().setProperty("flex-direction", "column");
        box.getStyle().setProperty("gap", "0.25rem");
        box.getStyle().setProperty("min-width", "0");
        // Placement and hiding were addressed to the control while it stood alone; the wrapper
        // takes its place in the parent, so they have to travel with it.
        movePlacementStyle(box, "grid-column");
        movePlacementStyle(box, "grid-row");
        if ("none".equals(getElement().getStyle().getPropertyValue("display"))) {
            getElement().getStyle().removeProperty("display");
            box.getStyle().setProperty("display", "none");
        }

        Node parent = getElement().getParentNode();
        if (parent != null) {
            parent.insertBefore(box, getElement());
        }

        labelElement = Window.current().getDocument()
                .createElement(labelTargetsControl() ? "label" : "span");
        labelElement.setClassName("text-sm font-medium text-base-content");
        labelElement.getStyle().setProperty("display", "none");
        if (labelTargetsControl()) {
            labelElement.setAttribute("for", id);
            labelElement.getStyle().setProperty("cursor", "pointer");
        } else {
            labelElement.setAttribute("id", id + "-label");
            getElement().setAttribute("role", "group");
            getElement().setAttribute("aria-labelledby", id + "-label");
        }
        captionElement = Window.current().getDocument().createElement("span");
        requiredMark = Window.current().getDocument().createElement("span");
        requiredMark.setClassName("text-error");
        requiredMark.setTextContent(" *");
        requiredMark.setAttribute("aria-hidden", "true");
        requiredMark.getStyle().setProperty("display", "none");
        labelElement.appendChild(captionElement);
        labelElement.appendChild(requiredMark);

        if (labelFollowsControl()) {
            controlRow = Window.current().getDocument().createElement("div");
            controlRow.getStyle().setProperty("display", "flex");
            controlRow.getStyle().setProperty("align-items", "center");
            controlRow.getStyle().setProperty("gap", "0.5rem");
            box.appendChild(controlRow);
            controlRow.appendChild(getElement());
            controlRow.appendChild(labelElement);
            labelElement.getStyle().setProperty("font-weight", "400");
        } else {
            box.appendChild(labelElement);
            box.appendChild(getElement());
        }

        helperElement = Window.current().getDocument().createElement("span");
        helperElement.setAttribute("id", id + "-help");
        helperElement.setClassName("text-xs text-base-content/60");
        helperElement.getStyle().setProperty("display", "none");
        box.appendChild(helperElement);

        errorElement = Window.current().getDocument().createElement("span");
        errorElement.setAttribute("id", id + "-error");
        errorElement.setAttribute("role", "alert");
        errorElement.setClassName("text-xs text-error");
        errorElement.getStyle().setProperty("display", "none");
        box.appendChild(errorElement);

        wrapper = box;
    }

    private void movePlacementStyle(HTMLElement target, String property) {
        String value = getElement().getStyle().getPropertyValue(property);
        if (value != null && !value.isEmpty()) {
            getElement().getStyle().removeProperty(property);
            target.getStyle().setProperty(property, value);
        }
    }

    protected abstract void setPresentationValue(T newPresentationValue);
}
