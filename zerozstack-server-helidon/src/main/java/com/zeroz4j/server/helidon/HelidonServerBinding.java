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
package com.zeroz4j.server.helidon;

import com.zeroz4j.api.BinaryRegistry;
import com.zeroz4j.server.spi.ServerBinding;
import io.helidon.microprofile.server.Server;

import java.util.logging.Logger;

/**
 * Starts the embedded Helidon MicroProfile server; the binding
 * {@link com.zeroz4j.server.Zeroz4jServer#start(int, String)} finds when
 * {@code zerozstack-server-helidon} is on the classpath. Registered under the name {@code helidon}.
 *
 * <p>Applications do not call this class. It does what {@code Zeroz4jServer.start} did when that
 * class lived in this module: {@code BinaryRegistry.init()}, then
 * {@code Server.builder().port(port).build().start()}.</p>
 */
public final class HelidonServerBinding implements ServerBinding {

    /** The name this binding answers to in {@code zeroz.server.binding}. */
    public static final String NAME = "helidon";

    // The logger name the start-up line has always been written under.
    private static final Logger LOG = Logger.getLogger("com.zeroz4j.server.Zeroz4jServer");

    /** Called by {@link java.util.ServiceLoader}. */
    public HelidonServerBinding() {
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public RunningServer start(int port, String appName) {
        BinaryRegistry.init();
        Server server = Server.builder()
            .port(port)
            .build();

        server.start();

        LOG.info(appName + " listening on http://localhost:" + port + "/ (RMI at /wasm-rmi)");

        return new RunningServer() {
            @Override
            public int port() {
                return server.port();
            }

            @Override
            public void close() {
                server.stop();
            }
        };
    }
}
