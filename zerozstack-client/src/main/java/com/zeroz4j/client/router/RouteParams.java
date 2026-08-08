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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The parts of the current URL a route can read: its path parameters and its query string.
 *
 * <p>Handed to a route's loader and to its renderer, so both see exactly the same request. The typed
 * accessors exist because a route parameter is almost always used as a number or an identifier, and
 * parsing it by hand at every call site is where the "silently rendered the wrong record" bugs come
 * from.</p>
 */
public final class RouteParams {

    private final String path;
    private final Map<String, String> pathParams;
    private final Map<String, String> queryParams;

    RouteParams(String path, Map<String, String> pathParams, Map<String, String> queryParams) {
        this.path = path;
        this.pathParams = Collections.unmodifiableMap(new LinkedHashMap<>(pathParams));
        this.queryParams = Collections.unmodifiableMap(new LinkedHashMap<>(queryParams));
    }

    /**
     * The concrete path this navigation matched, with parameters filled in and the query string
     * removed — {@code "/tasks/42"}, not the {@code "/tasks/:id"} pattern behind it.
     *
     * @return the matched path
     */
    public String path() {
        return path;
    }

    /**
     * A path parameter, e.g. {@code id} from {@code /tasks/:id}.
     *
     * @param name parameter name, without the colon
     * @return the value, or null when the route declares no such parameter
     */
    public String get(String name) {
        return pathParams.get(name);
    }

    /**
     * A path parameter as a number.
     *
     * @param name parameter name
     * @return the value
     * @throws IllegalArgumentException when the parameter is absent or not a number — a route
     *         reached with a non-numeric id is a broken link, not a record to go looking for
     */
    public long getLong(String name) {
        String value = get(name);
        if (value == null) {
            throw new IllegalArgumentException("Route " + path + " has no parameter '" + name + "'");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Route parameter '" + name + "' is '" + value
                    + "', which is not a number.");
        }
    }

    /**
     * A path parameter as an {@code int}.
     *
     * @param name parameter name
     * @return the value
     * @throws IllegalArgumentException when absent or not a number
     */
    public int getInt(String name) {
        return (int) getLong(name);
    }

    /**
     * Every path parameter at once, in declaration order, for a route that wants to iterate rather
     * than name each one. Read-only.
     *
     * @return the path parameters
     */
    public Map<String, String> all() {
        return pathParams;
    }

    /**
     * A query-string value, e.g. {@code page} from {@code ?page=2}.
     *
     * @param name parameter name
     * @return the value, or null when absent
     */
    public String query(String name) {
        return queryParams.get(name);
    }

    /**
     * A query-string value with a fallback.
     *
     * @param name         parameter name
     * @param defaultValue returned when the parameter is absent or empty
     * @return the value or the fallback
     */
    public String query(String name, String defaultValue) {
        String value = queryParams.get(name);
        return value == null || value.isEmpty() ? defaultValue : value;
    }

    /**
     * A query-string value as a number, with a fallback for absent or unparseable input.
     *
     * <p>Unlike {@link #getLong(String)} this does not throw: a query parameter is usually a
     * user-adjustable option like a page number, where falling back beats failing the navigation.</p>
     *
     * @param name         parameter name
     * @param defaultValue returned when absent or not a number
     * @return the value or the fallback
     */
    public long queryLong(String name, long defaultValue) {
        String value = queryParams.get(name);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    @Override
    public String toString() {
        return "RouteParams[" + path + " " + pathParams + (queryParams.isEmpty() ? "" : " ?" + queryParams) + "]";
    }
}
