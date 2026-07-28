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
 * and the user set is hardcoded.</p>
 */
public final class DevAuth {

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
     * @return true in dev mode
     */
    public static boolean isDevMode() {
        return "dev".equals(System.getProperty("zeroz.security.mode"));
    }

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
