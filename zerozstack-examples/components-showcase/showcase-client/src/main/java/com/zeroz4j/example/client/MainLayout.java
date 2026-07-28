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

import com.zeroz4j.api.RmiSecurityContext;
import com.zeroz4j.ui.component.Button;
import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.HorizontalLayout;
import com.zeroz4j.ui.layout.Span;
import com.zeroz4j.ui.layout.VerticalLayout;
import com.zeroz4j.signals.Effect;
import com.zeroz4j.signals.ValueSignal;
import com.zeroz4j.ui.layout.FlavourWrapper;
import com.zeroz4j.ui.component.Menu;
import com.zeroz4j.example.client.showcase.ShowcaseRegistry;
import com.zeroz4j.ui.component.ThemeController;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.teavm.jso.browser.Window;

public class MainLayout extends HorizontalLayout {

    public enum ViewType {
        DASHBOARD, SHOWCASE, ADMIN, PROFILE, HTML_EXAMPLE, UI_COMPONENTS
    }

    private final ValueSignal<ViewType> currentViewSignal = new ValueSignal<>(ViewType.DASHBOARD);
    private final ValueSignal<String> currentComponentSignal = new ValueSignal<>("btn");

    // Cache views
    private final Component dashboardView = new DashboardView();

    private final Component showcaseView = new ShowcaseView();
    private final Component adminView = new AdminView();
    private final Component profileView = new ProfileView();
    private final Component htmlExampleView = new FlavourWrapper(new HtmlExampleView());

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
        sidebar.addClassName("p-0"); // Menu will handle padding
        sidebar.addClassName("flex-shrink-0");
        sidebar.addClassName("overflow-y-auto");
        sidebar.addClassName("overflow-x-hidden");

        Menu menu = new Menu();
        menu.addClassName("h-full");
        menu.addClassName("w-full");
        menu.addClassName("rounded-none");
        menu.addClassName("flex-col");
        
        menu.addTitle("zeroz");

        menu.addItem("Dashboard", e -> currentViewSignal.set(ViewType.DASHBOARD));

        menu.addItem("Component Gallery", e -> currentViewSignal.set(ViewType.SHOWCASE));
        menu.addItem("Profile (Data Binding)", e -> currentViewSignal.set(ViewType.PROFILE));
        menu.addItem("HTML Template", e -> currentViewSignal.set(ViewType.HTML_EXAMPLE));

        Menu uiComponentsSubMenu = new Menu();
        uiComponentsSubMenu.addClassName("p-0");
        uiComponentsSubMenu.addClassName("flex-col");
        uiComponentsSubMenu.setAccordion(true);

        List<String> actions = Arrays.asList("btn", "link", "swap", "theme-controller");
        List<String> dataInput = Arrays.asList("checkbox", "file-input", "radio", "range", "rating", "select", "textarea", "input", "toggle");
        List<String> dataDisplay = Arrays.asList("accordion", "alert", "avatar", "badge", "card", "carousel", "chat-bubble", "collapse", "countdown", "diff", "kbd", "loading", "progress", "radial-progress", "skeleton", "stat", "table", "timeline", "tooltip");
        List<String> navigation = Arrays.asList("btm-nav", "breadcrumbs", "navbar", "pagination", "steps", "tab");
        List<String> layout = Arrays.asList("artboard", "divider", "drawer", "footer", "hero", "indicator", "join", "stack");
        List<String> mockup = Arrays.asList("mockup-browser", "mockup-code", "mockup-phone", "mockup-window");
        List<String> feedback = Arrays.asList("dialog", "toast");

        uiComponentsSubMenu.addSubMenu("Actions", createCategoryMenu(actions, currentComponentSignal, currentViewSignal));
        uiComponentsSubMenu.addSubMenu("Data Input", createCategoryMenu(dataInput, currentComponentSignal, currentViewSignal));
        uiComponentsSubMenu.addSubMenu("Data Display", createCategoryMenu(dataDisplay, currentComponentSignal, currentViewSignal));
        uiComponentsSubMenu.addSubMenu("Navigation", createCategoryMenu(navigation, currentComponentSignal, currentViewSignal));
        uiComponentsSubMenu.addSubMenu("Layout", createCategoryMenu(layout, currentComponentSignal, currentViewSignal));
        uiComponentsSubMenu.addSubMenu("Feedback", createCategoryMenu(feedback, currentComponentSignal, currentViewSignal));
        uiComponentsSubMenu.addSubMenu("Mockups", createCategoryMenu(mockup, currentComponentSignal, currentViewSignal));

        menu.addSubMenu("UI Components", uiComponentsSubMenu);

        if (RmiSecurityContext.hasAnyRole("admin")) {
            menu.addItem("Admin", e -> currentViewSignal.set(ViewType.ADMIN));
        }

        // Theme Toggle
        Component themeItem = new Component("li") {};
        HorizontalLayout themeLayout = new HorizontalLayout();
        themeLayout.addClassName("px-4");
        themeLayout.addClassName("py-4");
        themeLayout.addClassName("mt-auto"); // push to bottom if possible
        themeLayout.addClassName("justify-between");
        
        Span themeLabel = new Span("Dark Mode");
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

        sidebar.add(menu);

        add(sidebar);

        // --- Content Area ---
        Div contentArea = new Div();
        contentArea.addClassName("flex-1");
        contentArea.addClassName("p-8");
        contentArea.addClassName("overflow-y-auto");

        // Reactively switch content based on currentViewSignal
        Effect.create(() -> {
            contentArea.getElement().setInnerHTML(""); // clear
            ViewType view = currentViewSignal.get();
            if (view == ViewType.DASHBOARD) {
                contentArea.add(dashboardView);

            } else if (view == ViewType.SHOWCASE) {
                contentArea.add(showcaseView);
            } else if (view == ViewType.ADMIN) {
                contentArea.add(adminView);
            } else if (view == ViewType.PROFILE) {
                contentArea.add(profileView);
            } else if (view == ViewType.HTML_EXAMPLE) {
                contentArea.add(htmlExampleView);
            } else if (view == ViewType.UI_COMPONENTS) {
                Component showcase = ShowcaseRegistry.createShowcase(currentComponentSignal.get());
                if (showcase != null) {
                    contentArea.add(showcase);
                }
            }
        });

        add(contentArea);
    }

    private Menu createCategoryMenu(List<String> componentIds, ValueSignal<String> currentComponentSignal, ValueSignal<ViewType> currentViewSignal) {
        Menu categoryMenu = new Menu();
        categoryMenu.addClassName("p-0");
        categoryMenu.addClassName("flex-col");
        Map<String, String> labels = ShowcaseRegistry.getComponentLabels();
        for (String id : componentIds) {
            String label = labels.get(id);
            if (label != null) {
                categoryMenu.addItem(label, e -> {
                    currentComponentSignal.set(id);
                    currentViewSignal.set(ViewType.UI_COMPONENTS);
                });
            }
        }
        return categoryMenu;
    }

}
