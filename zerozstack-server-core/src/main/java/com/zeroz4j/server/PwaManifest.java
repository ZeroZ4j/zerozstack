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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the web app manifest that makes an application installable.
 *
 * <p>A manifest is built rather than shipped as a static file because in a multi-tenant product it is
 * not one document: the name, icons and theme colour belong to the tenant, and are only known per
 * request. Serve it from your own endpoint:</p>
 *
 * <pre>{@code
 * @GET @Path("/manifest.webmanifest")
 * @Produces("application/manifest+json")
 * public String manifest() {
 *     Tenant tenant = tenants.current();
 *     return PwaManifest.named(tenant.displayName(), tenant.shortName())
 *             .themeColor(tenant.brandColour())
 *             .icon("/icons/" + tenant.id() + "-192.png", 192)
 *             .icon("/icons/" + tenant.id() + "-512.png", 512)
 *             .toJson();
 * }
 * }</pre>
 *
 * <p>An application with one identity can equally drop a static {@code manifest.webmanifest} into
 * {@code META-INF/resources/} — it is served with the right content type already. This class exists
 * for the case a static file cannot cover.</p>
 *
 * <h2>What installing does and does not give you</h2>
 * <p>It gives a home-screen launch in a standalone window with no browser chrome. It does <b>not</b>
 * make the application work offline: a zeroz4j client is a shell whose every view loads its data over
 * the WebSocket, so with no connection there is nothing to render. That is a property of the
 * architecture, not a gap — see the offline page shipped alongside the service worker.</p>
 */
public final class PwaManifest {

    private final String name;
    private final String shortName;
    private String description;
    private String startUrl = "/";
    private String scope = "/";
    private String display = "standalone";
    private String themeColor = "#1f2937";
    private String backgroundColor = "#ffffff";
    private String orientation;
    private final List<Map<String, String>> icons = new ArrayList<>();

