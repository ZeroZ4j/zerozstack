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
package com.zeroz4j.example.scopedsignals.server;

import com.zeroz4j.server.Zeroz4jServer;

/**
 * Runs the scoped-signals example on http://localhost:8082.
 *
 * <p>Development authentication is on so the per-user signal has an identity to key on:
 * {@code demo}/{@code demo} and {@code admin}/{@code admin}. The per-browser signal needs none.</p>
 */
public final class ScopedSignalsServer {

    public static void main(String[] args) {
        System.setProperty("zeroz.security.mode", "dev");
        Zeroz4jServer.start(8082, "zeroz4j Scoped Signals").join();
    }
}
