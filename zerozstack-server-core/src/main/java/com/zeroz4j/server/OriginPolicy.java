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
import java.util.logging.Logger;

/**
 * Decides whether a WebSocket handshake may proceed, based on the page that opened it.
 *
 * <p>A browser attaches cookies to <em>any</em> connection to an origin, whichever page opened it,
 * and {@link ClientIdentity} puts an identity cookie on the handshake. So the server decides for
 * itself which pages it accepts a connection from: the handshake is refused unless the page that
 * opened it is one this deployment names.</p>
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
 *       <td>No check at all. Only for a deployment where something in front of the application
 *           already decides which pages may connect.</td></tr>
 * </table>
 *
 * <p>A handshake carrying <b>no</b> {@code Origin} header is allowed. Browsers always send one, so
 * its absence means the caller is not a browser and carries no cookies of its own; refusing it would
 * turn away native and test clients for nothing.</p>
 *
 * <h2>Naming the hosts this deployment answers for</h2>
 *
 * <p>{@code zeroz.hosts} asks a second, different question: not which page opened the connection,
 * but which name it was addressed to. The default rule only asks whether {@code Origin} and
 * {@code Host} agree, and they agree for any name that has been pointed at this server's address.
 * Listing the names actually served is what makes the second question answerable.</p>
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

    private static final String ORIGINS_PROPERTY = ServerSettings.ORIGINS;
    private static final String HOSTS_PROPERTY = ServerSettings.HOSTS;
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
        return isAllowed(ServerConfig.fromSystemProperties(), origin, host);
    }

    /**
     * Decides whether a handshake may proceed, using one server's own settings.
     *
     * @param config the server's settings
     * @param origin the {@code Origin} header, or null when absent
     * @param host   the {@code Host} header, or null when absent
     * @return true when the connection is permitted
     */
    public static boolean isAllowed(ServerConfig config, String origin, String host) {
        if (!isHostAllowed(config, host)) {
            return false;   // this request was addressed to a name we do not answer for
        }
        if (origin == null || origin.isEmpty()) {
            return true;   // not a browser: no ambient cookie to steal
        }
        Set<String> configured = configuredOrigins(config);
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
     * <p>The default rule compares the {@code Origin} header to the {@code Host} header, and any
     * name pointed at this server's address makes those two agree. This check asks the other
     * question instead: is the name the request was addressed to one of the names listed in
     * {@code zeroz.hosts}? A name that is not listed is refused however consistent the headers
     * are.</p>
     *
     * <p>Checked on every handshake, including one with no {@code Origin} header: the question is
     * which name the request was addressed to, not which page sent it.</p>
     *
     * @param host the {@code Host} header, or null when absent
     * @return true when the host is acceptable
     */
    public static boolean isHostAllowed(String host) {
        return isHostAllowed(ServerConfig.fromSystemProperties(), host);
    }

    /**
     * Whether the {@code Host} a request was addressed to is one this server answers for.
     *
     * @param config the server's settings
     * @param host   the {@code Host} header, or null when absent
     * @return true when the host is acceptable
     */
    public static boolean isHostAllowed(ServerConfig config, String host) {
        Set<String> allowed = configuredHosts(config);
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

    private static Set<String> configuredHosts(ServerConfig config) {
        return config.list(HOSTS_PROPERTY, true);
    }

    /**
     * Explains a refusal, for the one warning logged when a handshake is rejected.
     *
     * @param origin the refused origin
     * @param host   the host the request was sent to
     * @return a message naming what would have been accepted
     */
    static String explainRefusal(String origin, String host) {
        return explainRefusal(ServerConfig.fromSystemProperties(), origin, host);
    }

    /**
     * Explains a refusal, for the one warning logged when a handshake is rejected.
     *
     * @param config the server's settings
     * @param origin the refused origin
     * @param host   the host the request was sent to
     * @return a message naming what would have been accepted
     */
    static String explainRefusal(ServerConfig config, String origin, String host) {
        Set<String> hosts = configuredHosts(config);
        if (!isHostAllowed(config, host)) {
            return "Host '" + host + "' is not in " + HOSTS_PROPERTY + "=" + hosts
                    + ". Only those names are answered for; a request arriving under any other name "
                    + "is a DNS-rebinding attempt or a misconfigured proxy.";
        }
        Set<String> configured = configuredOrigins(config);
        if (!configured.isEmpty()) {
            return "Origin '" + origin + "' is not in " + ORIGINS_PROPERTY + "=" + configured + ".";
        }
        return "Origin '" + origin + "' does not match the Host '" + host
                + "' this request was sent to. Set " + ORIGINS_PROPERTY
                + " when the page is served from a different host than the socket.";
    }

    private static Set<String> configuredOrigins(ServerConfig config) {
        Set<String> origins = config.list(ORIGINS_PROPERTY, false);
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
