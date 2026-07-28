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

public class HeroShowcase extends ComponentShowcase {

    public HeroShowcase() {
        super();
        addTitle("Hero");
        addDescription("Hero is a large banner component used to grab user attention, typically shown at the top of a page.");

        // Hero 1: Text only centered
        Hero textHero = new Hero();
        textHero.addClassName("bg-base-200");
        textHero.addClassName("p-8");
        textHero.addClassName("rounded-box");

        Div content = new Div();
        content.addClassName("hero-content");
        content.addClassName("text-center");

        Div maxW = new Div();
        maxW.addClassName("max-w-md");

        Div title = new Div("Welcome to zeroz4j");
        title.addClassName("text-4xl");
        title.addClassName("font-bold");

        Span p = new Span("Build stunning, reactive web applications using Java and modern TailwindCSS/DaisyUI utilities.");
        p.addClassName("py-6");
        p.addClassName("block");

        Button actionBtn = new Button("Get Started");
        actionBtn.setThemeColor(ThemeColor.PRIMARY);

        maxW.add(title, p, actionBtn);
        content.add(maxW);
        textHero.add(content);

        addSection("Centered Text Hero", textHero);
    }
}
