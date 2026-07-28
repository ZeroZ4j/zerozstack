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

public class IndicatorShowcase extends ComponentShowcase {

    public IndicatorShowcase() {
        super();
        addTitle("Indicator");
        addDescription("Indicator is used to place elements (such as badges) on the corners of other elements.");

        // Showcase 1: Badge on Button
        Indicator ind1 = new Indicator();
        Badge badge1 = new Badge();
        badge1.setText("99+");
        badge1.addClassName("indicator-item");
        badge1.setThemeColor(ThemeColor.PRIMARY);
        Button btn1 = new Button("Inbox");
        ind1.add(badge1, btn1);

        // Showcase 2: Badge on Card / Box
        Indicator ind2 = new Indicator();
        Badge badge2 = new Badge();
        badge2.setText("New");
        badge2.addClassName("indicator-item");
        badge2.setThemeColor(ThemeColor.SECONDARY);
        
        Div box = new Div("Target Element");
        box.addClassName("grid");
        box.addClassName("w-32");
        box.addClassName("h-16");
        box.addClassName("bg-base-300");
        box.addClassName("place-items-center");
        box.addClassName("rounded");
        ind2.add(badge2, box);

        // Showcase 3: Bottom Start Position
        Indicator ind3 = new Indicator();
        Badge badge3 = new Badge();
        badge3.setText("Live");
        badge3.addClassName("indicator-item");
        badge3.addClassName("indicator-bottom");
        badge3.addClassName("indicator-start");
        badge3.setThemeColor(ThemeColor.SUCCESS);
        Button btn2 = new Button("Streaming");
        ind3.add(badge3, btn2);

        addSection("Badge on Button", ind1);
        addSection("Badge on Container", ind2);
        addSection("Custom Indicator Position", ind3);
    }
}
