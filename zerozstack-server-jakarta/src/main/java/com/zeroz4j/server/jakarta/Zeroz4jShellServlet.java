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
package com.zeroz4j.server.jakarta;

import com.zeroz4j.server.StaticContent;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Serves the client bundle and the application shell from a WAR.
 *
 * <p>The servlet counterpart to the JAX-RS resource in {@code zerozstack-server-jaxrs}, applying the
 * same rules from {@link StaticContent} — so a deep link behaves identically whichever binding is
 * deployed.</p>
 *
 * <p><b>Deliberately not annotated with {@code @WebServlet}.</b> Where it is mapped is the
 * deployment's decision, not the framework's: a WAR with its own servlets must be able to take this
 * module without something claiming {@code /}. Map it explicitly:</p>
 *
 * <pre>{@code
 * <servlet>
 *   <servlet-name>zeroz-shell</servlet-name>
 *   <servlet-class>com.zeroz4j.server.jakarta.Zeroz4jShellServlet</servlet-class>
 * </servlet>
 * <servlet-mapping>
 *   <servlet-name>zeroz-shell</servlet-name>
 *   <url-pattern>/</url-pattern>
 * </servlet-mapping>
 * }</pre>
 *
 * <p>Mapped at {@code /} it becomes the default servlet, which is what makes a bookmarked
 * {@code /tasks/42} return the shell instead of a 404 — the case that otherwise works right up until
 * somebody reloads the page.</p>
 */
public class Zeroz4jShellServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    /** Instantiated by the servlet container from the mapping the deployment declares. */
    public Zeroz4jShellServlet() {
        // Container-instantiated; nothing to set up here.
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String path = request.getPathInfo() != null ? request.getPathInfo() : request.getServletPath();

        StaticContent.Assets assets = assetsFor(request);
        String resolved = StaticContent.resolve(path, assets);
        if (resolved == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String contentType = StaticContent.contentType(resolved);

        // The shell is the one resource that depends on where the application is deployed: it gets a
        // <base href> for this context path, so a deep link three segments down still finds
        // js/classes.js, and the client reads the application's own root from document.baseURI.
        byte[] shell = StaticContent.SHELL.equals(resolved)
                ? StaticContent.shellBytes(request.getContextPath(), assets) : null;
        InputStream content = shell != null ? null : assets.open(resolved);
        if (shell == null && content == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        response.setContentType(contentType);

        String cookie = StaticContent.clientIdCookieFor(
                contentType, request.getHeader("Cookie"), request.getScheme());
        if (cookie != null) {
            response.addHeader("Set-Cookie", cookie);
        }

        if (shell != null) {
            response.setCharacterEncoding("UTF-8");
            response.setContentLength(shell.length);
            try (OutputStream out = response.getOutputStream()) {
                out.write(shell);
            }
            return;
        }

        try (InputStream in = content; OutputStream out = response.getOutputStream()) {
            in.transferTo(out);
        }
    }

    /**
     * The classpath first, then the WAR's own web content.
     *
     * <p><b>Both, because a WAR has two plausible homes for a static file and only one of them is on
     * the classpath.</b> The framework's service worker and offline page travel inside
     * {@code zerozstack-server-core}, so they are classpath resources under
     * {@code /META-INF/resources/}. An application's {@code index.html} and client bundle normally
     * live in {@code src/main/webapp}, which lands in the archive root — and a WAR's classloader sees
     * {@code WEB-INF/classes} and {@code WEB-INF/lib}, not the root. Mapped at {@code /} this servlet
     * <em>replaces</em> the container's default servlet, so nothing else is left to serve them: a WAR
     * packaged the obvious way answered 404 to every request, its own shell included.</p>
     *
     * <p>Classpath first so that a jar-packaged asset cannot be shadowed by a file dropped into the
     * archive root, and so the framework's own two files always resolve.</p>
     */
    private static StaticContent.Assets assetsFor(HttpServletRequest request) {
        ServletContext context = request.getServletContext();
        return new StaticContent.Assets() {

            @Override
            public boolean exists(String path) {
                return StaticContent.CLASSPATH.exists(path) || webResource(path) != null;
            }

            @Override
            public InputStream open(String path) {
                InputStream fromClasspath = StaticContent.CLASSPATH.open(path);
                return fromClasspath != null ? fromClasspath : webResource(path);
            }

            private InputStream webResource(String path) {
                if (context == null || path.startsWith("WEB-INF/") || path.startsWith("META-INF/")) {
                    // WEB-INF and META-INF are not public web content, and getResourceAsStream will
                    // happily hand them over. Serving web.xml would be a rather large mistake.
                    return null;
                }
                return context.getResourceAsStream("/" + path);
            }
        };
    }
}
