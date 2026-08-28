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

import com.zeroz4j.server.DevAuth;
import com.zeroz4j.server.Zeroz4jServer;

/**
 * Runs the scoped-signals example on http://localhost:8082.
 *
 * <p>The per-user signal needs an identity to key on, and the only accounts here are the
 * framework{@literal '}s built-in development ones. <b>Starting the server does not switch them
 * on</b> — pass {@code --dev-login}, and then sign in as {@code demo}/{@code demo} or
 * {@code admin}/{@code admin}. The per-browser signal needs no login at all.</p>
 *
 * <pre>{@code
 * java -jar scoped-signals-server/target/scoped-signals-server-0.6.2.jar --dev-login
 * }</pre>
 */
public final class ScopedSignalsServer {

    /**
     * The port this example serves on when nothing says otherwise.
     *
     * <p>Every example has a number of its own, so two of them started at the same time do not
     * fight over one address. Move this one somewhere else without editing the file: put
     * {@code --port 8092} on the command line, or start the JVM with {@code -Dzeroz.port=8092}.</p>
     */
    private static final int DEFAULT_PORT = 8082;

    private static final String DEV_LOGIN_FLAG = "--dev-login";

    private ScopedSignalsServer() {}

    /**
     * @param args pass {@code --dev-login} to enable the built-in development accounts
     */
    public static void main(String[] args) {
        if (devLoginRequested(args)) {
            System.setProperty("zeroz.security.mode", "dev");
            System.out.println(DevAuth.WARNING_BANNER);
        } else {
            System.out.println("[zeroz4j] Sign-in is off, and this example needs it. Restart with "
                    + DEV_LOGIN_FLAG + " to enable the built-in development accounts.");
        }
        Zeroz4jServer.start(port(args), "zeroz4j Scoped Signals").join();
    }

    /** True only when the flag was passed, or the property was already set by whoever started us. */
    private static boolean devLoginRequested(String[] args) {
        if (args != null) {
            for (String arg : args) {
                if (DEV_LOGIN_FLAG.equals(arg)) {
                    return true;
                }
            }
        }
        return "dev".equals(System.getProperty("zeroz.security.mode"));
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
