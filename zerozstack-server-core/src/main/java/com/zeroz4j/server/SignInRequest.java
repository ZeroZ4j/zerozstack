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

import java.io.IOException;
import java.io.InputStream;

/**
 * One request to a sign-in address, as a header lookup and a body.
 *
 * <p>Carries no HTTP type, which is what lets {@link SignInSessions} live in
 * {@code zerozstack-server-core}. Each binding supplies these four things and nothing else, so every
 * rule is applied once, the same way, whichever server is running.</p>
 *
 * @since 0.11.0
 */
public interface SignInRequest {

    /**
     * @param name a header name, in any case
     * @return its value, or null when absent
     */
    String header(String name);

    /**
     * @return the request body
     * @throws IOException when it cannot be read
     */
    InputStream body() throws IOException;

    /**
     * @return the scheme this server saw, {@code http} or {@code https}
     */
    String scheme();

    /**
     * @return the deployment's context path such as {@code /coachapp}; empty at the site root
     */
    String contextPath();
}
