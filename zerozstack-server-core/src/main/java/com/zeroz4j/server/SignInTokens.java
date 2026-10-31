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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.logging.Logger;

/**
 * Writes and reads the value of the session cookie.
 *
 * <p>The value is {@code 1.<id>.<started>.<issued>.<expires>.<name>.<tenant>.<signature>}: a format
 * number, 128 random bits, three times in milliseconds, the name and tenant in URL-safe base64, and
 * an HMAC-SHA256 over everything before it. Signed rather than encrypted: the cookie carries nothing
 * its holder does not already know, and it carries no roles.</p>
 *
 * <p>The key comes from {@code zeroz.signin.secret}, and is never shared with the browser-id cookie.
 * A secret under {@value #MIN_SECRET_LENGTH} characters is refused: every cookie is a value to test
 * guesses against offline, so a short key can be recovered, and with it a session can be forged for
 * any name. With no secret a key is generated at startup, so sessions are safe but end at every
 * restart.</p>
 */
final class SignInTokens {

    private static final Logger LOG = Logger.getLogger(SignInTokens.class.getName());

    private static final String HMAC = "HmacSHA256";
    private static final String FORMAT = "1";
    private static final int FIELDS = 8;
    private static final int MIN_SECRET_LENGTH = 32;

    /** Keeps the token, and so the cookie, well inside what every browser stores. */
    private static final int MAX_TOKEN_LENGTH = 3800;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final byte[] key;

    SignInTokens(ServerConfig config) {
        this.key = keyFrom(config);
    }

    private static byte[] keyFrom(ServerConfig config) {
        String configured = config.get(ServerSettings.SIGN_IN_SECRET);
        if (configured != null && !configured.trim().isEmpty()) {
            String secret = configured.trim();
            if (secret.length() < MIN_SECRET_LENGTH) {
                throw new IllegalStateException(ServerSettings.SIGN_IN_SECRET + " must be at least "
                        + MIN_SECRET_LENGTH + " characters. A short key can be recovered from any "
                        + "session cookie, and then a session can be forged for any name.");
            }
            return secret.getBytes(StandardCharsets.UTF_8);
        }
        byte[] generated = new byte[32];
        RANDOM.nextBytes(generated);
        LOG.warning("[zeroz4j] No " + ServerSettings.SIGN_IN_SECRET + " configured: session cookies "
                + "are signed with a key generated at startup, so every restart signs everybody out "
                + "and other nodes reject them. Put a long random value in the application's "
                + "property file.");
        return generated;
    }

    /** @return a new session id: 128 random bits */
    static String newId() {
        byte[] raw = new byte[16];
        RANDOM.nextBytes(raw);
        return ENCODER.encodeToString(raw);
    }

    /**
     * @param session the session to write
     * @return the cookie value, or null when the name and tenant are too long for a cookie
     */
    String write(SignInSession session) {
        String payload = FORMAT + "." + session.id()
                + "." + session.startedAtMillis()
                + "." + session.issuedAtMillis()
                + "." + session.expiresAtMillis()
                + "." + encode(session.name())
                + "." + (session.tenantId() == null ? "" : encode(session.tenantId()));
        String token = payload + "." + sign(payload);
        return token.length() > MAX_TOKEN_LENGTH ? null : token;
    }

    /**
     * Reads a cookie value. Says nothing about whether it has expired or been revoked.
     *
     * @param token the cookie value, or null
     * @return the session, or null when the value is missing, malformed or not signed by this key
     */
    SignInSession read(String token) {
        if (token == null || token.isEmpty() || token.length() > MAX_TOKEN_LENGTH) {
            return null;
        }
        int lastDot = token.lastIndexOf('.');
        if (lastDot <= 0) {
            return null;
        }
        String payload = token.substring(0, lastDot);
        byte[] expected = sign(payload).getBytes(StandardCharsets.UTF_8);
        byte[] presented = token.substring(lastDot + 1).getBytes(StandardCharsets.UTF_8);
        // Constant time, and before a single field is looked at: only values this key signed get
        // any further, so everything below is reading trusted data.
        if (!MessageDigest.isEqual(expected, presented)) {
            return null;
        }
        String[] fields = token.split("\\.", -1);
        if (fields.length != FIELDS || !FORMAT.equals(fields[0])) {
            return null;
        }
        try {
            return new SignInSession(fields[1], decode(fields[5]),
                    fields[6].isEmpty() ? null : decode(fields[6]),
                    Long.parseLong(fields[2]), Long.parseLong(fields[3]), Long.parseLong(fields[4]));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(key, HMAC));
            return ENCODER.encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("Cannot sign a session: " + ex.getMessage(), ex);
        }
    }

    private static String encode(String text) {
        return ENCODER.encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String encoded) {
        return new String(DECODER.decode(encoded), StandardCharsets.UTF_8);
    }
}
