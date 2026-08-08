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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Decides whether a WebSocket handshake may proceed, based on the page that opened it.
 *
 * <p>A browser attaches cookies to <em>any</em> connection to an origin, including one opened by a
 * page the user happens to be visiting. Since {@link ClientIdentity} puts an identity cookie on the
 * handshake, an unchecked {@code Origin} would let an attacker's page open a socket and be handed
 * the victim's client id by the browser. This check is what closes that: the connection is refused
 * unless the page that opened it is one this deployment trusts.</p>
 *
 * <h2>Configuration</h2>
 * <table border="1">
 *   <caption>System properties</caption>
 *   <tr><th>Value of {@code zeroz.origins}</th><th>Behaviour</th></tr>
 *   <tr><td>unset (default)</td>
 *       <td>Same-origin only: the {@code Origin} must match the {@code Host} the request was sent
 *           to. Correct for the usual deployment, where one server serves both the page and the
 *           socket.</td></tr>
 *   <tr><td>a comma-separated list</td>
 *       <td>Exactly those origins, e.g.
 *           {@code https://app.example.com,https://admin.example.com}. Needed when the page is
 *           served from a different host than the socket.</td></tr>
 *   <tr><td>{@code *}</td>
 *       <td>No check at all. Only for a deployment that already enforces origin in front of the
 *           application; otherwise it reopens the hijack this class exists to prevent.</td></tr>
 * </table>
 *
 * <p>A handshake carrying <b>no</b> {@code Origin} header is allowed. Browsers always send one, so
 * its absence means a non-browser client — which has no ambient cookies to be abused in the first
 * place, and would be refusing legitimate native and test clients for nothing.</p>
 */
public final class OriginPolicy {

    private static final Logger LOG = Logger.getLogger(OriginPolicy.class.getName());

    private static final String ORIGINS_PROPERTY = "zeroz.origins";
    private static final String ALLOW_ALL = "*";

    private OriginPolicy() {}

    /**
     * Decides whether a handshake may proceed.
     *
     * @param origin the {@code Origin} header, or null when absent
     * @param host   the {@code Host} header, or null when absent
     * @return true when the connection is permitted
     */
    public static boolean isAllowed(String origin, String host) {
        if (origin == null || origin.isEmpty()) {
            return true;   // not a browser: no ambient cookie to steal
        }
        Set<String> configured = configuredOrigins();
        if (configured.contains(ALLOW_ALL)) {
            return true;
        }
        if (!configured.isEmpty()) {
            return configured.contains(origin);
        }
        return host != null && !host.isEmpty() && authorityOf(origin).equals(host);
    }

    /**
     * Explains a refusal, for the one warning logged when a handshake is rejected.
     *
     * @param origin the refused origin
     * @param host   the host the request was sent to
     * @return a message naming what would have been accepted
     */
    static String explainRefusal(String origin, String host) {
        Set<String> configured = configuredOrigins();
        if (!configured.isEmpty()) {
            return "Origin '" + origin + "' is not in " + ORIGINS_PROPERTY + "=" + configured + ".";
        }
        return "Origin '" + origin + "' does not match the Host '" + host
                + "' this request was sent to. Set " + ORIGINS_PROPERTY
                + " when the page is served from a different host than the socket.";
    }

    private static Set<String> configuredOrigins() {
        String configured = System.getProperty(ORIGINS_PROPERTY);
        if (configured == null || configured.trim().isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> origins = new LinkedHashSet<>();
        for (String part : configured.split(",")) {
            String candidate = part.trim();
            if (!candidate.isEmpty()) {
                origins.add(candidate);
            }
        }
        if (origins.contains(ALLOW_ALL) && origins.size() > 1) {
            LOG.warning("[zeroz4j] " + ORIGINS_PROPERTY + " contains '*' alongside explicit "
                    + "origins; '*' wins and no origin check is performed.");
        }
        return origins;
    }

    /** Strips the scheme, leaving {@code host[:port]} to compare against a {@code Host} header. */
    private static String authorityOf(String origin) {
        int schemeEnd = origin.indexOf("://");
        return schemeEnd < 0 ? origin : origin.substring(schemeEnd + 3);
    }
}
