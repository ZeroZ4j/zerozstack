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
import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.theme.TextStyle;

/**
 * The landing view, and the simplest possible route: a path, no parameters, no data.
 *
 * <p>{@code RouteView<Void>} with no {@code load} override — a route that needs nothing fetches
 * nothing.</p>
 */
@Route(value = "/", layout = AppShell.class, label = "Home", order = 1)
public class HomeView implements RouteView<Void> {

    @Override
    public Component render(Void data, RouteParams params) {
        return Ui.box("flex flex-col gap-3 max-w-2xl",
                Ui.text("Routing tour", TextStyle.PAGE_TITLE.getClassNames()),
                Ui.text("Every route below declares the data it needs. The loader finishes before "
                        + "the view is built, so nothing here renders a spinner or refetches after "
                        + "mounting.", TextStyle.SECONDARY.getClassNames()),
                Ui.box("flex flex-col gap-1 pt-2",
                        Ui.text("Try these:", TextStyle.SECTION_TITLE.getClassNames()),
                        Ui.routerLink("/projects", "/projects — a list, loaded by the route"),
                        Ui.routerLink("/projects/1", "/projects/1 — a path parameter"),
                        Ui.routerLink("/projects/new", "/projects/new — a literal beating :id"),
                        Ui.routerLink("/projects/1/tasks/11", "/projects/1/tasks/11 — two parameters"),
                        Ui.routerLink("/projects?sort=name", "/projects?sort=name — a query parameter"),
                        Ui.routerLink("/admin", "/admin — guarded by a role"),
                        Ui.routerLink("/nowhere", "/nowhere — falls through to not-found")));
    }
}
