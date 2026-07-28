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
import com.zeroz4j.signals.*;

public class StackShowcase extends ComponentShowcase {

    public StackShowcase() {
        addTitle("Stack");
        addDescription("Stack visually stacks elements on top of each other.");

        Stack stack = new Stack();
        
        Div card1 = new Div();
        card1.addClassName("grid");
        card1.addClassName("w-32");
        card1.addClassName("h-20");
        card1.addClassName("rounded");
        card1.addClassName("bg-primary");
        card1.addClassName("text-primary-content");
        card1.addClassName("place-content-center");
        card1.getElement().setTextContent("Card 1");

        Div card2 = new Div();
        card2.addClassName("grid");
        card2.addClassName("w-32");
        card2.addClassName("h-20");
        card2.addClassName("rounded");
        card2.addClassName("bg-accent");
        card2.addClassName("text-accent-content");
        card2.addClassName("place-content-center");
        card2.getElement().setTextContent("Card 2");

        Div card3 = new Div();
        card3.addClassName("grid");
        card3.addClassName("w-32");
        card3.addClassName("h-20");
        card3.addClassName("rounded");
        card3.addClassName("bg-secondary");
        card3.addClassName("text-secondary-content");
        card3.addClassName("place-content-center");
        card3.getElement().setTextContent("Card 3");

        stack.add(card1, card2, card3);
        addSection("Stacked Cards", stack);
    }
}
