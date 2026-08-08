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

import java.io.InputStream;
import java.net.URL;

/**
 * How a request path becomes a file from {@code /META-INF/resources/}, independent of how it arrived.
 *
 * <p>Both HTTP bindings answer static requests: the JAX-RS resource in
 * {@code zerozstack-server-jaxrs}, and the servlet in {@code zerozstack-server-jakarta}. They must
 * agree exactly — particularly on the shell fallback, where disagreeing would mean deep links
 * working under one binding and 404-ing under the other — so the rules live here and neither binding
 * reimplements them.</p>
 *
 * <p>This class deliberately has no HTTP dependency of any kind, which is what lets it stay in
 * {@code zerozstack-server-core} while the bindings do not.</p>
 */
public final class StaticContent {

    private static final String ROOT = "/META-INF/resources/";
    /** The application shell, served for any path the client router owns. */
    public static final String SHELL = "index.html";

    private StaticContent() {}

    /**
     * Decides which classpath resource answers a request.
     *
     * <p>A client route like {@code /projects/42} has no file behind it. Real URLs mean the browser
     * asks the <em>server</em> for that path whenever a deep link is opened or reloaded, so
     * answering 404 would make every client route work exactly until it was refreshed. An unmatched
     * path that does not look like a file therefore falls back to the shell, and the router resolves
     * it once the page has loaded.</p>
     *
     * @param path the requested path, with or without a leading slash
     * @return the resource path to serve, or null when nothing should be
     */
    public static String resolve(String path) {
        String candidate = normalize(path);
        if (candidate.isEmpty()) {
            return exists(SHELL) ? SHELL : null;
        }
        if (exists(candidate)) {
            return candidate;
        }
        if (looksLikeAsset(candidate)) {
            return null;
        }
        return exists(SHELL) ? SHELL : null;
    }

    /**
     * Whether a missing path was asking for a file rather than a client route.
     *
     * <p>A missing asset must stay a 404: returning the HTML shell for {@code /js/classes.js} would
     * hand the browser a page where it expected a script, and the failure would surface as an
     * incomprehensible syntax error rather than a missing file. A dot in the last segment is the
     * signal — client routes are path segments, assets have extensions.</p>
     *
     * @param path the requested path
     * @return true when this looks like a file request
     */
    public static boolean looksLikeAsset(String path) {
        String candidate = normalize(path);
        int lastSlash = candidate.lastIndexOf('/');
        String lastSegment = lastSlash >= 0 ? candidate.substring(lastSlash + 1) : candidate;
        return lastSegment.indexOf('.') >= 0;
    }

    /**
     * The {@code Content-Type} for a resolved resource, by extension.
     *
     * @param path the resolved resource path
     * @return the media type; {@code application/octet-stream} when the extension is unknown
     */
    public static String contentType(String path) {
        if (path.endsWith(".html")) {
            return "text/html";
        } else if (path.endsWith(".js")) {
            return "application/javascript";
        } else if (path.endsWith(".css")) {
            return "text/css";
        } else if (path.endsWith(".png")) {
            return "image/png";
        } else if (path.endsWith(".ico")) {
            return "image/x-icon";
        } else if (path.endsWith(".svg")) {
            return "image/svg+xml";
        } else if (path.endsWith(".json")) {
            return "application/json";
        } else if (path.endsWith(".webmanifest")) {
            return "application/manifest+json";
        } else if (path.endsWith(".woff2")) {
            return "font/woff2";
        } else if (path.endsWith(".map")) {
            return "application/json";
        }
        return "application/octet-stream";
    }

    /**
     * Opens a resolved resource.
     *
     * @param path a path returned by {@link #resolve(String)}
     * @return the stream, or null when the resource has vanished since it was resolved
     */
    public static InputStream open(String path) {
        URL resource = StaticContent.class.getResource(ROOT + path);
        if (resource == null) {
            return null;
        }
        try {
            return resource.openStream();
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Whether an HTML response should carry a freshly issued client-id cookie.
     *
     * <p>Only on HTML: the id needs to exist before the page opens its WebSocket, and attaching it to
     * every image and script would just repeat the same header. The handshake mints one as a
     * fallback, so this is belt and braces — but it is the path that works when a proxy strips
     * {@code Set-Cookie} from a 101 upgrade response.</p>
     *
     * @param contentType the resolved content type
     * @param cookieHeader the request's {@code Cookie} header, or null
     * @param scheme       the request scheme, for the {@code Secure} attribute
     * @return the {@code Set-Cookie} value, or null when none is needed
     */
    public static String clientIdCookieFor(String contentType, String cookieHeader, String scheme) {
        if (!"text/html".equals(contentType)) {
            return null;
        }
        if (ClientIdentity.verify(ClientIdentity.fromCookieHeader(cookieHeader)) != null) {
            return null;
        }
        return ClientIdentity.cookieHeader(ClientIdentity.issue(), ClientIdentity.secureFor(scheme));
    }

    private static boolean exists(String path) {
        return StaticContent.class.getResource(ROOT + path) != null;
    }

    /** Strips a leading slash and treats null, empty and "/" alike, so both bindings agree. */
    private static String normalize(String path) {
        if (path == null) {
            return "";
        }
        String candidate = path.startsWith("/") ? path.substring(1) : path;
        return candidate.equals("/") ? "" : candidate;
    }
}
