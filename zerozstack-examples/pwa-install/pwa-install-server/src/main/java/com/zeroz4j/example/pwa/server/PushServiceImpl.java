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
package com.zeroz4j.example.pwa.server;

import com.zeroz4j.example.pwa.api.PushService;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Holds the VAPID key pair and the subscriptions browsers have handed in.
 *
 * <p>The key pair is generated at startup, which is right for an example and wrong for a deployment:
 * a restart invalidates every subscription taken out against the old key. Generate one pair, keep the
 * private half somewhere safe, and configure both.</p>
 *
 * <p>Subscriptions live in a map that dies with the process, for the same reason.</p>
 */
@ApplicationScoped
public class PushServiceImpl implements PushService {

    private static final Logger LOG = Logger.getLogger(PushServiceImpl.class.getName());

    /** endpoint → the keys needed to encrypt a payload for it */
    private final Map<String, String[]> subscriptions = new ConcurrentHashMap<>();

    private String publicKey;
    private KeyPair keyPair;

    @PostConstruct
    void generateKeys() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            keyPair = generator.generateKeyPair();
            publicKey = uncompressedPoint((ECPublicKey) keyPair.getPublic());
            LOG.info("VAPID key pair generated for this run. Public key: " + publicKey);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not generate a VAPID key pair", ex);
        }
    }

    @Override
    public String vapidPublicKey() {
        return publicKey;
    }

    @Override
    public String registerSubscription(String endpoint, String p256dh, String auth) {
        subscriptions.put(endpoint, new String[] { p256dh, auth });
        LOG.info("Subscription registered: " + endpoint);
        // A real implementation would persist this, and would delete it when the push service
        // answers 404 or 410 — which is how it tells you the subscription is gone for good.
        return "Stored. The server now holds " + subscriptions.size()
                + " subscription(s) and could deliver to this browser even with the app closed.";
    }

    @Override
    public int subscriptionCount() {
        return subscriptions.size();
    }

    /**
     * Encodes an EC public key the way the Push API wants it: the uncompressed point
     * {@code 0x04 || X || Y}, 65 bytes, base64url without padding.
     *
     * <p>{@code getEncoded()} would give X.509 SubjectPublicKeyInfo, which the browser rejects.</p>
     */
    private static String uncompressedPoint(ECPublicKey key) {
        byte[] point = new byte[65];
        point[0] = 0x04;
        copyFixedWidth(key.getW().getAffineX(), point, 1);
        copyFixedWidth(key.getW().getAffineY(), point, 33);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(point);
    }

    /** Writes a coordinate as exactly 32 bytes, left-padded — BigInteger gives neither guarantee. */
    private static void copyFixedWidth(BigInteger coordinate, byte[] target, int offset) {
        byte[] bytes = coordinate.toByteArray();
        int length = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - length, target, offset + 32 - length, length);
    }
}
