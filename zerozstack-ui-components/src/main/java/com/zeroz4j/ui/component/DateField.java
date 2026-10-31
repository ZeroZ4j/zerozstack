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
import java.time.LocalDate;
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.events.EventListener;
import org.teavm.jso.dom.html.HTMLInputElement;

/**
 * A date entry using the browser's own date picker. The value is a {@link LocalDate}, or null while
 * the box is empty or holds an incomplete date. Needs no date library.
 *
 * <pre>{@code
 * DateField due = new DateField().withLabel("Due date");
 * due.addValueChangeListener(e -> save(e.getValue()));
 * }</pre>
 */
public class DateField extends AbstractField<DateField, LocalDate> implements
        HasColorVariants<DateField>,
        HasSizeVariants<DateField> {

    public DateField() {
        super("input", null);
        getElement().setAttribute("type", "date");
        addClassName("input");
        addClassName("input-bordered");
        EventListener<Event> listener = evt -> {
            HTMLInputElement input = getElement().cast();
            setModelValue(parse(input.getValue()), true);
        };
        addDomEventListener("input", listener);
    }

    /**
     * Earliest date the picker allows.
     *
     * @param min the date, or null for no limit
     */
    public void setMin(LocalDate min) {
        setLimit("min", min);
    }

    /**
     * Latest date the picker allows.
     *
     * @param max the date, or null for no limit
     */
    public void setMax(LocalDate max) {
        setLimit("max", max);
    }

    private void setLimit(String name, LocalDate limit) {
        if (limit == null) {
            getElement().removeAttribute(name);
        } else {
            getElement().setAttribute(name, limit.toString());
        }
    }

    private static LocalDate parse(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (RuntimeException failure) {
            return null;
        }
    }

    @Override
    protected void setPresentationValue(LocalDate value) {
        HTMLInputElement input = getElement().cast();
        String text = value == null ? "" : value.toString();
        if (!text.equals(input.getValue())) {
            input.setValue(text);
        }
    }

    @Override
    public String getThemePrefix() {
        return "input";
    }
}
