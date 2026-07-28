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

public class DividerShowcase extends ComponentShowcase {

    public DividerShowcase() {
        super();
        addTitle("Divider");
        addDescription("Divider is used to separate content vertically or horizontally.");

        // Section 1: Default Text Divider
        Divider defaultDivider = new Divider();
        defaultDivider.setText("OR");
        
        Div container1 = new Div();
        container1.addClassName("flex");
        container1.addClassName("flex-col");
        container1.addClassName("w-full");
        container1.addClassName("border-opacity-50");
        
        Div content1 = new Div("content top");
        content1.addClassName("grid");
        content1.addClassName("h-20");
        content1.addClassName("card");
        content1.addClassName("bg-base-300");
        content1.addClassName("rounded-box");
        content1.addClassName("place-items-center");
        
        Div content2 = new Div("content bottom");
        content2.addClassName("grid");
        content2.addClassName("h-20");
        content2.addClassName("card");
        content2.addClassName("bg-base-300");
        content2.addClassName("rounded-box");
        content2.addClassName("place-items-center");
        
        container1.add(content1, defaultDivider, content2);

        addSection("Vertical Divider (Default)", container1);

        // Section 2: Horizontal Divider
        Divider horizontalDivider = new Divider();
        horizontalDivider.addClassName("divider-horizontal");
        horizontalDivider.setText("OR");

        Div container2 = new Div();
        container2.addClassName("flex");
        container2.addClassName("flex-row");
        container2.addClassName("w-full");
        container2.addClassName("h-32");
        
        Div leftContent = new Div("left");
        leftContent.addClassName("grid");
        leftContent.addClassName("flex-grow");
        leftContent.addClassName("card");
        leftContent.addClassName("bg-base-300");
        leftContent.addClassName("rounded-box");
        leftContent.addClassName("place-items-center");
        
        Div rightContent = new Div("right");
        rightContent.addClassName("grid");
        rightContent.addClassName("flex-grow");
        rightContent.addClassName("card");
        rightContent.addClassName("bg-base-300");
        rightContent.addClassName("rounded-box");
        rightContent.addClassName("place-items-center");
        
        container2.add(leftContent, horizontalDivider, rightContent);

        addSection("Horizontal Divider", container2);

        // Section 3: No Text Divider
        Divider noTextDivider = new Divider();
        
        Div container3 = new Div();
        container3.addClassName("flex");
        container3.addClassName("flex-col");
        container3.addClassName("w-full");
        
        Div item1 = new Div("Item 1");
        Div item2 = new Div("Item 2");
        container3.add(item1, noTextDivider, item2);
        
        addSection("Divider Without Text", container3);
    }
}
