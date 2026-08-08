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
package com.zeroz4j.example.scopedsignals.api;

import com.zeroz4j.api.Scope;
import com.zeroz4j.signals.ScopedSignal;
import com.zeroz4j.signals.Signals;
import com.zeroz4j.signals.ValueSignal;

/**
 * Three signals, one per kind of reach — declared once here and referenced from both tiers.
 *
 * <p>The contrast is the point of this example: the same declaration style produces state that
 * everyone shares, state one person shares across their devices, and state that belongs to a single
 * browser with no login involved at all.</p>
 */
public final class ShopSignals {

    private ShopSignals() {}

    /**
     * One value the whole server agrees on. Every connected browser sees the same number, whoever
     * they are — correct for genuinely public state, and a data leak for anything else.
     */
    public static final ValueSignal<Integer> VISITORS = Signals.shared("shop.visitors", 0);

    /**
     * One basket per browser.
     *
     * <p>{@code Scope.CLIENT} needs no authentication: the id is issued by the server and stored in
     * an {@code HttpOnly} cookie, so it survives a reconnect and a reload. Two tabs of the same
     * browser share a basket; a different browser, device or private window does not.</p>
     *
     * <p>It identifies a browser, not a person — fine for a basket, wrong for anything that must be
     * private to a particular human.</p>
     */
    public static final ScopedSignal<Basket> BASKET =
            Signals.scoped("shop.basket", Basket.empty(), Scope.CLIENT);

    /**
     * One notice per signed-in user, reaching all of that person's tabs and devices and nobody
     * else's. Unlike the basket this <em>is</em> a boundary between people, because it is keyed by an
     * authenticated identity.
     */
    public static final ScopedSignal<String> NOTICE =
            Signals.scoped("shop.notice", "", Scope.USER);
}
