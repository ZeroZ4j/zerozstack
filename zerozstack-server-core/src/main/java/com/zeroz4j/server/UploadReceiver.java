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

import jakarta.enterprise.inject.spi.CDI;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Receives one uploaded file: checks the pass, streams the bytes to a temporary file, hands it to
 * the application, and deletes it again.
 *
 * <p>This class carries no HTTP type at all, which is what lets it live in
 * {@code zerozstack-server-core} — the module that has to be safe inside a WAR it does not own. It
 * reaches a request through {@link UploadRequest}, so the two bindings — the JAX-RS resource in
 * {@code zerozstack-server-jaxrs} and the servlet in {@code zerozstack-server-jakarta} — supply a
 * header lookup and a stream and nothing else. <b>Every decision below is made here, once, for
 * both.</b> A rule that lived in a binding could be applied by one deployment shape and not the
 * other, which is the bug this shape exists to prevent.</p>
 *
 * <h2>The order things happen in, and why</h2>
 * <ol>
 *   <li><b>The origin is checked first</b>, by the same {@link OriginPolicy} rule the WebSocket
 *       handshake applies. Without it a page on another site could post files here using the
 *       browser's ambient cookie.</li>
 *   <li><b>The pass is checked next.</b> A request with no valid pass is refused before a temporary
 *       file is created, so an unauthenticated request cannot consume a single byte of disk.</li>
 *   <li><b>The declared length is checked next</b>, still before the body is read. An oversized
 *       upload is refused without transferring it.</li>
 *   <li><b>The count is checked again while streaming</b>, because a declared length is only
 *       something the client said. Reading stops the moment the limit is passed.</li>
 *   <li><b>The temporary file is named by the framework.</b> Nothing the browser sent reaches the
 *       filesystem.</li>
 *   <li><b>The temporary file is deleted in a finally block</b>, so a handler that throws, a
 *       cancelled upload and a connection that died all leave nothing behind.</li>
 * </ol>
 *
 * <p>Framework-internal.</p>
 */
public final class UploadReceiver {

    private static final Logger LOG = Logger.getLogger(UploadReceiver.class.getName());

    /** 64 KB: large enough that the syscall count stops mattering, small enough to stay off the heap. */
    private static final int CHUNK = 64 * 1024;

    /**
     * The path an upload is posted to, relative to wherever the application is mounted.
     *
     * <p>Declared here because the browser derives this exact path from the shell's
     * {@code <base href>}, and both bindings must publish that exact path. Reserved by the
     * framework, like {@code wasm-rmi}.</p>
     */
    public static final String UPLOAD_PATH = "zeroz4j-upload";

    /**
     * The header carrying the one-time pass.
     *
     * <p>Declared here rather than in a binding because the browser sends this exact name and both
     * bindings must read that exact name.</p>
     */
    public static final String PASS_HEADER = "X-Zeroz4j-Upload-Pass";

    /** Set by tests that run without a CDI container. Null in every real deployment. */
    private static volatile List<FileUploadHandler> handlersForTesting;

    private UploadReceiver() {}

    /**
     * Receives one file.
     *
     * <p>The name and type of the file are read from the pass, which was issued over the
     * authenticated live connection — not from this request, which could claim anything.</p>
     *
     * @param request the incoming request, as a header lookup and a body
     * @return the status and sentence to answer with; the sentence is written for a non-technical
     *         reader in every case, including the failures, because the component shows it verbatim
     */
    public static UploadOutcome receive(UploadRequest request) {
        if (!OriginPolicy.isAllowed(request.header("Origin"), request.header("Host"))) {
            return UploadOutcome.refused(403,
                    "We could not accept that file. Reload the page and try again.");
        }

        UploadPass pass;
        try {
            String clientId = ClientIdentity.verify(
                    ClientIdentity.fromCookieHeader(request.header("Cookie")));
            pass = UploadPasses.consume(request.header(PASS_HEADER), clientId);
        } catch (UploadRefusedException refused) {
            // Nothing has been created yet, and deliberately so: this is the branch an
            // unauthenticated request takes, and it must not make the server write anything.
            return UploadOutcome.refused(refused.getStatus(), refused.getMessage());
        }

        long max = UploadLimits.maxBytes();
        long declaredLength = declaredLength(request);
        if (declaredLength > max) {
            return UploadOutcome.refused(413, tooBig());
        }
        if (declaredLength > pass.getDeclaredSize()) {
            // The pass was issued for a file of a known size. A request claiming more bytes than
            // that is not the file the pass was for.
            return UploadOutcome.refused(400,
                    "That file changed while it was being sent. Please choose it again.");
        }

        List<FileUploadHandler> handlers = handlers();
        if (handlers.isEmpty()) {
            LOG.severe("[zeroz4j] A file was uploaded but this application has no FileUploadHandler. "
                    + "Add an @ApplicationScoped bean implementing "
                    + "com.zeroz4j.server.FileUploadHandler.");
            return UploadOutcome.refused(501,
                    "This application cannot take files yet. Please tell whoever runs it.");
        }
        if (handlers.size() > 1) {
            StringBuilder names = new StringBuilder();
            for (FileUploadHandler handler : handlers) {
                if (names.length() > 0) {
                    names.append(", ");
                }
                names.append(handler.getClass().getName());
            }
            LOG.warning("[zeroz4j] More than one FileUploadHandler is deployed; using "
                    + handlers.get(0).getClass().getName() + ". Found: " + names);
        }

        Path temp;
        try {
            // createTempFile generates the name. The file name from the browser gets nowhere near it.
            temp = Files.createTempFile(UploadLimits.tempDirectory(), "zeroz4j-", ".upload");
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "[zeroz4j] Could not create a temporary file for an upload", ex);
            return UploadOutcome.refused(500, "Something went wrong on our side. Please try again.");
        }

