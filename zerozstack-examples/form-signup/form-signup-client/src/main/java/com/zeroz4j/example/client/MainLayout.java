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
package com.zeroz4j.example.client;

import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.component.Menu;
import com.zeroz4j.ui.component.ThemeController;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.HorizontalLayout;
import com.zeroz4j.ui.layout.Span;
import com.zeroz4j.ui.layout.VerticalLayout;
import com.zeroz4j.signals.Effect;
import com.zeroz4j.signals.ValueSignal;
import org.teavm.jso.browser.Window;

public class MainLayout extends HorizontalLayout {

    private final Component signupView = new SignupView();

    public MainLayout() {
        super();
        addClassName("h-screen");
        addClassName("w-screen");
        addClassName("bg-base-100");
        addClassName("text-base-content");
        addClassName("flex");

        // --- Sidebar ---
        VerticalLayout sidebar = new VerticalLayout();
        sidebar.addClassName("w-64");
        sidebar.addClassName("bg-base-200");
        sidebar.addClassName("h-full");
        sidebar.addClassName("p-0");
        sidebar.addClassName("flex-shrink-0");
        sidebar.addClassName("overflow-y-auto");
        sidebar.addClassName("overflow-x-hidden");

        Menu menu = new Menu();
        menu.addClassName("h-full");
        menu.addClassName("w-full");
        menu.addClassName("rounded-none");
        menu.addClassName("flex-col");

        menu.addTitle("zeroz signup");

        // Theme Toggle
        Component themeItem = new Component("li") {};
        HorizontalLayout themeLayout = new HorizontalLayout();
        themeLayout.addClassName("px-4");
        themeLayout.addClassName("py-4");
        themeLayout.addClassName("mt-auto");
        themeLayout.addClassName("justify-between");

        Span themeLabel = new Span("Dark Mode");
        themeLayout.add(themeLabel);

        ThemeController themeToggle = new ThemeController(true);
        ValueSignal<Boolean> darkTheme = new ValueSignal<>(true);
        themeToggle.bindValue(darkTheme);

        Effect.create(() -> {
            Boolean isDark = darkTheme.get();
            Window.current().getDocument().getBody()
                    .setAttribute("data-theme", isDark != null && isDark ? "dark" : "light");
        });

        themeLayout.add(themeToggle);
        themeItem.getElement().appendChild(themeLayout.getElement());
        menu.add(themeItem);

        sidebar.add(menu);
        add(sidebar);

        // --- Content Area ---
        Div contentArea = new Div();
        contentArea.addClassName("flex-1");
        contentArea.addClassName("p-8");
        contentArea.addClassName("overflow-y-auto");

        contentArea.add(signupView);
        add(contentArea);
    }
}
