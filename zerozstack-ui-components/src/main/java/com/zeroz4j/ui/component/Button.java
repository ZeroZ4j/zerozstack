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
    
    /**
     * A button that is nothing but a picture.
     *
     * @deprecated since 0.8.0. A picture has no words, so this button is announced as "button" and
     *     nothing else - and somebody using voice control has nothing to say to press it. Use
     *     {@link #Button(Component, String)} and give it a name. This constructor still works and
     *     still draws the same button.
     */
    @Deprecated
    public Button(Component icon) {
        this();
        getElement().appendChild(icon.getElement());
    }

    /**
     * A button showing a picture, with words for anybody who cannot see the picture.
     *
     * <pre>{@code
     * new Button(Icon.of("trash"), "Delete this row");
     * }</pre>
     *
     * <p>Say what pressing it does, in the words somebody would use out loud - "Delete this row",
     * not "trash" and not "delete-row". That name is what a screen reader reads out and what voice
     * control listens for.</p>
     */
    public Button(Component icon, String accessibleName) {
        this(icon);
        setAriaLabel(accessibleName);
    }

    /** {@link #setAriaLabel(String)}, returning the button so it reads inside the expression that builds it. */
    public Button withAriaLabel(String accessibleName) {
        setAriaLabel(accessibleName);
        return this;
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
