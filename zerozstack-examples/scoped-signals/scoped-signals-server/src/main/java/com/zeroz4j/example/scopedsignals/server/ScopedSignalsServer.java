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
        Zeroz4jServer.start(8082, "zeroz4j Scoped Signals").join();
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
}
