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
import java.util.Map;

/**
 * What a sign-in form carried. {@link #toString()} leaves the password out, so an attempt cannot end
 * up in a log by accident.
 *
 * @since 0.11.0
 */
public final class SignInAttempt {

    private final String username;
    private final String password;
    private final Map<String, String> fields;

    /**
     * @param username the name, trimmed
     * @param password the password, exactly as typed
     * @param fields   every field of the form, first value of each
     */
    public SignInAttempt(String username, String password, Map<String, String> fields) {
        this.username = username;
        this.password = password;
        this.fields = fields == null ? Collections.emptyMap() : Collections.unmodifiableMap(fields);
    }

    /**
     * @return the name, trimmed; never empty
     */
    public String username() {
        return username;
    }

    /**
     * @return the password exactly as typed; never empty
     */
    public String password() {
        return password;
    }

    /**
     * Another field of the form, for a sign-in that asks for more than two things, such as which
     * practice to open. Set it with {@code Login.setHiddenField}.
     *
     * @param name the field name
     * @return its value, or null when absent
     */
    public String field(String name) {
        return fields.get(name);
    }

    @Override
    public String toString() {
        return "SignInAttempt[" + username + "]";
    }
}