    private PwaManifest(String name, String shortName) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("A manifest needs a name: it is what the installed "
                    + "application is called on the home screen.");
        }
        this.name = name.trim();
        this.shortName = shortName == null || shortName.trim().isEmpty()
                ? this.name : shortName.trim();
    }

    /**
     * Starts a manifest.
     *
     * @param name      the full application name, shown in the install prompt
     * @param shortName the name shown under the home-screen icon, where space is tight; null uses
     *                  {@code name}
     * @return the builder
     */
    public static PwaManifest named(String name, String shortName) {
        return new PwaManifest(name, shortName);
    }

    /**
     * Sets the description some app stores and install prompts show.
     *
     * @param description the description
     * @return this builder
     */
    public PwaManifest description(String description) {
        this.description = description;
        return this;
    }

    /**
     * Sets the path the installed application opens at; default {@code "/"}.
     *
     * @param startUrl the start URL
     * @return this builder
     */
    public PwaManifest startUrl(String startUrl) {
        this.startUrl = startUrl;
        return this;
    }

    /**
     * Sets the navigation scope the installed window keeps; default {@code "/"}. A link outside it
     * opens in the browser rather than in the app window.
     *
     * @param scope the scope
     * @return this builder
     */
    public PwaManifest scope(String scope) {
        this.scope = scope;
        return this;
    }

    /**
     * Sets the display mode; default {@code "standalone"}, which is the one that drops browser
     * chrome. Others are {@code "fullscreen"}, {@code "minimal-ui"} and {@code "browser"}.
     *
     * @param display the display mode
     * @return this builder
     */
    public PwaManifest display(String display) {
        this.display = display;
        return this;
    }

    /**
     * Sets the colour the OS tints the title bar and splash with.
     *
     * @param themeColor a CSS colour
     * @return this builder
     */
    public PwaManifest themeColor(String themeColor) {
        this.themeColor = themeColor;
        return this;
    }

    /**
     * Sets the splash-screen background shown before the client bundle has painted.
     *
     * @param backgroundColor a CSS colour
     * @return this builder
     */
    public PwaManifest backgroundColor(String backgroundColor) {
        this.backgroundColor = backgroundColor;
        return this;
    }

    /**
     * Locks the installed window's orientation, e.g. {@code "portrait"}. Unset by default, which
     * leaves it to the device.
     *
     * @param orientation the orientation
     * @return this builder
     */
    public PwaManifest orientation(String orientation) {
        this.orientation = orientation;
        return this;
    }

    /**
     * Adds a square icon.
     *
     * <p>Supply at least 192 and 512 pixels: the first is the home-screen icon, the second is what
     * the splash screen and app switcher scale from. PNG is the safe choice everywhere; SVG works on
     * current Chrome and Edge but not universally.</p>
     *
     * @param path the URL, absolute from the site root
     * @param size the width and height in pixels
     * @return this builder
     */
    public PwaManifest icon(String path, int size) {
        return icon(path, size + "x" + size, mediaTypeFor(path), null);
    }

    /**
     * Adds an icon with an explicit purpose.
     *
     * <p>{@code "maskable"} is worth supplying: without one, Android puts the icon in a white circle
     * rather than filling the shape it wants. A maskable icon keeps its important content inside the
     * middle 80%, because the edges are cropped.</p>
     *
     * @param path      the URL, absolute from the site root
     * @param sizes     the {@code sizes} value, e.g. {@code "192x192"}
     * @param mediaType the icon's media type, e.g. {@code "image/png"}
     * @param purpose   {@code "any"}, {@code "maskable"}, {@code "monochrome"}, or null
     * @return this builder
     */
    public PwaManifest icon(String path, String sizes, String mediaType, String purpose) {
        Map<String, String> icon = new LinkedHashMap<>();
        icon.put("src", path);
        icon.put("sizes", sizes);
        icon.put("type", mediaType);
        if (purpose != null && !purpose.isEmpty()) {
            icon.put("purpose", purpose);
        }
        icons.add(icon);
        return this;
    }

    /**
     * Renders the manifest.
     *
     * <p>Written by hand rather than through a JSON library: this is a small, fixed document, and the
     * server module has no JSON dependency to spend on it.</p>
     *
     * @return the manifest as JSON
     */
    public String toJson() {
        StringBuilder json = new StringBuilder(512);
        json.append("{\n");
        field(json, "name", name, true);
        field(json, "short_name", shortName, true);
        if (description != null) {
            field(json, "description", description, true);
        }
        field(json, "start_url", startUrl, true);
        field(json, "scope", scope, true);
        field(json, "display", display, true);
        field(json, "theme_color", themeColor, true);
        field(json, "background_color", backgroundColor, true);
        if (orientation != null) {
            field(json, "orientation", orientation, true);
        }
        json.append("  \"icons\": [");
        for (int i = 0; i < icons.size(); i++) {
            json.append(i == 0 ? "\n" : ",\n").append("    {");
            Map<String, String> icon = icons.get(i);
            int written = 0;
            for (Map.Entry<String, String> entry : icon.entrySet()) {
                json.append(written++ == 0 ? "" : ", ")
                    .append('"').append(entry.getKey()).append("\": \"")
                    .append(escape(entry.getValue())).append('"');
            }
            json.append('}');
        }
        json.append(icons.isEmpty() ? "]\n" : "\n  ]\n");
        json.append("}\n");
        return json.toString();
    }

    private static void field(StringBuilder json, String key, String value, boolean comma) {
        json.append("  \"").append(key).append("\": \"").append(escape(value)).append('"')
            .append(comma ? ",\n" : "\n");
    }

    /** Minimal JSON string escaping — enough for a name, a colour and a path. */
    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':  escaped.append("\\\""); break;
                case '\\': escaped.append("\\\\"); break;
                case '\n': escaped.append("\\n"); break;
                case '\r': escaped.append("\\r"); break;
                case '\t': escaped.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
            }
        }
        return escaped.toString();
    }

    private static String mediaTypeFor(String path) {
        if (path.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (path.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/png";
    }
}
