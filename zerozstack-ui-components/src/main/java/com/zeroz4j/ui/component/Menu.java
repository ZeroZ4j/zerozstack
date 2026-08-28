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

import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.component.HasStyle;
import org.teavm.jso.dom.events.Event;


public class Menu extends Component implements HasComponents, HasStyle {

    private static int accordionCounter = 0;
    private boolean isAccordion = false;
    private String accordionGroupName = "accordion-group-" + (++accordionCounter);

    public Menu() {
        super("ul");
        addClassName("menu");
        addClassName("bg-base-200");
        addClassName("w-56");
        addClassName("rounded-box");
        // A menu never grows past what it was put in. Without this a single long word - a German
        // compound noun, a file path, an address - made the whole page scroll sideways, because a
        // menu entry's words have no place to break and the entry's own width follows them.
        getElement().getStyle().setProperty("max-width", "100%");
    }

    @Override
    public Component getComponent() {
        return this;
    }

    /** An entry that does something when it is pressed. */
    public void addItem(String text, EventListener<ClickEvent<MenuItem>> clickListener) {
        MenuItem item = new MenuItem(text);
        item.addClickListener(clickListener);
        add(item);
    }

    /**
     * An entry that goes somewhere.
     *
     * <p>Use this rather than an entry with a listener that navigates by hand: a real address is
     * what lets somebody middle-click it, copy it, or see where it goes before following it.</p>
     */
    public void addLink(String text, String href) {
        add(new MenuItem(text, href));
    }

    public void setAccordion(boolean accordion) {
        this.isAccordion = accordion;
    }

    public void addSubMenu(String text, Menu subMenu) {
        class SubMenuContainer extends Component implements HasStyle, HasComponents {
            public SubMenuContainer() { super("li"); }
            @Override public Component getComponent() { return this; }
        }
        SubMenuContainer li = new SubMenuContainer();
        
        class Details extends Component implements HasComponents, HasStyle {
            public Details() { super("details"); }
            @Override public Component getComponent() { return this; }
        }
        Details details = new Details();
        if (this.isAccordion) {
            details.getElement().setAttribute("name", this.accordionGroupName);
        }
        
        class Summary extends Component implements HasText {
            public Summary(String t) {
                super("summary");
                setText(t);
                wrapLongWords(getElement());
            }
            @Override public Component getComponent() { return this; }
        }
        details.add(new Summary(text));
        
        subMenu.removeClassName("menu");
        subMenu.removeClassName("bg-base-200");
        subMenu.removeClassName("w-56");
        subMenu.removeClassName("rounded-box");
        
        details.add(subMenu);
        li.add(details);
        add(li);
    }
    
    public void addTitle(String text) {
        class MenuTitle extends Component implements HasStyle, HasComponents {
            public MenuTitle() { super("li"); addClassName("menu-title"); }
            @Override public Component getComponent() { return this; }
        }
        MenuTitle title = new MenuTitle();
        class SpanText extends Component implements HasStyle {
            public SpanText(String t) { super("span"); getElement().setTextContent(t); }
            @Override public Component getComponent() { return this; }
        }
        SpanText titleText = new SpanText(text);
        wrapLongWords(titleText.getElement());
        title.add(titleText);
        add(title);
    }
    
    /**
     * Lets a word longer than the menu break, and stops it setting the menu's width.
     *
     * <p>{@code anywhere} rather than {@code break-word}: only {@code anywhere} makes the browser
     * count the broken word when it works out how narrow the entry is allowed to be. With
     * {@code break-word} the entry still claims the width of the whole unbroken word, which is
     * what pushed a page 2,773 pixels sideways in this library's own gallery.</p>
     */
    private static void wrapLongWords(org.teavm.jso.dom.html.HTMLElement element) {
        element.getStyle().setProperty("overflow-wrap", "anywhere");
        element.getStyle().setProperty("min-width", "0");
    }

    /**
     * One entry in a menu.
     *
     * <p>An entry that <b>does</b> something is a {@code <button>}; an entry that <b>goes</b>
     * somewhere is an {@code <a>} with an address. Both are in the tab order and both answer
     * Enter, which is the whole point: through 0.7.0 every entry was an {@code <a>} with no
     * address, and the browser leaves those out of the tab order entirely. Every menu built with
     * this library was mouse-only, including the one down the side of its own gallery.</p>
     *
     * <p><b>Changed in 0.8.0.</b> An entry with a listener is now a {@code <button>}. A stylesheet
     * rule written as {@code .menu a} no longer reaches it; write {@code .menu li > *}, which is
     * what daisyUI itself uses.</p>
     */
    public static class MenuItem extends Component implements HasText {

        private final org.teavm.jso.dom.html.HTMLElement control;

        /** An entry that does something. It is a button, because pressing it is the point. */
        public MenuItem(String text) {
            this(text, null);
        }

        /**
         * An entry that goes somewhere, or - with a null address - one that does something.
         *
         * @param text the words on the entry
         * @param href where it goes, or null for an entry you give a listener to instead
         */
        public MenuItem(String text, String href) {
            super("li");
            if (href == null) {
                control = org.teavm.jso.browser.Window.current().getDocument()
                        .createElement("button");
                control.setAttribute("type", "button");
            } else {
                control = org.teavm.jso.browser.Window.current().getDocument()
                        .createElement("a");
                control.setAttribute("href", href);
            }
            control.setTextContent(text);
            wrapLongWords(control);
            getElement().appendChild(control);
        }

        @Override
        public Component getComponent() {
            return this;
        }

        @Override
        public void setText(String text) {
            control.setTextContent(text);
        }

        @Override
        public String getText() {
            return control.getTextContent();
        }

        public DomListenerRegistration addClickListener(EventListener<ClickEvent<MenuItem>> listener) {
            // One wrapper, kept. The old code built a second one to unregister with, so the
            // registration it handed back removed nothing and the listener stayed for good.
            org.teavm.jso.dom.events.EventListener<Event> domListener =
                    threaded(evt -> listener.onComponentEvent(new ClickEvent<>(this, true)));
            control.addEventListener("click", domListener);
            return () -> control.removeEventListener("click", domListener);
        }
    }
}
