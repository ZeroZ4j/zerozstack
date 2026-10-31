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

import com.zeroz4j.client.AppBase;
import com.zeroz4j.ui.component.Link;

/**
 * A link to a route inside the application. {@code data-route} hands the click to the {@link Router},
 * so the page is not reloaded, and the {@code href} is a real address carrying the application's
 * base path, so the link also works in a new tab, from the keyboard and for a screen reader.
 *
 * <pre>{@code
 * RouteLink orders = new RouteLink("/orders", "Orders");
 * orders.setCurrent(Router.currentPath().startsWith("/orders"));
 * }</pre>
 *
 * <p>Mark the link for the page on screen with {@link #setCurrent(boolean)}, which sets
 * {@code aria-current="page"} for screen readers and gives styles a hook:
 * {@code a[aria-current="page"]}. Remove the {@code link} class for a navigation entry that should
 * not look underlined.</p>
 */
public class RouteLink extends Link {

    /**
     * Creates a link to a route.
     *
     * @param path the route path, such as {@code /orders/42}, without the application's base path
     * @param text the words of the link
     */
    public RouteLink(String path, String text) {
        super(text, AppBase.location(path));
        getElement().setAttribute("data-route", "");
    }

    /**
     * Marks this link as the page on screen, or not.
     *
     * @param current true to set {@code aria-current="page"}
     * @return this link, for chaining
     */
    public RouteLink setCurrent(boolean current) {
        if (current) {
            getElement().setAttribute("aria-current", "page");
        } else {
            getElement().removeAttribute("aria-current");
        }
        return this;
    }
}
