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

import com.zeroz4j.server.SignInAnswer;
import com.zeroz4j.server.SignInRequest;
import com.zeroz4j.server.SignInSessions;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;

/**
 * The sign-in addresses for a WAR: {@code POST <context>/zeroz4j-session/sign-in}, {@code /sign-out}
 * and {@code /renew}.
 *
 * <p>The servlet counterpart to {@code com.zeroz4j.server.SignInResource}. Every rule lives in
 * {@link SignInSessions}, so a WAR and a standalone server answer the same request the same way.
 * It maps itself for the same reason {@link FileUploadServlet} does: the {@code Login} form derives
 * this address from the shell's {@code <base href>}, so no other mapping can work. It reads the body
 * as a stream and never calls {@code getParameter}, which would consume it.</p>
 *
 * @since 0.11.0
 */
@WebServlet(name = "zeroz4j-session", urlPatterns = "/" + SignInSessions.PATH + "/*")
public class SignInServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    /** Instantiated by the servlet container from the {@code @WebServlet} mapping. */
    public SignInServlet() {
        // Container-instantiated; nothing to set up here.
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        String info = request.getPathInfo();
        String action = info == null ? "" : info.startsWith("/") ? info.substring(1) : info;

        SignInAnswer answer = SignInSessions.current().handle(action, new SignInRequest() {
            @Override
            public String header(String name) {
                return request.getHeader(name);
            }

            @Override
            public InputStream body() throws IOException {
                return request.getInputStream();
            }

            @Override
            public String scheme() {
                return request.getScheme();
            }

            @Override
            public String contextPath() {
                return request.getContextPath();
            }
        });

        response.setStatus(answer.status());
        response.setHeader("Cache-Control", "no-store");
        if (answer.location() != null) {
            response.setHeader("Location", answer.location());
        }
        for (String cookie : answer.cookies()) {
            response.addHeader("Set-Cookie", cookie);
        }
    }
}
