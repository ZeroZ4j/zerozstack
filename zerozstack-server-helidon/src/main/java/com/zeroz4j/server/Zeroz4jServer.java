/*
 * Copyright 2026 Franz Schöning
 * Project: https://www.zeroz4j.com
 * Author: Franz Schöning - Principal Enterprise Architect (https://www.franzschoning.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in cmpliance with the License.
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

import io.helidon.microprofile.server.Server;
import java.util.logging.Logger;
import com.zeroz4j.api.BinaryRegistry;

/**
 * Entry point for starting an embedded Helidon MicroProfile application server with zeroz4j.
 *
 * <p>Initializes the {@link BinaryRegistry} and starts the embedded Helidon MP web server listening on the designated port.</p>
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>Initialization:</b> {@link #start(int, String)} calls {@code BinaryRegistry.init()} to perform SPI serializer discovery before starting Helidon MP.</li>
 *   <li><b>Lifecycle Management:</b> Implements {@link AutoCloseable}; calling {@link #close()} stops the underlying Helidon server.</li>
 * </ul>
 */
public final class Zeroz4jServer implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(Zeroz4jServer.class.getName());

    private final Server server;

    private Zeroz4jServer(Server server) {
        this.server = server;
    }

    /**
     * Starts the Zeroz4j application on the specified HTTP port.
     *
     * @param port    the TCP port to listen on
     * @param appName the display name of the application for logging
     * @return the started {@link Zeroz4jServer} instance
     *
     * <p><b>Under the hood:</b> Calls {@link BinaryRegistry#init()}. Builds {@link Server} via {@code Server.builder().port(port).build()} and starts it.</p>
     */
    public static Zeroz4jServer start(int port, String appName) {
        BinaryRegistry.init();
        Server server = Server.builder()
            .port(port)
            .build();
            
        server.start();
        
        LOG.info(appName + " listening on http://localhost:" + port + "/ (RMI at /wasm-rmi)");
        
        return new Zeroz4jServer(server);
    }
    
    /**
     * Retrieves the actual TCP port the embedded server is listening on.
     *
     * @return bound TCP port number
     */
    public int port() {
        return server.port();
    }
    
    /**
     * Keeps the calling main thread alive until the server is shut down.
     *
     * <p><b>Under the hood:</b> Invokes {@code Thread.currentThread().join()}.</p>
     */
    public void join() {
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Stops the embedded Helidon server gracefully.
     *
     * <p><b>Under the hood:</b> Invokes {@code server.stop()}.</p>
     */
    @Override
    public void close() {
        if (server != null) {
            server.stop();
        }
    }
}
