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

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

/** Application-style REST resource used by the real-socket tests. */
@Path("/rest-test")
public class RestTestResource {

    static final List<String> LOGGED = new CopyOnWriteArrayList<>();

    @Inject
    Greeter greeter;

    @POST
    @Path("/client-log")
    @Consumes(MediaType.TEXT_PLAIN)
    public Response clientLog(@QueryParam("level") String level, String body) {
        LOGGED.add(level + ":" + body);
        return Response.noContent().build();
    }

    @GET
    @Path("/echo/{name}")
    @Produces(MediaType.TEXT_PLAIN)
    public String echo(@PathParam("name") String name) {
        return "echo " + name;
    }

    @GET
    @Path("/greet/{name}")
    @Produces(MediaType.TEXT_PLAIN)
    public String greet(@PathParam("name") String name) {
        return greeter.greet(name);
    }

    @GET
    @Path("/context")
    @Produces(MediaType.TEXT_PLAIN)
    public String context(@Context HttpHeaders headers, @Context UriInfo uriInfo) {
        return headers.getHeaderString("X-Probe") + "|" + uriInfo.getPath()
                + "|" + uriInfo.getQueryParameters().getFirst("q");
    }

    @GET
    @Path("/missing")
    public Response missing() {
        return Response.status(404).entity("resource says no").build();
    }
}
