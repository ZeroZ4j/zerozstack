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
    }

    @Override
    public Component getComponent() {
        return this;
    }

    public void addItem(String text, EventListener<ClickEvent<MenuItem>> clickListener) {
        MenuItem item = new MenuItem(text);
        item.addClickListener(clickListener);
        add(item);
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
            public Summary(String t) { super("summary"); setText(t); }
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
        title.add(new SpanText(text));
        add(title);
    }
    
    public static class MenuItem extends Component implements HasText {
        
        private final Component link;

        public MenuItem(String text) {
            super("li");
            class Link extends Component {
                public Link() { super("a"); }
            }
            link = new Link();
            link.getElement().setTextContent(text);
            getElement().appendChild(link.getElement());
        }

        @Override
        public Component getComponent() {
            return this;
        }

        @Override
        public void setText(String text) {
            link.getElement().setTextContent(text);
        }

        @Override
        public String getText() {
            return link.getElement().getTextContent();
        }

        public DomListenerRegistration addClickListener(EventListener<ClickEvent<MenuItem>> listener) {
            org.teavm.jso.dom.events.EventListener<Event> domListener = evt -> {
                listener.onComponentEvent(new ClickEvent<>(this, true));
            };
            link.getElement().addEventListener("click", threaded(domListener));
            return () -> link.getElement().removeEventListener("click", threaded(domListener));
        }
    }
}
