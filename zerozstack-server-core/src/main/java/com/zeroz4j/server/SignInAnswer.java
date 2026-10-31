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

import java.util.Collections;
import java.util.List;

/**
 * What a binding writes back for a {@link SignInRequest}: a status, at most one redirect, and any
 * number of cookies. The body is always empty. A binding must also send
 * {@code Cache-Control: no-store}.
 *
 * @since 0.11.0
 */
public final class SignInAnswer {

    private final int status;
    private final String location;
    private final List<String> cookies;

    private SignInAnswer(int status, String location, List<String> cookies) {
        this.status = status;
        this.location = location;
        this.cookies = Collections.unmodifiableList(cookies);
    }

    static SignInAnswer status(int status) {
        return new SignInAnswer(status, null, Collections.emptyList());
    }

    static SignInAnswer status(int status, List<String> cookies) {
        return new SignInAnswer(status, null, cookies);
    }

    static SignInAnswer redirect(String location, List<String> cookies) {
        return new SignInAnswer(303, location, cookies);
    }

    /**
     * @return the HTTP status
     */
    public int status() {
        return status;
    }

    /**
     * @return the {@code Location} value, a path from the site root, or null
     */
    public String location() {
        return location;
    }

    /**
     * @return every {@code Set-Cookie} value to send, each as a header of its own
     */
    public List<String> cookies() {
        return cookies;
    }
}
