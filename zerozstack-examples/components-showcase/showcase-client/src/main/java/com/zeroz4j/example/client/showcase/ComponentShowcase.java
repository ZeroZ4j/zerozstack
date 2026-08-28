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
package com.zeroz4j.example.client.showcase;

import com.zeroz4j.ui.component.Card;
import com.zeroz4j.ui.component.CardTitle;
import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.component.HasStyle;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.VerticalLayout;
import com.zeroz4j.ui.theme.TextStyle;

public abstract class ComponentShowcase extends VerticalLayout {

    public ComponentShowcase() {
        super();
        addClassName("p-6");
        addClassName("gap-6");
        addClassName("max-w-4xl");
    }

    /**
     * The name of this page, in the library's own type scale. The accent colour is the one thing
     * chosen here; the size and weight are asked for by name so every page is the same.
     */
    protected void addTitle(String text) {
        class H1Title extends Component implements HasStyle {
            public H1Title(String t) {
                super("h1");
                getElement().setTextContent(t);
                addClassName("mb-2 text-primary");
                TextStyle.PAGE_TITLE.applyTo(this);
            }

            @Override
            public Component getComponent() {
                return this;
            }
        }
        add(new H1Title(text));
    }

    /** The sentence under the title: supporting words, one step quieter than the prose. */
    protected void addDescription(String text) {
        class DescParagraph extends Component implements HasStyle {
            public DescParagraph(String t) {
                super("p");
                getElement().setTextContent(t);
                addClassName("mb-4");
                TextStyle.SECONDARY.applyTo(this);
            }

            @Override
            public Component getComponent() {
                return this;
            }
        }
        add(new DescParagraph(text));
    }

    /**
     * The short "try this, and this would be broken" note the hard pages carry under their title.
     * One style, defined once, so six pages do not each invent a tinted box of their own.
     *
     * @param heading  what the reader is being asked to do
     * @param points   one sentence per thing to try or to watch for
     */
    protected void addWhatToCheck(String heading, String... points) {
        Div box = new Div();
        box.addClassName("rounded-box border border-warning/40 bg-warning/10 p-4 mb-2");

        class BoxHeading extends Component implements HasStyle {
            BoxHeading(String t) {
                super("p");
                getElement().setTextContent(t);
                addClassName("font-semibold mb-2");
            }

            @Override
            public Component getComponent() {
                return this;
            }
        }
        box.add(new BoxHeading(heading));

        class Bullets extends Component implements HasStyle {
            Bullets() {
                super("ul");
                addClassName("list-disc pl-5 space-y-1 text-sm");
            }

            @Override
            public Component getComponent() {
                return this;
            }
        }
        class Bullet extends Component implements HasStyle {
            Bullet(String t) {
                super("li");
                getElement().setTextContent(t);
            }

            @Override
            public Component getComponent() {
                return this;
            }
        }
        Bullets list = new Bullets();
        for (String point : points) {
            list.getElement().appendChild(new Bullet(point).getElement());
        }
        box.add(list);
        add(box);
    }

    protected void addSection(String title, Component... components) {
        Card sectionCard = new Card();
        sectionCard.addClassName("p-6");
        sectionCard.addClassName("bg-base-200");
        sectionCard.addClassName("shadow");
        
        sectionCard.add(new CardTitle(title));
        
        Div content = new Div();
        content.addClassName("flex");
        content.addClassName("flex-wrap");
        content.addClassName("gap-4");
        content.addClassName("mt-4");
        content.addClassName("items-center");
        content.add(components);
        
        sectionCard.add(content);
        add(sectionCard);
    }
}
