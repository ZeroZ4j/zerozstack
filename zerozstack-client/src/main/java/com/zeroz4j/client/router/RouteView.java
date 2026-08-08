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
 * A view reached by a URL, together with the data it needs.
 *
 * <p>The data is declared here, on the route, rather than fetched by the component once it is on
 * screen. That ordering is the entire point: {@link #load(RouteParams)} runs and completes
 * <em>before</em> {@link #render} is called, so a view is never constructed in a half-loaded state,
 * never renders a spinner it has to tear down, and never discovers halfway through building its
 * children that it needs another round trip.</p>
 *
 * <pre>{@code
 * @Route("/tasks/:id")
 * public class TaskDetailView implements RouteView<Task> {
 *
 *     private final TaskService tasks = RmiProxy.of(TaskService.class);
 *
 *     @Override
 *     public Task load(RouteParams params) {
 *         return tasks.byId(params.getLong("id"));      // reads as blocking; suspends the coroutine
 *     }
 *
 *     @Override
 *     public Component render(Task task, RouteParams params) {
 *         return new Div(new Span(task.getTitle()), new Span(task.getDetail()));
 *     }
 * }
 * }</pre>
 *
 * <p>A view needing no data implements {@code RouteView<Void>} and leaves {@link #load} alone.</p>
 *
 * @param <T> what this route loads; {@code Void} when it loads nothing
 */
public interface RouteView<T> {

    /**
     * Fetches what this route needs, before anything is rendered.
     *
     * <p>Runs on the client, so it can call {@code @RmiService} stubs directly — those suspend the
     * browser coroutine and read as ordinary blocking calls. Throwing here aborts the navigation and
     * reports through the router's error handler rather than leaving a half-built page.</p>
     *
     * @param params the matched path and query parameters
     * @return the loaded data, passed straight to {@link #render}
     */
    default T load(RouteParams params) {
        return null;
    }

    /**
     * Builds the view from data that has already arrived.
     *
     * @param data   whatever {@link #load} returned
     * @param params the matched path and query parameters
     * @return the component to display
     */
    Component render(T data, RouteParams params);
}
