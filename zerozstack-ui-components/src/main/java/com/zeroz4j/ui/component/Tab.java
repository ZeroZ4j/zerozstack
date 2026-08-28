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

/**
 * One heading in a row of tabs.
 *
 * <p>A tab is a control you press, so it is a real {@code <button>}: the keyboard reaches it with
 * Tab, presses it with Enter or Space, and a screen reader announces it as a tab and says whether
 * it is the one showing. Through 0.7.0 it was an {@code <a>} with nowhere to go, which the browser
 * skips entirely - a row of tabs no keyboard could touch.</p>
 *
 * <pre>{@code
 * Tab overview = new Tab("Overview");
 * overview.setSelected(true);
 * overview.addClickListener(e -> show(overviewPanel));
 * }</pre>
 *
 * <p><b>Changed in 0.8.0.</b> The element is a {@code <button>} rather than an {@code <a>}. A
 * stylesheet rule written as {@code a.tab} no longer matches; write {@code .tab}.</p>
 */
public class Tab extends Component implements HasText, HasComponents, HasStyle, HasSize {

    public Tab() {
        super("button");
        addClassName("tab");
        getElement().setAttribute("type", "button");
        // A row of tabs is a set of choices, and only one of them is showing. Saying so is what
        // lets a screen reader read "tab 2 of 4, selected" instead of four unrelated buttons.
        getElement().setAttribute("role", "tab");
        setSelected(false);
    }

    /** A tab with its heading already on it. */
    public Tab(String text) {
        this();
        setText(text);
    }

    /**
     * Marks this tab as the one whose panel is showing.
     *
     * <p>It colours the tab and says the same thing to a screen reader, so the two cannot drift
     * apart.</p>
     */
    public void setSelected(boolean selected) {
        getElement().setAttribute("aria-selected", selected ? "true" : "false");
        if (selected) {
            addClassName("tab-active");
        } else {
            removeClassName("tab-active");
        }
    }

    /** True when this is the tab whose panel is showing. */
    public boolean isSelected() {
        return "true".equals(getElement().getAttribute("aria-selected"));
    }

    /** Hears somebody choosing this tab, from a click or from the keyboard - they are the same event. */
    public DomListenerRegistration addClickListener(EventListener<ClickEvent<Tab>> listener) {
        return addDomEventListener("click",
                (org.teavm.jso.dom.events.EventListener<org.teavm.jso.dom.events.Event>)
                        evt -> listener.onComponentEvent(new ClickEvent<>(this, true)));
    }

    @Override
    public Component getComponent() {
        return this;
    }
}

