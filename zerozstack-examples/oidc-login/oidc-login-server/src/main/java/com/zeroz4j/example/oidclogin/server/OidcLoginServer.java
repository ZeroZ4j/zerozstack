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
package com.zeroz4j.example.oidclogin.server;

import com.zeroz4j.server.Zeroz4jServer;

/**
 * Runs the OIDC example on http://localhost:8081, against a Keycloak realm.
 *
 * <p>Configured here so the example runs with one command. A real deployment passes these as system
 * properties or environment configuration rather than hardcoding a realm URL.</p>
 *
 * <p>See the module README for the four commands that create the realm, client, role and user this
 * expects.</p>
 */
public final class OidcLoginServer {

    /**
     * The port this example serves on when nothing says otherwise.
     *
     * <p>Every example has a number of its own, so two of them started at the same time do not
     * fight over one address. Move this one somewhere else without editing the file: put
     * {@code --port 8091} on the command line, or start the JVM with {@code -Dzeroz.port=8091}.</p>
     */
    private static final int DEFAULT_PORT = 8081;

    public static void main(String[] args) {
        setDefault("zeroz.oidc.issuer", "http://localhost:18081/realms/zeroz-tour");
        setDefault("zeroz.oidc.clientId", "zeroz-app");
        // Keycloak puts the tenant wherever a mapper says; this realm maps a user attribute.
        setDefault("zeroz.oidc.tenantClaim", "tenant");
        // The page and the socket are the same origin here, so the default same-origin check applies.
        Zeroz4jServer.start(port(args), "zeroz4j OIDC Login").join();
    }

    /** Lets a caller override any of these from the command line without editing the example. */
    private static void setDefault(String key, String value) {
        if (System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
    }

    /**
     * Works out which port to listen on.
     *
     * <p>In order: {@code --port <number>} on the command line, then the {@code zeroz.port} system
     * property, then {@link #DEFAULT_PORT}.</p>
     *
     * @param args the command line this server was started with
     * @return the port to bind
     */
    private static int port(String[] args) {
        if (args != null) {
            for (int i = 0; i + 1 < args.length; i++) {
                if ("--port".equals(args[i])) {
                    return Integer.parseInt(args[i + 1].trim());
                }
            }
        }
        String configured = System.getProperty("zeroz.port", "").trim();
        return configured.isEmpty() ? DEFAULT_PORT : Integer.parseInt(configured);
    }
}
