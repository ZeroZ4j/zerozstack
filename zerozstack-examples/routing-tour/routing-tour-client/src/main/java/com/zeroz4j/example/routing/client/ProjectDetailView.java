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
import com.zeroz4j.example.routing.model.Task;
import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.layout.Div;

import java.util.List;

/**
 * A parameterised route, and the clearest case for loading on the route rather than in the
 * component: this screen needs two things, and both are here.
 *
 * <p>Loading them inside the rendered component instead would mean the view mounts, shows nothing
 * useful, fires two calls, and re-renders twice. Here the view is only built once both have
 * arrived.</p>
 */
@Route(value = "/projects/:id", layout = AppShell.class)
public class ProjectDetailView implements RouteView<ProjectDetailView.Data> {

    /** What this route needs, in one object, so {@code render} cannot be reached without it. */
    public static final class Data {
        final Project project;
        final List<Task> tasks;

        Data(Project project, List<Task> tasks) {
            this.project = project;
            this.tasks = tasks;
        }
    }

    private final TourService service = new TourService_Stub();

    @Override
    public Data load(RouteParams params) {
        // getLong throws on a non-numeric id: /projects/banana is a broken link, not a project to
        // go looking for. The router turns that into its error handler rather than a blank screen.
        long id = params.getLong("id");
        return new Data(service.getProject(id), service.listTasks(id));
    }

    @Override
    public Component render(Data data, RouteParams params) {
        Div taskRows = Ui.box("flex flex-col gap-2");
        for (Task task : data.tasks) {
            taskRows.add(Ui.box("flex items-center gap-3 p-3 rounded bg-base-200",
                    Ui.text(task.isDone() ? "done" : "open",
                            "badge " + (task.isDone() ? "badge-success" : "badge-warning")),
                    Ui.routerLink("/projects/" + data.project.getId() + "/tasks/" + task.getId(),
                            task.getTitle())));
        }

        return Ui.box("flex flex-col gap-4 max-w-3xl",
                Ui.routerLink("/projects", "← All projects"),
                Ui.text(data.project.getName(), "text-2xl font-bold"),
                Ui.text(data.project.getSummary(), "opacity-80"),
                Ui.text("Tasks", "font-semibold pt-2"),
                taskRows);
    }
}
