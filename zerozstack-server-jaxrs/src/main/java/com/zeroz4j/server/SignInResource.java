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

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.io.InputStream;

/**
 * The sign-in addresses for a standalone server: {@code POST /zeroz4j-session/sign-in},
 * {@code /sign-out} and {@code /renew}.
 *
 * <p><b>Every rule lives in {@link SignInSessions},</b> shared with
 * {@code com.zeroz4j.server.jakarta.SignInServlet} and the Vidocq binding. This class does nothing
 * but unwrap the HTTP. With no {@link SignInHandler} in the application it answers 404.</p>
 *
 * @since 0.11.0
 */
@Path(SignInSessions.PATH)
public class SignInResource {

    @Context
    private HttpHeaders httpHeaders;

    @Context
    private UriInfo uriInfo;

    /** Instantiated by the JAX-RS runtime per request, not by application code. */
    public SignInResource() {
        // Context fields are injected after construction.
    }

    /**
     * Answers one request.
     *
     * @param action what follows {@code /zeroz4j-session/}
     * @param body   the request body
     * @return the status, redirect and cookies {@link SignInSessions} decided on
     */
    @POST
    @Path("{action}")
    @Consumes(MediaType.WILDCARD)
    public Response post(@PathParam("action") String action, InputStream body) {
        String basePath = uriInfo.getBaseUri().getPath();
        String context = basePath == null || "/".equals(basePath) ? ""
                : basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;

        SignInAnswer answer = SignInSessions.current().handle(action, new SignInRequest() {
            @Override
            public String header(String name) {
                return httpHeaders.getHeaderString(name);
            }

            @Override
            public InputStream body() {
                return body;
            }

            @Override
            public String scheme() {
                return uriInfo.getBaseUri().getScheme();
            }

            @Override
            public String contextPath() {
                return context;
            }
        });

        Response.ResponseBuilder response = Response.status(answer.status())
                .header(HttpHeaders.CACHE_CONTROL, "no-store");
        if (answer.location() != null) {
            response.header(HttpHeaders.LOCATION, answer.location());
        }
        for (String cookie : answer.cookies()) {
            response.header(HttpHeaders.SET_COOKIE, cookie);
        }
        return response.build();
    }
}
