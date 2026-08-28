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

import com.zeroz4j.server.DevAuth;
import com.zeroz4j.server.Zeroz4jServer;

/**
 * Runs the routing tour on http://localhost:8080.
 *
 * <p>The role-guarded route needs somebody signed in, and the only accounts here are the
 * framework{@literal '}s built-in development ones. <b>Starting the server does not switch them
 * on</b> — pass {@code --dev-login}:</p>
 *
 * <pre>{@code
 * java -jar routing-tour-server/target/routing-tour-server-0.6.2.jar --dev-login
 * }</pre>
 *
 * <p>Then {@code demo}/{@code demo} holds {@code user} and {@code admin}/{@code admin} also holds
 * {@code admin}; append {@code ?user=admin&password=admin} to the page URL to reach
 * {@code /admin}.</p>
 */
public final class RoutingTourServer {

    /**
     * The port this example serves on when nothing says otherwise.
     *
     * <p>Every example has a number of its own, so two of them started at the same time do not
     * fight over one address. Move this one somewhere else without editing the file: put
     * {@code --port 9000} on the command line, or start the JVM with {@code -Dzeroz.port=9000}.</p>
     */
    private static final int DEFAULT_PORT = 8091;

    private static final String DEV_LOGIN_FLAG = "--dev-login";

    private RoutingTourServer() {}

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
        Zeroz4jServer.start(port(args), "zeroz4j Routing Tour").join();
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
