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

import com.zeroz4j.server.DevAuth;
import com.zeroz4j.server.Zeroz4jServer;

/**
 * A main class that runs any ZeroZ Stack application on the Vidocq runtime, so an application
 * needs no entry point of its own to try it.
 *
 * <pre>{@code
 * java -cp "target/classes;target/libs/*" com.zeroz4j.server.vidocq.VidocqLauncher --port 8195 --dev-login
 * }</pre>
 *
 * <p>Arguments: {@code --port <n>} (else the {@code zeroz.port} system property, else 8080) and
 * {@code --dev-login} (the same as {@code -Dzeroz.security.mode=dev}: turns on the built-in
 * development accounts and prints the warning banner).</p>
 */
public final class VidocqLauncher {

    private static final int DEFAULT_PORT = 8080;

    private VidocqLauncher() {
    }

    /**
     * @param args {@code --port <n>} and/or {@code --dev-login}
     */
    public static void main(String[] args) {
        if (hasFlag(args, "--dev-login")) {
            System.setProperty("zeroz.security.mode", "dev");
        }
        if ("dev".equals(System.getProperty("zeroz.security.mode"))) {
            System.out.println(DevAuth.WARNING_BANNER);
        }
        // This launcher means Vidocq, so it says so when another binding is on the classpath too.
        if (System.getProperty(Zeroz4jServer.BINDING_PROPERTY) == null) {
            System.setProperty(Zeroz4jServer.BINDING_PROPERTY, VidocqServerBinding.NAME);
        }
        Zeroz4jServer.start(port(args), "zeroz4j Vidocq Server").join();
    }

    private static boolean hasFlag(String[] args, String flag) {
        if (args != null) {
            for (String arg : args) {
                if (flag.equals(arg)) {
                    return true;
                }
            }
        }
        return false;
    }

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
