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

import java.util.Locale;
import java.util.Set;

/**
 * Whether a state-changing HTTP request came from this application's own pages.
 *
 * <p>A browser attaches cookies by destination, not by who asked. So another site can make a
 * visitor's browser post to this one: sign them out, renew their session, or sign them in to the
 * attacker's account so that what they type next lands there. This refuses all three.</p>
 *
 * <p>Two independent checks. {@link OriginPolicy} applies the same rule the WebSocket handshake
 * applies, including {@code zeroz.hosts}. {@code Sec-Fetch-Site} then catches a page on a sibling
 * subdomain and a browser that left {@code Origin} off. Only a same-origin request passes, or a
 * same-site or cross-site one whose origin {@code zeroz.origins} names.</p>
 *
 * <p>A request carrying neither header is allowed. Every browser that matters sends at least one,
 * so its absence means a program, which holds no visitor's cookies to abuse.</p>
 *
 * @since 0.11.0
 */
public final class CrossSiteGuard {

    /** What browsers say about where a request came from. */
    public static final String FETCH_SITE_HEADER = "Sec-Fetch-Site";

    private CrossSiteGuard() {
    }

    /**
     * Decides whether a request may proceed.
     *
     * @param config    the server's settings
     * @param origin    the {@code Origin} header, or null
     * @param host      the {@code Host} header, or null
     * @param fetchSite the {@code Sec-Fetch-Site} header, or null
     * @return true when the request came from this application's own pages, or from no browser
     */
    public static boolean isAllowed(ServerConfig config, String origin, String host,
                                    String fetchSite) {
        if (!OriginPolicy.isAllowed(config, origin, host)) {
            return false;
        }
        if (fetchSite == null || fetchSite.trim().isEmpty()) {
            return true;
        }
        String site = fetchSite.trim().toLowerCase(Locale.ROOT);
        if ("same-origin".equals(site) || "none".equals(site)) {
            return true;
        }
        Set<String> named = config.list(ServerSettings.ORIGINS, false);
        return origin != null && !origin.isEmpty() && !named.contains("*") && named.contains(origin);
    }
}
