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
import com.zeroz4j.ui.component.mixin.HasSizeVariants;
import org.teavm.jso.dom.events.EventListener;

public class Rating extends AbstractField<Rating, Integer> implements
        HasSizeVariants<Rating> {

    private final HTMLInputElement[] radios = new HTMLInputElement[5];

    public Rating() {
        super("div", 0);
        addClassName("rating");
        
        String groupName = "rating_" + hashCode();
        
        EventListener<Event> changeListener = evt -> {
            HTMLInputElement target = evt.getTarget().cast();
            if (target.isChecked()) {
                try {
                    setModelValue(Integer.parseInt(target.getValue()), true);
                } catch (NumberFormatException e) {
                    setModelValue(0, true);
                }
            }
        };

        for (int i = 0; i < 5; i++) {
            HTMLInputElement radio = (HTMLInputElement) Window.current().getDocument().createElement("input");
            radio.setAttribute("type", "radio");
            radio.setAttribute("name", groupName);
            radio.setAttribute("class", "mask mask-star-2 bg-orange-400");
            radio.setValue(String.valueOf(i + 1));
            radio.addEventListener("change", threaded(changeListener));
            radios[i] = radio;
            getElement().appendChild(radio);
        }
    }

    @Override
    protected void setPresentationValue(Integer value) {
        int v = value != null ? value : 0;
        for (int i = 0; i < 5; i++) {
            radios[i].setChecked((i + 1) == v);
        }
    }

    @Override
    public String getThemePrefix() {
        return "rating";
    }
}
