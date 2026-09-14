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

/**
 * A navigation to an address no route answers to, when no not-found route is set.
 *
 * <p>Reported to error listeners and to {@link Router.LifecycleListener#onNavigationFailed}. With
 * {@link Router#notFoundRoute(String)} set, the router redirects there instead and nothing fails.</p>
 *
 * <p>An {@code IllegalStateException}, which is what the router reported this as before the type
 * existed, so code catching that is unaffected.</p>
 */
public class RouteNotFoundException extends IllegalStateException {

    private final String path;

    /**
     * @param path the address nothing matched
     */
    public RouteNotFoundException(String path) {
        super("No route matches '" + path + "'. Declare one with @Route(\"" + path
                + "\"), or set a not-found route.");
        this.path = path;
    }

    /**
     * @return the address nothing matched
     */
    public String getPath() {
        return path;
    }
}
