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
 * Issues and verifies the client id that identifies one browser without any login.
 *
 * <p>An application with no authentication still needs to keep a browser's own state to itself —
 * that is what {@link com.zeroz4j.api.Scope#CLIENT} filters on. The id cannot come from the browser:
 * anything a client says about its own identity is a claim it can edit. So the <b>server</b> mints
 * it, signs it, and hands it out; the browser only stores it and gives it back.</p>
 *
 * <p>The token is {@code <id>.<issuedAt>.<signature>}, where the id is 256 bits from
 * {@link SecureRandom} and the signature is an HMAC-SHA256 over {@code <id>.<issuedAt>} keyed with a
 * server secret. Verification is therefore stateless — no registry of issued ids to hold in memory
 * or lose on restart — and an expiry falls out of the embedded issue time. A forged token fails the
 * signature check and is treated as no id at all, so the connection simply becomes a fresh client
 * rather than someone else's.</p>
 *
 * <h2>Configuration</h2>
 * <table border="1">
 *   <caption>System properties</caption>
 *   <tr><th>Property</th><th>Meaning</th></tr>
 *   <tr><td>{@code zeroz.clientId.secret}</td>
 *       <td>HMAC key. <b>Set this in production.</b> Without it a random key is generated at
 *           startup, which is safe but means every client id is invalidated by a restart and no two
 *           nodes in a cluster accept each other's ids.</td></tr>
 *   <tr><td>{@code zeroz.clientId.ttlDays}</td>
 *       <td>How long an issued id stays valid; default 365. An expired token verifies as absent, so
 *           the browser is issued a new id on its next page load.</td></tr>
 * </table>
 *
 * <p><b>This identifies a browser, not a person.</b> Two people sharing a machine share the id, and
 * clearing cookies produces a new one. It is not a substitute for authentication and must never be
 * used to keep one user's data away from another.</p>
 */
public final class ClientIdentity {

    private static final Logger LOG = Logger.getLogger(ClientIdentity.class.getName());

    /** Name of the cookie carrying the signed client id. */
    public static final String COOKIE_NAME = "zeroz_cid";

    private static final String SECRET_PROPERTY = ServerSettings.CLIENT_ID_SECRET;
    private static final String TTL_PROPERTY = ServerSettings.CLIENT_ID_TTL_DAYS;
    private static final String SECURE_COOKIE_PROPERTY = ServerSettings.CLIENT_ID_SECURE_COOKIE;
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long DEFAULT_TTL_DAYS = 365L;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private static volatile byte[] secret;

    private ClientIdentity() {}

    /**
     * Mints a fresh signed client id.
     *
     * @return the token to hand to the browser, never null
     */
    public static String issue() {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String id = ENCODER.encodeToString(raw);
        String issuedAt = Long.toString(System.currentTimeMillis());
        String payload = id + "." + issuedAt;
        return payload + "." + sign(payload);
    }

    /**
     * Verifies a token presented by a browser.
     *
     * @param token the cookie value, or null
     * @return the client id when the signature is valid and the token has not expired, otherwise
     *         null — a caller must treat null as "no client id", never as a failure to report to the
     *         client, since a tampered token is indistinguishable from a first visit
     */
    public static String verify(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        int lastDot = token.lastIndexOf('.');
        if (lastDot <= 0) {
            return null;
        }
        String payload = token.substring(0, lastDot);
        String presented = token.substring(lastDot + 1);

        // Constant-time: every comparison takes the same time whether the first byte differs or
        // the last one does, so how long the answer took says nothing about the signature.
        byte[] expectedBytes = sign(payload).getBytes(StandardCharsets.UTF_8);
        byte[] presentedBytes = presented.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedBytes, presentedBytes)) {
            return null;
        }

        int separator = payload.lastIndexOf('.');
        if (separator <= 0) {
            return null;
        }
        String id = payload.substring(0, separator);
        long issuedAt;
        try {
            issuedAt = Long.parseLong(payload.substring(separator + 1));
        } catch (NumberFormatException ex) {
            return null;
        }
        if (System.currentTimeMillis() - issuedAt > ttlMillis()) {
            return null;
        }
        return id;
    }

    /**
     * Builds the {@code Set-Cookie} value carrying a client id.
     *
     * <p>{@code HttpOnly} is the point of the exercise: script on the page cannot read the cookie,
     * so a cross-site scripting bug cannot steal the id the way it could read browser storage.
     * {@code SameSite=Strict} keeps the cookie off cross-site requests, and {@code Secure} keeps it
     * off plaintext ones.</p>
     *
     * @param token    the token from {@link #issue()}
     * @param secureOnly whether to add the {@code Secure} attribute; pass false only for plain-HTTP
     *                   local development, where a Secure cookie would never be stored at all
     * @return the header value
     */
    public static String cookieHeader(String token, boolean secureOnly) {
        StringBuilder cookie = new StringBuilder(160);
        cookie.append(COOKIE_NAME).append('=').append(token)
              .append("; Path=/")
              .append("; Max-Age=").append(ttlMillis() / 1000L)
              .append("; HttpOnly")
              .append("; SameSite=Strict");
        if (secureOnly) {
            cookie.append("; Secure");
        }
        return cookie.toString();
    }

    /**
     * Whether a cookie issued for this request should carry {@code Secure}.
     *
     * <p>Derived from the request scheme, because a {@code Secure} cookie sent over plain HTTP is
     * dropped by the browser — which would silently break client identity in local development.
     * Override with {@code -Dzeroz.clientId.secureCookie=true|false} behind a TLS-terminating proxy,
     * where the application itself only ever sees plain HTTP.</p>
     *
     * @param scheme the request scheme ({@code https}, {@code wss}, {@code http}, {@code ws}), or null
     * @return true when the {@code Secure} attribute should be set
     */
    public static boolean secureFor(String scheme) {
        String override = System.getProperty(SECURE_COOKIE_PROPERTY);
        if (override != null && !override.trim().isEmpty()) {
            return Boolean.parseBoolean(override.trim());
        }
        return "https".equalsIgnoreCase(scheme) || "wss".equalsIgnoreCase(scheme);
    }

    /**
     * Extracts this framework's cookie from a raw {@code Cookie} header.
     *
     * @param cookieHeader the header value, or null
     * @return the token, or null when the header is absent or carries no client id
     */
    public static String fromCookieHeader(String cookieHeader) {
        if (cookieHeader == null || cookieHeader.isEmpty()) {
            return null;
        }
        for (String part : cookieHeader.split(";")) {
            String candidate = part.trim();
            if (candidate.startsWith(COOKIE_NAME + "=")) {
                return candidate.substring(COOKIE_NAME.length() + 1);
            }
        }
        return null;
    }

    private static String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret(), HMAC_ALGORITHM));
            return ENCODER.encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            // Every JVM ships HmacSHA256; if this fails the platform is broken and silently
            // returning an unverifiable token would be worse than refusing to start issuing.
            throw new IllegalStateException("Cannot sign client ids: " + ex.getMessage(), ex);
        }
    }

    private static byte[] secret() {
        byte[] current = secret;
        if (current != null) {
            return current;
        }
        synchronized (ClientIdentity.class) {
            if (secret == null) {
                String configured = System.getProperty(SECRET_PROPERTY);
                if (configured != null && !configured.trim().isEmpty()) {
                    secret = configured.trim().getBytes(StandardCharsets.UTF_8);
                } else {
                    byte[] generated = new byte[32];
                    RANDOM.nextBytes(generated);
                    secret = generated;
                    LOG.warning("[zeroz4j] No " + SECRET_PROPERTY + " configured: client ids are "
                            + "signed with a key generated at startup. They are secure, but every "
                            + "restart invalidates them and other nodes will reject them. Set the "
                            + "property in any deployment running more than one node or surviving "
                            + "a restart.");
                }
            }
            return secret;
        }
    }

    private static long ttlMillis() {
        long days = DEFAULT_TTL_DAYS;
        String configured = System.getProperty(TTL_PROPERTY);
        if (configured != null && !configured.trim().isEmpty()) {
            try {
                days = Long.parseLong(configured.trim());
            } catch (NumberFormatException ex) {
                LOG.warning("[zeroz4j] Ignoring non-numeric " + TTL_PROPERTY + "='" + configured
                        + "'; using " + DEFAULT_TTL_DAYS + " days.");
            }
        }
        return days * 24L * 60L * 60L * 1000L;
    }

    /** Test support: forces the signing key to be resolved again from system properties. */
    static void resetForTesting() {
        synchronized (ClientIdentity.class) {
            secret = null;
        }
    }
}
