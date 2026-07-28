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

public class DrawerShowcase extends ComponentShowcase {

    private static class Label extends Component implements HasStyle, HasComponents, HasText {
        public Label() {
            super("label");
        }
        @Override
        public Component getComponent() {
            return this;
        }
    }

    public DrawerShowcase() {
        super();
        addTitle("Drawer");
        addDescription("Drawer is a grid layout that can show/hide a sidebar menu.");

        // Drawer Container
        Drawer drawer = new Drawer();
        drawer.addClassName("h-72");
        drawer.addClassName("border");
        drawer.addClassName("border-base-300");
        drawer.addClassName("rounded-box");
        drawer.addClassName("overflow-hidden");

        // Toggle Checkbox
        Component toggle = new Component("input") {};
        toggle.getElement().setAttribute("type", "checkbox");
        toggle.getElement().setClassName("drawer-toggle");
        toggle.getElement().setId("my-drawer-showcase");

        // Drawer Content
        Div drawerContent = new Div();
        drawerContent.addClassName("drawer-content");
        drawerContent.addClassName("flex");
        drawerContent.addClassName("flex-col");
        drawerContent.addClassName("items-center");
        drawerContent.addClassName("justify-center");

        Label openButton = new Label();
        openButton.addClassName("btn");
        openButton.addClassName("btn-primary");
        openButton.addClassName("drawer-button");
        openButton.getElement().setAttribute("for", "my-drawer-showcase");
        openButton.setText("Open drawer");

        drawerContent.add(new Span("Main Content Area"), openButton);

        // Drawer Side
        Div drawerSide = new Div();
        drawerSide.addClassName("drawer-side");
        drawerSide.addClassName("z-[2]");

        Label overlay = new Label();
        overlay.addClassName("drawer-overlay");
        overlay.getElement().setAttribute("for", "my-drawer-showcase");

        Div menu = new Div();
        menu.addClassName("menu");
        menu.addClassName("p-4");
        menu.addClassName("w-60");
        menu.addClassName("min-h-full");
        menu.addClassName("bg-base-200");
        menu.addClassName("text-base-content");
        menu.addClassName("gap-2");

        Div title = new Div("Navigation");
        title.addClassName("font-bold");
        title.addClassName("mb-2");
        
        Div item1 = new Div("Dashboard");
        item1.addClassName("p-2");
        item1.addClassName("hover:bg-base-300");
        item1.addClassName("rounded");
        item1.addClassName("cursor-pointer");

        Div item2 = new Div("Settings");
        item2.addClassName("p-2");
        item2.addClassName("hover:bg-base-300");
        item2.addClassName("rounded");
        item2.addClassName("cursor-pointer");

        menu.add(title, item1, item2);
        drawerSide.add(overlay, menu);

        // Assemble Drawer
        drawer.add(toggle, drawerContent, drawerSide);

        addSection("Default Drawer Layout", drawer);
    }
}
