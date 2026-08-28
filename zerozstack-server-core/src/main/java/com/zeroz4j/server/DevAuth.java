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
package com.zeroz4j.server;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Development-mode credential store, active only when the system property
 * {@code zeroz.security.mode} is {@code "dev"}.
 *
 * <p>Provides the demo users {@code demo/demo} (role {@code user}) and
 * {@code admin/admin} (roles {@code user, admin}) so examples and local development can
 * authenticate WebSocket sessions without a servlet container or an identity provider.
 * The {@code RmiEndpointConfigurator} consults this store for {@code user}/{@code password}
 * query parameters on the WebSocket handshake.</p>
 *
 * <p><b>Never enable dev mode in production</b> — credentials travel as query parameters
 * and the user set is hardcoded. Nothing switches it on by itself: an application or an example has
 * to set the property, and the first handshake that finds it set logs {@link #WARNING_BANNER} so a
 * server running this way says so on its own.</p>
 *
 * <p>The framework writes no log line containing a handshake password. The exposure that remains is
 * the URL itself, which is why this is a development-only mechanism.</p>
 */
public final class DevAuth {

    private static final Logger LOG = Logger.getLogger(DevAuth.class.getName());

    private static final AtomicBoolean WARNED = new AtomicBoolean();

    private static final Map<String, DevUser> DEV_USERS = new LinkedHashMap<>();

    static {
        DEV_USERS.put("demo", new DevUser("demo", new LinkedHashSet<>(Arrays.asList("user"))));
        DEV_USERS.put("admin", new DevUser("admin", new LinkedHashSet<>(Arrays.asList("user", "admin"))));
    }

    private DevAuth() {}

    /**
     * Returns whether development authentication is enabled
     * ({@code -Dzeroz.security.mode=dev}).
     *
     * <p>The first call that finds it on logs {@link #WARNING_BANNER} at {@code WARNING}. It is
     * printed once per JVM, from the framework rather than from the application, so an application
     * that switches this on cannot switch the notice off by not printing it.</p>
     *
     * @return true in dev mode
     */
    public static boolean isDevMode() {
        return isDevMode(ServerConfig.fromSystemProperties());
    }

    /**
     * Whether one server has the development logins switched on.
     *
     * @param config that server's settings
     * @return true when {@code zeroz.security.mode} is {@code dev} for that server
     */
    public static boolean isDevMode(ServerConfig config) {
        boolean on = "dev".equals(config.get(ServerSettings.SECURITY_MODE));
        if (on && WARNED.compareAndSet(false, true)) {
            LOG.warning(WARNING_BANNER);
        }
        return on;
    }

    /**
     * The notice logged when development authentication is on.
     *
     * <p>Public so that an application's own start-up message can print the same words, rather than
     * inventing a milder version of them.</p>
     */
    public static final String WARNING_BANNER =
            System.lineSeparator()
            + "***************************************************************************" + System.lineSeparator()
            + "  DEVELOPER LOGIN IS ON  (zeroz.security.mode=dev)" + System.lineSeparator()
            + System.lineSeparator()
            + "  Anyone who can reach this server can sign in as:" + System.lineSeparator()
            + "      demo  / demo   (role: user)" + System.lineSeparator()
            + "      admin / admin  (roles: user, admin)" + System.lineSeparator()
            + System.lineSeparator()
            + "  The password travels in the WebSocket URL, so it can end up in browser" + System.lineSeparator()
            + "  history, proxy logs and Referer headers." + System.lineSeparator()
            + System.lineSeparator()
            + "  Use this on your own machine and nowhere else. For anything real,"  + System.lineSeparator()
            + "  register an AuthenticationProvider - see docs/guides/security-auth.md." + System.lineSeparator()
            + "***************************************************************************";


    /**
     * Validates dev credentials.
     *
     * @param username the username
     * @param password the password
     * @return the user's roles on success, or null if the credentials are invalid
     */
    public static Set<String> authenticate(String username, String password) {
        if (username == null || password == null) {
            return null;
        }
        DevUser user = DEV_USERS.get(username);
        if (user == null || !user.password.equals(password)) {
            return null;
        }
        return Collections.unmodifiableSet(user.roles);
    }

    private static final class DevUser {
        final String password;
        final Set<String> roles;

        DevUser(String password, Set<String> roles) {
            this.password = password;
            this.roles = roles;
        }
    }
}
