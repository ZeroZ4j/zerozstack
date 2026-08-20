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
package com.zeroz4j.server.jakarta;

import com.zeroz4j.server.UploadOutcome;
import com.zeroz4j.server.UploadReceiver;
import com.zeroz4j.server.UploadRequest;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * The address a file is uploaded to, for a WAR.
 *
 * <p>The servlet counterpart to the JAX-RS resource in {@code zerozstack-server-jaxrs}. Both do the
 * same nothing: read headers, hand over the body stream, write back the answer. Every rule — the
 * origin check, the one-time pass, the size limit enforced twice, the framework-named temporary file
 * and its deletion — lives in {@link UploadReceiver} and is therefore identical in both deployment
 * shapes, down to the status codes and the wording.</p>
 *
 * <h2>Why this one maps itself and {@link Zeroz4jShellServlet} does not</h2>
 * <p>The shell servlet is deliberately unmapped because the only mapping that makes it useful is
 * {@code /}, and claiming {@code /} inside a WAR that has its own servlets takes the default servlet
 * out from under an application that never asked for it. Where it goes has to be the deployment's
 * decision.</p>
 *
 * <p>This servlet has the opposite shape. It claims one exact path,
 * {@code /zeroz4j-upload} — framework-reserved in the same way {@code /wasm-rmi} is, and no more
 * likely to collide with an application path than that endpoint is. And unlike the shell, there is
 * only one mapping that can possibly work: the browser derives the upload address from the shell's
 * {@code <base href>} plus {@link UploadReceiver#UPLOAD_PATH}, so a deployment that mapped this
 * anywhere else would simply have broken uploads. Requiring a {@code web.xml} entry would therefore
 * buy no flexibility and cost a silent failure — the component would look fine and every upload
 * would 404 — which is exactly the failure mode this servlet exists to remove.</p>
 *
 * <p>A deployment that genuinely needs that path for itself can still take it: a {@code web.xml}
 * entry for the servlet name {@code zeroz4j-upload} overrides the annotated mapping, and
 * {@code metadata-complete="true"} ignores annotations altogether. Uploads then stop working, which
 * is the honest outcome of taking the address away.</p>
 */
@WebServlet(name = "zeroz4j-upload", urlPatterns = "/" + UploadReceiver.UPLOAD_PATH)
public class FileUploadServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    /** Instantiated by the servlet container from the {@code @WebServlet} mapping. */
    public FileUploadServlet() {
        // Container-instantiated; nothing to set up here.
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        UploadOutcome outcome = UploadReceiver.receive(new UploadRequest() {

            @Override
            public String header(String name) {
                return request.getHeader(name);
            }

            @Override
            public InputStream body() throws IOException {
                return request.getInputStream();
            }
        });

        // Written by hand rather than through sendError: sendError replaces the body with the
        // container's own HTML error page, and this body is a sentence the upload box shows the
        // person who chose the file.
        byte[] message = outcome.getMessage().getBytes(StandardCharsets.UTF_8);
        response.setStatus(outcome.getStatus());
        response.setContentType("text/plain;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setContentLength(message.length);
        try (OutputStream out = response.getOutputStream()) {
            out.write(message);
        }
    }
}
