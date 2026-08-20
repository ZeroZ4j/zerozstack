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

import java.security.Principal;
import java.util.Collections;
import java.util.Set;

/**
 * One issued permission to upload one file: who asked, what they said they were sending, and until
 * when.
 *
 * <p>Everything here was captured from the live connection at the moment the pass was issued, and is
 * therefore as trustworthy as that connection's login. The file name and content type are the
 * exception — they came from the browser and are recorded, not believed.</p>
 *
 * <p>Framework-internal; created and consumed by {@link UploadPasses}.</p>
 */
public final class UploadPass {

    private final String token;
    private final Principal principal;
    private final Set<String> roles;
    private final String sessionId;
    private final String tenantId;
    private final String clientId;
    private final String fileName;
    private final String contentType;
    private final long declaredSize;
    private final long expiresAt;

    UploadPass(String token, Principal principal, Set<String> roles, String sessionId,
               String tenantId, String clientId, String fileName, String contentType,
               long declaredSize, long expiresAt) {
        this.token = token;
        this.principal = principal;
        this.roles = roles == null ? Collections.emptySet()
                : Collections.unmodifiableSet(new java.util.LinkedHashSet<>(roles));
        this.sessionId = sessionId;
        this.tenantId = tenantId;
        this.clientId = clientId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.declaredSize = declaredSize;
        this.expiresAt = expiresAt;
    }

    /** @return the opaque token the browser sends back with the file */
    public String getToken() {
        return token;
    }

    /** @return the authenticated caller who asked for this pass, or null when anonymous */
    public Principal getPrincipal() {
        return principal;
    }

    /** @return the roles that caller held, never null */
    public Set<String> getRoles() {
        return roles;
    }

    /** @return the WebSocket session id the pass was issued to */
    public String getSessionId() {
        return sessionId;
    }

    /** @return the tenant the caller belongs to, or null */
    public String getTenantId() {
        return tenantId;
    }

    /** @return the browser id the pass was issued to, or null when the handshake carried none */
    public String getClientId() {
        return clientId;
    }

    /**
     * The name the browser reported for the file.
     *
     * <p><b>Untrusted.</b> It is whatever the page sent, up to and including {@code ../../etc/passwd}
     * or a name containing a null byte. Never build a path from it.</p>
     *
     * @return the reported name, never null
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * The content type the browser reported.
     *
     * <p><b>Untrusted.</b> A browser derives it from the file extension; it proves nothing about the
     * bytes.</p>
     *
     * @return the reported type, never null
     */
    public String getContentType() {
        return contentType;
    }

    /** @return the size the browser reported, already checked against the configured maximum */
    public long getDeclaredSize() {
        return declaredSize;
    }

    /** @return the epoch millisecond after which this pass is refused */
    public long getExpiresAt() {
        return expiresAt;
    }

    /**
     * Whether this pass is past its expiry.
     *
     * @param nowMillis the current time
     * @return true when it may no longer be used
     */
    public boolean isExpired(long nowMillis) {
        return nowMillis > expiresAt;
    }
}
