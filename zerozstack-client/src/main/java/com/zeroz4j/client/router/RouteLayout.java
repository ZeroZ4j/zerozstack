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
package com.zeroz4j.client.router;

import com.zeroz4j.ui.component.Component;

/**
 * A view that wraps another, for chrome shared across several routes — a navigation bar, a sidebar,
 * a page frame.
 *
 * <p>Referenced from a child route's {@code @Route(layout = ...)}. Like {@link RouteView} it can
 * declare its own data, which is loaded before it renders: a shell showing the signed-in user's name
 * fetches that once, in {@link #load}, rather than every view underneath doing it separately.</p>
 *
 * <pre>{@code
 * @Route("/")
 * public class AppShell implements RouteLayout<User> {
 *
 *     @Override
 *     public User load(RouteParams params) { return users.current(); }
 *
 *     @Override
 *     public Component render(User user, RouteParams params, Component child) {
 *         return new Div(
 *                 new Div(new Span(user.getName())),   // the chrome
 *                 child);                              // where the matched route goes
 *     }
 * }
 * }</pre>
 *
 * <p>Layouts nest: a layout may itself declare a {@code layout}, and the router builds the whole
 * chain outward from the matched route.</p>
 *
 * @param <T> what this layout loads; {@code Void} when it loads nothing
 */
public interface RouteLayout<T> {

    /**
     * Fetches what this layout needs, before it renders.
     *
     * @param params the matched path and query parameters
     * @return the loaded data
     */
    default T load(RouteParams params) {
        return null;
    }

    /**
     * Builds the layout around the view it wraps.
     *
     * @param data   whatever {@link #load} returned
     * @param params the matched path and query parameters
     * @param child  the nested route's component; place it wherever it belongs
     * @return the component to display
     */
    Component render(T data, RouteParams params, Component child);
}
