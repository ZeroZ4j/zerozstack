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
package com.zeroz4j.store.eclipsestore;

/**
 * How this server process gets at its data — set with {@code zeroz4j.store.mode}.
 *
 * <p>The application-facing API is the same in every mode: services send
 * {@code DbCommand}/{@code DbQuery} objects through the tenant's node and never know which
 * shape they are running in. That is the point of the setting — it is a deployment decision,
 * not an application one, so the same build runs on a laptop and behind a shared database
 * server.</p>
 */
public enum StoreMode {

    /**
     * Default. This process owns its tenant stores and serves them to nobody: one copy of the
     * graph in memory, no socket, full transactions. The right choice for a single server
     * instance, for development, and for tenant data that only this process touches.
     */
    EMBEDDED,

    /**
     * Own each store if it is free, otherwise join whichever process already owns it, and take
     * over if that process dies. Lets several instances share stores over a common filesystem
     * without any coordination beyond the store directory itself.
     */
    AUTO_SERVER,

    /**
     * Never own data: connect to a {@code zeroz4j-db} server, configured with
     * {@code zeroz4j.store.server.host} and {@code .port}. Application instances become
     * stateless, so they can be restarted and scaled without moving data — at the cost of
     * running and operating that server.
     */
    CLIENT
}
