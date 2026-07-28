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

import com.zeroz4j.ui.component.*;
import com.zeroz4j.ui.layout.*;
import com.zeroz4j.ui.theme.*;

public class AccordionShowcase extends ComponentShowcase {

    public AccordionShowcase() {
        addTitle("Accordion");
        addDescription("Accordions are collapse elements grouped together, allowing only one to open at a time.");

        // Create individual collapse elements that act as accordion items via radio input name
        Accordion item1 = new Accordion();
        Component radio1 = new Component("input") {};
        radio1.getElement().setAttribute("type", "radio");
        radio1.getElement().setAttribute("name", "my-accordion");
        radio1.getElement().setAttribute("checked", "true");
        
        Div title1 = new Div("What is zeroz4j?");
        title1.addClassName("collapse-title text-xl font-medium");
        
        Div content1 = new Div("zeroz4j is a modern Java framework for building fast, single-page web applications using TeaVM and DaisyUI.");
        content1.addClassName("collapse-content");
        
        item1.add(radio1, title1, content1);

        Accordion item2 = new Accordion();
        Component radio2 = new Component("input") {};
        radio2.getElement().setAttribute("type", "radio");
        radio2.getElement().setAttribute("name", "my-accordion");
        
        Div title2 = new Div("Is it open source?");
        title2.addClassName("collapse-title text-xl font-medium");
        
        Div content2 = new Div("Yes, zeroz4j is fully open source and free to use for both commercial and non-commercial projects.");
        content2.addClassName("collapse-content");
        
        item2.add(radio2, title2, content2);

        Accordion item3 = new Accordion();
        Component radio3 = new Component("input") {};
        radio3.getElement().setAttribute("type", "radio");
        radio3.getElement().setAttribute("name", "my-accordion");
        
        Div title3 = new Div("How do I get started?");
        title3.addClassName("collapse-title text-xl font-medium");
        
        Div content3 = new Div("Check out the quickstart guide on GitHub and explore the components shown in this application.");
        content3.addClassName("collapse-content");
        
        item3.add(radio3, title3, content3);

        VerticalLayout accordionGroup = new VerticalLayout();
        accordionGroup.addClassName("w-full gap-2");
        accordionGroup.add(item1, item2, item3);

        addSection("Accordion Example", accordionGroup);
    }
}
