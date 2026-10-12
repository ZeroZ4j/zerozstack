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
package com.zeroz4j.server.vidocq;

import com.zeroz4j.server.spi.ServerBinding;

/**
 * Starts the server on the Vidocq runtime; the binding
 * {@link com.zeroz4j.server.Zeroz4jServer#start(int, String)} finds when
 * {@code zerozstack-server-vidocq} is on the classpath. Registered under the name {@code vidocq}.
 *
 * <p>Applications do not call this class. It hands the work to {@link Zeroz4jVidocqServer}.</p>
 */
public final class VidocqServerBinding implements ServerBinding {

    /** The name this binding answers to in {@code zeroz.server.binding}. */
    public static final String NAME = "vidocq";

    /** Called by {@link java.util.ServiceLoader}. */
    public VidocqServerBinding() {
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public RunningServer start(int port, String appName) {
        Zeroz4jVidocqServer server = Zeroz4jVidocqServer.start(port, appName);
        return new RunningServer() {
            @Override
            public int port() {
                return server.port();
            }

            @Override
            public void close() {
                server.close();
            }
        };
    }
}
