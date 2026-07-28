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
import jakarta.ws.rs.core.Response;
import java.io.InputStream;
import java.net.URL;

/**
 * JAX-RS endpoint serving static web resources (.html, .js, .wasm, .css, images) from classpath location {@code /META-INF/resources/}.
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>Classpath Resource Loading:</b> Locates resources using {@code getClass().getResource("/META-INF/resources/" + path)}.</li>
 *   <li><b>MIME Type Resolution:</b> Dynamically infers Content-Type header based on target file extension (HTML, JS, CSS, PNG, ICO, SVG).</li>
 * </ul>
 */
@Path("/")
public class StaticContentResource {

    /**
     * Serves a static resource matching the path relative to {@code /META-INF/resources/}.
     * Defaults to {@code index.html} when path is empty or "/".
     *
     * @param path relative resource path
     * @return JAX-RS {@link Response} containing input stream and appropriate Content-Type header
     *
     * <p><b>Under the hood:</b> Resolves resource URL via classpath loader. If resource is missing, returns 404 NOT_FOUND.
     * Evaluates extension string, opens InputStream, and returns 200 OK with stream.</p>
     */
    @GET
    @Path("{path: .*}")
    public Response serve(@PathParam("path") String path) {
        if (path == null || path.isEmpty() || path.equals("/")) {
            path = "index.html";
        }
        URL resource = getClass().getResource("/META-INF/resources/" + path);
        if (resource == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        try {
            InputStream is = resource.openStream();
            String contentType = "application/octet-stream";
            if (path.endsWith(".html")) contentType = "text/html";
            else if (path.endsWith(".js")) contentType = "application/javascript";
            else if (path.endsWith(".css")) contentType = "text/css";
            else if (path.endsWith(".png")) contentType = "image/png";
            else if (path.endsWith(".ico")) contentType = "image/x-icon";
            else if (path.endsWith(".svg")) contentType = "image/svg+xml";
            return Response.ok(is, contentType).build();
        } catch (Exception e) {
            return Response.serverError().build();
        }
    }
}
