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
import com.zeroz4j.example.routing.model.Task;
import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.theme.TextStyle;

/**
 * Two path parameters in one pattern, both typed at the point of use.
 */
@Route(value = "/projects/:projectId/tasks/:taskId", layout = AppShell.class)
public class TaskDetailView implements RouteView<Task> {

    private final TourService service = new TourService_Stub();

    @Override
    public Task load(RouteParams params) {
        return service.getTask(params.getLong("taskId"));
    }

    @Override
    public Component render(Task task, RouteParams params) {
        return Ui.box("flex flex-col gap-4 max-w-2xl",
                Ui.routerLink("/projects/" + params.getLong("projectId"), "← Back to project"),
                Ui.text(task.getTitle(), TextStyle.PAGE_TITLE.getClassNames()),
                Ui.text(task.isDone() ? "Done" : "Open",
                        "badge " + (task.isDone() ? "badge-success" : "badge-warning") + " w-fit"),
                Ui.text(task.getDetail(), TextStyle.SECONDARY.getClassNames()),
                Ui.text("Path parameters: projectId=" + params.get("projectId")
                        + ", taskId=" + params.get("taskId"), TextStyle.SECONDARY.getClassNames() + " pt-4"));
    }
}
