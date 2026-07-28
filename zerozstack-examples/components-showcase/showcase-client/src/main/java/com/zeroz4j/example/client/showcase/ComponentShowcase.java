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
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.VerticalLayout;

public abstract class ComponentShowcase extends VerticalLayout {

    public ComponentShowcase() {
        super();
        addClassName("p-6");
        addClassName("gap-6");
        addClassName("max-w-4xl");
    }

    protected void addTitle(String text) {
        class H1Title extends Component {
            public H1Title(String t) {
                super("h1");
                getElement().setTextContent(t);
                getElement().setClassName("text-3xl font-bold mb-2 text-primary");
            }
        }
        add(new H1Title(text));
    }

    protected void addDescription(String text) {
        class DescSpan extends Component {
            public DescSpan(String t) {
                super("p");
                getElement().setTextContent(t);
                getElement().setClassName("text-base text-base-content/75 mb-4");
            }
        }
        add(new DescSpan(text));
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
