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
import java.util.Set;
import java.util.function.Supplier;

/**
 * One entry in the route table: a path pattern, what to build when it matches, and who may see it.
 *
 * <p>Created by the code the annotation processor generates from {@code @Route}, never by hand — the
 * supplier is a direct constructor reference precisely so nothing has to be instantiated
 * reflectively, which the browser runtime cannot do.</p>
 */
public final class RouteDefinition {

    private final String pattern;
    private final String[] segments;
    private final String targetClassName;
    private final String layoutClassName;
    private final Set<String> requiredRoles;
    private final Supplier<Object> factory;
    private final boolean layout;
    private final String label;
    private final int order;
    private final int parameterCount;

    /**
     * Builds one entry of the route table. Called by generated code, not by applications.
     *
     * @param pattern         the path pattern, e.g. {@code "/tasks/:id"}
     * @param targetClassName the annotated class's name, for diagnostics and layout linking
     * @param layoutClassName the parent layout's class name, or null
     * @param requiredRoles   roles from {@code @RequiresRole}; empty when unrestricted
     * @param factory         builds the view or layout instance
     * @param layout          true when the target is a {@link RouteLayout}
     * @param label           display label for generated navigation
     * @param order           sort order for generated navigation
     */
    public RouteDefinition(String pattern, String targetClassName, String layoutClassName,
                           Set<String> requiredRoles, Supplier<Object> factory, boolean layout,
                           String label, int order) {
        this.pattern = normalize(pattern);
        this.segments = splitSegments(this.pattern);
        this.targetClassName = targetClassName;
        this.layoutClassName = layoutClassName;
        this.requiredRoles = requiredRoles == null ? Collections.emptySet() : requiredRoles;
        this.factory = factory;
        this.layout = layout;
        this.label = label;
        this.order = order;

        int params = 0;
        for (String segment : segments) {
            if (isParameter(segment)) {
                params++;
            }
        }
        this.parameterCount = params;
    }

    /**
     * Matches a path against this route's pattern.
     *
     * @param path the path to match, without query string
     * @return the extracted path parameters, or null when this route does not match
     */
    public Map<String, String> match(String path) {
        String[] actual = splitSegments(normalize(path));
        if (actual.length != segments.length) {
            return null;
        }
        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 0; i < segments.length; i++) {
            String expected = segments[i];
            if (isParameter(expected)) {
                params.put(expected.substring(1), decode(actual[i]));
            } else if (!expected.equals(actual[i])) {
                return null;
            }
        }
        return params;
    }

    /**
     * The path this route answers to, normalised — a leading slash, no trailing one.
     *
     * @return the declared pattern, e.g. {@code "/tasks/:id"}
     */
    public String pattern() {
        return pattern;
    }

    /**
     * The {@code @Route} class this entry was generated from. Used to link a child to its layout,
     * and to name the offender in a diagnostic.
     *
     * @return the fully-qualified class name
     */
    public String targetClassName() {
        return targetClassName;
    }

    /**
     * The layout this route renders inside, by class name rather than by {@code Class} so nothing
     * has to be resolved reflectively.
     *
     * @return the layout's class name, or null when this route stands alone
     */
    public String layoutClassName() {
        return layoutClassName;
    }

    /**
     * What {@code @RequiresRole} asked for. The router checks these before loading anything — though
     * the server re-checks every call regardless, which is what actually protects data.
     *
     * @return the required roles; empty means unrestricted
     */
    public Set<String> requiredRoles() {
        return requiredRoles;
    }

    /**
     * Builds the view or layout. A direct constructor reference generated at compile time, because
     * the browser runtime cannot instantiate reflectively.
     *
     * @return a fresh instance
     */
    public Object newInstance() {
        return factory.get();
    }

    /**
     * Whether this entry is chrome that wraps another route rather than a destination of its own.
     * Layouts are skipped when matching a URL.
     *
     * @return true when the target implements {@link RouteLayout}
     */
    public boolean isLayout() {
        return layout;
    }

    /**
     * A human-readable name for this route, for an application building its own navigation.
     * Defaults to the class name with a {@code View} or {@code Layout} suffix removed.
     *
     * @return the display label
     */
    public String label() {
        return label;
    }

    /**
     * Where this route should sit in generated navigation; lower comes first. Has no effect on
     * matching.
     *
     * @return the sort order
     */
    public int order() {
        return order;
    }

    /**
     * How specific this pattern is, for resolving overlaps.
     *
     * <p>Fewer parameters wins, so {@code /tasks/new} beats {@code /tasks/:id} regardless of the
     * order the two were declared in — which matters because declaration order is whatever the
     * compiler happened to produce.</p>
     *
     * @return the number of parameter segments
     */
    int parameterCount() {
        return parameterCount;
    }

    private static boolean isParameter(String segment) {
        return segment.length() > 1 && segment.charAt(0) == ':';
    }

    private static String normalize(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        String normalized = path.startsWith("/") ? path : "/" + path;
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String[] splitSegments(String path) {
        if ("/".equals(path)) {
            return new String[0];
        }
        String body = path.substring(1);
        return body.split("/");
    }

    /** Percent-decoding, since a path parameter can legitimately contain encoded characters. */
    private static String decode(String segment) {
        if (segment.indexOf('%') < 0) {
            return segment;
        }
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream(segment.length());
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            if (c == '%' && i + 2 < segment.length()) {
                int high = Character.digit(segment.charAt(i + 1), 16);
                int low = Character.digit(segment.charAt(i + 2), 16);
                if (high >= 0 && low >= 0) {
                    bytes.write((high << 4) + low);
                    i += 2;
                    continue;
                }
            }
            byte[] encoded = String.valueOf(c).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            bytes.write(encoded, 0, encoded.length);
        }
        return new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return "Route[" + pattern + " -> " + targetClassName + "]";
    }
}
