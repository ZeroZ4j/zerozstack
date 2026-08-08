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
package com.zeroz4j.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds a URL path to a client-side view.
 *
 * <p>The annotated class implements {@code RouteView} — which declares both the data the route needs
 * and how to render it — or {@code RouteLayout} for a view that wraps a nested one. The annotation
 * processor finds every annotated class at compile time and generates the route table, so no
 * reflection is involved and a route that does not compile is not a route.</p>
 *
 * <pre>{@code
 * @Route("/tasks")
 * public class TaskListView implements RouteView<List<Task>> {
 *     public List<Task> load(RouteParams params) { return tasks.findAll(); }
 *     public Component render(List<Task> data, RouteParams params) { ... }
 * }
 *
 * @Route(value = "/tasks/:id", layout = AppShell.class)
 * public class TaskDetailView implements RouteView<Task> {
 *     public Task load(RouteParams params) { return tasks.byId(params.getLong("id")); }
 *     public Component render(Task task, RouteParams params) { ... }
 * }
 * }</pre>
 *
 * <h2>Paths</h2>
 * <p>Real paths, handled through the browser's history API — {@code /tasks/42}, not
 * {@code #/tasks/42}. A segment beginning with {@code :} is a parameter, readable from
 * {@code RouteParams}. Matching prefers the more specific route, so {@code /tasks/new} wins over
 * {@code /tasks/:id} regardless of declaration order.</p>
 *
 * <p>Because the paths are real, the server must serve the application's HTML for any path the
 * router owns — otherwise a reload or a shared link is a 404. {@code StaticContentResource} already
 * matches every path, so this works out of the box.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Route {

    /**
     * The URL path, e.g. {@code "/tasks"} or {@code "/tasks/:id"}. Use {@code "/"} for the
     * application's landing view.
     *
     * @return the path pattern
     */
    String value();

    /**
     * A {@code RouteLayout} to wrap this view in, for chrome shared across several routes — a
     * navigation bar, a sidebar, a page frame.
     *
     * <p>The layout declares its own data, loaded once per navigation before any of its children
     * render, so shared state is fetched in one place rather than by every view underneath. Note
     * that the chain is rebuilt on each navigation: moving between two children of the same layout
     * re-runs that layout's loader.</p>
     *
     * @return the layout class, or {@link NoLayout} when this view stands alone
     */
    Class<?> layout() default NoLayout.class;

    /**
     * Display label for generated navigation. Defaults to the class name with any {@code View}
     * suffix removed.
     *
     * @return the label
     */
    String label() default "";

    /**
     * Sort order in generated navigation; lower comes first.
     *
     * @return the order
     */
    int order() default 100;
}
