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
import java.util.List;
import java.util.Map;

/**
 * What the WebSocket handshake carried, handed to an {@link AuthenticationProvider}.
 *
 * <p>A read-only view over the handshake request, so a provider needs no dependency on the Jakarta
 * WebSocket API and can be unit-tested without a container.</p>
 */
public final class HandshakeCredentials {

    private final Map<String, List<String>> parameters;
    private final Map<String, List<String>> headers;
    private final Principal containerPrincipal;

    /**
     * @param parameters         query parameters from the handshake URL
     * @param headers            handshake request headers
     * @param containerPrincipal the principal the servlet or WebSocket container already
     *                           authenticated, or null
     */
    public HandshakeCredentials(Map<String, List<String>> parameters,
                                Map<String, List<String>> headers,
                                Principal containerPrincipal) {
        this.parameters = parameters == null ? Collections.emptyMap() : parameters;
        this.headers = headers == null ? Collections.emptyMap() : headers;
        this.containerPrincipal = containerPrincipal;
    }

    /**
     * @param name parameter name
     * @return the first value of a query parameter, or null when absent
     */
    public String parameter(String name) {
        return first(parameters, name);
    }

    /**
     * @param name header name
     * @return the first value of a handshake header, or null when absent
     */
    public String header(String name) {
        return first(headers, name);
    }

    /**
     * @return all query parameters; never null
     */
    public Map<String, List<String>> parameters() {
        return parameters;
    }

    /**
     * @return all handshake headers; never null
     */
    public Map<String, List<String>> headers() {
        return headers;
    }

    /**
     * The principal the container authenticated before the upgrade, if any.
     *
     * <p>Present when the deployment sits behind container-managed security. A provider may accept it
     * as-is, enrich it with roles and a tenant, or ignore it.</p>
     *
     * @return the container principal, or null
     */
    public Principal containerPrincipal() {
        return containerPrincipal;
    }

    private static String first(Map<String, List<String>> map, String name) {
        List<String> values = map.get(name);
        return values != null && !values.isEmpty() ? values.get(0) : null;
    }
}
