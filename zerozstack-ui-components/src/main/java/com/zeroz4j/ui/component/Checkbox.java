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

public class Checkbox extends AbstractField<Checkbox, Boolean> implements
        HasColorVariants<Checkbox>,
        HasSizeVariants<Checkbox> {

    public Checkbox() {
        super("input", false);
        getElement().setAttribute("type", "checkbox");
        addClassName("checkbox");
        
        EventListener<Event> changeListener = evt -> {
            HTMLInputElement input = getElement().cast();
            setModelValue(input.isChecked(), true);
        };
        addDomEventListener("change", changeListener);
    }

    /**
     * Adds classes, sending the ones that size the space to the row around the box.
     *
     * <p>The box is the {@code input} itself, so {@code min-h-11}, {@code h-*}, {@code w-*} and
     * {@code size-*} on it stretch the box and its tick out of shape. Those classes land on the
     * row that holds the box and its caption instead, which gets taller or wider and leaves the
     * box alone. {@code getElement()} is still the input. The row is the wrapper a caption
     * already builds, so {@link #getOuterElement()} returns it from the first such class on, and
     * containers that use {@code add(...)} place it without any change in the caller. Use
     * {@code setThemeSize} to make the box itself larger.</p>
     */
    @Override
    public void addClassName(String className) {
        if (className == null || className.isEmpty()) {
            return;
        }
        StringBuilder own = new StringBuilder();
        StringBuilder row = new StringBuilder();
        for (String name : className.split(" ")) {
            (isSizing(name) ? row : own).append(name).append(' ');
        }
        if (own.length() > 0) {
            super.addClassName(own.toString().trim());
        }
        if (row.length() > 0) {
            addControlRowClassName(row.toString().trim());
        }
    }

    @Override
    public void removeClassName(String className) {
        super.removeClassName(className);
        if (className != null && !className.isEmpty()) {
            removeControlRowClassName(className);
        }
    }

    /** True for h-*, w-*, min-*, max-* and size-* utilities, with or without a variant prefix. */
    static boolean isSizing(String name) {
        String base = name.substring(name.lastIndexOf(':') + 1);
        if (base.startsWith("-")) {
            base = base.substring(1);
        }
        if (base.startsWith("min-") || base.startsWith("max-")) {
            base = base.substring(4);
        }
        return base.startsWith("h-") || base.startsWith("w-") || base.startsWith("size-");
    }

    /** A checkbox reads as "[x] Remember me": the caption follows the box on the same line. */
    @Override
    protected boolean labelFollowsControl() {
        return true;
    }

    @Override
    protected void setPresentationValue(Boolean value) {
        HTMLInputElement input = getElement().cast();
        boolean b = value != null && value;
        if (b != input.isChecked()) {
            input.setChecked(b);
        }
    }

    @Override
    public String getThemePrefix() {
        return "checkbox";
    }
}
