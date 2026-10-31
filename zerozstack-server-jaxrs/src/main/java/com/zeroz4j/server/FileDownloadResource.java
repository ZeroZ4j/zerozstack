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
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.util.Map;

/**
 * Serves application files at {@code /zeroz4j-download} on a standalone server.
 *
 * <p>All rules live in {@link FileDownloadServer}, which is shared with
 * {@code com.zeroz4j.server.jakarta.FileDownloadServlet} so that a WAR and a standalone server cannot
 * answer the same request differently. This class only moves a request in and a response out.</p>
 *
 * @since 0.11.0
 */
@Path(FileDownloadServer.DOWNLOAD_PATH)
public class FileDownloadResource {

    @Context
    private HttpHeaders httpHeaders;

    @Context
    private UriInfo uriInfo;

    @Context
    private jakarta.ws.rs.core.Request jaxrsRequest;

    /** Container-instantiated; nothing to set up here. */
    public FileDownloadResource() {
        // Context fields are injected after construction.
    }

    /**
     * Answers a GET: the whole file, or the byte range asked for. JAX-RS runs this method for a HEAD
     * as well and drops the body, so the headers of a HEAD are exactly those of the GET.
     *
     * @return the response
     */
    @GET
    public Response download() {
        return answer(jaxrsRequest == null ? "GET" : jaxrsRequest.getMethod());
    }

    private Response answer(String method) {
        DownloadResponse response = FileDownloadServer.respond(new DownloadHttpRequest() {

            @Override
            public String method() {
                return method;
            }

            @Override
            public String header(String name) {
                return httpHeaders == null ? null : httpHeaders.getHeaderString(name);
            }

            @Override
            public String queryParameter(String name) {
                return uriInfo == null ? null : uriInfo.getQueryParameters().getFirst(name);
            }
        });
        Response.ResponseBuilder builder = Response.status(response.getStatus());
        for (Map.Entry<String, String> header : response.getHeaders().entrySet()) {
            builder.header(header.getKey(), header.getValue());
        }
        return builder.entity((jakarta.ws.rs.core.StreamingOutput) response::writeBody).build();
    }
}
