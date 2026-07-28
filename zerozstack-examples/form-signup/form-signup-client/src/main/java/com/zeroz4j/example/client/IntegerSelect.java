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
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.events.EventListener;
import org.teavm.jso.dom.html.HTMLOptionElement;
import org.teavm.jso.dom.html.HTMLSelectElement;

import java.util.List;

public class IntegerSelect extends AbstractField<IntegerSelect, Integer> implements
        HasColorVariants<IntegerSelect>,
        HasSizeVariants<IntegerSelect> {

    public IntegerSelect() {
        super("select", 0);
        addClassName("select");
        addClassName("select-bordered");

        EventListener<Event> changeListener = evt -> {
            HTMLSelectElement select = getElement().cast();
            try {
                setModelValue(Integer.parseInt(select.getValue()), true);
            } catch (NumberFormatException e) {
                setModelValue(0, true);
            }
        };
        addDomEventListener("change", changeListener);
    }

    public void setItems(List<Integer> items) {
        HTMLSelectElement select = getElement().cast();
        while (select.getLastChild() != null) {
            select.removeChild(select.getLastChild());
        }

        for (Integer item : items) {
            HTMLOptionElement option = (HTMLOptionElement) Window.current().getDocument().createElement("option");
            option.setValue(String.valueOf(item));
            option.setText(String.valueOf(item));
            select.appendChild(option);
        }
    }

    @Override
    protected void setPresentationValue(Integer value) {
        HTMLSelectElement select = getElement().cast();
        String strVal = value != null ? String.valueOf(value) : "0";
        if (!strVal.equals(select.getValue())) {
            select.setValue(strVal);
        }
    }

    @Override
    public String getThemePrefix() {
        return "select";
    }
}
