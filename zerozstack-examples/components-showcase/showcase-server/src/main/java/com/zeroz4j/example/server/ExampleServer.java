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

import com.zeroz4j.server.DevAuth;
import com.zeroz4j.server.Zeroz4jServer;

/**
 * Embeds the zeroz4j RMI backend (CDI via Helidon MP) inside the JVM.
 *
 * <p>This example needs somebody to sign in, and the only accounts it has are the framework's
 * built-in development ones — {@code demo/demo} and {@code admin/admin}, with the password sent in
 * the URL. <b>Starting the server does not switch them on.</b> Ask for them, once, on the command
 * line:</p>
 *
 * <pre>{@code
 * java -cp "target/classes;target/libs/*" com.zeroz4j.example.server.ExampleServer --dev-login
 * }</pre>
 *
 * <p>{@code -Dzeroz.security.mode=dev} does the same thing. Either way the server prints what it has
 * enabled, because this file is the one people copy and a copy that quietly accepts a hardcoded
 * password is how it ends up on a real machine.</p>
 */
public final class ExampleServer {

    private static final String DEV_LOGIN_FLAG = "--dev-login";

    /** {@code --port 8095}, for the times something else already has 8080. */
    private static final String PORT_FLAG = "--port";

    private static final int DEFAULT_PORT = 8080;

    private ExampleServer() {}

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
        Zeroz4jServer.start(portFrom(args), "zeroz4j Example Server").join();
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
     * The port asked for on the command line, or 8080. Several of these examples want 8080 and a
     * machine can only give it to one of them, so this one can be told to take another.
     *
     * @param args the command line
     * @return the port to listen on
     */
    private static int portFrom(String[] args) {
        if (args == null) {
            return DEFAULT_PORT;
        }
        for (int i = 0; i < args.length - 1; i++) {
            if (PORT_FLAG.equals(args[i])) {
                try {
                    return Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException notANumber) {
                    System.out.println("[zeroz4j] " + PORT_FLAG + " needs a number; using "
                            + DEFAULT_PORT + ".");
                    return DEFAULT_PORT;
                }
            }
        }
        return DEFAULT_PORT;
    }
}
