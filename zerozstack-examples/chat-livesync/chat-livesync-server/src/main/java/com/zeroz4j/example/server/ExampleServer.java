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
package com.zeroz4j.example.server;

import com.zeroz4j.server.Zeroz4jServer;

/**
 * Embeds the zeroz4j RMI backend (CDI via Helidon MP) inside the JVM.
 */
public final class ExampleServer {

    public static void main(String[] args) {
        // Example convenience: enable dev login (demo/demo, admin/admin) unless overridden.
        if (System.getProperty("zeroz.security.mode") == null) {
            System.setProperty("zeroz.security.mode", "dev");
        }
        System.out.println("[zeroz4j] Dev login enabled - sign in as demo/demo or admin/admin");
        Zeroz4jServer.start(8080, "zeroz4j Example Server").join();
    }
}
