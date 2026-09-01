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
package com.zeroz4j.example.payments.server;

import com.zeroz4j.server.Zeroz4jServer;

/**
 * Embeds the zeroz4j RMI backend (CDI via Helidon MP) inside the JVM.
 */
public final class ExampleServer {

    /**
     * The port this example serves on when nothing says otherwise.
     *
     * <p>Every example has a number of its own, so two of them started at the same time do not
     * fight over one address. Move this one somewhere else without editing the file: put
     * {@code --port 8092} on the command line, or start the JVM with {@code -Dzeroz.port=8099}.</p>
     */
    private static final int DEFAULT_PORT = 8092;

    public static void main(String[] args) {
        Zeroz4jServer.start(port(args), "zeroz4j Payments Desk Server").join();
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
