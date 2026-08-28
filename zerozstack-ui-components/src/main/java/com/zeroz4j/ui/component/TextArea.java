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

import org.teavm.jso.dom.html.HTMLTextAreaElement;
import org.teavm.jso.dom.events.Event;
import com.zeroz4j.ui.component.mixin.HasColorVariants;
import com.zeroz4j.ui.component.mixin.HasSizeVariants;
import org.teavm.jso.dom.events.EventListener;

public class TextArea extends AbstractField<TextArea, String> implements
        HasColorVariants<TextArea>,
        HasSizeVariants<TextArea> {

    public TextArea() {
        super("textarea", "");
        addClassName("textarea");
        addClassName("textarea-bordered");
        
        EventListener<Event> inputListener = evt -> {
            HTMLTextAreaElement input = getElement().cast();
            setModelValue(input.getValue(), true);
        };
        addDomEventListener("input", inputListener);
    }
    
    /**
     * Creates a text area with the given <b>placeholder</b> - the grey text shown while the area is
     * empty, which disappears as soon as somebody types. For the name of the field, which has to
     * stay visible, use {@link #withLabel(String)}.
     *
     * @param placeholder the grey example text shown while the area is empty
     */
    public TextArea(String placeholder) {
        this();
        setPlaceholder(placeholder);
    }

    /**
     * Sets the grey example text shown while the area is empty.
     *
     * @param placeholder the placeholder text, or null to remove it
     */
    public void setPlaceholder(String placeholder) {
        if (placeholder == null) {
            getElement().removeAttribute("placeholder");
        } else {
            getElement().setAttribute("placeholder", placeholder);
        }
    }

    /**
     * Returns the grey example text shown while the area is empty, or null when it has none.
     *
     * @return the placeholder text
     */
    public String getPlaceholder() {
        return getElement().getAttribute("placeholder");
    }

    @Override
    protected void setPresentationValue(String value) {
        HTMLTextAreaElement input = getElement().cast();
        if (value == null) {
            input.setValue("");
        } else if (!value.equals(input.getValue())) {
            input.setValue(value);
        }
    }

    @Override
    public String getThemePrefix() {
        return "textarea";
    }
}
