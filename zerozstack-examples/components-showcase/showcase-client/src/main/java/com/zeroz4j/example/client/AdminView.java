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
import com.zeroz4j.ui.component.Button;
import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.layout.FlexComponent.Alignment;
import com.zeroz4j.ui.layout.FlexComponent.JustifyContentMode;
import com.zeroz4j.ui.layout.HorizontalLayout;
import com.zeroz4j.ui.layout.VerticalLayout;
import org.teavm.jso.browser.Window;

public class AdminView extends VerticalLayout {

    private final UserService userService;


    public AdminView() {
        super();
        userService = new UserService_Stub();


        addClassName("card");
        addClassName("bg-base-100");
        addClassName("shadow-xl");
        addClassName("p-6");

        Component title = new Component("h2") {};
        title.getElement().setClassName("card-title text-error mb-4");
        title.getElement().setInnerHTML("Administration Panel");
        add(title);

        Component alert = new Component("div") {};
        alert.getElement().setClassName("alert alert-warning mb-6");
        alert.getElement().setInnerHTML("<span>Restricted area. Changes made here affect the entire system.</span>");
        add(alert);

        VerticalLayout actions = new VerticalLayout();
        actions.addClassName("space-y-4");

        actions.add(createActionRow("System Maintenance", "Enable maintenance mode to prevent user logins.", "Enable", "btn-warning", this::runDiagnostics));


        add(actions);
    }

    private HorizontalLayout createActionRow(String titleStr, String descStr, String btnText, String btnClass, Runnable action) {
        HorizontalLayout row = new HorizontalLayout();
        row.addClassName("bg-base-200");
        row.addClassName("rounded-box");
        row.addClassName("p-4");
        row.setAlignItems(Alignment.CENTER);
        row.setJustifyContentMode(JustifyContentMode.BETWEEN);

        VerticalLayout textLayout = new VerticalLayout();
        Component title = new Component("h4") {};
        title.getElement().setClassName("font-medium");
        title.getElement().setInnerHTML(titleStr);
        Component desc = new Component("p") {};
        desc.getElement().setClassName("text-sm opacity-70");
        desc.getElement().setInnerHTML(descStr);
        textLayout.add(title, desc);

        Button btn = new Button(btnText);
        btn.addClassName(btnClass);
        btn.addClickListener(e -> action.run());

        row.add(textLayout, btn);
        return row;
    }

    private void runDiagnostics() {
        try {
            String res = userService.systemDiagnostics("full", 5);
            Window.current().alert("Diagnostics: " + res);
        } catch (Exception ex) {
            Window.current().alert("Error: " + ex.getMessage());
        }
    }


}
