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
package com.zeroz4j.api;

import java.security.SecureRandom;
import java.util.Random;

/**
 * Random identifiers that work on both tiers, including in a browser on a plain {@code http://}
 * address.
 *
 * <p><b>Why this exists rather than {@link java.util.UUID#randomUUID()}.</b> TeaVM compiles
 * {@code UUID.randomUUID()} into a call to the browser's {@code crypto.randomUUID()}, and that
 * function is only present in a <i>secure context</i> — {@code https://} or {@code localhost}.
 * A page served from a plain {@code http://} LAN address, which is how a self-hosted application
 * on a home or office network is normally reached, does not get one: the call fails with
 * {@code TypeError: crypto.randomUUID is not a function}. Every framework operation that needed an
 * identifier then failed, and there was nothing the application could do about it.</p>
 *
 * <p><b>What it uses instead.</b> {@link SecureRandom}, which needs no secure context anywhere:</p>
 * <ul>
 *   <li><b>On the server</b> it is the platform's cryptographic generator — the same source
 *       {@code UUID.randomUUID()} draws on, so nothing is weakened by this change.</li>
 *   <li><b>In a browser</b> TeaVM implements it with {@code crypto.getRandomValues()}, which — unlike
 *       {@code crypto.randomUUID()} — is <i>not</i> restricted to a secure context and is therefore
 *       available on a plain {@code http://} address. Still a cryptographic generator.</li>
 *   <li><b>Where no {@code crypto} object exists at all</b>, TeaVM falls back to an ordinary
 *       pseudo-random generator. Identifiers stay unique enough to name objects within one
 *       application; they are no longer unguessable.</li>
 * </ul>
 *
 * <p><b>Scope of the guarantee.</b> The last case is the only one that is not cryptographic, and it
 * cannot be reached in any browser released this decade. Treat what comes out of here as a name for
 * something, not as a secret: a password reset link, a session token or a signed URL must draw on
 * {@link SecureRandom} directly and fail loudly rather than degrade.</p>
 *
 * <p>The output is a canonical version-4 UUID string — thirty-six characters, lower case, four
 * hyphens — so it is interchangeable with what {@code UUID.randomUUID().toString()} produced
 * before, including for anything already written down.</p>
 */
public final class Ids {

    /**
     * One shared generator. {@link SecureRandom} is thread-safe, and creating one per call costs
     * a re-seed on the server for no benefit.
     */
    private static final Random RANDOM = new SecureRandom();

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private Ids() {
    }

    /**
     * @return a new random identifier in canonical version-4 UUID form, for example
     *         {@code 1b4e28ba-2fa1-4d1d-883f-b0e5d2fd7f2b}
     *
     * <p><b>Under the hood:</b> draws sixteen bytes from {@link SecureRandom}, stamps the four bits
     * that say "version 4" and the two that say "variant 1" as RFC 4122 requires, and formats the
     * result as hexadecimal with hyphens after the 4th, 6th, 8th and 10th byte. No browser API is
     * named anywhere in this path, so nothing here depends on how the page was served.</p>
     */
    public static String newId() {
        return newId(RANDOM);
    }

    /**
     * The generator above, with the source of randomness handed in.
     *
     * <p>Package-private so a test can hand it an ordinary {@link Random} — which is exactly what a
     * browser with no {@code crypto} object falls back to — and check that the last resort still
     * produces a well-formed identifier.</p>
     *
     * @param random where the sixteen bytes come from
     * @return a canonical version-4 UUID string
     */
    static String newId(Random random) {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);

        // RFC 4122: high nibble of byte 6 is the version, top two bits of byte 8 are the variant.
        bytes[6] = (byte) ((bytes[6] & 0x0F) | 0x40);
        bytes[8] = (byte) ((bytes[8] & 0x3F) | 0x80);

        char[] out = new char[36];
        int at = 0;
        for (int i = 0; i < 16; i++) {
            if (i == 4 || i == 6 || i == 8 || i == 10) {
                out[at++] = '-';
            }
            int b = bytes[i] & 0xFF;
            out[at++] = HEX[b >>> 4];
            out[at++] = HEX[b & 0x0F];
        }
        return new String(out);
    }
}
