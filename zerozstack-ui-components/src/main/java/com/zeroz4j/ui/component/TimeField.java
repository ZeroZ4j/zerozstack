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
import java.time.LocalTime;
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.events.EventListener;
import org.teavm.jso.dom.html.HTMLInputElement;

/**
 * A time entry using the browser's own time input. The value is a {@link LocalTime}, or null while
 * the box is empty or holds an incomplete time. Needs no date library. Seconds are kept only when set.
 *
 * <pre>{@code
 * TimeField start = new TimeField().withLabel("Start time");
 * start.addValueChangeListener(e -> save(e.getValue()));
 * }</pre>
 */
public class TimeField extends AbstractField<TimeField, LocalTime> implements
        HasColorVariants<TimeField>,
        HasSizeVariants<TimeField> {

    public TimeField() {
        super("input", null);
        getElement().setAttribute("type", "time");
        addClassName("input");
        addClassName("input-bordered");
        EventListener<Event> listener = evt -> {
            HTMLInputElement input = getElement().cast();
            setModelValue(parse(input.getValue()), true);
        };
        addDomEventListener("input", listener);
    }

    /**
     * Earliest time the field allows.
     *
     * @param min the time, or null for no limit
     */
    public void setMin(LocalTime min) {
        setLimit("min", min);
    }

    /**
     * Latest time the field allows.
     *
     * @param max the time, or null for no limit
     */
    public void setMax(LocalTime max) {
        setLimit("max", max);
    }

    private void setLimit(String name, LocalTime limit) {
        if (limit == null) {
            getElement().removeAttribute(name);
        } else {
            getElement().setAttribute(name, limit.toString());
        }
    }

    private static LocalTime parse(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            return LocalTime.parse(text);
        } catch (RuntimeException failure) {
            return null;
        }
    }

    @Override
    protected void setPresentationValue(LocalTime value) {
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
