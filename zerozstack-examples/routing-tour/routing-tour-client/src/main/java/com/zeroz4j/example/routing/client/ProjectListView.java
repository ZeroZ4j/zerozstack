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
import com.zeroz4j.example.routing.api.TourService;
import com.zeroz4j.example.routing.api.TourService_Stub;
import com.zeroz4j.example.routing.model.Project;
import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.layout.Div;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A list route, and where query parameters belong: {@code /projects?sort=name}.
 *
 * <p>The sort is read with a fallback rather than throwing. A query parameter is a user-adjustable
 * option, so a typo in one should not fail the navigation — unlike a path parameter, which is part
 * of the address and means a broken link when it will not parse.</p>
 */
@Route(value = "/projects", layout = AppShell.class, label = "Projects", order = 2)
public class ProjectListView implements RouteView<List<Project>> {

    private final TourService service = new TourService_Stub();

    @Override
    public List<Project> load(RouteParams params) {
        List<Project> projects = new ArrayList<>(service.listProjects());
        if ("name".equals(params.query("sort", "id"))) {
            projects.sort(Comparator.comparing(Project::getName));
        }
        return projects;
    }

    @Override
    public Component render(List<Project> projects, RouteParams params) {
        Div rows = Ui.box("flex flex-col gap-2");
        for (Project project : projects) {
            rows.add(Ui.box("flex items-center gap-3 p-3 rounded bg-base-200",
                    Ui.routerLink("/projects/" + project.getId(), project.getName()),
                    Ui.text(project.getSummary(), "text-sm opacity-70 flex-1"),
                    Ui.text(project.getOpenTasks() + " open", "badge badge-ghost")));
        }

        return Ui.box("flex flex-col gap-4 max-w-3xl",
                Ui.text("Projects", "text-2xl font-bold"),
                Ui.box("flex gap-3 text-sm",
                        Ui.text("Sort:", "opacity-70"),
                        Ui.routerLink("/projects?sort=id", "by id"),
                        Ui.routerLink("/projects?sort=name", "by name"),
                        Ui.text("currently: " + params.query("sort", "id"), "opacity-70")),
                rows);
    }
}
