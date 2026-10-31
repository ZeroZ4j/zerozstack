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
package com.zeroz4j.example.signin.server;

import com.zeroz4j.server.AuthenticatedPrincipal;
import com.zeroz4j.server.SignInAttempt;
import com.zeroz4j.server.SignInHandler;
import com.zeroz4j.server.SignInSession;
import jakarta.enterprise.context.ApplicationScoped;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Two accounts held in memory, standing in for the application's own user records. This bean is all
 * an application writes on the server to turn cookie sign-in on.
 *
 * <p>Passwords are kept as PBKDF2 hashes, and an unknown name is hashed against a dummy value, so the
 * time an answer takes does not say which names exist. A real application keeps a random salt per
 * account.</p>
 */
@ApplicationScoped
public class Accounts implements SignInHandler {

    /** The connection attribute {@link AccountServiceImpl} reads. */
    static final String ROLE_LINE = "roleLine";

    private static final int ITERATIONS = 210_000;
    private static final byte[] SALT = "sign-in example salt".getBytes(StandardCharsets.UTF_8);

    private record Account(byte[] hash, Set<String> roles, Instant passwordChangedAt) {
    }

    private final Map<String, Account> accounts = new ConcurrentHashMap<>();
    private final byte[] dummy = hash("no such account");

    /** Sets up the two demo accounts. */
    public Accounts() {
        accounts.put("anna@example.com",
                new Account(hash("anna-password"), Set.of("user"), Instant.EPOCH));
        accounts.put("admin@example.com",
                new Account(hash("admin-password"), Set.of("user", "admin"), Instant.EPOCH));
    }

    @Override
    public AuthenticatedPrincipal signIn(SignInAttempt attempt) {
        Account account = accounts.get(attempt.username());
        byte[] presented = hash(attempt.password());
        boolean matches = MessageDigest.isEqual(presented, account != null ? account.hash() : dummy);
        return account != null && matches
                ? new AuthenticatedPrincipal(attempt.username(), account.roles()) : null;
    }

    /** Roles come from here at every handshake, so a change reaches the next connection. */
    @Override
    public AuthenticatedPrincipal resume(SignInSession session) {
        Account account = accounts.get(session.name());
        if (account == null || session.startedAt().isBefore(account.passwordChangedAt())) {
            return null;
        }
        return new AuthenticatedPrincipal(session.name(), account.roles())
                .withAttribute(ROLE_LINE, String.join(" and ", account.roles()));
    }

    private static byte[] hash(String password) {
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(
                    new PBEKeySpec(password.toCharArray(), SALT, ITERATIONS, 256)).getEncoded();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("PBKDF2 is not available", ex);
        }
    }
}
