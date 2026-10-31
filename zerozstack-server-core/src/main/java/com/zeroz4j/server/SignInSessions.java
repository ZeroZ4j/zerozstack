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

import jakarta.enterprise.inject.Instance;
import jakarta.websocket.Session;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Cookie sign-in: the session cookie, its renewal, and the end of a session. One per server.
 *
 * <h2>How it fits together</h2>
 * <ol>
 *   <li>The {@code Login} component posts a real HTML form to {@code zeroz4j-session/sign-in}. The
 *       application's {@link SignInHandler} checks it, and the answer is a redirect carrying the
 *       cookie.</li>
 *   <li>The page opens its WebSocket, the browser attaches the cookie, and the handshake reads it
 *       ({@link #resume(String)}) after any {@link AuthenticationProvider} has passed. Roles come from
 *       the handler each time.</li>
 *   <li>The server closes such a connection after {@code zeroz.signin.connectionMinutes}. The client
 *       reconnects by itself, so that is a fresh check of the cookie and the roles, not a sign-out.</li>
 *   <li>When the cookie is old enough, the AUTH frame says so and the client posts to
 *       {@code zeroz4j-session/renew}.</li>
 * </ol>
 *
 * <h2>Why every cookie is set over plain HTTP, never on the upgrade</h2>
 * Chappe, the Vidocq server, writes a fixed 101 response and cannot add a header to it, and a proxy
 * may strip one. Ordinary POST addresses work the same under all three bindings.
 *
 * <h2>Why the session is a signed cookie and not a row on the server</h2>
 * The framework has no store of its own, and sessions kept in memory would all end at every restart,
 * which defeats thirty days. So the cookie holds the session and the signature protects it. The
 * price is that ending a session needs a record: {@link #revokeSession(String, String)} and
 * {@link #revokeUser(String, String, String)} keep one in memory, which a restart loses. Everything
 * that must outlive a restart goes through {@link SignInHandler#resume(SignInSession)}, which the
 * application answers from its own records and which runs at every handshake and renewal.
 *
 * <p>Plain class owned by {@link ServerRuntime}, reached with {@link #current()}. It carries no HTTP
 * type; each binding hands {@link #handle(String, SignInRequest)} a {@link SignInRequest}.</p>
 *
 * @since 0.11.0
 */
public final class SignInSessions {

    private static final Logger LOG = Logger.getLogger(SignInSessions.class.getName());

    /** The path under which the sign-in addresses live, relative to the application root. */
    public static final String PATH = "zeroz4j-session";

    /** Takes the sign-in form: {@code POST zeroz4j-session/sign-in}. */
    public static final String SIGN_IN = "sign-in";

    /** Ends this browser's session: {@code POST zeroz4j-session/sign-out}. */
    public static final String SIGN_OUT = "sign-out";

    /** Moves this browser's cookie forward: {@code POST zeroz4j-session/renew}. */
    public static final String RENEW = "renew";

    /** The form field carrying the name. */
    public static final String USERNAME_FIELD = "username";

    /** The form field carrying the password. */
    public static final String PASSWORD_FIELD = "password";

    /** The form field carrying the page to land on. */
    public static final String RETURN_FIELD = "return";

    /** The query parameter that tells the form why it is being shown again. */
    public static final String PROBLEM_PARAMETER = "signin";

    /** Where a connection carries its {@link SignInSession}. */
    static final String SESSION_KEY = "zeroz.signin.session";

    /** Where a connection carries the time it must be closed by. */
    static final String CLOSE_AT_KEY = "zeroz.signin.closeAt";

    /** Where a connection carries whether its cookie is due for renewal. */
    static final String RENEW_DUE_KEY = "zeroz.signin.renewDue";

    private static final String DEFAULT_COOKIE_NAME = "zeroz_session";
    private static final int DEFAULT_DAYS = 30;
    private static final int DEFAULT_MAX_DAYS = 365;
    private static final int DEFAULT_RENEW_MINUTES = 60;
    private static final int DEFAULT_CONNECTION_MINUTES = 480;
    private static final int DEFAULT_MAX_ATTEMPTS = 10;
    private static final int DEFAULT_LOCK_MINUTES = 15;

    private static final long MINUTE_MILLIS = 60_000L;
    private static final long DAY_MILLIS = 24L * 60L * MINUTE_MILLIS;

    /** Far more than a name and a password need; refused before anything is parsed. */
    private static final int MAX_FORM_BYTES = 8 * 1024;

    /** Past this a name is not worth hashing a password for, or storing in a cookie. */
    private static final int MAX_USERNAME_CHARS = 320;

    private static final long SWEEP_SECONDS = 30L;

    private static final String ROTATED_REASON = "Reconnecting to check your sign-in.";

    private final ServerRuntime runtime;
    private final SignInThrottle throttle = new SignInThrottle();

    /** Session id to when its cookie would have stopped being accepted anyway. */
    private final Map<String, Long> revokedSessions = new ConcurrentHashMap<>();

    /** Name and tenant to the moment every session started up to then was ended. */
    private final Map<String, Long> revokedUsers = new ConcurrentHashMap<>();

    private final AtomicBoolean plainHttpReported = new AtomicBoolean();

    private volatile Instance<SignInHandler> handlers;
    private volatile SignInTokens tokens;
    private volatile Clock clock = Clock.systemUTC();
    private ScheduledExecutorService sweeper;

    SignInSessions(ServerRuntime runtime) {
        this.runtime = runtime;
    }

    /**
     * This deployment's sessions, for a binding and for an application ending somebody's access.
     *
     * @return the object belonging to the server in this CDI container
     * @throws IllegalStateException when no server is running here
     */
    public static SignInSessions current() {
        ServerRuntime found = ServerRuntime.fromCdi();
        if (found == null) {
            throw new IllegalStateException("No ZeroZ Stack server is running in this CDI container.");
        }
        return found.signIn();
    }

    void useHandlers(Instance<SignInHandler> found) {
        this.handlers = found;
    }

    /**
     * Replaces the clock. Test support: this is how a test reaches day 31 without waiting for it.
     *
     * @param replacement the clock to read from now on
     */
    public void useClock(Clock replacement) {
        this.clock = replacement;
    }

    /**
     * @return whether the application has a {@link SignInHandler}, which is what turns this on
     */
    public boolean isEnabled() {
        Instance<SignInHandler> found = handlers;
        return found != null && !found.isUnsatisfied();
    }

    private SignInHandler handler() {
        Instance<SignInHandler> found = handlers;
        if (found.isAmbiguous()) {
            // Refused rather than resolved arbitrarily: picking one decides who can sign in.
            throw new IllegalStateException("More than one SignInHandler is deployed. "
                    + "Exactly one must be.");
        }
        return found.get();
    }

    private ServerConfig config() {
        return runtime.config();
    }

    private SignInTokens tokens() {
        SignInTokens current = tokens;
        if (current == null) {
            synchronized (this) {
                if (tokens == null) {
                    tokens = new SignInTokens(config());
                }
                current = tokens;
            }
        }
        return current;
    }

    private long now() {
        return clock.millis();
    }

    private String cookieName() {
        return config().get(ServerSettings.SIGN_IN_COOKIE_NAME, DEFAULT_COOKIE_NAME).trim();
    }

    private long connectionMillis() {
        return config().positiveLong(ServerSettings.SIGN_IN_CONNECTION_MINUTES,
                DEFAULT_CONNECTION_MINUTES) * MINUTE_MILLIS;
    }

    private long renewMillis() {
        return config().positiveLong(ServerSettings.SIGN_IN_RENEW_MINUTES, DEFAULT_RENEW_MINUTES)
                * MINUTE_MILLIS;
    }

    // ------------------------------------------------------------------ the addresses

    /**
     * Answers one request to a sign-in address. Every binding calls this and writes back what it
     * returns, so the three cannot differ.
     *
     * @param action  what follows {@value #PATH}: {@value #SIGN_IN}, {@value #SIGN_OUT} or
     *                {@value #RENEW}
     * @param request the request
     * @return what to answer
     */
    public SignInAnswer handle(String action, SignInRequest request) {
        try {
            if (!isEnabled()) {
                return SignInAnswer.status(404);
            }
            String origin = request.header("Origin");
            String host = request.header("Host");
            if (!CrossSiteGuard.isAllowed(config(), origin, host,
                    request.header(CrossSiteGuard.FETCH_SITE_HEADER))) {
                LOG.warning("[zeroz4j] Refused a cross-site request to " + PATH + ". "
                        + OriginPolicy.explainRefusal(config(), origin, host));
                return SignInAnswer.status(403);
            }
            if (SIGN_IN.equals(action)) {
                return signIn(request);
            }
            if (SIGN_OUT.equals(action)) {
                return signOut(request);
            }
            if (RENEW.equals(action)) {
                return renew(request);
            }
            return SignInAnswer.status(404);
        } catch (IOException ex) {
            LOG.fine("[zeroz4j] A sign-in request did not finish: " + ex.getMessage());
            return SignInAnswer.status(400);
        } catch (RuntimeException ex) {
            LOG.log(Level.SEVERE, "[zeroz4j] A sign-in request failed", ex);
            return SignInAnswer.status(500);
        }
    }

    private SignInAnswer signIn(SignInRequest request) throws IOException {
        String type = request.header("Content-Type");
        if (type == null || !type.toLowerCase(Locale.ROOT)
                .startsWith("application/x-www-form-urlencoded")) {
            return SignInAnswer.status(415);
        }
        Map<String, String> form = readForm(request.body());
        if (form == null) {
            return SignInAnswer.status(413);
        }
        String context = request.contextPath();
        String back = form.get(RETURN_FIELD);
        String username = form.getOrDefault(USERNAME_FIELD, "").trim();
        String password = form.getOrDefault(PASSWORD_FIELD, "");
        if (username.isEmpty() || password.isEmpty() || username.length() > MAX_USERNAME_CHARS) {
            return refused(context, back, "failed");
        }
        int maxAttempts = config().positiveInt(ServerSettings.SIGN_IN_MAX_ATTEMPTS,
                DEFAULT_MAX_ATTEMPTS);
        long lockMillis = config().positiveLong(ServerSettings.SIGN_IN_LOCK_MINUTES,
                DEFAULT_LOCK_MINUTES) * MINUTE_MILLIS;
        if (!throttle.attempt(username, now(), maxAttempts, lockMillis)) {
            return refused(context, back, "locked");
        }

        AuthenticatedPrincipal principal;
        try {
            principal = handler().signIn(new SignInAttempt(username, password, form));
        } catch (RuntimeException ex) {
            LOG.log(Level.SEVERE, "[zeroz4j] SignInHandler threw while checking a sign-in", ex);
            return refused(context, back, "error");
        }
        if (principal == null) {
            // The name is not logged. People type their password into the wrong box.
            return refused(context, back, "failed");
        }
        throttle.succeeded(username);

        // Always a new session, whatever cookie came in. A session somebody planted in this browser
        // before the sign-in is therefore never promoted, and the person it belonged to, if any, is
        // no longer shown on this browser's other tabs.
        SignInSession previous = verified(cookieFrom(request));
        long now = now();
        SignInSession session = new SignInSession(SignInTokens.newId(), principal.name(),
                principal.tenantId(), now, now, expiryFor(now, now));
        String token = tokens().write(session);
        if (token == null) {
            LOG.severe("[zeroz4j] A sign-in was refused because the principal name and tenant "
                    + "do not fit in a cookie.");
            return refused(context, back, "error");
        }
        if (previous != null) {
            revokeSession(previous.id(), ROTATED_REASON);
        }
        LOG.info("[zeroz4j] Signed in: " + session);
        return SignInAnswer.redirect(ReturnPage.landingUrl(context, back),
                List.of(sessionCookie(token, session, request)));
    }

    /** Back to the page the form was on, saying why, and not a word about which field was wrong. */
    private static SignInAnswer refused(String context, String back, String problem) {
        String page = ReturnPage.landingUrl(context, back);
        return SignInAnswer.redirect(page + (page.indexOf('?') < 0 ? '?' : '&')
                + PROBLEM_PARAMETER + "=" + problem, Collections.emptyList());
    }

    private SignInAnswer signOut(SignInRequest request) {
        SignInSession session = verified(cookieFrom(request));
        if (session != null) {
            revokeSession(session.id(), "Signed out.");
            try {
                handler().signedOut(session);
            } catch (RuntimeException ex) {
                // The session has ended. The browser must still be told to forget it.
                LOG.log(Level.WARNING, "[zeroz4j] SignInHandler.signedOut threw", ex);
            }
            LOG.info("[zeroz4j] Signed out: " + session);
        }
        // The browser id goes too. On a shared computer the next person must not inherit the record
        // of what this browser was sent, or anything kept under Scope.CLIENT.
        boolean secure = secure(request);
        return SignInAnswer.redirect(StaticContent.baseHref(request.contextPath()), Arrays.asList(
                clearingCookie(request, secure), ClientIdentity.clearingCookieHeader(secure)));
    }

    private SignInAnswer renew(SignInRequest request) {
        SignInSession session = verified(cookieFrom(request));
        if (session == null || isOver(session)) {
            return SignInAnswer.status(401,
                    Collections.singletonList(clearingCookie(request, secure(request))));
        }
        AuthenticatedPrincipal principal;
        try {
            principal = handler().resume(session);
        } catch (RuntimeException ex) {
            // Could not find out. Not a reason to throw a good cookie away; the next try may work.
            LOG.log(Level.WARNING, "[zeroz4j] SignInHandler.resume threw during a renewal", ex);
            return SignInAnswer.status(503);
        }
        if (principal == null) {
            return SignInAnswer.status(401,
                    Collections.singletonList(clearingCookie(request, secure(request))));
        }
        long now = now();
        if (now - session.issuedAtMillis() < renewMillis()) {
            return SignInAnswer.status(204);
        }
        SignInSession renewed = new SignInSession(session.id(), session.name(), session.tenantId(),
                session.startedAtMillis(), now, expiryFor(session.startedAtMillis(), now));
        return SignInAnswer.status(204,
                List.of(sessionCookie(tokens().write(renewed), renewed, request)));
    }

    /** Thirty days from now, but never past the limit counted from the password. */
    private long expiryFor(long startedAt, long now) {
        long days = config().positiveLong(ServerSettings.SIGN_IN_DAYS, DEFAULT_DAYS);
        return Math.min(now + days * DAY_MILLIS, startedAt + maxLifeMillis());
    }

    private long maxLifeMillis() {
        return config().positiveLong(ServerSettings.SIGN_IN_MAX_DAYS, DEFAULT_MAX_DAYS) * DAY_MILLIS;
    }

    // ------------------------------------------------------------------ the form

    /** @return the fields, first value of each, or null when the form is too large */
    private static Map<String, String> readForm(InputStream body) throws IOException {
        ByteArrayOutputStream collected = new ByteArrayOutputStream(256);
        byte[] chunk = new byte[1024];
        int read;
        while ((read = body.read(chunk)) != -1) {
            collected.write(chunk, 0, read);
            if (collected.size() > MAX_FORM_BYTES) {
                return null;
            }
        }
        Map<String, String> fields = new LinkedHashMap<>();
        String text = collected.toString(StandardCharsets.UTF_8);
        if (text.isEmpty()) {
            return fields;
        }
        try {
            for (String pair : text.split("&")) {
                int equals = pair.indexOf('=');
                String name = equals < 0 ? pair : pair.substring(0, equals);
                String value = equals < 0 ? "" : pair.substring(equals + 1);
                fields.putIfAbsent(URLDecoder.decode(name, StandardCharsets.UTF_8),
                        URLDecoder.decode(value, StandardCharsets.UTF_8));
            }
        } catch (IllegalArgumentException malformed) {
            fields.clear();          // treated as an empty form: refused like any other wrong sign-in
        }
        return fields;
    }

    // ------------------------------------------------------------------ the cookie

    private String cookieFrom(SignInRequest request) {
        return cookieFrom(request.header("Cookie"));
    }

    /** The name is matched whole, so {@code zeroz_session_old} cannot answer for the real one. */
    private String cookieFrom(String header) {
        if (header == null || header.isEmpty()) {
            return null;
        }
        String wanted = cookieName();
        for (String part : header.split(";")) {
            String candidate = part.trim();
            int equals = candidate.indexOf('=');
            if (equals > 0 && candidate.substring(0, equals).equals(wanted)) {
                return candidate.substring(equals + 1);
            }
        }
        return null;
    }

    /**
     * Reads a cookie value and checks everything the cookie itself can answer.
     *
     * @return the session, or null when it is unreadable, forged or past its expiry
     */
    private SignInSession verified(String token) {
        SignInSession session = tokens().read(token);
        return session != null && now() < session.expiresAtMillis() ? session : null;
    }

    /** Whether somebody ended this session after its cookie was issued. */
    private boolean isOver(SignInSession session) {
        if (revokedSessions.containsKey(session.id())) {
            return true;
        }
        Long before = revokedUsers.get(userKey(session.name(), session.tenantId()));
        return before != null && session.startedAtMillis() <= before;
    }

    private String sessionCookie(String token, SignInSession session, SignInRequest request) {
        long seconds = Math.max(0L, (session.expiresAtMillis() - now()) / 1000L);
        return cookie(token, seconds, request, secure(request));
    }

    private String clearingCookie(SignInRequest request, boolean secure) {
        return cookie("", 0L, request, secure);
    }

    private String cookie(String value, long maxAgeSeconds, SignInRequest request, boolean secure) {
        String context = request.contextPath();
        String path = context == null || context.isEmpty() ? "/" : context;
        return cookieName() + "=" + value + "; Path=" + path + "; Max-Age=" + maxAgeSeconds
                + "; HttpOnly; SameSite=" + sameSite() + (secure ? "; Secure" : "");
    }

    /**
     * {@code Strict} works even for a link opened from an email, because this cookie is read only at
     * the WebSocket handshake, which the page makes itself, on its own site.
     */
    private String sameSite() {
        return "lax".equalsIgnoreCase(config().get(ServerSettings.SIGN_IN_SAME_SITE, "").trim())
                ? "Lax" : "Strict";
    }

    /**
     * Whether a cookie carries {@code Secure}.
     *
     * <p>Yes unless told otherwise or the page is on this machine. Going by the scheme alone would
     * fail open: behind a proxy ending TLS this server only ever sees plain HTTP, and the session
     * cookie would quietly lose the flag.</p>
     */
    private boolean secure(SignInRequest request) {
        String forced = config().get(ServerSettings.SIGN_IN_SECURE_COOKIE);
        if (forced != null && !forced.trim().isEmpty()) {
            return Boolean.parseBoolean(forced.trim());
        }
        String scheme = request.scheme();
        if ("https".equalsIgnoreCase(scheme)) {
            return true;
        }
        String host = hostName(request.header("Host"));
        if (host.equals("localhost") || host.endsWith(".localhost")
                || host.equals("127.0.0.1") || host.equals("[::1]")) {
            return false;
        }
        if (plainHttpReported.compareAndSet(false, true)) {
            LOG.warning("[zeroz4j] This server sees plain HTTP on " + host + ", so the session cookie "
                    + "carries Secure, assuming a proxy ends TLS. A browser really on plain HTTP "
                    + "drops it and sign-in appears to do nothing. For that case set "
                    + ServerSettings.SIGN_IN_SECURE_COOKIE + "=false.");
        }
        return true;
    }

    private static String hostName(String hostHeader) {
        if (hostHeader == null) {
            return "";
        }
        String host = hostHeader.trim().toLowerCase(Locale.ROOT);
        int colon = host.lastIndexOf(':');
        return colon > 0 && host.indexOf(']') < colon ? host.substring(0, colon) : host;
    }

    // ------------------------------------------------------------------ the handshake

    /**
     * What the handshake learned from a cookie.
     *
     * @param session   the verified session
     * @param principal who the application says this is now
     * @param closeAt   when the connection must be closed by
     * @param renewDue  whether the cookie is old enough to renew
     */
    record Resumed(SignInSession session, AuthenticatedPrincipal principal, long closeAt,
                   boolean renewDue) {
    }

    /**
     * Reads a handshake's cookie.
     *
     * @param cookieHeader the {@code Cookie} header, or null
     * @return the identity, or null when the handshake stays anonymous
     */
    Resumed resume(String cookieHeader) {
        if (!isEnabled()) {
            return null;
        }
        String token = cookieFrom(cookieHeader);
        if (token == null) {
            return null;
        }
        SignInSession session = verified(token);
        if (session == null || isOver(session)) {
            return null;
        }
        AuthenticatedPrincipal principal = handler().resume(session);
        if (principal == null) {
            return null;
        }
        startSweeping();
        long now = now();
        return new Resumed(session, principal,
                Math.min(now + connectionMillis(), session.expiresAtMillis()),
                now - session.issuedAtMillis() >= renewMillis());
    }

    // ------------------------------------------------------------------ ending sessions

    /**
     * Ends one session: its cookie stops working, here, at once, and every connection on it closes.
     * The browser's other tabs are on the same session. Its other devices are not.
     *
     * <p>Remembered in memory until the cookie would have expired. A restart forgets it; see
     * {@link SignInHandler#signedOut(SignInSession)}.</p>
     *
     * @param sessionId {@link SignInSession#id()}
     * @param reason    the close reason, for the log
     * @return how many connections closed
     */
    public int revokeSession(String sessionId, String reason) {
        if (sessionId == null || sessionId.isEmpty()) {
            return 0;
        }
        revokedSessions.put(sessionId, now() + maxLifeMillis());
        return closeMatching(reason, session -> sessionId.equals(session.id()));
    }

    /**
     * Ends every session one person has, on every device, and closes every connection on them. For
     * a disabled account, a changed password, a "sign out everywhere" button, or a role change that
     * must apply at once.
     *
     * <p>Sessions started afterwards work, so a person with a new password can sign in. Remembered
     * in memory; a restart forgets it. Make it permanent in
     * {@link SignInHandler#resume(SignInSession)}.</p>
     *
     * @param name     the principal name; null or blank closes nothing, so a failed lookup cannot
     *                 sign everybody out
     * @param tenantId the tenant, or null in a single-tenant application
     * @param reason   the close reason, for the log
     * @return how many connections closed
     */
    public int revokeUser(String name, String tenantId, String reason) {
        if (name == null || name.trim().isEmpty()) {
            LOG.warning("[zeroz4j] revokeUser() called with no name; nothing was closed.");
            return 0;
        }
        revokedUsers.put(userKey(name, tenantId), now());
        return closeMatching(reason, session -> name.equals(session.name())
                && java.util.Objects.equals(tenantId, session.tenantId()));
    }

    private static String userKey(String name, String tenantId) {
        return (tenantId == null ? "" : tenantId) + '\n' + name;
    }

    private int closeMatching(String reason, java.util.function.Predicate<SignInSession> matches) {
        int closed = 0;
        for (Session connection : runtime.sessions()) {
            Object held = connection.getUserProperties().get(SESSION_KEY);
            if (held instanceof SignInSession session && matches.test(session)
                    && WasmRmiServerEngine.closeSession(connection, reason)) {
                closed++;
            }
        }
        return closed;
    }

    /**
     * Closes every connection past its time and every connection whose session has ended. Runs by
     * itself every {@value #SWEEP_SECONDS} seconds, which also catches a connection that opened
     * while its session was being ended. Public so a test can run it at a moment of its choosing.
     *
     * @return how many connections closed
     */
    public int sweep() {
        long now = now();
        int closed = 0;
        for (Session connection : runtime.sessions()) {
            Object held = connection.getUserProperties().get(SESSION_KEY);
            if (!(held instanceof SignInSession session)) {
                continue;
            }
            Object closeAt = connection.getUserProperties().get(CLOSE_AT_KEY);
            boolean late = closeAt instanceof Long limit && now >= limit;
            if ((late || isOver(session))
                    && WasmRmiServerEngine.closeSession(connection, ROTATED_REASON)) {
                closed++;
            }
        }
        forgetExpired(now);
        return closed;
    }

    private void forgetExpired(long now) {
        for (Iterator<Long> ends = revokedSessions.values().iterator(); ends.hasNext(); ) {
            if (now >= ends.next()) {
                ends.remove();
            }
        }
        long oldest = now - maxLifeMillis();
        for (Iterator<Long> starts = revokedUsers.values().iterator(); starts.hasNext(); ) {
            if (starts.next() < oldest) {
                starts.remove();
            }
        }
    }

    /** Started by the first signed-in connection, so an application without sign-in has no thread. */
    private synchronized void startSweeping() {
        if (sweeper != null) {
            return;
        }
        sweeper = Executors.newSingleThreadScheduledExecutor(work -> {
            Thread thread = new Thread(work, "zeroz4j-sign-in-sweeper");
            thread.setDaemon(true);
            return thread;
        });
        sweeper.scheduleWithFixedDelay(() -> {
            try {
                sweep();
            } catch (RuntimeException ex) {
                LOG.log(Level.WARNING, "[zeroz4j] The sign-in sweep failed", ex);
            }
        }, SWEEP_SECONDS, SWEEP_SECONDS, TimeUnit.SECONDS);
    }

    synchronized void shutDown() {
        if (sweeper != null) {
            sweeper.shutdownNow();
            sweeper = null;
        }
    }
}
