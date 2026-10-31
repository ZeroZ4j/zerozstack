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
 * One request for a file, with the identity of the live connection the address was issued to.
 *
 * <p>The identity is the one the server recorded when it issued the address, so a stranger who finds
 * the address cannot change it, and a request made later still carries the roles the person had
 * then. Check it on every call.</p>
 *
 * @since 0.11.0
 */
public final class DownloadRequest {

    private final String kind;
    private final String id;
    private final Principal principal;
    private final Set<String> roles;
    private final String tenantId;
    private final String clientId;
    private final String sessionId;

    DownloadRequest(String kind, String id, Principal principal, Set<String> roles, String tenantId,
                    String clientId, String sessionId) {
        this.kind = kind;
        this.id = id;
        this.principal = principal;
        this.roles = roles == null ? Collections.emptySet() : roles;
        this.tenantId = tenantId;
        this.clientId = clientId;
        this.sessionId = sessionId;
    }

    /** @return the kind the page asked for, empty for the default handler */
    public String getKind() {
        return kind;
    }

    /**
     * The id the page asked for. Text from the browser: look it up, never use it as a path.
     *
     * @return the id, never null
     */
    public String getId() {
        return id;
    }

    /** @return the authenticated caller, or null when the connection was anonymous */
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

    /** @return the browser the address was issued to, or null */
    public String getClientId() {
        return clientId;
    }

    /** @return the WebSocket session id the address was issued to */
    public String getSessionId() {
        return sessionId;
    }
}
