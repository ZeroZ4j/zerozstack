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
package com.zeroz4j.client;

/**
 * Logs the browser in against an OpenID Connect provider such as Keycloak, and keeps the resulting
 * access token available for the WebSocket handshake.
 *
 * <p>Implements the authorization-code flow with PKCE, which is what a browser application is
 * supposed to use: there is nowhere in a page to keep a client secret, so instead the client proves
 * at token-exchange time that it is the same one that started the flow, by presenting a verifier
 * whose hash it sent up front. An authorization code stolen in transit is then worth nothing without
 * that verifier.</p>
 *
 * <p>One call does the whole thing — it works out for itself whether this page load is a fresh
 * visit, a return from the provider, or an already-authenticated reload:</p>
 *
 * <pre>{@code
 * public static void main(String[] args) {
 *     OidcClient.start(
 *         new OidcClient.Config("https://keycloak.example.com/realms/acme", "zeroz-app"),
 *         () -> {
 *             Zeroz4jClient.connect(OidcClient.appendToken("wss://example.com/wasm-rmi"));
 *             buildUi();
 *         });
 * }
 * }</pre>
 *
 * <h2>What this does and does not protect</h2>
 * <ul>
 *   <li>The access token lives in {@code sessionStorage}, which page script can read. That is
 *       unavoidable for a browser client — the token has to be sent from script — and it is the
 *       reason the {@link com.zeroz4j.api.Scope#CLIENT} identity cookie is kept separate and
 *       {@code HttpOnly} instead of being folded in here.</li>
 *   <li>{@code sessionStorage} rather than {@code localStorage}: the token dies with the tab
 *       instead of persisting on a shared machine.</li>
 *   <li>The {@code state} parameter is generated per attempt and checked on return, so a code
 *       delivered by a page the user did not start the login from is rejected.</li>
 *   <li>The authorization code is stripped from the address bar as soon as it is used, keeping it
 *       out of history and bookmarks.</li>
 * </ul>
 *
 * <h2>Expiry</h2>
 * <p>Identity on a zeroz4j connection is fixed when the socket opens, so a token expiring later does
 * not interrupt anything. It matters on <em>reconnect</em>, which happens with the token that is
 * current at the time — so this class silently refreshes ahead of expiry and hands the channel a URL
 * provider rather than a fixed URL. A user whose refresh fails is sent back to the provider to log
 * in again.</p>
 */
public final class OidcClient {

    /** Where the provider lives and who this application is to it. */
    public static final class Config {
        private final String issuer;
        private final String clientId;
        private String redirectUri;
        private String scope = "openid profile";
        private String authorizeEndpoint;
        private String tokenEndpoint;
        private String logoutEndpoint;

        /**
         * Names the provider and this application. Everything else has a Keycloak-shaped default,
         * overridable with the builder methods below.
         *
         * @param issuer   realm URL, e.g. {@code https://keycloak.example.com/realms/acme}
         * @param clientId this application's client id, registered as a public client with PKCE
         */
        public Config(String issuer, String clientId) {
            if (issuer == null || issuer.isEmpty()) {
                throw new IllegalArgumentException("OIDC needs an issuer URL");
            }
            if (clientId == null || clientId.isEmpty()) {
                throw new IllegalArgumentException("OIDC needs a client id");
            }
            this.issuer = issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
            this.clientId = clientId;
        }

        /**
         * Where the provider sends the browser back; must be registered as a valid redirect URI.
         * Defaults to the current page without its query string.
         *
         * @param uri the redirect URI
         * @return this config
         */
        public Config redirectUri(String uri) {
            this.redirectUri = uri;
            return this;
        }

        /**
         * Scopes to request; default {@code "openid profile"}.
         *
         * @param scope space-delimited scopes
         * @return this config
         */
        public Config scope(String scope) {
            this.scope = scope;
            return this;
        }

        /**
         * Overrides the endpoints, for a provider that does not use Keycloak's paths.
         *
         * @param authorize the authorization endpoint
         * @param token     the token endpoint
         * @param logout    the end-session endpoint
         * @return this config
         */
        public Config endpoints(String authorize, String token, String logout) {
            this.authorizeEndpoint = authorize;
            this.tokenEndpoint = token;
            this.logoutEndpoint = logout;
            return this;
        }

        String authorizeEndpoint() {
            return authorizeEndpoint != null ? authorizeEndpoint
                    : issuer + "/protocol/openid-connect/auth";
        }

        String tokenEndpoint() {
            return tokenEndpoint != null ? tokenEndpoint
                    : issuer + "/protocol/openid-connect/token";
        }

        String logoutEndpoint() {
            return logoutEndpoint != null ? logoutEndpoint
                    : issuer + "/protocol/openid-connect/logout";
        }

        String resolvedRedirectUri() {
            return redirectUri != null ? redirectUri : OidcBrowser.currentUrlWithoutQuery();
        }
    }

