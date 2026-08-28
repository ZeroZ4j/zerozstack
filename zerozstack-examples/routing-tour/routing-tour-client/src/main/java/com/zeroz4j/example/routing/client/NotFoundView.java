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

/** Where an unmatched path lands, registered with {@code Router.notFoundRoute}. */
@Route(value = "/not-found", layout = AppShell.class, label = "Not found", order = 99)
public class NotFoundView implements RouteView<Void> {

    @Override
    public Component render(Void data, RouteParams params) {
        return Ui.box("flex flex-col gap-3 max-w-2xl",
                Ui.text("No such page", TextStyle.PAGE_TITLE.getClassNames()),
                Ui.text("Nothing claims that path. The router replaced the history entry rather "
                        + "than pushing one, so Back returns where you actually came from.",
                        TextStyle.SECONDARY.getClassNames()),
                Ui.routerLink("/", "Back to the tour"));
    }
}
