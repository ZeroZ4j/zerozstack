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
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.InputStream;

/**
 * The address a file is uploaded to, for a standalone server.
 *
 * <p>Uploads do not travel over the live WebSocket connection. That connection assembles a whole
 * message in memory before any code runs, so it could neither stream a file to disk nor report
 * progress, and it has a message size limit an upload would collide with. This address takes the
 * bytes as the request body and writes them straight to disk, so a large file never has to fit in
 * memory.</p>
 *
 * <p>The request carries nothing but the bytes and one header: the upload pass, minted seconds
 * earlier over the live connection. The file name, its type and the identity of the uploader all
 * come from the pass, so nothing in this request has to be believed.</p>
 *
 * <p><b>Every rule it applies lives in {@link UploadReceiver}</b> — the origin check, the pass, the
 * size limit, the temporary file and its deletion — shared with
 * {@code com.zeroz4j.server.jakarta.FileUploadServlet} so that a WAR and a standalone server cannot
 * answer the same request differently. This class does nothing but unwrap the HTTP.</p>
 *
 * <p>Like {@link StaticContentResource}, it lives in {@code zerozstack-server-jaxrs} rather than in
 * {@code zerozstack-server-core}, because a WAR that serves its own content must be able to depend
 * on the framework without acquiring JAX-RS resources.</p>
 */
@Path(UploadReceiver.UPLOAD_PATH)
public class FileUploadResource {

    @Context
    private HttpHeaders httpHeaders;

    /** Instantiated by the JAX-RS runtime per request, not by application code. */
    public FileUploadResource() {
        // Context fields are injected after construction.
    }

    /**
     * Receives one file.
     *
     * @param body the raw bytes of the file
     * @return 200 and a sentence when the application kept the file; 4xx or 5xx and a sentence when
     *         it did not. The body is always plain text written for a non-technical reader, because
     *         the component shows it verbatim.
     */
    @POST
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.TEXT_PLAIN + ";charset=UTF-8")
    public Response upload(InputStream body) {
        UploadOutcome outcome = UploadReceiver.receive(new UploadRequest() {

            @Override
            public String header(String name) {
                return httpHeaders == null ? null : httpHeaders.getHeaderString(name);
            }

            @Override
            public InputStream body() {
                return body;
            }
        });
        return Response.status(outcome.getStatus()).entity(outcome.getMessage()).build();
    }
}
