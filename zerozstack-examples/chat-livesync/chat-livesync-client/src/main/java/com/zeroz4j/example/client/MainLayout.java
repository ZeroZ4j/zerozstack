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

import com.zeroz4j.example.api.AppText_Text;
import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.HorizontalLayout;
import com.zeroz4j.ui.layout.Span;
import com.zeroz4j.ui.layout.VerticalLayout;
import com.zeroz4j.signals.Effect;
import com.zeroz4j.signals.ValueSignal;
import com.zeroz4j.ui.component.LanguageSelector;
import com.zeroz4j.ui.component.Menu;
import com.zeroz4j.ui.component.ThemeController;
import org.teavm.jso.browser.Window;

/**
 * Main application layout.
 */
public class MainLayout extends HorizontalLayout {

    private final Component chatView = new ChatView();

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
        
        menu.addTitle("zeroz chat livesync");

        // Theme Toggle
        Component themeItem = new Component("li") {};
        HorizontalLayout themeLayout = new HorizontalLayout();
        themeLayout.addClassName("px-4");
        themeLayout.addClassName("py-4");
        themeLayout.addClassName("mt-auto");
        themeLayout.addClassName("justify-between");
        
        Span themeLabel = new Span("");
        themeLabel.setId("dark-mode-label");
        // Read inside an effect, so it comes back in the new language when somebody switches.
        // Read at construction instead and it would sit there in the old language forever - the
        // mistake MessageReadContractTest exists to stop.
        Effect.create(() -> themeLabel.setText(AppText_Text.chatDarkMode().text()));
        themeLayout.add(themeLabel);
        
        ThemeController themeToggle = new ThemeController(true);
        ValueSignal<Boolean> darkThemeSignal = new ValueSignal<>(true);
        themeToggle.bindValue(darkThemeSignal);
        
        Effect.create(() -> {
            Boolean isDark = darkThemeSignal.get();
            String theme = (isDark != null && isDark) ? "dark" : "light";
            Window.current().getDocument().getBody().setAttribute("data-theme", theme);
        });
        
        themeLayout.add(themeToggle);
        themeItem.getElement().appendChild(themeLayout.getElement());
        menu.add(themeItem);

        // The language picker. It offers exactly the languages this deployment has words for -
        // the list arrived with the words when the connection opened - and it binds itself to the
        // language, so this is the whole of adding one.
        Component languageItem = new Component("li") {};
        VerticalLayout languageLayout = new VerticalLayout();
        languageLayout.addClassName("px-4");
        languageLayout.addClassName("pb-4");
        languageLayout.addClassName("gap-2");
        languageLayout.add(new LanguageSelector());

        Span switchHint = new Span("");
        switchHint.setId("switch-hint");
        switchHint.addClassName("text-xs");
        switchHint.addClassName("opacity-70");
        Effect.create(() -> switchHint.setText(AppText_Text.chatSwitchHint().text()));
        languageLayout.add(switchHint);

        languageItem.getElement().appendChild(languageLayout.getElement());
        menu.add(languageItem);

        sidebar.add(menu);

        add(sidebar);

        // --- Content Area ---
        Div contentArea = new Div();
        contentArea.addClassName("flex-1");
        contentArea.addClassName("p-8");
        contentArea.addClassName("overflow-y-auto");

        contentArea.add(chatView);

        add(contentArea);
    }
}
