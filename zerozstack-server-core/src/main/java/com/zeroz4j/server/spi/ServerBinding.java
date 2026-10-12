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
package com.zeroz4j.server.spi;

/**
 * One way of starting a standalone ZeroZ Stack server: the piece a binding module supplies so that
 * {@link com.zeroz4j.server.Zeroz4jServer#start(int, String)} never has to name a container.
 *
 * <p>A binding module registers its implementation in
 * {@code META-INF/services/com.zeroz4j.server.spi.ServerBinding}. Discovery is by
 * {@link java.util.ServiceLoader}, not CDI, because it runs before any bean container exists.
 * {@code zerozstack-server-helidon} registers {@code helidon} and {@code zerozstack-server-vidocq}
 * registers {@code vidocq}. A WAR has no binding of this kind: its servlet container starts it.</p>
 *
 * <p>An implementation needs a public no-argument constructor and must do nothing until
 * {@link #start(int, String)} is called.</p>
 */
public interface ServerBinding {

    /**
     * The short name of this binding, as given to the {@code zeroz.server.binding} system property
     * and written to the log.
     *
     * @return a lower-case name such as {@code helidon}
     */
    String name();

    /**
     * Starts the server and returns once it is accepting connections.
     *
     * <p>The binding calls {@code BinaryRegistry.init()} itself, before it starts its container.</p>
     *
     * @param port    the TCP port to listen on
     * @param appName the display name of the application, for logging
     * @return the running server
     */
    RunningServer start(int port, String appName);

    /** A server a binding has started. */
    interface RunningServer {

        /**
         * The TCP port the server is listening on.
         *
         * @return the bound port number
         */
        int port();

        /** Stops the server and releases what the binding started with it. */
        void close();
    }
}
