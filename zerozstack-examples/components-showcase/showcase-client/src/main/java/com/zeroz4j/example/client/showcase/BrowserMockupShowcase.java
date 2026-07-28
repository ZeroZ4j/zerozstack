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

public class BrowserMockupShowcase extends ComponentShowcase {

    public BrowserMockupShowcase() {
        addTitle("Browser Mockup");
        addDescription("Browser Mockup wraps content inside a mock browser window frame.");

        BrowserMockup mockup = new BrowserMockup();
        mockup.addClassName("border border-base-300 w-full");

        Div toolbar = new Div();
        toolbar.addClassName("mockup-browser-toolbar");
        
        Div urlInput = new Div("https://zeroz4j.com");
        urlInput.addClassName("input border border-base-300");
        toolbar.add(urlInput);

        Div body = new Div("Welcome to zeroz4j UI components browser preview!");
        body.addClassName("flex justify-center px-4 py-16 bg-base-200 text-base-content border-t border-base-300");

        mockup.add(toolbar, body);

        addSection("Browser Mockup Example", mockup);
    }
}
