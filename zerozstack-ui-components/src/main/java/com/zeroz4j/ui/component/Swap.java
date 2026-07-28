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
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.events.EventListener;

public class Swap extends AbstractField<Swap, Boolean> {
    private final HTMLInputElement checkbox;

    public Swap() {
        super("label", false);
        addClassName("swap");
        
        checkbox = (HTMLInputElement) Window.current().getDocument().createElement("input");
        checkbox.setAttribute("type", "checkbox");
        
        EventListener<Event> changeListener = evt -> {
            setModelValue(checkbox.isChecked(), true);
        };
        checkbox.addEventListener("change", threaded(changeListener));
        
        getElement().appendChild(checkbox);
    }

    @Override
    protected void setPresentationValue(Boolean value) {
        boolean b = value != null && value;
        if (b != checkbox.isChecked()) {
            checkbox.setChecked(b);
        }
    }
}
