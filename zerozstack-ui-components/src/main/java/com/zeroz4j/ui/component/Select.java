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

import org.teavm.jso.dom.html.HTMLSelectElement;
import org.teavm.jso.dom.html.HTMLOptionElement;
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.browser.Window;
import java.util.List;
import com.zeroz4j.ui.component.mixin.HasColorVariants;
import com.zeroz4j.ui.component.mixin.HasSizeVariants;
import org.teavm.jso.dom.events.EventListener;

public class Select extends AbstractField<Select, String> implements
        HasColorVariants<Select>,
        HasSizeVariants<Select> {

    public Select() {
        super("select", null);
        addClassName("select");
        addClassName("select-bordered");
        
        EventListener<Event> changeListener = evt -> {
            HTMLSelectElement select = getElement().cast();
            setModelValue(select.getValue(), true);
        };
        addDomEventListener("change", changeListener);
    }
    
    public void setItems(List<String> items) {
        HTMLSelectElement select = getElement().cast();
        // Clear existing options
        while (select.getLastChild() != null) {
            select.removeChild(select.getLastChild());
        }
        
        for (String item : items) {
            HTMLOptionElement option = (HTMLOptionElement) Window.current().getDocument().createElement("option");
            option.setValue(item);
            option.setText(item);
            select.appendChild(option);
        }
    }

    @Override
    protected void setPresentationValue(String value) {
        HTMLSelectElement select = getElement().cast();
        if (value == null) {
            select.setValue("");
        } else if (!value.equals(select.getValue())) {
            select.setValue(value);
        }
    }

    @Override
    public String getThemePrefix() {
        return "select";
    }
}
