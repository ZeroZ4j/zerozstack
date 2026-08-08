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
import com.zeroz4j.client.router.RouteLayout;
import com.zeroz4j.client.router.RouteParams;
import com.zeroz4j.example.routing.api.TourService;
import com.zeroz4j.example.routing.api.TourService_Stub;
import com.zeroz4j.example.routing.model.Account;
import com.zeroz4j.ui.component.Component;

/**
 * The chrome every view sits inside: a header naming the signed-in account, and the navigation.
 *
 * <p>Its {@code load} is the reason a layout has one. The account is needed by the header on every
 * screen; fetching it here means it is fetched once per navigation instead of by each view
 * underneath. The chain is rebuilt on each navigation, so this does re-run when moving between two
 * views inside the same shell — see the limits in {@code docs/ROUTING.md}.</p>
 */
@Route("/")
public class AppShell implements RouteLayout<Account> {

    private final TourService service = new TourService_Stub();

    @Override
    public Account load(RouteParams params) {
        return service.getAccount();
    }

    @Override
    public Component render(Account account, RouteParams params, Component child) {
        Component header = Ui.box("flex items-center gap-4 px-6 py-4 bg-base-200 border-b border-base-300",
                Ui.text("zeroz4j routing tour", "font-semibold text-lg"),
                Ui.box("flex-1"),
                Ui.text(account.getDisplayName(), "text-sm"),
                Ui.text(account.getPlan(), "badge badge-primary badge-sm"));

        Component nav = Ui.box("flex gap-4 px-6 py-3 bg-base-100 border-b border-base-300",
                Ui.routerLink("/", "Home"),
                Ui.routerLink("/projects", "Projects"),
                Ui.routerLink("/projects/new", "New project"),
                Ui.routerLink("/admin", "Admin"),
                Ui.routerLink("/nowhere", "A broken link"));

        return Ui.box("min-h-screen bg-base-100 text-base-content",
                header,
                nav,
                Ui.box("p-6", child));
    }
}
