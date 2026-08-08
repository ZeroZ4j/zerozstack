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

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.io.InputStream;

/**
 * Serves the client bundle and the application shell over JAX-RS.
 *
 * <p>Every rule it applies — which resource answers a path, the shell fallback for client routes,
 * content types, the client-id cookie — lives in {@link StaticContent}, shared with the servlet
 * binding so the two cannot drift.</p>
 *
 * <p><b>This class is a catch-all at the application root.</b> That is right for a standalone server
 * and wrong inside a WAR that has its own servlets, which is exactly why it lives in this module
 * rather than in {@code zerozstack-server-core}: a deployment that serves its own content simply does
 * not depend on {@code zerozstack-server-jaxrs}.</p>
 */
@Path("/")
public class StaticContentResource {

    @Context
    private HttpHeaders httpHeaders;

    @Context
    private UriInfo uriInfo;

    /** Instantiated by the JAX-RS runtime per request, not by application code. */
    public StaticContentResource() {
        // Context fields are injected after construction.
    }

    /**
     * Serves a static resource, or the application shell for a path the client router owns.
     *
     * @param path the requested path, relative to {@code /META-INF/resources/}
     * @return the resource with its content type, or 404 when the path asked for a file that is not
     *         there
     */
    @GET
    @Path("{path: .*}")
    public Response serve(@PathParam("path") String path) {
        String resolved = StaticContent.resolve(path);
        if (resolved == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        InputStream content = StaticContent.open(resolved);
        if (content == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        String contentType = StaticContent.contentType(resolved);
        Response.ResponseBuilder response = Response.ok(content, contentType);

        String cookie = StaticContent.clientIdCookieFor(contentType,
                httpHeaders == null ? null : httpHeaders.getHeaderString(HttpHeaders.COOKIE),
                uriInfo == null || uriInfo.getRequestUri() == null
                        ? null : uriInfo.getRequestUri().getScheme());
        if (cookie != null) {
            response.header("Set-Cookie", cookie);
        }
        return response.build();
    }
}
