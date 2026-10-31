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

import com.zeroz4j.ui.component.mixin.HasColorVariants;
import com.zeroz4j.ui.component.mixin.HasSizeVariants;
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.events.EventListener;
import org.teavm.jso.dom.html.HTMLInputElement;

/**
 * A number entry. The value is a {@link Double}, or null while the box is empty or holds something
 * that is not a number. Use {@link #getIntValue()} for a whole number.
 *
 * <pre>{@code
 * NumberField quantity = new NumberField().withLabel("Quantity");
 * quantity.setMin(1);
 * quantity.setStep(1);
 * }</pre>
 */
public class NumberField extends AbstractField<NumberField, Double> implements
        HasColorVariants<NumberField>,
        HasSizeVariants<NumberField> {

    public NumberField() {
        super("input", null);
        getElement().setAttribute("type", "number");
        addClassName("input");
        addClassName("input-bordered");
        EventListener<Event> listener = evt -> {
            HTMLInputElement input = getElement().cast();
            setModelValue(parse(input.getValue()), true);
        };
        addDomEventListener("input", listener);
    }

    /**
     * Smallest value the field allows.
     *
     * @param min the limit, or null for none
     */
    public void setMin(Double min) {
        setLimit("min", min);
    }

    /**
     * Largest value the field allows.
     *
     * @param max the limit, or null for none
     */
    public void setMax(Double max) {
        setLimit("max", max);
    }

    /**
     * Size of one step of the arrow keys and the spinner. {@code 1} for whole numbers,
     * {@code 0.01} for money.
     *
     * @param step the step, or null for the browser's default
     */
    public void setStep(Double step) {
        setLimit("step", step);
    }

    /**
     * The value as a whole number.
     *
     * @return the value rounded down to an int, or null when the field is empty
     */
    public Integer getIntValue() {
        Double value = getValue();
        return value == null ? null : Integer.valueOf(value.intValue());
    }

    private void setLimit(String name, Double limit) {
        if (limit == null) {
            getElement().removeAttribute(name);
        } else {
            getElement().setAttribute(name, format(limit));
        }
    }

    private static Double parse(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            return Double.valueOf(text);
        } catch (RuntimeException failure) {
            return null;
        }
    }

    /** A whole value is written without a trailing ".0", which a number input would show. */
    private static String format(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    @Override
    protected void setPresentationValue(Double value) {
        HTMLInputElement input = getElement().cast();
        String text = value == null ? "" : format(value);
        if (!text.equals(input.getValue())) {
            input.setValue(text);
        }
    }

    @Override
    public String getThemePrefix() {
        return "input";
    }
}
