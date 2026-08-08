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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * The route table: every {@code @Route} the build found, and the matching that turns a URL into one
 * of them.
 */
public final class RouteRegistry {

    private static final List<RouteDefinition> routes = new ArrayList<>();
    private static final Map<String, RouteDefinition> byClassName = new LinkedHashMap<>();
    private static boolean loaded;

    private RouteRegistry() {}

    /**
     * Loads every generated registrar on the classpath. Called by {@link Router#start}; calling it
     * twice is harmless.
     */
    public static synchronized void init() {
        if (loaded) {
            return;
        }
        loaded = true;
        for (RouteRegistrar registrar : ServiceLoader.load(RouteRegistrar.class)) {
            registrar.registerAll();
        }
    }

    /**
     * Adds a route. Called by generated registrars.
     *
     * @param definition the route
     */
    public static synchronized void register(RouteDefinition definition) {
        RouteDefinition existing = byClassName.get(definition.targetClassName());
        if (existing != null) {
            return;   // the same registrar ran twice; re-registering would duplicate the table
        }
        for (RouteDefinition other : routes) {
            if (other.pattern().equals(definition.pattern()) && other.isLayout() == definition.isLayout()) {
                throw new IllegalStateException(
                        "Two routes both claim '" + definition.pattern() + "': "
                        + other.targetClassName() + " and " + definition.targetClassName()
                        + ". Which one a URL reached would be decided by build order, so this is "
                        + "refused rather than resolved arbitrarily.");
            }
        }
        routes.add(definition);
        byClassName.put(definition.targetClassName(), definition);
        // Most specific first, so /tasks/new is preferred over /tasks/:id whatever order the
        // compiler emitted them in.
        Collections.sort(routes, (a, b) -> Integer.compare(a.parameterCount(), b.parameterCount()));
    }

    /**
     * Finds the route matching a path.
     *
     * @param path the path, without query string
     * @return the match, or null when no route claims this path
     */
    public static synchronized RouteMatch match(String path) {
        for (RouteDefinition definition : routes) {
            if (definition.isLayout()) {
                continue;   // a layout is reached through its children, never on its own
            }
            Map<String, String> params = definition.match(path);
            if (params != null) {
                return new RouteMatch(definition, params);
            }
        }
        return null;
    }

    /**
     * Looks up a route by the class it was declared on, for resolving a layout reference.
     *
     * @param className fully-qualified class name
     * @return the route, or null
     */
    public static synchronized RouteDefinition byClassName(String className) {
        return byClassName.get(className);
    }

    /**
     * Every registered route, most specific first. Layouts are included; filter them out to build
     * navigation.
     *
     * @return the route table
     */
    public static synchronized List<RouteDefinition> all() {
        return Collections.unmodifiableList(new ArrayList<>(routes));
    }

    /** Empties the table. Test support only. */
    static synchronized void resetForTesting() {
        routes.clear();
        byClassName.clear();
        loaded = false;
    }

    /** A matched route together with the parameters extracted from the URL. */
    public static final class RouteMatch {
        private final RouteDefinition definition;
        private final Map<String, String> pathParams;

        RouteMatch(RouteDefinition definition, Map<String, String> pathParams) {
            this.definition = definition;
            this.pathParams = pathParams;
        }

        /**
         * The table entry whose pattern matched.
         *
         * @return the matched route
         */
        public RouteDefinition definition() {
            return definition;
        }

        /**
         * The values the pattern's {@code :name} segments captured from this URL.
         *
         * @return the path parameters
         */
        public Map<String, String> pathParams() {
            return pathParams;
        }
    }
}
