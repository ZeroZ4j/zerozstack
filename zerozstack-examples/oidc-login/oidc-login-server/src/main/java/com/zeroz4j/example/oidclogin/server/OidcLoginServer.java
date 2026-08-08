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

    public static void main(String[] args) {
        setDefault("zeroz.oidc.issuer", "http://localhost:18081/realms/zeroz-tour");
        setDefault("zeroz.oidc.clientId", "zeroz-app");
        // Keycloak puts the tenant wherever a mapper says; this realm maps a user attribute.
        setDefault("zeroz.oidc.tenantClaim", "tenant");
        // The page and the socket are the same origin here, so the default same-origin check applies.
        Zeroz4jServer.start(8081, "zeroz4j OIDC Login").join();
    }

    /** Lets a caller override any of these from the command line without editing the example. */
    private static void setDefault(String key, String value) {
        if (System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
    }
}