    private static final String VERIFIER_KEY = "zeroz.oidc.verifier";
    private static final String STATE_KEY = "zeroz.oidc.state";
    private static final String ACCESS_TOKEN_KEY = "zeroz.oidc.accessToken";
    private static final String REFRESH_TOKEN_KEY = "zeroz.oidc.refreshToken";
    private static final String EXPIRES_AT_KEY = "zeroz.oidc.expiresAt";

    /** Refresh at this fraction of the token's life, leaving room for a slow network. */
    private static final double REFRESH_AT = 0.8d;
    /** Never schedule a refresh closer than this, so a very short token cannot spin. */
    private static final int MIN_REFRESH_MILLIS = 5_000;

    private static Config config;

    private OidcClient() {}

    /**
     * Ensures the browser is logged in, then runs the callback.
     *
     * <p>Three cases, decided here rather than by the application: returning from the provider with
     * an authorization code, already holding a usable token, or needing to be sent to log in. Only
     * the first two reach the callback — the third navigates away, and the callback runs on the way
     * back instead.</p>
     *
     * @param config    provider and client details
     * @param onReady   run once an access token is held; this is where the application connects and
     *                  builds its UI
     */
    public static void start(Config config, Runnable onReady) {
        OidcClient.config = config;

        String code = OidcBrowser.queryParameter("code");
        if (code != null && !code.isEmpty()) {
            completeLogin(code, onReady);
            return;
        }

        if (hasUsableToken()) {
            scheduleRefresh();
            onReady.run();
            return;
        }
        beginLogin();
    }

    /**
     * Whether an unexpired access token is held.
     *
     * @return true when {@link #accessToken()} will return something usable
     */
    public static boolean isAuthenticated() {
        return hasUsableToken();
    }

    /**
     * The current access token.
     *
     * @return the token, or null when not logged in
     */
    public static String accessToken() {
        return OidcBrowser.storageGet(ACCESS_TOKEN_KEY);
    }

    /**
     * Appends the current access token to a WebSocket URL, and installs a provider so every
     * reconnect picks up whichever token is current by then.
     *
     * @param wsUrl the WebSocket endpoint
     * @return the URL to connect with
     */
    public static String appendToken(String wsUrl) {
        Zeroz4jClient.setConnectUrlProvider(() -> withToken(wsUrl));
        return withToken(wsUrl);
    }

    private static String withToken(String wsUrl) {
        String token = accessToken();
        if (token == null) {
            return wsUrl;
        }
        return wsUrl + (wsUrl.indexOf('?') >= 0 ? "&" : "?") + "token=" + token;
    }

    /**
     * Discards the local session and sends the browser to the provider to end its session too.
     *
     * <p>Clearing only local storage would leave the provider's own session cookie intact, so the
     * next login would silently succeed without asking — which does not look like a logout to
     * anyone using a shared machine.</p>
     */
    public static void logout() {
        clearTokens();
        if (config == null) {
            return;
        }
        OidcBrowser.navigate(config.logoutEndpoint()
                + "?client_id=" + encode(config.clientId)
                + "&post_logout_redirect_uri=" + encode(config.resolvedRedirectUri()));
    }

    // ---------------------------------------------------------------- the flow

    /** Sends the browser to the provider, having stashed the verifier this attempt will need. */
    private static void beginLogin() {
        String verifier = OidcBrowser.randomToken();
        String state = OidcBrowser.randomToken();
        OidcBrowser.storageSet(VERIFIER_KEY, verifier);
        OidcBrowser.storageSet(STATE_KEY, state);

        OidcBrowser.codeChallenge(verifier, challenge -> {
            if (challenge == null) {
                // Rather than falling back to PKCE's "plain" method, which would send the verifier
                // itself and protect nothing. Web Crypto needs a secure context, so this almost
                // always means the page is on plain http.
                OidcBrowser.warn("[zeroz4j] Cannot compute a PKCE challenge. Web Crypto is only "
                        + "available in a secure context -- serve the application over https (or "
                        + "from localhost) and try again.");
                return;
            }
            OidcBrowser.navigate(config.authorizeEndpoint()
                    + "?response_type=code"
                    + "&client_id=" + encode(config.clientId)
                    + "&redirect_uri=" + encode(config.resolvedRedirectUri())
                    + "&scope=" + encode(config.scope)
                    + "&state=" + encode(state)
                    + "&code_challenge=" + encode(challenge)
                    + "&code_challenge_method=S256");
        });
    }

