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

public class JoinShowcase extends ComponentShowcase {

    public JoinShowcase() {
        super();
        addTitle("Join");
        addDescription("Join is a container to group buttons, inputs, or other components together with merged borders and rounded corners.");

        // Showcase 1: Joined Buttons
        Join joinedButtons = new Join();
        Button btn1 = new Button("Button 1");
        btn1.addClassName("join-item");
        Button btn2 = new Button("Button 2");
        btn2.addClassName("join-item");
        Button btn3 = new Button("Button 3");
        btn3.addClassName("join-item");
        joinedButtons.add(btn1, btn2, btn3);

        addSection("Joined Buttons", joinedButtons);

        // Showcase 2: Joined Input & Button
        Join joinedInputButton = new Join();
        TextField input = new TextField("Search...");
        input.addClassName("join-item");
        Button searchBtn = new Button("Search");
        searchBtn.addClassName("join-item");
        searchBtn.setThemeColor(ThemeColor.PRIMARY);
        joinedInputButton.add(input, searchBtn);

        addSection("Joined Input and Button", joinedInputButton);

        // Showcase 3: Vertical Join
        Join verticalJoin = new Join();
        verticalJoin.addClassName("join-vertical");
        
        Button v1 = new Button("Top Button");
        v1.addClassName("join-item");
        Button v2 = new Button("Middle Button");
        v2.addClassName("join-item");
        Button v3 = new Button("Bottom Button");
        v3.addClassName("join-item");
        
        verticalJoin.add(v1, v2, v3);

        addSection("Vertical Join", verticalJoin);
    }
}
