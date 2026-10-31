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

import java.time.Instant;

/**
 * One signed-in browser, as read back from its verified session cookie.
 *
 * <p>Carries who signed in and when. It never carries roles: those come from
 * {@link SignInHandler#resume(SignInSession)} every time, because identity stays true while
 * permissions go stale.</p>
 *
 * @since 0.11.0
 */
public final class SignInSession {

    private final String id;
    private final String name;
    private final String tenantId;
    private final long startedAtMillis;
    private final long issuedAtMillis;
    private final long expiresAtMillis;

    SignInSession(String id, String name, String tenantId, long startedAtMillis,
                  long issuedAtMillis, long expiresAtMillis) {
        this.id = id;
        this.name = name;
        this.tenantId = tenantId;
        this.startedAtMillis = startedAtMillis;
        this.issuedAtMillis = issuedAtMillis;
        this.expiresAtMillis = expiresAtMillis;
    }

    /**
     * @return this session's id, fixed from sign-in to sign-out and the same across renewals
     */
    public String id() {
        return id;
    }

    /**
     * @return the principal name {@link SignInHandler#signIn(SignInAttempt)} returned
     */
    public String name() {
        return name;
    }

    /**
     * @return the tenant the sign-in resolved, or null. Part of who this is, since one name may
     *         exist in two tenants
     */
    public String tenantId() {
        return tenantId;
    }

    /**
     * @return when the password was entered; a renewal does not move it
     */
    public Instant startedAt() {
        return Instant.ofEpochMilli(startedAtMillis);
    }

    /**
     * @return when this cookie was issued; moved by a renewal
     */
    public Instant issuedAt() {
        return Instant.ofEpochMilli(issuedAtMillis);
    }

    /**
     * @return when this cookie stops being accepted
     */
    public Instant expiresAt() {
        return Instant.ofEpochMilli(expiresAtMillis);
    }

    long startedAtMillis() {
        return startedAtMillis;
    }

    long issuedAtMillis() {
        return issuedAtMillis;
    }

    long expiresAtMillis() {
        return expiresAtMillis;
    }

    @Override
    public String toString() {
        return "SignInSession[" + name + (tenantId == null ? "" : ", tenant=" + tenantId) + "]";
    }
}
