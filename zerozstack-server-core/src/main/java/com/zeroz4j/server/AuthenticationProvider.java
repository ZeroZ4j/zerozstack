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

/**
 * Authenticates a WebSocket connection at handshake time. Implement this to replace the built-in
 * development authentication with something real.
 *
 * <p>Discovered through {@link java.util.ServiceLoader}, not CDI: the handshake runs before the
 * endpoint exists and outside a reliable CDI context. Declare your implementation in
 * {@code META-INF/services/com.zeroz4j.server.AuthenticationProvider}.</p>
 *
 * <pre>{@code
 * public final class JwtAuthProvider implements AuthenticationProvider {
 *     @Override
 *     public AuthenticatedPrincipal authenticate(HandshakeCredentials credentials) {
 *         String token = credentials.parameter("token");
 *         if (token == null) {
 *             return null;                     // leaves the session anonymous
 *         }
 *         Claims claims = verify(token);       // throw or return null to refuse
 *         return new AuthenticatedPrincipal(claims.subject(), claims.roles(), claims.tenant());
 *     }
 * }
 * }</pre>
 *
 * <p><b>Identity is fixed for the life of the connection.</b> Roles are read once at handshake, so a
 * user whose roles change must reconnect. That is a deliberate simplification, not an oversight —
 * working the identity out again on every frame would put that lookup on the busiest path there
 * is.</p>
 *
 * <p><b>This is the only authentication boundary.</b> Client-side checks are user feedback; every
 * {@code @Secured}, {@code @RolesAllowed} and {@code @ClientWritable} decision is made server-side
 * from what this method returns.</p>
 */
public interface AuthenticationProvider {

    /**
     * Authenticates a connection attempt.
     *
     * @param credentials whatever the handshake carried — query parameters, headers, and the
     *                    container's own principal if it already authenticated the request
     * @return the authenticated principal, or {@code null} to leave the connection anonymous
     * @throws RuntimeException to refuse the connection outright; the exception is logged and the
     *         session is left anonymous rather than the handshake being failed, because a rejected
     *         upgrade gives the client no way to report why
     */
    AuthenticatedPrincipal authenticate(HandshakeCredentials credentials);
}
