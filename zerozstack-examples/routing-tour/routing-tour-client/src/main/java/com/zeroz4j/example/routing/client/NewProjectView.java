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
package com.zeroz4j.example.routing.client;

import com.zeroz4j.api.Route;
import com.zeroz4j.client.router.RouteParams;
import com.zeroz4j.client.router.RouteView;
import com.zeroz4j.client.router.Router;
import com.zeroz4j.example.routing.api.TourService;
import com.zeroz4j.example.routing.api.TourService_Stub;
import com.zeroz4j.ui.component.Button;
import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.component.TextField;

/**
 * The route that proves specificity: {@code /projects/new} is a literal, and
 * {@code /projects/:id} is a parameter that would also match "new".
 *
 * <p>The more specific pattern wins regardless of which class the compiler emitted first, so this
 * view is never reached as "the project whose id is new".</p>
 */
@Route(value = "/projects/new", layout = AppShell.class, label = "New project", order = 3)
public class NewProjectView implements RouteView<Void> {

    private final TourService service = new TourService_Stub();

    @Override
    public Component render(Void data, RouteParams params) {
        TextField name = new TextField("Project name");
        name.setValue("Untitled project");

        Button create = new Button("Create");
        create.addClickListener(event -> {
            long id = service.createProject(name.getValue());
            // replace(), not navigate(): pressing Back should return to wherever the user came
            // from, not to a form that has already been submitted.
            Router.replace("/projects/" + id);
        });

        return Ui.box("flex flex-col gap-4 max-w-md",
                Ui.text("New project", "text-2xl font-bold"),
                Ui.text("/projects/new is a literal path. /projects/:id would match it too, and "
                        + "does not, because the more specific route wins.", "text-sm opacity-70"),
                name,
                create);
    }
}
