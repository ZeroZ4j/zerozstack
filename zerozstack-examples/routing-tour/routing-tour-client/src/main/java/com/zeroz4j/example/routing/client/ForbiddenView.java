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

import com.zeroz4j.api.RmiSecurityContext;
import com.zeroz4j.api.Route;
import com.zeroz4j.client.router.RouteParams;
import com.zeroz4j.client.router.RouteView;
import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.theme.TextStyle;

/** Where a role-guarded navigation lands when the user does not hold the role. */
@Route(value = "/forbidden", layout = AppShell.class, label = "Forbidden", order = 98)
public class ForbiddenView implements RouteView<Void> {

    @Override
    public Component render(Void data, RouteParams params) {
        return Ui.box("flex flex-col gap-3 max-w-2xl",
                Ui.text("Not for this account", TextStyle.PAGE_TITLE.getClassNames()),
                Ui.text("That route needs a role this connection does not hold. Sign in as 'admin' "
                        + "to reach it — the dev login grants 'admin' to that user only.",
                        TextStyle.SECONDARY.getClassNames()),
                Ui.text("Signed in as " + RmiSecurityContext.getUsername()
                        + " with roles " + RmiSecurityContext.getRoles(), TextStyle.SECONDARY.getClassNames()),
                Ui.routerLink("/", "Back to the tour"));
    }
}
