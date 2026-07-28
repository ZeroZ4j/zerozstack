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

public class WindowMockupShowcase extends ComponentShowcase {

    public WindowMockupShowcase() {
        addTitle("WindowMockup");
        addDescription("WindowMockup wraps elements to make them look like an operating system window.");

        WindowMockup window = new WindowMockup();
        window.addClassName("border");
        window.addClassName("border-base-300");

        Div body = new Div();
        body.addClassName("flex");
        body.addClassName("justify-center");
        body.addClassName("px-4");
        body.addClassName("py-16");
        body.addClassName("bg-base-200");
        body.getElement().setTextContent("Hello World! This is inside a mockup window.");

        window.add(body);

        addSection("Basic Window Mockup", window);
    }
}
