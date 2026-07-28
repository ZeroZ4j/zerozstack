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

public class PhoneMockupShowcase extends ComponentShowcase {

    public PhoneMockupShowcase() {
        super();
        addTitle("PhoneMockup");
        addDescription("PhoneMockup displays a phone device mockup frame containing arbitrary child components.");

        // Phone Mockup instance
        PhoneMockup phone = new PhoneMockup();
        phone.addClassName("border-base-300");

        // DaisyUI requirements for mockup-phone:
        // 1. A child with class 'camera'
        Div camera = new Div();
        camera.addClassName("camera");

        // 2. A child with class 'display'
        Div display = new Div();
        display.addClassName("display");

        // Artboard / Screen Content
        Div artboard = new Div();
        artboard.addClassName("artboard");
        artboard.addClassName("artboard-demo");
        artboard.addClassName("phone-1");
        artboard.addClassName("bg-base-200");
        artboard.addClassName("flex");
        artboard.addClassName("flex-col");
        artboard.addClassName("justify-center");
        artboard.addClassName("items-center");
        artboard.addClassName("gap-4");

        Span text = new Span("Hello from zeroz4j!");
        text.addClassName("text-lg font-semibold");

        Button action = new Button("Press Me");
        action.setThemeColor(ThemeColor.PRIMARY);

        artboard.add(text, action);
        display.add(artboard);

        phone.add(camera, display);

        addSection("Interactive Phone Mockup", phone);
    }
}
