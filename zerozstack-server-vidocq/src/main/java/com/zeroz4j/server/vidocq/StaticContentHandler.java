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

import com.zeroz4j.server.StaticContent;
import io.vidocq.chappe.api.Body;
import io.vidocq.chappe.api.HttpMethod;
import io.vidocq.chappe.api.Request;
import io.vidocq.chappe.api.Response;
import io.vidocq.chappe.api.StatusCode;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Serves the client bundle and the application shell, the way {@code StaticContentResource} does
 * under Helidon.
 *
 * <p>Every rule it applies lives in {@link StaticContent}: which resource answers a path, the shell
 * fallback for client routes, content types, and the browser-id cookie. This class only unwraps the
 * Chappe request and wraps the answer. The cookie is set on the HTML page because Chappe cannot put
 * a header on the WebSocket's 101 response, so the page is the one place it can be set.</p>
 */
final class StaticContentHandler {

    /** Answers a GET or HEAD request; anything else is a 405. */
    Response serve(Request request) throws IOException {
        HttpMethod method = request.method();
        boolean head = method == HttpMethod.HEAD;
        if (method != HttpMethod.GET && !head) {
            return Response.builder().status(StatusCode.METHOD_NOT_ALLOWED).header("Allow", "GET, HEAD").build();
        }

        String resolved = StaticContent.resolve(pathOf(request));
        if (resolved == null) {
            return Response.of(StatusCode.NOT_FOUND);
        }
        byte[] bytes;
        if (StaticContent.SHELL.equals(resolved)) {
            // This binding is a catch-all at the application root, so the shell's <base href> is "/".
            bytes = StaticContent.shellBytes("/");
        } else {
            try (InputStream in = StaticContent.open(resolved)) {
                bytes = in == null ? null : in.readAllBytes();
            }
        }
        if (bytes == null) {
            return Response.of(StatusCode.NOT_FOUND);
        }

        String contentType = StaticContent.contentType(resolved);
        Response.Builder response = Response.builder().status(StatusCode.OK)
                .header("Content-Type", contentType)
                .body(head ? Body.empty() : Body.of(bytes));
        String cookie = StaticContent.clientIdCookieFor(contentType,
                request.header("Cookie").orElse(null), request.scheme());
        if (cookie != null) {
            response.header("Set-Cookie", cookie);
        }
        return response.build();
    }

    /** The request path, percent-decoded, without its leading slash. */
    private static String pathOf(Request request) {
        String raw = request.path();
        String decoded;
        try {
            decoded = URLDecoder.decode(raw.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException malformed) {
            decoded = raw;
        }
        return decoded.startsWith("/") ? decoded.substring(1) : decoded;
    }
}
