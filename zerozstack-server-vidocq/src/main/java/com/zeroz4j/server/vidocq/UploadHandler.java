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

import com.zeroz4j.server.UploadOutcome;
import com.zeroz4j.server.UploadReceiver;
import com.zeroz4j.server.UploadRequest;
import io.vidocq.chappe.api.Body;
import io.vidocq.chappe.api.HttpMethod;
import io.vidocq.chappe.api.Request;
import io.vidocq.chappe.api.Response;
import io.vidocq.chappe.api.StatusCode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Receives an uploaded file at {@code /zeroz4j-upload}, the address {@code FileUploadResource}
 * serves under Helidon.
 *
 * <p>Every rule it applies lives in {@link UploadReceiver}: the origin check, the pass, the size
 * limit ({@code zeroz.upload.maxBytes}), the temporary file and its deletion. This class does
 * nothing but unwrap the Chappe request, so this binding cannot answer the same request differently
 * from the others.</p>
 */
final class UploadHandler {

    private static final Logger LOG = Logger.getLogger(UploadHandler.class.getName());

    /** The path an upload is posted to, as the browser derives it from the shell. */
    static final String PATH = "/" + UploadReceiver.UPLOAD_PATH;

    /** Answers a POST. */
    Response receive(Request request) {
        if (request.method() != HttpMethod.POST) {
            return Response.builder().status(StatusCode.METHOD_NOT_ALLOWED).header("Allow", "POST").build();
        }
        UploadOutcome outcome;
        try {
            outcome = UploadReceiver.receive(new UploadRequest() {
                @Override
                public String header(String name) {
                    return request.header(name).orElse(null);
                }

                @Override
                public InputStream body() throws IOException {
                    return request.body().asInputStream();
                }
            });
        } catch (RuntimeException ex) {
            LOG.log(Level.SEVERE, "[zeroz4j] An upload failed unexpectedly", ex);
            outcome = UploadOutcome.refused(500, "Something went wrong on our side. Please try again.");
        }
        return Response.builder().status(StatusCode.of(outcome.getStatus()))
                .header("Content-Type", "text/plain;charset=UTF-8")
                .body(Body.of(outcome.getMessage().getBytes(StandardCharsets.UTF_8)))
                .build();
    }
}
