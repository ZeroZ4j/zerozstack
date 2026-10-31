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
package com.zeroz4j.server.vidocq;

import com.zeroz4j.server.DownloadHttpRequest;
import com.zeroz4j.server.DownloadResponse;
import com.zeroz4j.server.FileDownloadServer;
import io.vidocq.chappe.api.Body;
import io.vidocq.chappe.api.HttpMethod;
import io.vidocq.chappe.api.Request;
import io.vidocq.chappe.api.Response;
import io.vidocq.chappe.api.StatusCode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Serves application files at {@code /zeroz4j-download}, the address {@code FileDownloadResource}
 * serves on Helidon and {@code FileDownloadServlet} serves in a WAR.
 *
 * <p>All rules live in {@link FileDownloadServer}. A file slice is handed to Chappe as a file body, so
 * the server sends it from disk without holding it in memory.</p>
 *
 * @since 0.11.0
 */
final class DownloadHandler {

    private static final Logger LOG = Logger.getLogger(DownloadHandler.class.getName());

    static final String PATH = "/" + FileDownloadServer.DOWNLOAD_PATH;

    Response serve(Request request) {
        boolean head = request.method() == HttpMethod.HEAD;
        String method = head ? "HEAD" : request.method().name();
        DownloadResponse result;
        try {
            result = FileDownloadServer.respond(new DownloadHttpRequest() {
                @Override
                public String method() {
                    return method;
                }

                @Override
                public String header(String name) {
                    return request.header(name).orElse(null);
                }

                @Override
                public String queryParameter(String name) {
                    return request.queryParam(name).orElse(null);
                }
            });
        } catch (RuntimeException ex) {
            LOG.log(Level.SEVERE, "[zeroz4j] A download failed unexpectedly", ex);
            return Response.of(StatusCode.INTERNAL_SERVER_ERROR);
        }

        Response.Builder builder = Response.builder().status(StatusCode.of(result.getStatus()));
        for (Map.Entry<String, String> header : result.getHeaders().entrySet()) {
            // The body decides the length of everything but a HEAD, which has no body to ask.
            if (!"Content-Length".equalsIgnoreCase(header.getKey()) || head) {
                builder.header(header.getKey(), header.getValue());
            }
        }
        if (head || result.getStatus() == 304) {
            return builder.build();
        }
        if (result.getFile() != null) {
            return builder.body(result.getLength() == 0L ? Body.empty()
                    : Body.ofFile(result.getFile(), result.getOffset(), result.getLength())).build();
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            result.writeBody(bytes);
            return builder.body(bytes.toByteArray()).build();
        } catch (IOException ex) {
            return Response.of(StatusCode.INTERNAL_SERVER_ERROR);
        }
    }
}
