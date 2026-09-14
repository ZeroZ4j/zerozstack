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
 * A navigation to a route whose {@code @RequiresRole} the person does not meet, when no forbidden
 * route is set.
 *
 * <p>Reported to error listeners and to {@link Router.LifecycleListener#onNavigationFailed}. With
 * {@link Router#forbiddenRoute(String)} set, the router redirects there instead and nothing fails.
 * This check only decides what to show; the server checks every call again.</p>
 *
 * <p>A {@code SecurityException}, which is what the router reported this as before the type
 * existed, so code catching that is unaffected.</p>
 */
public class RouteForbiddenException extends SecurityException {

    private final String path;

    /**
     * @param path    the address that was refused
     * @param message what was required and what the person has
     */
    public RouteForbiddenException(String path, String message) {
        super(message);
        this.path = path;
    }

    /**
     * @return the address that was refused
     */
    public String getPath() {
        return path;
    }
}
