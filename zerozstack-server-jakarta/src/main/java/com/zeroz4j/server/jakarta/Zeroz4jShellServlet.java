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

        String resolved = StaticContent.resolve(path);
        if (resolved == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        InputStream content = StaticContent.open(resolved);
        if (content == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String contentType = StaticContent.contentType(resolved);
        response.setContentType(contentType);

        String cookie = StaticContent.clientIdCookieFor(
                contentType, request.getHeader("Cookie"), request.getScheme());
        if (cookie != null) {
            response.addHeader("Set-Cookie", cookie);
        }

        try (InputStream in = content; OutputStream out = response.getOutputStream()) {
            in.transferTo(out);
        }
    }
}
