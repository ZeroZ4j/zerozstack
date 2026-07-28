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

import org.teavm.jso.dom.html.HTMLInputElement;
import org.teavm.jso.dom.events.Event;
import com.zeroz4j.ui.component.mixin.HasColorVariants;
import com.zeroz4j.ui.component.mixin.HasSizeVariants;
import org.teavm.jso.dom.events.EventListener;

public class Range extends AbstractField<Range, Double> implements
        HasColorVariants<Range>,
        HasSizeVariants<Range> {

    public Range() {
        super("input", 0.0);
        addClassName("range");
        getElement().setAttribute("type", "range");
        
        EventListener<Event> inputListener = evt -> {
            HTMLInputElement input = getElement().cast();
            try {
                setModelValue(Double.parseDouble(input.getValue()), true);
            } catch (NumberFormatException e) {
                setModelValue(0.0, true);
            }
        };
        addDomEventListener("input", inputListener);
        addDomEventListener("change", inputListener);
    }

    @Override
    protected void setPresentationValue(Double value) {
        HTMLInputElement input = getElement().cast();
        input.setValue(value != null ? String.valueOf(value) : "0");
    }

    @Override
    public String getThemePrefix() {
        return "range";
    }
}
