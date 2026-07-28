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

public class StatShowcase extends ComponentShowcase {

    public StatShowcase() {
        addTitle("Stat");
        addDescription("Stat is used to show statistics or numbers.");

        // Container with "stats shadow" class to wrap individual stat components
        Div statsContainer = new Div();
        statsContainer.addClassName("stats");
        statsContainer.addClassName("shadow");
        statsContainer.addClassName("bg-base-100");

        // Stat 1: Downloads
        Stat stat1 = new Stat();
        
        Div title1 = new Div();
        title1.addClassName("stat-title");
        title1.getElement().setTextContent("Downloads");
        
        Div value1 = new Div();
        value1.addClassName("stat-value");
        value1.getElement().setTextContent("31K");
        
        Div desc1 = new Div();
        desc1.addClassName("stat-desc");
        desc1.getElement().setTextContent("Jan 1st - Feb 1st");
        
        stat1.add(title1, value1, desc1);

        // Stat 2: New Users
        Stat stat2 = new Stat();
        
        Div title2 = new Div();
        title2.addClassName("stat-title");
        title2.getElement().setTextContent("New Users");
        
        Div value2 = new Div();
        value2.addClassName("stat-value");
        value2.addClassName("text-primary");
        value2.getElement().setTextContent("4,200");
        
        Div desc2 = new Div();
        desc2.addClassName("stat-desc");
        desc2.getElement().setTextContent("Ã¢â€ â€”Ã¯Â¸Å½ 400 (22%)");
        
        stat2.add(title2, value2, desc2);

        statsContainer.add(stat1, stat2);

        addSection("Stats Dashboard Group", statsContainer);
    }
}
