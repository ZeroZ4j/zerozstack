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
package com.zeroz4j.example.pwa.api;

import com.zeroz4j.api.RmiService;

/**
 * The two halves of a web push subscription: the key the browser needs before it will subscribe, and
 * the endpoint it hands back afterwards.
 */
@RmiService
public interface PushService {

    /**
     * The application server's VAPID public key, base64url.
     *
     * <p>The browser refuses to subscribe without one — it is how the push service later recognises
     * that a delivery really came from this application.</p>
     *
     * @return the key
     */
    String vapidPublicKey();

    /**
     * Records a subscription so the server can push to this browser later.
     *
     * @param endpoint the push service URL to deliver to
     * @param p256dh   the client's public key, base64url
     * @param auth     the shared auth secret, base64url
     * @return a description of what was stored
     */
    String registerSubscription(String endpoint, String p256dh, String auth);

    /**
     * @return how many subscriptions the server currently holds
     */
    int subscriptionCount();
}
