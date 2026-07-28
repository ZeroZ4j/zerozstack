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

import com.zeroz4j.signals.Effect;
import com.zeroz4j.signals.Signal;
import org.teavm.jso.dom.events.Event;
import com.zeroz4j.ui.component.mixin.HasColorVariants;
import com.zeroz4j.ui.component.mixin.HasOutlineVariant;
import com.zeroz4j.ui.component.mixin.HasSizeVariants;


public class Button extends Component implements HasText, HasStyle, HasEnabled, HasSize, Focusable,
        HasColorVariants<Button>,
        HasSizeVariants<Button>,
        HasOutlineVariant<Button> {

    public Button() {
        super("button");
        addClassName("btn");
    }

    public Button(String text) {
        this();
        setText(text);
    }

    public Button(String text, EventListener<ClickEvent<Button>> clickListener) {
        this(text);
        addClickListener(clickListener);
    }
    
    public Button(Component icon) {
        this();
        getElement().appendChild(icon.getElement());
    }

    @Override
    public Component getComponent() {
        return this;
    }

    public DomListenerRegistration addClickListener(EventListener<ClickEvent<Button>> listener) {
        org.teavm.jso.dom.events.EventListener<Event> domListener = evt -> {
            listener.onComponentEvent(new ClickEvent<>(this, true));
        };
        return addDomEventListener("click", domListener);
    }
    
    public void bindEnabled(Signal<Boolean> enabledSignal) {
        Effect.create(() -> setEnabled(enabledSignal.get()));
    }

    @Override
    public String getThemePrefix() {
        return "btn";
    }
}
