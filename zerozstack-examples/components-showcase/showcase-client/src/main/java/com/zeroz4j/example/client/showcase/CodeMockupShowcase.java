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

public class CodeMockupShowcase extends ComponentShowcase {

    public CodeMockupShowcase() {
        addTitle("Code Mockup");
        addDescription("Code Mockup wraps code snippets inside a mock terminal window layout.");

        CodeMockup mockup = new CodeMockup();
        mockup.addClassName("w-full shadow-lg");

        Component line1 = new Component("pre") {};
        line1.getElement().setAttribute("data-prefix", "$");
        Component code1 = new Component("code") {};
        code1.getElement().setTextContent("npm install zeroz4j");
        line1.getElement().appendChild(code1.getElement());

        Component line2 = new Component("pre") {};
        line2.getElement().setAttribute("data-prefix", ">");
        line2.getElement().setClassName("text-warning");
        Component code2 = new Component("code") {};
        code2.getElement().setTextContent("fetching dependencies...");
        line2.getElement().appendChild(code2.getElement());

        Component line3 = new Component("pre") {};
        line3.getElement().setAttribute("data-prefix", ">");
        line3.getElement().setClassName("text-success");
        Component code3 = new Component("code") {};
        code3.getElement().setTextContent("Done! UI library initialized.");
        line3.getElement().appendChild(code3.getElement());

        mockup.add(line1, line2, line3);

        addSection("Code Mockup Example", mockup);
    }
}