    /** Handles the return leg: check state, swap the code for tokens, tidy the URL. */
    private static void completeLogin(String code, Runnable onReady) {
        String expectedState = OidcBrowser.storageGet(STATE_KEY);
        String returnedState = OidcBrowser.queryParameter("state");
        String verifier = OidcBrowser.storageGet(VERIFIER_KEY);

        OidcBrowser.storageRemove(STATE_KEY);
        OidcBrowser.storageRemove(VERIFIER_KEY);
        // Strip the code before anything else can fail: it is single-use, and it should not survive
        // in the address bar whether or not the exchange works.
        OidcBrowser.replaceUrl(config.resolvedRedirectUri());

        if (expectedState == null || !expectedState.equals(returnedState)) {
            // A code arriving without the state this tab generated did not come from a login this
            // tab started. Starting over is the only safe response.
            OidcBrowser.warn("[zeroz4j] Discarding an authorization code with an unexpected state "
                    + "parameter; restarting the login.");
            beginLogin();
            return;
        }
        if (verifier == null) {
            beginLogin();
            return;
        }

        String body = "grant_type=authorization_code"
                + "&client_id=" + encode(config.clientId)
                + "&code=" + encode(code)
                + "&redirect_uri=" + encode(config.resolvedRedirectUri())
                + "&code_verifier=" + encode(verifier);

        OidcBrowser.postForm(config.tokenEndpoint(), body,
                (accessToken, refreshToken, expiresIn, error) -> {
                    if (error != null) {
                        OidcBrowser.warn("[zeroz4j] Token exchange failed: " + error);
                        return;
                    }
                    storeTokens(accessToken, refreshToken, expiresIn);
                    scheduleRefresh();
                    onReady.run();
                });
    }

    /**
     * Renews the access token before it expires.
     *
     * <p>Scheduled rather than done on demand because the reconnect path needs a token
     * <em>synchronously</em> — there is no point at which it could wait for a network round trip.</p>
     */
    private static void scheduleRefresh() {
        String refreshToken = OidcBrowser.storageGet(REFRESH_TOKEN_KEY);
        if (refreshToken == null) {
            return;   // provider issued none; the session simply ends when the token expires
        }
        long remaining = expiresAt() - System.currentTimeMillis();
        int delay = (int) Math.max(MIN_REFRESH_MILLIS, remaining * REFRESH_AT);

        OidcBrowser.setTimeout(delay, ignored -> {
            String current = OidcBrowser.storageGet(REFRESH_TOKEN_KEY);
            if (current == null) {
                return;
            }
            String body = "grant_type=refresh_token"
                    + "&client_id=" + encode(config.clientId)
                    + "&refresh_token=" + encode(current);
            OidcBrowser.postForm(config.tokenEndpoint(), body,
                    (accessToken, newRefreshToken, expiresIn, error) -> {
                        if (error != null) {
                            // The refresh token has been revoked or has expired. Nothing local can
                            // recover from that; the user logs in again.
                            OidcBrowser.warn("[zeroz4j] Could not refresh the session: " + error);
                            clearTokens();
                            beginLogin();
                            return;
                        }
                        storeTokens(accessToken, newRefreshToken, expiresIn);
                        scheduleRefresh();
                    });
        });
    }

    // ---------------------------------------------------------------- token storage

    private static boolean hasUsableToken() {
        return accessToken() != null && expiresAt() > System.currentTimeMillis();
    }

    private static long expiresAt() {
        String stored = OidcBrowser.storageGet(EXPIRES_AT_KEY);
        if (stored == null) {
            return 0L;
        }
        try {
            return Long.parseLong(stored);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private static void storeTokens(String accessToken, String refreshToken, int expiresInSeconds) {
        OidcBrowser.storageSet(ACCESS_TOKEN_KEY, accessToken);
        if (refreshToken != null && !refreshToken.isEmpty()) {
            OidcBrowser.storageSet(REFRESH_TOKEN_KEY, refreshToken);
        }
        long lifetime = expiresInSeconds > 0 ? expiresInSeconds * 1000L : 300_000L;
        OidcBrowser.storageSet(EXPIRES_AT_KEY, Long.toString(System.currentTimeMillis() + lifetime));
    }

    private static void clearTokens() {
        OidcBrowser.storageRemove(ACCESS_TOKEN_KEY);
        OidcBrowser.storageRemove(REFRESH_TOKEN_KEY);
        OidcBrowser.storageRemove(EXPIRES_AT_KEY);
    }

    /**
     * Percent-encodes a value for a query string.
     *
     * <p>Hand-rolled because {@code URLEncoder} is not available to TeaVM, and because its
     * {@code application/x-www-form-urlencoded} rules encode a space as {@code +}, which is wrong
     * inside a redirect URI.</p>
     */
    static String encode(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder encoded = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~') {
                encoded.append(c);
            } else if (c < 0x80) {
                appendHex(encoded, c);
            } else {
                // Non-ASCII must travel as UTF-8 bytes, each percent-encoded.
                for (byte b : String.valueOf(c).getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
                    appendHex(encoded, b & 0xFF);
                }
            }
        }
        return encoded.toString();
    }

    private static void appendHex(StringBuilder target, int value) {
        target.append('%');
        target.append(Character.toUpperCase(Character.forDigit((value >> 4) & 0xF, 16)));
        target.append(Character.toUpperCase(Character.forDigit(value & 0xF, 16)));
    }
}
