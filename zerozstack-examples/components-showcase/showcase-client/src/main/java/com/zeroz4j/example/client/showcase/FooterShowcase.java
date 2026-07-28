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

public class FooterShowcase extends ComponentShowcase {

    public FooterShowcase() {
        super();
        addTitle("Footer");
        addDescription("Footer is a container for links, copyright notices, and website maps at the bottom of pages.");

        // Section 1: Standard Footer
        Footer standardFooter = new Footer();
        standardFooter.addClassName("p-10");
        standardFooter.addClassName("bg-neutral");
        standardFooter.addClassName("text-neutral-content");
        standardFooter.addClassName("rounded-box");

        // Column 1
        Div col1 = new Div();
        col1.addClassName("flex");
        col1.addClassName("flex-col");
        col1.addClassName("gap-2");
        
        Div header1 = new Div("Services");
        header1.addClassName("footer-title");
        
        Link link1 = new Link();
        link1.setText("Branding");
        link1.addClassName("link-hover");
        
        Link link2 = new Link();
        link2.setText("Design");
        link2.addClassName("link-hover");
        
        col1.add(header1, link1, link2);

        // Column 2
        Div col2 = new Div();
        col2.addClassName("flex");
        col2.addClassName("flex-col");
        col2.addClassName("gap-2");
        
        Div header2 = new Div("Company");
        header2.addClassName("footer-title");
        
        Link link3 = new Link();
        link3.setText("About us");
        link3.addClassName("link-hover");
        
        Link link4 = new Link();
        link4.setText("Contact");
        link4.addClassName("link-hover");
        
        col2.add(header2, link3, link4);

        // Column 3
        Div col3 = new Div();
        col3.addClassName("flex");
        col3.addClassName("flex-col");
        col3.addClassName("gap-2");
        
        Div header3 = new Div("Legal");
        header3.addClassName("footer-title");
        
        Link link5 = new Link();
        link5.setText("Terms of use");
        link5.addClassName("link-hover");
        
        Link link6 = new Link();
        link6.setText("Privacy policy");
        link6.addClassName("link-hover");
        
        col3.add(header3, link5, link6);

        standardFooter.add(col1, col2, col3);

        addSection("Standard Footer with Columns", standardFooter);
    }
}
