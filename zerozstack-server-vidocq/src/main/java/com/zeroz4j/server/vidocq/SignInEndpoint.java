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

import com.zeroz4j.server.SignInAnswer;
import com.zeroz4j.server.SignInRequest;
import com.zeroz4j.server.SignInSessions;
import io.vidocq.chappe.api.HttpMethod;
import io.vidocq.chappe.api.Request;
import io.vidocq.chappe.api.Response;
import io.vidocq.chappe.api.StatusCode;

import java.io.IOException;
import java.io.InputStream;

/**
 * Answers {@code /zeroz4j-session/...}, the addresses {@code SignInResource} serves under Helidon.
 * Every rule lives in {@link SignInSessions}; this class does nothing but unwrap the Chappe request.
 */
final class SignInEndpoint {

    /** The paths this class answers start with this. */
    static final String PREFIX = "/" + SignInSessions.PATH + "/";

    /** Answers a POST. */
    Response answer(Request request) {
        if (request.method() != HttpMethod.POST) {
            return Response.builder().status(StatusCode.METHOD_NOT_ALLOWED).header("Allow", "POST").build();
        }
        SignInAnswer answer = SignInSessions.current().handle(
                request.path().substring(PREFIX.length()), new SignInRequest() {
                    @Override
                    public String header(String name) {
                        return request.header(name).orElse(null);
                    }

                    @Override
                    public InputStream body() throws IOException {
                        return request.body().asInputStream();
                    }

                    @Override
                    public String scheme() {
                        return request.isSecure() ? "https" : "http";
                    }

                    @Override
                    public String contextPath() {
                        return "";
                    }
                });
        Response.Builder response = Response.builder().status(StatusCode.of(answer.status()))
                .header("Cache-Control", "no-store");
        if (answer.location() != null) {
            response.header("Location", answer.location());
        }
        for (String cookie : answer.cookies()) {
            response.header("Set-Cookie", cookie);
        }
        return response.build();
    }
}
