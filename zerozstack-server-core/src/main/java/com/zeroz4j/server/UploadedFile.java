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

import java.nio.file.Path;
import java.security.Principal;
import java.util.Collections;
import java.util.Set;

/**
 * A finished upload, handed to the application's {@link FileUploadHandler}.
 *
 * <p>The bytes are already on disk and complete when the handler runs. Nothing is streaming, nothing
 * is half-written, and the size has been checked twice.</p>
 *
 * <h2>What is trustworthy here and what is not</h2>
 * <p>{@link #getTempFile()}, {@link #getSizeBytes()} and every identity field come from the server
 * and can be relied on. {@link #getFileName()} and {@link #getContentType()} came from the browser
 * and cannot: they are carried through so the application can show a name and make a guess, and for
 * no other purpose.</p>
 */
public final class UploadedFile {

    private final Path tempFile;
    private final String fileName;
    private final String contentType;
    private final long sizeBytes;
    private final Principal principal;
    private final Set<String> roles;
    private final String tenantId;
    private final String clientId;
    private final String sessionId;

    UploadedFile(Path tempFile, String fileName, String contentType, long sizeBytes,
                 Principal principal, Set<String> roles, String tenantId, String clientId,
                 String sessionId) {
        this.tempFile = tempFile;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.principal = principal;
        this.roles = roles == null ? Collections.emptySet() : roles;
        this.tenantId = tenantId;
        this.clientId = clientId;
        this.sessionId = sessionId;
    }

    /**
     * The complete file, under a name the framework generated.
     *
     * <p>Read it, validate it, and move or copy it wherever it belongs. The framework deletes this
     * file when the handler returns — whether it returned a result or threw — so anything not moved
     * or copied is gone.</p>
     *
     * @return an existing, readable file
     */
    public Path getTempFile() {
        return tempFile;
    }

    /**
     * The name the browser reported for this file.
     *
     * <p><b>This is untrusted data. Never use it to build a path.</b> It is whatever the page sent,
     * which may be {@code ../../etc/passwd}, may contain a null byte or a newline, may be
     * {@code CON} or {@code NUL} (device names on Windows), and may be several kilobytes long. The
     * framework has already named the temporary file itself for exactly this reason; if the
     * application wants to keep the name, store it as data — a database column, a field beside the
     * file — never as a path segment.</p>
     *
     * <p>It is safe to <em>show</em>, provided it is escaped like any other user-supplied text.</p>
     *
     * @return the reported name, never null but possibly empty
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * The content type the browser reported.
     *
     * <p><b>Untrusted, and proof of nothing.</b> A browser derives it from the file extension, so
     * {@code image/png} means the name ended in {@code .png} and nothing more. An application that
     * needs to know what the bytes are must look at the bytes.</p>
     *
     * @return the reported type, never null but possibly empty
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * The size of {@link #getTempFile()}, counted by the server as the bytes arrived.
     *
     * @return the size in bytes
     */
    public long getSizeBytes() {
        return sizeBytes;
    }

    /**
     * Who uploaded this, taken from the live connection that asked for the upload pass.
     *
     * @return the authenticated caller, or null when the connection was anonymous
     */
    public Principal getPrincipal() {
        return principal;
    }

    /** @return the roles that caller held, never null */
    public Set<String> getRoles() {
        return roles;
    }

    /** @return the tenant the caller belongs to, or null in a single-tenant application */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * The browser the upload came from.
     *
     * <p>Identifies a browser, not a person — see {@link RmiRequestContext#getClientId()}.</p>
     *
     * @return the client id, or null
     */
    public String getClientId() {
        return clientId;
    }

    /** @return the WebSocket session id the upload pass was issued to */
    public String getSessionId() {
        return sessionId;
    }
}
