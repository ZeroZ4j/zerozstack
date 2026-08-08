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

import com.zeroz4j.api.RequiresRole;
import com.zeroz4j.api.RmiSecurityContext;
import com.zeroz4j.api.Route;
import com.zeroz4j.client.router.RouteParams;
import com.zeroz4j.client.router.RouteView;
import com.zeroz4j.ui.component.Component;

/**
 * A guarded route.
 *
 * <p>The dev login grants {@code admin} to the {@code admin} user only, so signing in as
 * {@code demo} sends this navigation to the forbidden route instead.</p>
 *
 * <p><b>The guard decides what to show, not what is allowed.</b> The server re-checks every call
 * against {@code @Secured} and {@code @RolesAllowed} — removing this annotation would let the user
 * reach a view whose calls then fail, not let them see anything they should not.</p>
 */
@Route(value = "/admin", layout = AppShell.class, label = "Admin", order = 9)
@RequiresRole("admin")
public class AdminView implements RouteView<Void> {

    @Override
    public Component render(Void data, RouteParams params) {
        return Ui.box("flex flex-col gap-3 max-w-2xl",
                Ui.text("Admin", "text-2xl font-bold"),
                Ui.text("Reached because this connection holds the 'admin' role.", "opacity-80"),
                Ui.text("Signed in as " + RmiSecurityContext.getUsername()
                        + " with roles " + RmiSecurityContext.getRoles(), "text-sm opacity-60"));
    }
}
