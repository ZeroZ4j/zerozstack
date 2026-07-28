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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The outcome of a successful authentication: who the caller is, what they may do, and which tenant
 * they belong to.
 *
 * <p>The tenant is carried here rather than resolved later because a connection's tenant is decided
 * at the same moment as its identity. It is what makes {@link com.zeroz4j.api.Scope#TENANT} possible:
 * without an authenticated tenant, a session has no tenant, and a tenant-scoped push has nothing to
 * filter on.</p>
 */
public final class AuthenticatedPrincipal {

    private final String name;
    private final Set<String> roles;
    private final String tenantId;

    /**
     * @param name     the user name; must not be null or blank
     * @param roles    granted roles; null is treated as none
     * @param tenantId the tenant this user belongs to, or null in a single-tenant application
     */
    public AuthenticatedPrincipal(String name, Set<String> roles, String tenantId) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("An authenticated principal must have a name");
        }
        this.name = name;
        this.roles = roles == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(roles));
        this.tenantId = tenantId;
    }

    /**
     * Convenience for single-tenant applications.
     *
     * @param name  the user name
     * @param roles granted roles
     */
    public AuthenticatedPrincipal(String name, Set<String> roles) {
        this(name, roles, null);
    }

    /**
     * @return the user name, as reported by {@code RmiRequestContext.getPrincipal()}
     */
    public String name() {
        return name;
    }

    /**
     * @return the granted roles, never null; checked by {@code @RolesAllowed} and
     *         {@code @ClientWritable}
     */
    public Set<String> roles() {
        return roles;
    }

    /**
     * @return the tenant this session belongs to, or null when the application is single-tenant. A
     *         session with no tenant never matches a {@link com.zeroz4j.api.Scope#TENANT} push.
     */
    public String tenantId() {
        return tenantId;
    }

    @Override
    public String toString() {
        return "AuthenticatedPrincipal[" + name
                + ", roles=" + roles
                + (tenantId == null ? "" : ", tenant=" + tenantId) + "]";
    }
}
