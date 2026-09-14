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
import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.theme.TextStyle;

/**
 * A page whose loader takes about two seconds, so there is something to see while it loads.
 *
 * <p>Nothing in this class shows a spinner. {@code RoutingTourApp} switches on the router's own busy
 * indicator once, and it appears for this route because the navigation takes longer than 300
 * milliseconds - and for no other route here, because none of the others do.</p>
 *
 * <p>Things to try on it: click another link while it is still loading and the other page wins, with
 * the address bar to match; press Back while it loads and the page you came from stays; stop the
 * server while it loads and the failure message appears, with a Retry button that works once the
 * server is back.</p>
 */
@Route(value = "/slow", layout = AppShell.class, label = "Slow page", order = 7)
public class SlowView implements RouteView<String> {

    private final TourService service = new TourService_Stub();

    @Override
    public String load(RouteParams params) {
        return service.slowSummary();
    }

    @Override
    public Component render(String summary, RouteParams params) {
        return Ui.box("flex flex-col gap-3 max-w-2xl",
                Ui.text("A slow page", TextStyle.PAGE_TITLE.getClassNames()),
                Ui.text(summary, TextStyle.SECONDARY.getClassNames()),
                Ui.text("While it loaded, the router showed its busy indicator by itself: a bar "
                        + "along the top, a spinner in the middle and a wait cursor. None of that is "
                        + "code in this view.", TextStyle.SECONDARY.getClassNames()));
    }
}
