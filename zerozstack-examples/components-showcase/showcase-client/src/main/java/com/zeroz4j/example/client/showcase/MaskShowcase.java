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

public class MaskShowcase extends ComponentShowcase {

    public MaskShowcase() {
        super();
        addTitle("Mask");
        addDescription("Mask crops an element to a specific shape, like a circle, squircle, heart, or polygon.");

        // Shape 1: Squircle
        Mask squircle = new Mask();
        squircle.addClassName("mask-squircle");
        Div box1 = new Div();
        box1.addClassName("w-24");
        box1.addClassName("h-24");
        box1.addClassName("bg-primary");
        squircle.add(box1);

        // Shape 2: Heart
        Mask heart = new Mask();
        heart.addClassName("mask-heart");
        Div box2 = new Div();
        box2.addClassName("w-24");
        box2.addClassName("h-24");
        box2.addClassName("bg-secondary");
        heart.add(box2);

        // Shape 3: Hexagon
        Mask hexagon = new Mask();
        hexagon.addClassName("mask-hexagon");
        Div box3 = new Div();
        box3.addClassName("w-24");
        box3.addClassName("h-24");
        box3.addClassName("bg-accent");
        hexagon.add(box3);

        // Shape 4: Diamond
        Mask diamond = new Mask();
        diamond.addClassName("mask-diamond");
        Div box4 = new Div();
        box4.addClassName("w-24");
        box4.addClassName("h-24");
        box4.addClassName("bg-neutral");
        diamond.add(box4);

        // Shape 5: Decagon
        Mask decagon = new Mask();
        decagon.addClassName("mask-decagon");
        Div box5 = new Div();
        box5.addClassName("w-24");
        box5.addClassName("h-24");
        box5.addClassName("bg-info");
        decagon.add(box5);

        // Shape 6: Circle
        Mask circle = new Mask();
        circle.addClassName("mask-circle");
        Div box6 = new Div();
        box6.addClassName("w-24");
        box6.addClassName("h-24");
        box6.addClassName("bg-success");
        circle.add(box6);

        addSection("Squircle & Heart & Hexagon", squircle, heart, hexagon);
        addSection("Diamond & Decagon & Circle", diamond, decagon, circle);
    }
}
