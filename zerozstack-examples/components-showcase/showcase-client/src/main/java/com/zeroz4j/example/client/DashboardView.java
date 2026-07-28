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

import com.zeroz4j.example.api.UserService;
import com.zeroz4j.example.api.UserService_Stub;
import com.zeroz4j.example.model.UserInfo;
import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.component.Stat;
import com.zeroz4j.ui.layout.HorizontalLayout;
import com.zeroz4j.ui.layout.VerticalLayout;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.html.HTMLElement;

public class DashboardView extends VerticalLayout {

    private final UserService userService;
    private final Stat nameStat;
    private final Stat scoreStat;
    private final Stat statusStat;

    public DashboardView() {
        super();
        userService = new UserService_Stub();
        
        addClassName("card");
        addClassName("bg-base-100");
        addClassName("shadow-xl");
        addClassName("p-6");

        Component title = new Component("h2") {};
        title.getElement().setClassName("card-title");
        title.getElement().setInnerHTML("Dashboard");
        add(title);

        HorizontalLayout statsLayout = new HorizontalLayout();
        statsLayout.addClassName("stats");
        statsLayout.addClassName("stats-vertical");
        statsLayout.addClassName("lg:stats-horizontal");
        statsLayout.addClassName("shadow");
        statsLayout.addClassName("mt-4");

        nameStat = createStat("Username", "Loading...", "text-primary");
        statusStat = createStat("System Status", "Checking...", "text-success");
        scoreStat = createStat("Score", "0", "text-secondary");

        statsLayout.add(nameStat, statusStat, scoreStat);
        add(statsLayout);

        loadUserInfo();
    }

    private Stat createStat(String titleText, String valueText, String valueClass) {
        Stat stat = new Stat();
        Component title = new Component("div") {};
        title.getElement().setClassName("stat-title uppercase font-bold");
        title.getElement().setInnerHTML(titleText);
        
        Component value = new Component("div") {};
        value.getElement().setClassName("stat-value " + valueClass);
        value.getElement().setInnerHTML(valueText);
        
        stat.getElement().appendChild(title.getElement());
        stat.getElement().appendChild(value.getElement());
        return stat;
    }

    private void loadUserInfo() {
        try {
            UserInfo info = userService.getUserInfo("anonymous");
            updateStatValue(nameStat, info.getName());
            updateStatValue(scoreStat, String.valueOf(info.getScore()));
            updateStatValue(statusStat, info.isActive() ? "Online" : "Offline");
        } catch (Exception e) {
            System.err.println("[zeroz4j] Dashboard error: " + e.getMessage());
            updateStatValue(nameStat, "Error");
            updateStatValue(statusStat, "Error");
        }
    }
    
    private void updateStatValue(Stat stat, String newValue) {
        HTMLElement child = stat.getElement().getChildNodes().get(1).cast();
        child.setInnerHTML(newValue);
    }
}

