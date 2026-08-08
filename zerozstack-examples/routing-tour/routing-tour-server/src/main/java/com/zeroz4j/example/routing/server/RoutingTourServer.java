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
package com.zeroz4j.example.routing.server;

import com.zeroz4j.server.Zeroz4jServer;

/**
 * Runs the routing tour on http://localhost:8080.
 *
 * <p>Development authentication is enabled so the role-guarded route has something to check:
 * {@code demo}/{@code demo} holds {@code user}, {@code admin}/{@code admin} also holds
 * {@code admin}. Append {@code ?user=admin&password=admin} to the page URL to reach
 * {@code /admin}.</p>
 */
public final class RoutingTourServer {

    public static void main(String[] args) {
        // The tour needs a signed-in identity for @RequiresRole to mean anything. Never in a
        // deployment: the credentials are hardcoded and travel as query parameters.
        System.setProperty("zeroz.security.mode", "dev");
        Zeroz4jServer.start(8080, "zeroz4j Routing Tour").join();
    }
}
