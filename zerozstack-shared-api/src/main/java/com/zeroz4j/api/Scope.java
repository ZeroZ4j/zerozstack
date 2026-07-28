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
package com.zeroz4j.api;

/**
 * How far a server-to-client push reaches.
 *
 * <p>One concept shared by every push mechanism — LiveSync updates and server events alike — so that
 * "who receives this" is asked and answered the same way regardless of which one you are using.</p>
 *
 * <p>Choosing a scope is a security decision, not a performance one. {@link #GLOBAL} reaches every
 * connected session with no principal check, so anything belonging to one user or one tenant must be
 * scoped explicitly.</p>
 */
public enum Scope {

    /**
     * Every connected session.
     *
     * <p>No principal or tenant filter is applied. Never use this for data belonging to a particular
     * user or tenant.</p>
     */
    GLOBAL,

    /**
     * One WebSocket session, identified by its session id.
     *
     * <p>The narrowest scope: a single browser tab. Use it for a reply meant only for the client that
     * caused it, such as a corrective sync after a rejected write.</p>
     */
    SESSION,

    /**
     * Every session belonging to one authenticated principal, identified by user name.
     *
     * <p>Reaches the same person's other tabs and devices, and nobody else's. This is the scope for
     * per-user data.</p>
     */
    USER,

    /**
     * Every session belonging to one tenant, identified by tenant id.
     *
     * <p>Requires an {@code AuthenticationProvider} that reports a tenant on the authenticated
     * principal. A session with no tenant — anonymous, or authenticated by a provider that does not
     * set one — never matches, so a tenant-scoped push cannot leak to an unauthenticated connection by
     * default.</p>
     */
    TENANT
}