        try {
            long written;
            try {
                written = copyCapped(request.body(), temp, max);
            } catch (SizeExceeded exceeded) {
                // The declared length lied, or there was none. Reading stopped at the limit.
                return UploadOutcome.refused(413, tooBig());
            } catch (IOException ex) {
                // A cancelled upload and a dropped connection both land here.
                LOG.fine("[zeroz4j] Upload did not finish: " + ex.getMessage());
                return UploadOutcome.refused(400,
                        "That file did not finish sending. Please try again.");
            }

            if (declaredLength >= 0L && written != declaredLength) {
                return UploadOutcome.refused(400,
                        "That file did not finish sending. Please try again.");
            }
            if (written != pass.getDeclaredSize()) {
                return UploadOutcome.refused(400,
                        "That file did not finish sending. Please try again.");
            }

            UploadedFile file = new UploadedFile(temp, pass.getFileName(), pass.getContentType(),
                    written, pass.getPrincipal(), pass.getRoles(), pass.getTenantId(),
                    pass.getClientId(), pass.getSessionId());

            UploadResult result;
            try {
                result = handlers.get(0).onFileUploaded(file);
            } catch (Exception ex) {
                LOG.log(Level.SEVERE, "[zeroz4j] FileUploadHandler threw while handling an upload of "
                        + written + " bytes", ex);
                return UploadOutcome.refused(500,
                        "Something went wrong on our side. Please try again.");
            }
            if (result == null) {
                LOG.warning("[zeroz4j] FileUploadHandler " + handlers.get(0).getClass().getName()
                        + " returned null; treating the file as accepted.");
                return UploadOutcome.ok("Done.");
            }
            return result.isAccepted()
                    ? UploadOutcome.ok(result.getMessage())
                    : UploadOutcome.refused(422, result.getMessage());
        } finally {
            deleteQuietly(temp);
        }
    }

    /**
     * The declared body length.
     *
     * <p>Parsed here rather than in a binding so that an absent, empty or malformed
     * {@code Content-Length} means the same thing in both: unknown, which the streaming cap catches
     * anyway.</p>
     *
     * @param request the incoming request
     * @return the declared length, or -1 when the request did not state a usable one
     */
    private static long declaredLength(UploadRequest request) {
        String declared = request.header("Content-Length");
        if (declared == null || declared.trim().isEmpty()) {
            return -1L;
        }
        try {
            return Long.parseLong(declared.trim());
        } catch (NumberFormatException ex) {
            return -1L;
        }
    }

    /**
     * Copies until the source ends, or until one byte past {@code max} has been seen.
     *
     * @param body   the source
     * @param target the file to write
     * @param max    the largest acceptable total
     * @return the number of bytes written
     * @throws IOException  when reading or writing failed
     * @throws SizeExceeded when the limit is passed; nothing further is read
     */
    private static long copyCapped(InputStream body, Path target, long max)
            throws IOException, SizeExceeded {
        long total = 0L;
        byte[] buffer = new byte[CHUNK];
        try (OutputStream out = Files.newOutputStream(target)) {
            int read;
            while ((read = body.read(buffer)) != -1) {
                total += read;
                if (total > max) {
                    throw new SizeExceeded();
                }
                out.write(buffer, 0, read);
            }
        }
        return total;
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            LOG.warning("[zeroz4j] Could not delete the temporary upload file " + path + ": "
                    + ex.getMessage());
        }
    }

    private static String tooBig() {
        return "That file is too big. The largest we can take is "
                + UploadLimits.describeMaxSize() + ".";
    }

    /** Every deployed {@link FileUploadHandler}, or the ones a test installed. */
    private static List<FileUploadHandler> handlers() {
        List<FileUploadHandler> injected = handlersForTesting;
        if (injected != null) {
            return injected;
        }
        List<FileUploadHandler> found = new ArrayList<>();
        try {
            for (FileUploadHandler handler : CDI.current().select(FileUploadHandler.class)) {
                found.add(handler);
            }
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "[zeroz4j] Could not look up a FileUploadHandler", ex);
        }
        return found;
    }

    /** Test support only: runs the receiver against handlers supplied directly, with no CDI. */
    static void setHandlersForTesting(List<FileUploadHandler> handlers) {
        handlersForTesting = handlers;
    }

    /** Signals that the cap was passed. Carries no stack trace: it is control flow, not a fault. */
    private static final class SizeExceeded extends Exception {
        private static final long serialVersionUID = 1L;

        SizeExceeded() {
            super(null, null, false, false);
        }
    }
}
