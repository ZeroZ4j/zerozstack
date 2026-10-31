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

import com.zeroz4j.server.DownloadHttpRequest;
import com.zeroz4j.server.DownloadResponse;
import com.zeroz4j.server.FileDownloadServer;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

/**
 * Serves application files at {@code /zeroz4j-download} inside a WAR.
 *
 * <p>All rules live in {@link FileDownloadServer}, which {@code FileDownloadResource} uses as well on
 * a standalone server, so both answer the same request identically. The servlet maps itself, the way
 * {@link FileUploadServlet} does; a {@code web.xml} entry for the servlet name
 * {@code zeroz4j-download} overrides that.</p>
 *
 * @since 0.11.0
 */
@WebServlet(name = "zeroz4j-download", urlPatterns = "/" + FileDownloadServer.DOWNLOAD_PATH)
public class FileDownloadServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    /** Container-instantiated; nothing to set up here. */
    public FileDownloadServlet() {
        // Nothing to set up.
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        answer("GET", request, response);
    }

    @Override
    protected void doHead(HttpServletRequest request, HttpServletResponse response) throws IOException {
        answer("HEAD", request, response);
    }

    private void answer(String method, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        DownloadResponse result = FileDownloadServer.respond(new DownloadHttpRequest() {

            @Override
            public String method() {
                return method;
            }

            @Override
            public String header(String name) {
                return request.getHeader(name);
            }

            @Override
            public String queryParameter(String name) {
                return request.getParameter(name);
            }
        });

        response.setStatus(result.getStatus());
        for (Map.Entry<String, String> header : result.getHeaders().entrySet()) {
            if ("Content-Length".equalsIgnoreCase(header.getKey())) {
                response.setContentLengthLong(Long.parseLong(header.getValue()));
            } else if ("Content-Type".equalsIgnoreCase(header.getKey())) {
                response.setContentType(header.getValue());
            } else {
                response.setHeader(header.getKey(), header.getValue());
            }
        }
        if ("HEAD".equals(method)) {
            return;
        }
        try (OutputStream out = response.getOutputStream()) {
            result.writeBody(out);
        }
    }
}
