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
package com.zeroz4j.example.scopedsignals.server;

import com.zeroz4j.example.scopedsignals.api.Basket;
import com.zeroz4j.example.scopedsignals.api.ShopService;
import com.zeroz4j.example.scopedsignals.api.ShopSignals;
import com.zeroz4j.server.RmiRequestContext;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Where every "whose state is this?" decision is made.
 *
 * <p>Each method takes its target from {@link RmiRequestContext} — the identity established at the
 * handshake — and never from a method argument. That is the whole discipline: a client that could
 * name the target could name somebody else's.</p>
 */
@ApplicationScoped
public class ShopServiceImpl implements ShopService {

    @Override
    public void addToBasket(String item) {
        String clientId = requireClientId();
        ShopSignals.BASKET.forTarget(clientId)
                .update(basket -> basket.plus(item == null || item.isBlank() ? "Something" : item));
    }

    @Override
    public void clearBasket() {
        ShopSignals.BASKET.forTarget(requireClientId()).set(Basket.empty());
    }

    @Override
    public void noticeMyself(String message) {
        if (RmiRequestContext.getPrincipal() == null) {
            // Scope.USER has no target on an anonymous connection. Skipping is the only correct
            // answer: there is nobody to address, and picking anyone would be a leak.
            return;
        }
        ShopSignals.NOTICE.forTarget(RmiRequestContext.getPrincipal().getName())
                .set(message + "  (" + System.currentTimeMillis() % 100000 + ")");
    }

    @Override
    public void countVisitor() {
        ShopSignals.VISITORS.update(count -> count + 1);
    }

    @Override
    public String whoAmI() {
        String user = RmiRequestContext.getPrincipal() != null
                ? RmiRequestContext.getPrincipal().getName() : "anonymous";
        String clientId = RmiRequestContext.getClientId();
        return "user=" + user
                + " roles=" + RmiRequestContext.getRoles()
                + " client=" + (clientId == null ? "none" : abbreviate(clientId))
                + " session=" + RmiRequestContext.getSessionId();
    }

    /**
     * The client id is present whether or not anyone is logged in — that is what makes
     * {@code Scope.CLIENT} the scope for an open application.
     */
    private static String requireClientId() {
        String clientId = RmiRequestContext.getClientId();
        if (clientId == null) {
            throw new IllegalStateException(
                    "No client id on this connection. The browser is normally issued one when the "
                    + "page is served or at the handshake; a non-browser client gets none.");
        }
        return clientId;
    }

    /** Client ids are 43 characters of base64; the first few are enough to tell two browsers apart. */
    private static String abbreviate(String clientId) {
        return clientId.length() <= 8 ? clientId : clientId.substring(0, 8) + "…";
    }
}
