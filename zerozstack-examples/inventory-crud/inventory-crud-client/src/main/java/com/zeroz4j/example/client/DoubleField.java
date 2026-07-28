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

import com.zeroz4j.ui.component.AbstractField;
import com.zeroz4j.ui.component.mixin.HasColorVariants;
import com.zeroz4j.ui.component.mixin.HasSizeVariants;
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.events.EventListener;
import org.teavm.jso.dom.html.HTMLInputElement;

public class DoubleField extends AbstractField<DoubleField, Double> implements
        HasColorVariants<DoubleField>,
        HasSizeVariants<DoubleField> {

    public DoubleField() {
        super("input", 0.0);
        getElement().setAttribute("type", "number");
        getElement().setAttribute("step", "0.01");
        addClassName("input");
        addClassName("input-bordered");

        EventListener<Event> inputListener = evt -> {
            HTMLInputElement input = getElement().cast();
            String valStr = input.getValue();
            try {
                double parsed = (valStr != null && !valStr.isEmpty()) ? Double.parseDouble(valStr) : 0.0;
                setModelValue(parsed, true);
            } catch (NumberFormatException e) {
                setModelValue(0.0, true);
            }
        };
        addDomEventListener("input", inputListener);
    }

    public DoubleField(String placeholder) {
        this();
        getElement().setAttribute("placeholder", placeholder);
    }

    @Override
    protected void setPresentationValue(Double value) {
        HTMLInputElement input = getElement().cast();
        String strVal = value != null ? String.valueOf(value) : "0.0";
        if (!strVal.equals(input.getValue())) {
            input.setValue(strVal);
        }
    }

    @Override
    public String getThemePrefix() {
        return "input";
    }
}
