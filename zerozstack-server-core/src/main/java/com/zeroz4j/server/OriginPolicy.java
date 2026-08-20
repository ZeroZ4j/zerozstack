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
import java.util.Locale;
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
 *
 * <h2>The host allowlist, and why the same-origin rule is not enough on its own</h2>
 *
 * <p>The default rule compares two headers the attacker's page controls together. Under <b>DNS
 * rebinding</b> a page on {@code evil.com} makes that name resolve to this server's address; the
 * browser then opens a socket carrying {@code Origin: http://evil.com:8080} and
 * {@code Host: evil.com:8080}, they match, and the handshake is allowed. Naming the hosts this
 * deployment answers for closes it:</p>
 *
 * <table border="1">
 *   <caption>{@code zeroz.hosts}</caption>
 *   <tr><th>Value</th><th>Behaviour</th></tr>
 *   <tr><td>unset (default)</td>
 *       <td>No host check — exactly the behaviour of every release before this one.</td></tr>
 *   <tr><td>a comma-separated list</td>
 *       <td>The {@code Host} header must be one of them, e.g.
 *           {@code app.example.com,app.example.com:8443}. An entry with no port accepts that name on
 *           any port. Matching is case-insensitive.</td></tr>
 *   <tr><td>{@code *}</td>
 *       <td>No host check, said out loud.</td></tr>
 * </table>
 *
 * <p>The two settings are independent: {@code zeroz.origins=*} turns off the origin check and leaves
 * the host allowlist doing its job. Set {@code zeroz.hosts} in any deployment reachable from a
 * browser, and serve it over TLS — a valid certificate is what keeps a rebound name from being
 * accepted by the browser in the first place.</p>
 */
public final class OriginPolicy {

    private static final Logger LOG = Logger.getLogger(OriginPolicy.class.getName());

    private static final String ORIGINS_PROPERTY = "zeroz.origins";
    private static final String HOSTS_PROPERTY = "zeroz.hosts";
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
        if (!isHostAllowed(host)) {
            return false;   // this request was addressed to a name we do not answer for
        }
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
     * Whether the {@code Host} this request was addressed to is one this deployment answers for.
     *
     * <p><b>The attack this closes is DNS rebinding.</b> The default same-origin rule compares the
     * {@code Origin} header to the {@code Host} header, and a page the victim is visiting controls
     * both. The attacker serves {@code evil.com}, points that name at this server's address, and the
     * victim's browser opens a socket sending {@code Origin: http://evil.com:8080} and
     * {@code Host: evil.com:8080} — which match, so the same-origin rule lets it through. Listing the
     * names this deployment actually answers for is what stops it, because {@code evil.com} is not
     * one of them.</p>
     *
     * <p>Checked on every handshake, including one with no {@code Origin} header: the question is
     * which name the request was addressed to, not which page sent it.</p>
     *
     * @param host the {@code Host} header, or null when absent
     * @return true when the host is acceptable
     */
    public static boolean isHostAllowed(String host) {
        Set<String> allowed = configuredHosts();
        if (allowed.isEmpty() || allowed.contains(ALLOW_ALL)) {
            return true;                       // unset: behave exactly as every release before this
        }
        if (host == null || host.isEmpty()) {
            return false;
        }
        String candidate = host.trim().toLowerCase(Locale.ROOT);
        if (allowed.contains(candidate)) {
            return true;
        }
        int colon = candidate.lastIndexOf(':');
        if (colon > 0 && candidate.indexOf(']') < colon) {
            // An entry with no port accepts the name on any port, so a deployment behind a proxy
            // does not have to list every port it might be reached on.
            return allowed.contains(candidate.substring(0, colon));
        }
        return false;
    }

    private static Set<String> configuredHosts() {
        return listProperty(HOSTS_PROPERTY, true);
    }

    /**
     * Explains a refusal, for the one warning logged when a handshake is rejected.
     *
     * @param origin the refused origin
     * @param host   the host the request was sent to
     * @return a message naming what would have been accepted
     */
    static String explainRefusal(String origin, String host) {
        Set<String> hosts = configuredHosts();
        if (!isHostAllowed(host)) {
            return "Host '" + host + "' is not in " + HOSTS_PROPERTY + "=" + hosts
                    + ". Only those names are answered for; a request arriving under any other name "
                    + "is a DNS-rebinding attempt or a misconfigured proxy.";
        }
        Set<String> configured = configuredOrigins();
        if (!configured.isEmpty()) {
            return "Origin '" + origin + "' is not in " + ORIGINS_PROPERTY + "=" + configured + ".";
        }
        return "Origin '" + origin + "' does not match the Host '" + host
                + "' this request was sent to. Set " + ORIGINS_PROPERTY
                + " when the page is served from a different host than the socket.";
    }

    private static Set<String> configuredOrigins() {
        Set<String> origins = listProperty(ORIGINS_PROPERTY, false);
        if (origins.contains(ALLOW_ALL) && origins.size() > 1) {
            LOG.warning("[zeroz4j] " + ORIGINS_PROPERTY + " contains '*' alongside explicit "
                    + "origins; '*' wins and no origin check is performed.");
        }
        return origins;
    }

    /** Reads a comma-separated system property into a set, dropping blanks. */
    private static Set<String> listProperty(String name, boolean lowercase) {
        String configured = System.getProperty(name);
        if (configured == null || configured.trim().isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> values = new LinkedHashSet<>();
        for (String part : configured.split(",")) {
            String candidate = part.trim();
            if (!candidate.isEmpty()) {
                values.add(lowercase ? candidate.toLowerCase(Locale.ROOT) : candidate);
            }
        }
        return values;
    }

    /** Strips the scheme, leaving {@code host[:port]} to compare against a {@code Host} header. */
    private static String authorityOf(String origin) {
        int schemeEnd = origin.indexOf("://");
        return schemeEnd < 0 ? origin : origin.substring(schemeEnd + 3);
    }
}
