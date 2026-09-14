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
 * A page whose server call does not come back in time, to show what the person sees when that
 * happens.
 *
 * <p>The server holds the call for 45 seconds, longer than the client's 30-second request timeout.
 * At the timeout the navigation fails, the busy indicator goes, and the failure message says to
 * check the connection and try again. The indicator does not give up on a timer of its own: what
 * ends it is the call really failing.</p>
 *
 * <p>Deliberately not in the navigation bar. While the server holds this call, every later call on
 * the same connection waits behind it - the server handles one connection's calls in order - so
 * the rest of the tour stops answering too until the 45 seconds are up. Open it by its address.
 * Adding {@code ?timeout=3000} to the page address makes the client give up after three seconds,
 * which is how the browser test uses it.</p>
 */
@Route(value = "/stalled", layout = AppShell.class, label = "Stalled page", order = 8)
public class StalledView implements RouteView<String> {

    private final TourService service = new TourService_Stub();

    @Override
    public String load(RouteParams params) {
        return service.stalledSummary();
    }

    @Override
    public Component render(String summary, RouteParams params) {
        return Ui.box("flex flex-col gap-3 max-w-2xl",
                Ui.text("A stalled page", TextStyle.PAGE_TITLE.getClassNames()),
                Ui.text(summary, TextStyle.SECONDARY.getClassNames()));
    }
}
