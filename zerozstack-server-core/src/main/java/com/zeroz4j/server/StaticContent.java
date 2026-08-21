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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

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
 *
 * <p>It is also where a request path is refused. {@link #isSafePath(String)} runs before any lookup,
 * under both bindings, so a path carrying a {@code ..} segment, a backslash or a control character
 * never reaches a resource loader at all.</p>
 */
public final class StaticContent {

    private static final String ROOT = "/META-INF/resources/";
    /** The application shell, served for any path the client router owns. */
    public static final String SHELL = "index.html";

    private StaticContent() {}

    /**
     * Where the served files are, for a deployment that does not keep them all on the classpath.
     *
     * <p>The framework's own assets — the service worker and the offline page — travel inside
     * {@code zerozstack-server-core} and are therefore classpath resources under
     * {@code /META-INF/resources/}, which is {@link #CLASSPATH} and the default everywhere.</p>
     *
     * <p>A WAR is the case that needs more. Its natural home for {@code index.html} and the client
     * bundle is {@code src/main/webapp}, which lands in the archive root — <b>not</b> on the
     * classloader's path, because a WAR's resource roots are {@code WEB-INF/classes} and
     * {@code WEB-INF/lib}. A WAR packaged that way and served by {@code Zeroz4jShellServlet} answered
     * 404 to every request including its own shell, and would have gone on doing so until somebody
     * deployed it. The servlet therefore supplies an implementation that asks the classpath first and
     * the {@code ServletContext} second, so both layouts work and neither has to be documented as the
     * one that does.</p>
     */
    public interface Assets {

        /**
         * @param path a normalised path, with no leading slash
         * @return whether something is there to serve
         */
        boolean exists(String path);

        /**
         * @param path a normalised path, with no leading slash
         * @return the content, or null when it has vanished since it was resolved
         */
        InputStream open(String path);
    }

    /** The classpath under {@code /META-INF/resources/}: the default, and all a standalone server has. */
    public static final Assets CLASSPATH = new Assets() {

        @Override
        public boolean exists(String path) {
            return StaticContent.class.getResource(ROOT + path) != null;
        }

        @Override
        public InputStream open(String path) {
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
    };

    /**
     * Decides which classpath resource answers a request.
     *
     * <p>A client route like {@code /projects/42} has no file behind it. Real URLs mean the browser
     * asks the <em>server</em> for that path whenever a deep link is opened or reloaded, so
     * answering 404 would make every client route work exactly until it was refreshed. An unmatched
     * path that does not look like a file therefore falls back to the shell, and the router resolves
     * it once the page has loaded.</p>
     *
     * <p>A path that fails {@link #isSafePath(String)} resolves to null — the same answer an unknown
     * asset gets — so no binding can look one up.</p>
     *
     * @param path the requested path, with or without a leading slash
     * @return the resource path to serve, or null when nothing should be
     */
    public static String resolve(String path) {
        return resolve(path, CLASSPATH);
    }

    /**
     * {@link #resolve(String)} against somewhere other than the classpath.
     *
     * @param path   the requested path, with or without a leading slash
     * @param assets where to look
     * @return the resource path to serve, or null when nothing should be
     */
    public static String resolve(String path, Assets assets) {
        if (!isSafePath(path)) {
            return null;                              // 404, exactly like an asset that is not there
        }
        String candidate = normalize(path);
        if (candidate.isEmpty()) {
            return assets.exists(SHELL) ? SHELL : null;
        }
        if (assets.exists(candidate)) {
            return candidate;
        }
        if (looksLikeAsset(candidate)) {
            return null;
        }
        return assets.exists(SHELL) ? SHELL : null;
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
     * Whether a requested path may be looked up at all.
     *
     * <p><b>Paths arrive already percent-decoded, and this class never decodes again.</b> The JAX-RS
     * runtime decodes a {@code @PathParam} before the resource method sees it, and a servlet
     * container decodes {@code getPathInfo()} — so {@code ..%2f..%2f} is a plain {@code ../../} by
     * the time it gets here. Decoding a second time would turn an ordinary file name containing a
     * literal {@code %} into a different path, and would let {@code %252e%252e} become {@code ..}
     * after the check had already passed. A {@code %} that survives to this point is therefore
     * treated as an ordinary character in a file name.</p>
     *
     * <p>The check still looks at a decoded copy, because a request may have been double-encoded
     * ({@code %252e%252e}) and arrived here as {@code %2e%2e}. That copy is only ever inspected; it
     * is never the path that gets served.</p>
     *
     * <p>Refused: a {@code ..} segment in any form, a backslash (a path separator on Windows and in
     * some class loaders), a null byte or any other control character, and anything under
     * {@code WEB-INF/} or {@code META-INF/}. A refused path gets the same 404 an unknown asset gets:
     * one answer for "no such file" and "not a path we serve", so the answer reveals nothing about
     * which it was.</p>
     *
     * <p>The classpath loader happens to collapse {@code ..} lexically, so most of this could not
     * escape a jar in practice. "Probably safe because of how somebody else's class loader behaves"
     * is not a control, and the servlet binding serves real files where it would not hold at all.</p>
     *
     * @param path the requested path, exactly as the binding received it
     * @return true when the path may be resolved
     */
    public static boolean isSafePath(String path) {
        if (path == null || path.isEmpty()) {
            return true;                              // the root: the shell answers it
        }
        return isSafeForm(path) && isSafeForm(percentDecoded(path));
    }

    /** The rules themselves, applied to one spelling of the path. */
    private static boolean isSafeForm(String path) {
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '\\' || c < 0x20 || c == 0x7F) {
                return false;                         // backslash, null byte, any control character
            }
        }
        String candidate = normalize(path);
        for (String segment : candidate.split("/", -1)) {
            if ("..".equals(segment)) {
                return false;
            }
        }
        String upper = candidate.toUpperCase(Locale.ROOT);
        return !upper.startsWith("WEB-INF/") && !upper.startsWith("META-INF/")
                && !upper.equals("WEB-INF") && !upper.equals("META-INF");
    }

    /**
     * One round of percent-decoding, for inspection only.
     *
     * <p>Byte by byte rather than character by character: an overlong or invalid UTF-8 sequence
     * decodes to bytes that are not {@code '.'} or {@code '/'}, and turning them into characters
     * first is what makes such a sequence look like a separator. An incomplete or non-hexadecimal
     * {@code %} is left as the literal character it is.</p>
     */
    private static String percentDecoded(String path) {
        if (path.indexOf('%') < 0) {
            return path;
        }
        StringBuilder out = new StringBuilder(path.length());
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '%' && i + 2 < path.length()) {
                int hi = Character.digit(path.charAt(i + 1), 16);
                int lo = Character.digit(path.charAt(i + 2), 16);
                if (hi >= 0 && lo >= 0) {
                    out.append((char) ((hi << 4) + lo));
                    i += 2;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
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
        return CLASSPATH.open(path);
    }

    /**
     * The application shell with a {@code <base href>} for the deployment's context path.
     *
     * <p><b>Why the shell cannot be served byte-for-byte.</b> A shell answers two kinds of URL: its
     * own, and every client route that falls back to it. Relative asset references therefore resolve
     * against a <em>different</em> directory depending on which route the browser happened to ask
     * for — {@code js/classes.js} on {@code /messages/42} means {@code /messages/js/classes.js},
     * which is a 404 and a blank page. Writing the references absolute (`/js/classes.js`) fixes that
     * and breaks the moment the application is deployed under a context path, because the leading
     * slash escapes it.</p>
     *
     * <p>One {@code <base>} fixes both, and only the server knows what to put in it. Everything the
     * page and the client then resolve relatively — the bundle, the manifest, the icons, a form
     * action, {@code document.baseURI} itself, which is where {@code AppBase} on the client reads the
     * application's root from — lands inside the deployment wherever it was deployed.</p>
     *
     * <p>An application that already declares its own {@code <base>} is left alone.</p>
     *
     * @param contextPath the deployment's context path, e.g. {@code "/coachapp"}; {@code null} or
     *                    empty for an application served from the site root
     * @return the shell, UTF-8 encoded, or null when there is no shell to serve
     */
    public static byte[] shellBytes(String contextPath) {
        return shellBytes(contextPath, CLASSPATH);
    }

    /**
     * {@link #shellBytes(String)} against somewhere other than the classpath.
     *
     * @param contextPath the deployment's context path
     * @param assets      where the shell is
     * @return the shell, UTF-8 encoded, or null when there is no shell to serve
     */
    public static byte[] shellBytes(String contextPath, Assets assets) {
        InputStream in = assets.open(SHELL);
        if (in == null) {
            return null;
        }
        String html;
        try (InputStream stream = in) {
            html = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return null;
        }
        return withBaseHref(html, contextPath).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Inserts a {@code <base href>} into a document that has none.
     *
     * <p>Split out from {@link #shellBytes(String)} so the rule is testable without a shell on the
     * classpath, and shared by both HTTP bindings so they cannot disagree about it.</p>
     *
     * @param html        the document
     * @param contextPath the deployment's context path
     * @return the document, with a base element when it needed one
     */
    public static String withBaseHref(String html, String contextPath) {
        if (html == null) {
            return null;
        }
        String lower = html.toLowerCase(Locale.ROOT);
        if (lower.contains("<base ") || lower.contains("<base>")) {
            return html;                              // the application has said what it wants
        }
        int head = lower.indexOf("<head");
        if (head < 0) {
            return html;                              // not a document we can safely rewrite
        }
        int insertAt = html.indexOf('>', head);
        if (insertAt < 0) {
            return html;
        }
        return html.substring(0, insertAt + 1)
                + "\n<base href=\"" + baseHref(contextPath) + "\">"
                + html.substring(insertAt + 1);
    }

    /**
     * The value a {@code <base href>} takes for a context path: always absolute, always ending in a
     * slash, because a base without a trailing slash resolves relative URLs against the parent
     * directory and would silently strip the context path back off again.
     *
     * @param contextPath the deployment's context path, possibly null or empty
     * @return the href, e.g. {@code "/coachapp/"} or {@code "/"}
     */
    public static String baseHref(String contextPath) {
        if (contextPath == null || contextPath.isEmpty() || "/".equals(contextPath)) {
            return "/";
        }
        String path = contextPath.startsWith("/") ? contextPath : "/" + contextPath;
        return path.endsWith("/") ? path : path + "/";
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

    /** Strips a leading slash and treats null, empty and "/" alike, so both bindings agree. */
    private static String normalize(String path) {
        if (path == null) {
            return "";
        }
        String candidate = path.startsWith("/") ? path.substring(1) : path;
        return candidate.equals("/") ? "" : candidate;
    }
}
