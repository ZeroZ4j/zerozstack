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
package com.zeroz4j.server;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/**
 * The page somebody asked for before they had to sign in, and the check that decides whether it is
 * safe to send them there afterwards.
 *
 * <p>The value comes from the browser, so anybody can write a link carrying anything. Followed
 * blindly, a link could send a person who has just signed in to a copy of this site on another
 * host. So it is only ever treated as a path inside this application. It is refused, which lands the
 * person on the application root, when it:</p>
 *
 * <ul>
 *   <li>is absolute ({@code https://...}, {@code javascript:...}) or starts with {@code //};</li>
 *   <li>contains a backslash, which browsers read as a slash in a URL;</li>
 *   <li>contains a control character. Browsers delete tabs and newlines from a URL before reading
 *       it, so {@code "/\t/evil.example"} would become {@code "//evil.example"}, and a line break
 *       would end a response header early;</li>
 *   <li>has a {@code .} or {@code ..} segment in any spelling, including {@code %2e}, because
 *       browsers resolve those and {@code a/../../other} would leave the application;</li>
 *   <li>names a framework address, which is not a page;</li>
 *   <li>cannot be parsed as a URI, or is longer than {@value #MAX_LENGTH} characters.</li>
 * </ul>
 *
 * @since 0.11.0
 */
public final class ReturnPage {

    /** Longer than any real page address. */
    static final int MAX_LENGTH = 1024;

    private static final Set<String> NOT_PAGES = Set.of(
            SignInSessions.PATH, UploadReceiver.UPLOAD_PATH, "wasm-rmi");

    private ReturnPage() {
    }

    /**
     * The page as a route inside the application.
     *
     * @param requested what the browser sent, such as {@code "tasks/7?tab=notes"}
     * @return the route with no leading slash and no fragment; null means the application root
     */
    public static String route(String requested) {
        if (requested == null) {
            return null;
        }
        for (int i = 0; i < requested.length(); i++) {
            char c = requested.charAt(i);
            if (c < 0x20 || c == 0x7f) {
                return null;
            }
        }
        String candidate = requested.trim();
        if (candidate.isEmpty() || candidate.length() > MAX_LENGTH) {
            return null;
        }
        if (candidate.startsWith("//") || candidate.indexOf('\\') >= 0) {
            return null;
        }
        int fragment = candidate.indexOf('#');
        if (fragment >= 0) {
            candidate = candidate.substring(0, fragment);
        }

        URI uri;
        try {
            uri = new URI(candidate);
        } catch (URISyntaxException ex) {
            return null;
        }
        if (uri.isAbsolute() || uri.getRawAuthority() != null) {
            return null;
        }

        // On the decoded path, so %2e%2e counts as .. and %5c as a backslash. The route returned is
        // the undecoded one, so nothing decoded here ever reaches a header.
        String path = uri.getPath() == null ? "" : uri.getPath();
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c < 0x20 || c == 0x7f || c == '\\') {
                return null;
            }
        }
        if (path.startsWith("//")) {
            return null;
        }
        String[] segments = (path.startsWith("/") ? path.substring(1) : path).split("/", -1);
        for (String segment : segments) {
            String bare = withoutPathParameters(segment);
            if (".".equals(bare) || "..".equals(bare)) {
                return null;
            }
        }
        if (NOT_PAGES.contains(withoutPathParameters(segments[0]).toLowerCase(Locale.ROOT))) {
            return null;
        }

        String route = candidate.startsWith("/") ? candidate.substring(1) : candidate;
        return route.isEmpty() ? null : route;
    }

    /**
     * Where to send somebody: the page when it is safe, the application root otherwise.
     *
     * @param contextPath the deployment's context path; empty or null at the site root
     * @param requested   what the browser sent, or null
     * @return a path from the site root
     */
    public static String landingUrl(String contextPath, String requested) {
        String base = StaticContent.baseHref(contextPath);
        String route = route(requested);
        return route == null ? base : base + route;
    }

    /** {@code page;jsessionid=1} is mapped to {@code page} by a container, so compare it as that. */
    private static String withoutPathParameters(String segment) {
        int semicolon = segment.indexOf(';');
        return semicolon < 0 ? segment : segment.substring(0, semicolon);
    }
}
