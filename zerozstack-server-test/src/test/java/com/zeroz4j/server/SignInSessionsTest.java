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

import com.zeroz4j.api.RmiService;
import com.zeroz4j.api.SyncFrameTypes;
import com.zeroz4j.server.test.TestConnection;
import com.zeroz4j.server.test.TestServer;
import jakarta.enterprise.context.ApplicationScoped;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cookie sign-in, attacked.
 *
 * <p>Runs on a real {@link TestServer}: real CDI finding the real handler, the real handshake
 * configurator, the real engine. The HTTP side is the {@link SignInRequest} every binding reduces a
 * request to, so what is proved here holds for all three bindings. That each binding reaches this
 * code is proved in {@code VidocqServerTest} and {@code SignInServletTest}.</p>
 */
class SignInSessionsTest {

    private static final String HOST = "app.example.com";
    private static final String SITE = "https://" + HOST;
    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final String COOKIE = "zeroz_session";
    private static final Instant START = Instant.parse("2026-11-01T08:00:00Z");

    /** The application's accounts. Static because a bean is built by the container. */
    @ApplicationScoped
    public static class Accounts implements SignInHandler {
        static final Map<String, String> PASSWORDS = new ConcurrentHashMap<>();
        static final Map<String, Set<String>> ROLES = new ConcurrentHashMap<>();
        static final Set<String> DISABLED = ConcurrentHashMap.newKeySet();
        static final AtomicInteger CHECKED = new AtomicInteger();
        static final List<String> SIGNED_OUT = new java.util.concurrent.CopyOnWriteArrayList<>();
        static volatile boolean broken;

        @Override
        public AuthenticatedPrincipal signIn(SignInAttempt attempt) {
            CHECKED.incrementAndGet();
            if (broken) {
                throw new IllegalStateException("database is down: secret-detail");
            }
            String expected = PASSWORDS.get(attempt.username());
            return expected != null && expected.equals(attempt.password())
                    ? new AuthenticatedPrincipal(attempt.username(), Set.of("old-role"),
                            attempt.field("tenant"))
                    : null;
        }

        @Override
        public AuthenticatedPrincipal resume(SignInSession session) {
            if (broken) {
                throw new IllegalStateException("database is down");
            }
            if (DISABLED.contains(session.name())) {
                return null;
            }
            return new AuthenticatedPrincipal(session.name(),
                    ROLES.getOrDefault(session.name(), Set.of("user")), session.tenantId())
                    .withAttribute("caller", "resolved:" + session.name());
        }

        @Override
        public void signedOut(SignInSession session) {
            SIGNED_OUT.add(session.id());
        }

        static void reset() {
            PASSWORDS.clear();
            ROLES.clear();
            DISABLED.clear();
            SIGNED_OUT.clear();
            CHECKED.set(0);
            broken = false;
            PASSWORDS.put("alice@example.com", "correct horse");
            PASSWORDS.put("bob@example.com", "battery staple");
        }
    }

    /** A second handler, for the test that refuses two. */
    @ApplicationScoped
    public static class OtherAccounts extends Accounts {
    }

    /** What a screen would call. */
    @RmiService
    public interface Who {
        String who();

        String attribute(String name);

        void keep(String name, String value);
    }

    /** Answers from the request context. */
    @ApplicationScoped
    public static class WhoImpl implements Who {
        @Override
        public String who() {
            return RmiRequestContext.getPrincipal() == null ? "nobody"
                    : RmiRequestContext.getPrincipal().getName();
        }

        @Override
        public String attribute(String name) {
            return RmiRequestContext.getSessionAttribute(name);
        }

        @Override
        public void keep(String name, String value) {
            RmiRequestContext.setSessionAttribute(name, value);
        }
    }

    /** A clock a test can move. */
    private static final class MovableClock extends Clock {
        private Instant now = START;

        void forward(Duration amount) {
            now = now.plus(amount);
        }

        @Override public Instant instant() { return now; }
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
    }

    private final MovableClock clock = new MovableClock();
    private TestServer server;

    @BeforeEach
    void start() {
        Accounts.reset();
        server = server(TestServer.builder());
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.close();
        }
    }

    private TestServer server(TestServer.Builder builder) {
        TestServer started = builder.ignoringSystemProperties()
                .named("sign-in")
                .beans(Accounts.class, WhoImpl.class)
                .set(ServerSettings.SIGN_IN_SECRET, SECRET)
                .start();
        started.runtime().signIn().useClock(clock);
        return started;
    }

    private SignInSessions sessions() {
        return server.runtime().signIn();
    }

    // ------------------------------------------------------------------ a browser, by hand

    /** One POST, shaped the way a same-origin form post from a browser is. */
    private final class Post implements SignInRequest {
        final Map<String, String> headers = new HashMap<>();
        byte[] body = new byte[0];
        String scheme = "https";
        String context = "";

        Post() {
            headers.put("host", HOST);
            headers.put("origin", SITE);
            headers.put("sec-fetch-site", "same-origin");
            headers.put("content-type", "application/x-www-form-urlencoded");
        }

        Post form(String... pairs) {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < pairs.length; i += 2) {
                if (text.length() > 0) {
                    text.append('&');
                }
                text.append(URLEncoder.encode(pairs[i], StandardCharsets.UTF_8)).append('=')
                        .append(URLEncoder.encode(pairs[i + 1], StandardCharsets.UTF_8));
            }
            body = text.toString().getBytes(StandardCharsets.UTF_8);
            return this;
        }

        Post header(String name, String value) {
            if (value == null) {
                headers.remove(name.toLowerCase());
            } else {
                headers.put(name.toLowerCase(), value);
            }
            return this;
        }

        Post cookie(String token) {
            return header("Cookie", COOKIE + "=" + token);
        }

        SignInAnswer to(String action) {
            return sessions().handle(action, this);
        }

        @Override public String header(String name) { return headers.get(name.toLowerCase()); }
        @Override public InputStream body() { return new ByteArrayInputStream(body); }
        @Override public String scheme() { return scheme; }
        @Override public String contextPath() { return context; }
    }

    private SignInAnswer signIn(String username, String password) {
        return new Post().form("username", username, "password", password).to(SignInSessions.SIGN_IN);
    }

    private static String tokenIn(SignInAnswer answer) {
        for (String cookie : answer.cookies()) {
            if (cookie.startsWith(COOKIE + "=")) {
                return cookie.substring(COOKIE.length() + 1, cookie.indexOf(';'));
            }
        }
        return null;
    }

    private String aliceToken() {
        String token = tokenIn(signIn("alice@example.com", "correct horse"));
        assertNotNull(token, "the sign-in should have set a cookie");
        return token;
    }

    private TestConnection connect(String token) {
        return server.connectWithHeaders(Map.of("Cookie", COOKIE + "=" + token));
    }

    private static String nameOn(TestConnection connection) {
        return connection.getUserPrincipal() == null ? null : connection.getUserPrincipal().getName();
    }

    // ------------------------------------------------------------------ what should work

    @Test
    @DisplayName("a sign-in sets the cookie, and the connection it opens is that person")
    void signInAndConnect() {
        SignInAnswer answer = new Post().form("username", "  alice@example.com ",
                "password", "correct horse", "return", "tasks/7?tab=notes").to(SignInSessions.SIGN_IN);

        assertEquals(303, answer.status());
        assertEquals("/tasks/7?tab=notes", answer.location(), "back to the page that was asked for");
        assertEquals(1, answer.cookies().size());
        String cookie = answer.cookies().get(0);
        assertTrue(cookie.contains("; HttpOnly"), cookie);
        assertTrue(cookie.contains("; SameSite=Strict"), cookie);
        assertTrue(cookie.contains("; Secure"), cookie);
        assertTrue(cookie.contains("; Path=/"), cookie);
        assertTrue(cookie.contains("; Max-Age=" + Duration.ofDays(30).toSeconds()), cookie);

        try (TestConnection browser = connect(tokenIn(answer))) {
            assertEquals("alice@example.com", nameOn(browser));
            assertEquals("alice@example.com", browser.call(Who.class, "who"));
        }
    }

    @Test
    @DisplayName("roles come from the application at every handshake, never from the cookie")
    void rolesAreFresh() {
        String token = aliceToken();
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[5]),
                StandardCharsets.UTF_8);
        assertEquals("alice@example.com", payload);
        assertFalse(token.contains(Base64.getUrlEncoder().withoutPadding()
                .encodeToString("old-role".getBytes(StandardCharsets.UTF_8))), "no role in the cookie");

        Accounts.ROLES.put("alice@example.com", Set.of("promoted"));
        try (TestConnection browser = connect(token)) {
            assertEquals(Set.of("promoted"), browser.getUserProperties().get("zeroz.roles"));
        }
    }

    @Test
    @DisplayName("the context path is in the cookie's Path and in every redirect")
    void underAContextPath() {
        Post post = new Post().form("username", "alice@example.com", "password", "correct horse",
                "return", "tasks/7");
        post.context = "/coachapp";
        SignInAnswer answer = post.to(SignInSessions.SIGN_IN);

        assertEquals("/coachapp/tasks/7", answer.location());
        assertTrue(answer.cookies().get(0).contains("; Path=/coachapp;"), answer.cookies().get(0));
    }

    @Test
    @DisplayName("Secure is on behind a proxy that ends TLS, and off only for a page on this machine")
    void secureFlag() {
        Post behindProxy = new Post().form("username", "alice@example.com", "password", "correct horse");
        behindProxy.scheme = "http";
        assertTrue(behindProxy.to(SignInSessions.SIGN_IN).cookies().get(0).contains("; Secure"));

        Post local = new Post().form("username", "alice@example.com", "password", "correct horse");
        local.scheme = "http";
        local.header("Host", "localhost:8093").header("Origin", "http://localhost:8093");
        assertFalse(local.to(SignInSessions.SIGN_IN).cookies().get(0).contains("Secure"));
    }

    // ------------------------------------------------------------------ guessing

    @Test
    @DisplayName("a wrong password and an unknown name get the same answer, and neither gets a cookie")
    void refusalSaysNothing() {
        SignInAnswer wrong = signIn("alice@example.com", "wrong");
        SignInAnswer unknown = signIn("nobody@example.com", "wrong");

        assertEquals(303, wrong.status());
        assertEquals("/?signin=failed", wrong.location());
        assertEquals(wrong.location(), unknown.location());
        assertTrue(wrong.cookies().isEmpty());
        assertTrue(unknown.cookies().isEmpty());
    }

    @Test
    @DisplayName("past the attempt limit even the right password is refused, without being checked")
    void attemptsAreLimited() {
        for (int i = 0; i < 10; i++) {
            assertEquals("/?signin=failed", signIn("alice@example.com", "guess" + i).location());
        }
        int checked = Accounts.CHECKED.get();

        SignInAnswer locked = signIn("alice@example.com", "correct horse");

        assertEquals("/?signin=locked", locked.location());
        assertTrue(locked.cookies().isEmpty());
        assertEquals(checked, Accounts.CHECKED.get(), "the handler was not asked");
        assertEquals("/?signin=locked", signIn("ALICE@example.com", "x").location(),
                "changing the case does not get a fresh count");
        assertNotNull(tokenIn(signIn("bob@example.com", "battery staple")), "another name is fine");

        clock.forward(Duration.ofMinutes(16));
        assertNotNull(tokenIn(signIn("alice@example.com", "correct horse")), "the wait ends");
    }

    @Test
    @DisplayName("a name that does not exist is stopped the same way, so being stopped proves nothing")
    void lockingRevealsNothing() {
        for (int i = 0; i < 10; i++) {
            signIn("nobody@example.com", "guess" + i);
        }
        assertEquals("/?signin=locked", signIn("nobody@example.com", "x").location());
    }

    @Test
    @DisplayName("a correct password clears the count")
    void successForgivesFailures() {
        for (int i = 0; i < 9; i++) {
            signIn("alice@example.com", "guess" + i);
        }
        assertNotNull(tokenIn(signIn("alice@example.com", "correct horse")));
        assertEquals("/?signin=failed", signIn("alice@example.com", "guess").location());
    }

    @Test
    @DisplayName("fifty guesses at once cannot get past the limit while the first is being checked")
    void parallelGuessesAreCounted() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            List<Future<SignInAnswer>> answers = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                int guess = i;
                answers.add(pool.submit(() -> signIn("alice@example.com", "guess" + guess)));
            }
            for (Future<SignInAnswer> answer : answers) {
                answer.get();
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(10, Accounts.CHECKED.get());
    }

    // ------------------------------------------------------------------ a forged cookie

    @Test
    @DisplayName("a cookie changed in any way is no cookie at all")
    void tamperedCookies() {
        String good = aliceToken();
        String[] parts = good.split("\\.", -1);
        String bobName = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("bob@example.com".getBytes(StandardCharsets.UTF_8));

        List<String> forged = new ArrayList<>();
        forged.add(good.replace(parts[5], bobName));                       // somebody else's name
        forged.add(good.replace(parts[4], String.valueOf(Long.MAX_VALUE))); // a later expiry
        forged.add(good.substring(0, good.length() - 1)
                + (good.endsWith("A") ? "B" : "A"));                       // one character of the MAC
        forged.add(good.substring(0, good.lastIndexOf('.') + 1));           // an empty MAC
        forged.add(good.substring(0, good.lastIndexOf('.')));               // no MAC
        forged.add(parts[0] + "." + parts[1]);                              // cut short
        forged.add("garbage");
        forged.add("");
        forged.add("x".repeat(100_000));

        for (String token : forged) {
            try (TestConnection browser = connect(token)) {
                assertNull(nameOn(browser), "accepted: " + token.substring(0, Math.min(60, token.length())));
                assertTrue(browser.isOpen(), "a forged cookie is an anonymous visit, not an error");
            }
            assertEquals(401, new Post().cookie(token).to(SignInSessions.RENEW).status());
        }
    }

    @Test
    @DisplayName("a cookie signed with another key, which is what another deployment holds, is refused")
    void anotherServersCookie() {
        String theirs = aliceToken();
        server.close();
        server = TestServer.builder().ignoringSystemProperties().named("other")
                .beans(Accounts.class, WhoImpl.class)
                .set(ServerSettings.SIGN_IN_SECRET, "another-key-another-key-another-key")
                .start();

        try (TestConnection browser = connect(theirs)) {
            assertNull(nameOn(browser));
        }
    }

    @Test
    @DisplayName("a short secret is refused rather than used, and nobody can sign in with it")
    void shortSecret() {
        server.close();
        server = TestServer.builder().ignoringSystemProperties().named("short")
                .beans(Accounts.class, WhoImpl.class)
                .set(ServerSettings.SIGN_IN_SECRET, "too-short")
                .start();

        assertEquals(500, signIn("alice@example.com", "correct horse").status());
    }

    @Test
    @DisplayName("a lookalike cookie name does not answer for the real one")
    void lookalikeCookie() {
        String token = aliceToken();
        try (TestConnection browser = server.connectWithHeaders(Map.of(
                "Cookie", COOKIE + "_old=" + token + "; x" + COOKIE + "=" + token))) {
            assertNull(nameOn(browser));
        }
    }

    // ------------------------------------------------------------------ lifetimes

    @Test
    @DisplayName("a cookie dies thirty days after it was issued, not a second later")
    void cookieExpires() {
        String token = aliceToken();

        clock.forward(Duration.ofDays(30).minusSeconds(1));
        try (TestConnection browser = connect(token)) {
            assertEquals("alice@example.com", nameOn(browser));
        }
        clock.forward(Duration.ofSeconds(1));
        try (TestConnection browser = connect(token)) {
            assertNull(nameOn(browser));
        }
        assertEquals(401, new Post().cookie(token).to(SignInSessions.RENEW).status());
    }

    @Test
    @DisplayName("renewal slides the thirty days, keeps the session, and is not issued too often")
    void renewal() {
        String first = aliceToken();

        assertEquals(204, new Post().cookie(first).to(SignInSessions.RENEW).status());
        assertTrue(new Post().cookie(first).to(SignInSessions.RENEW).cookies().isEmpty(),
                "too soon to be worth a new cookie");

        clock.forward(Duration.ofDays(29));
        SignInAnswer renewed = new Post().cookie(first).to(SignInSessions.RENEW);
        String second = tokenIn(renewed);
        assertEquals(204, renewed.status());
        assertNotNull(second);
        assertNotEquals(first, second);

        clock.forward(Duration.ofDays(29));
        try (TestConnection browser = connect(second)) {
            assertEquals("alice@example.com", nameOn(browser), "alive on day 58");
            assertEquals("alice@example.com", browser.call(Who.class, "who"));
        }
        assertEquals(first.split("\\.")[1], second.split("\\.")[1], "one session across renewals");
        assertEquals(first.split("\\.")[2], second.split("\\.")[2], "the password time does not move");
    }

    @Test
    @DisplayName("renewal cannot carry a session past a year from the password")
    void renewalHasAnEnd() {
        String token = aliceToken();
        for (int day = 0; day < 13; day++) {
            clock.forward(Duration.ofDays(29));
            String next = tokenIn(new Post().cookie(token).to(SignInSessions.RENEW));
            assertNotNull(next, "renewed on month " + day);
            token = next;
        }
        clock.forward(Duration.ofDays(29));
        assertEquals(401, new Post().cookie(token).to(SignInSessions.RENEW).status(),
                "377 days after the password");
    }

    @Test
    @DisplayName("the AUTH frame says when the cookie is old enough, and only then")
    void authFrameAsksForRenewal() {
        String token = aliceToken();
        try (TestConnection young = connect(token)) {
            assertEquals(SyncFrameTypes.AUTH_SESSION_COOKIE, lastByte(young.frame(0)));
        }
        clock.forward(Duration.ofMinutes(61));
        try (TestConnection old = connect(token)) {
            assertEquals(SyncFrameTypes.AUTH_SESSION_COOKIE | SyncFrameTypes.AUTH_RENEW_DUE,
                    lastByte(old.frame(0)));
        }
        try (TestConnection anonymous = server.connectWithHeaders(Map.of())) {
            assertEquals(0, lastByte(anonymous.frame(0)));
        }
    }

    private static byte lastByte(byte[] frame) {
        assertEquals(TestConnection.AUTH, frame[4]);
        return frame[frame.length - 1];
    }

    @Test
    @DisplayName("a connection is closed at eight hours and not before, and the reconnect is checked again")
    void connectionCap() {
        String token = aliceToken();
        TestConnection browser = connect(token);

        clock.forward(Duration.ofHours(8).minusSeconds(1));
        assertEquals(0, sessions().sweep());
        assertTrue(browser.isOpen());

        clock.forward(Duration.ofSeconds(1));
        assertEquals(1, sessions().sweep());
        assertFalse(browser.isOpen());

        Accounts.ROLES.put("alice@example.com", Set.of("changed"));
        try (TestConnection again = connect(token)) {
            assertEquals(Set.of("changed"), again.getUserProperties().get("zeroz.roles"),
                    "the reconnect picks up the change");
        }
    }

    @Test
    @DisplayName("a connection does not outlive its cookie, whatever the cap says")
    void connectionEndsWithItsCookie() {
        String token = aliceToken();
        clock.forward(Duration.ofDays(30).minusHours(1));
        TestConnection browser = connect(token);

        clock.forward(Duration.ofHours(1));
        assertEquals(1, sessions().sweep());
        assertFalse(browser.isOpen());
    }

    // ------------------------------------------------------------------ ending a session

    @Test
    @DisplayName("sign-out clears both cookies, closes every tab, and the old cookie is dead")
    void signOut() {
        String token = aliceToken();
        TestConnection tabOne = connect(token);
        TestConnection tabTwo = connect(token);
        String otherDevice = aliceToken();
        TestConnection phone = connect(otherDevice);

        SignInAnswer answer = new Post().cookie(token).to(SignInSessions.SIGN_OUT);

        assertEquals(303, answer.status());
        assertEquals("/", answer.location());
        assertTrue(answer.cookies().stream().anyMatch(
                c -> c.startsWith(COOKIE + "=;") && c.contains("Max-Age=0")), answer.cookies().toString());
        assertTrue(answer.cookies().stream().anyMatch(
                        c -> c.startsWith(ClientIdentity.COOKIE_NAME + "=;") && c.contains("Max-Age=0")),
                "the browser id goes too, so the next person on this computer starts clean");
        assertFalse(tabOne.isOpen());
        assertFalse(tabTwo.isOpen());
        assertTrue(phone.isOpen(), "signing out here does not sign out the phone");
        assertEquals(1, Accounts.SIGNED_OUT.size(), "the application is told");

        try (TestConnection replay = connect(token)) {
            assertNull(nameOn(replay), "a copy of the cookie stops working");
        }
        assertEquals(401, new Post().cookie(token).to(SignInSessions.RENEW).status());
    }

    @Test
    @DisplayName("sign-out with no cookie, or a bad one, still clears the browser")
    void signOutWithoutASession() {
        assertEquals(2, new Post().to(SignInSessions.SIGN_OUT).cookies().size());
        assertEquals(2, new Post().cookie("garbage").to(SignInSessions.SIGN_OUT).cookies().size());
        assertTrue(Accounts.SIGNED_OUT.isEmpty());
    }

    @Test
    @DisplayName("revoking a person closes every device, kills every cookie, and leaves others alone")
    void revokeUser() {
        String laptop = aliceToken();
        String phone = aliceToken();
        String bob = tokenIn(signIn("bob@example.com", "battery staple"));
        TestConnection one = connect(laptop);
        TestConnection two = connect(phone);
        TestConnection bystander = connect(bob);

        clock.forward(Duration.ofSeconds(1));
        assertEquals(2, sessions().revokeUser("alice@example.com", null, "Access withdrawn."));

        assertFalse(one.isOpen());
        assertFalse(two.isOpen());
        assertTrue(bystander.isOpen());
        try (TestConnection replay = connect(laptop)) {
            assertNull(nameOn(replay));
        }

        clock.forward(Duration.ofSeconds(1));
        try (TestConnection fresh = connect(aliceToken())) {
            assertEquals("alice@example.com", nameOn(fresh), "a new sign-in works");
        }
    }

    @Test
    @DisplayName("revoking with a name that failed to look up closes nothing")
    void revokeNobody() {
        TestConnection browser = connect(aliceToken());

        assertEquals(0, sessions().revokeUser(null, null, "x"));
        assertEquals(0, sessions().revokeUser(" ", null, "x"));
        assertEquals(0, sessions().revokeSession(null, "x"));
        assertTrue(browser.isOpen());
    }

    @Test
    @DisplayName("one name in two tenants is two people")
    void revokeIsPerTenant() {
        String acme = tokenIn(new Post().form("username", "alice@example.com",
                "password", "correct horse", "tenant", "acme").to(SignInSessions.SIGN_IN));
        String globex = tokenIn(new Post().form("username", "alice@example.com",
                "password", "correct horse", "tenant", "globex").to(SignInSessions.SIGN_IN));
        TestConnection first = connect(acme);
        TestConnection second = connect(globex);
        assertEquals("acme", first.getUserProperties().get("zeroz.tenant"));

        clock.forward(Duration.ofSeconds(1));
        assertEquals(1, sessions().revokeUser("alice@example.com", "acme", "x"));

        assertFalse(first.isOpen());
        assertTrue(second.isOpen());
    }

    @Test
    @DisplayName("a disabled account is refused at the next handshake and the next renewal")
    void disabledAccount() {
        String token = aliceToken();
        Accounts.DISABLED.add("alice@example.com");

        try (TestConnection browser = connect(token)) {
            assertNull(nameOn(browser));
        }
        SignInAnswer renew = new Post().cookie(token).to(SignInSessions.RENEW);
        assertEquals(401, renew.status());
        assertTrue(renew.cookies().get(0).contains("Max-Age=0"), "the cookie is cleared");
    }

    @Test
    @DisplayName("a connection that opened while its session was ending is caught by the sweep")
    void sweepCatchesARevokedConnection() {
        TestConnection browser = connect(aliceToken());
        SignInSession session = (SignInSession) browser.getUserProperties()
                .get(SignInSessions.SESSION_KEY);
        browser.getUserProperties().remove(SignInSessions.SESSION_KEY);
        sessions().revokeSession(session.id(), "x");
        assertTrue(browser.isOpen(), "stands in for one the revocation did not see");
        browser.getUserProperties().put(SignInSessions.SESSION_KEY, session);

        assertEquals(1, sessions().sweep());
        assertFalse(browser.isOpen());
    }

    @Test
    @DisplayName("when the application is down the cookie is kept, because that is not a sign-out")
    void applicationDown() {
        String token = aliceToken();
        clock.forward(Duration.ofHours(2));
        Accounts.broken = true;

        SignInAnswer renew = new Post().cookie(token).to(SignInSessions.RENEW);
        assertEquals(503, renew.status());
        assertTrue(renew.cookies().isEmpty());
        try (TestConnection browser = connect(token)) {
            assertNull(nameOn(browser), "anonymous for now");
        }
        Accounts.broken = false;
        try (TestConnection browser = connect(token)) {
            assertEquals("alice@example.com", nameOn(browser));
        }
    }

    @Test
    @DisplayName("a handler that throws at sign-in gets a plain refusal, with no detail in it")
    void handlerFailureLeaksNothing() {
        Accounts.broken = true;
        SignInAnswer answer = signIn("alice@example.com", "correct horse");

        assertEquals("/?signin=error", answer.location());
        assertTrue(answer.cookies().isEmpty());
    }

    // ------------------------------------------------------------------ session fixation

    @Test
    @DisplayName("a session planted in the browser beforehand is never carried into the sign-in")
    void sessionFixation() {
        String planted = tokenIn(signIn("bob@example.com", "battery staple"));
        TestConnection plantedTab = connect(planted);

        SignInAnswer answer = new Post().cookie(planted)
                .form("username", "alice@example.com", "password", "correct horse")
                .to(SignInSessions.SIGN_IN);

        String mine = tokenIn(answer);
        assertNotEquals(planted.split("\\.")[1], mine.split("\\.")[1], "a new session id");
        assertFalse(plantedTab.isOpen(), "and the old one ends");
        try (TestConnection replay = connect(planted)) {
            assertNull(nameOn(replay));
        }
    }

    // ------------------------------------------------------------------ the page to go back to

    @Test
    @DisplayName("every spelling of 'another site' lands on the application root")
    void openRedirect() {
        String[] hostile = {
            "https://evil.example/", "//evil.example", "///evil.example", "/\\evil.example",
            "\\\\evil.example", "javascript:alert(1)", "data:text/html,x", "http:evil.example",
            "/\t/evil.example", "\t//evil.example", "/\r\nSet-Cookie: x=1", "/%0d%0aSet-Cookie: x=1",
            "/%5cevil.example", "/%0aevil", "..", "../other", "a/../../other", "a/%2e%2e/%2e%2e/other",
            "/%2e%2e/other", "a/..;x/b", "%2F%2Fevil.example", "zeroz4j-session/sign-out",
            "Zeroz4j-Session/renew", "zeroz4j-session;x=1/sign-out", "wasm-rmi", "x".repeat(2000),
            "space in it"
        };
        for (String back : hostile) {
            String location = new Post().form("username", "alice@example.com",
                    "password", "correct horse", "return", back).to(SignInSessions.SIGN_IN).location();
            assertEquals("/", location, "followed: " + back);

            String refused = new Post().form("username", "bob@example.com",
                    "password", "wrong", "return", back).to(SignInSessions.SIGN_IN).location();
            assertEquals("/?signin=failed", refused, "followed on refusal: " + back);
            Accounts.reset();
            server.close();
            server = server(TestServer.builder());
        }
    }

    @Test
    @DisplayName("ordinary pages are kept, with their query, and without a fragment")
    void returnPagesThatAreFine() {
        assertEquals("tasks/7", ReturnPage.route("tasks/7"));
        assertEquals("tasks/7", ReturnPage.route("/tasks/7"));
        assertEquals("tasks/7?tab=a&b=c", ReturnPage.route("tasks/7?tab=a&b=c#top"));
        assertEquals("people/caf%C3%A9", ReturnPage.route("people/caf%C3%A9"));
        assertEquals("search?next=//elsewhere", ReturnPage.route("search?next=//elsewhere"));
        assertNull(ReturnPage.route(""));
        assertNull(ReturnPage.route("/"));
        assertNull(ReturnPage.route(null));
        assertEquals("/?signin=failed", new Post().form("username", "x", "password", "y")
                .to(SignInSessions.SIGN_IN).location());
        assertEquals("/tasks/7?tab=a&signin=failed", new Post().form("username", "x", "password", "y",
                "return", "tasks/7?tab=a").to(SignInSessions.SIGN_IN).location());
    }

    // ------------------------------------------------------------------ another site's page

    @Test
    @DisplayName("another site cannot sign a visitor in, out, or renew them")
    void crossSiteRequests() {
        String token = aliceToken();
        TestConnection browser = connect(token);
        for (String action : new String[] {SignInSessions.SIGN_IN, SignInSessions.SIGN_OUT,
                SignInSessions.RENEW}) {
            assertEquals(403, new Post().cookie(token).form("username", "bob@example.com",
                            "password", "battery staple")
                    .header("Origin", "https://evil.example").header("Sec-Fetch-Site", "cross-site")
                    .to(action).status(), action);
            assertEquals(403, new Post().cookie(token).header("Origin", "null").to(action).status(),
                    "a sandboxed frame says null: " + action);
            assertEquals(403, new Post().cookie(token).header("Origin", null)
                    .header("Sec-Fetch-Site", "cross-site").to(action).status(),
                    "a browser that left Origin off: " + action);
            assertEquals(403, new Post().cookie(token).header("Origin", null)
                    .header("Sec-Fetch-Site", "same-site").to(action).status(),
                    "a page on a sibling subdomain: " + action);
        }
        assertTrue(browser.isOpen(), "nothing happened");
        assertTrue(Accounts.SIGNED_OUT.isEmpty());
        assertEquals(1, Accounts.CHECKED.get(), "the attacker's password was never checked");
    }

    @Test
    @DisplayName("a page the deployment names may post from another origin; zeroz.hosts still applies")
    void namedOriginAndHosts() {
        server.close();
        server = server(TestServer.builder()
                .set(ServerSettings.ORIGINS, "https://www.example.com")
                .set(ServerSettings.HOSTS, HOST));

        assertEquals(303, new Post().form("username", "alice@example.com", "password", "correct horse")
                .header("Origin", "https://www.example.com").header("Sec-Fetch-Site", "same-site")
                .to(SignInSessions.SIGN_IN).status());
        assertEquals(403, new Post().form("username", "alice@example.com", "password", "correct horse")
                .header("Host", "rebound.example").to(SignInSessions.SIGN_IN).status());
    }

    @Test
    @DisplayName("a page on another site cannot open a signed-in connection with the visitor's cookie")
    void crossSiteWebSocket() {
        String token = aliceToken();
        Accounts.CHECKED.set(0);

        TestConnection hijack = server.connectWithHeaders(Map.of(
                "Cookie", COOKIE + "=" + token, "Origin", "https://evil.example", "Host", HOST));

        assertFalse(hijack.isOpen(), "closed before it is told anything");
        assertNull(nameOn(hijack));
    }

    // ------------------------------------------------------------------ what the form may carry

    @Test
    @DisplayName("only a form is accepted, and only a small one")
    void bodyLimits() {
        assertEquals(415, new Post().form("username", "alice@example.com", "password", "correct horse")
                .header("Content-Type", "application/json").to(SignInSessions.SIGN_IN).status());
        assertEquals(415, new Post().header("Content-Type", null).to(SignInSessions.SIGN_IN).status());
        assertEquals(413, new Post().form("username", "a".repeat(9000), "password", "x")
                .to(SignInSessions.SIGN_IN).status());
        assertEquals(0, Accounts.CHECKED.get());
    }

    @Test
    @DisplayName("an empty, malformed or absurd form is a refusal the application never sees")
    void uselessForms() {
        assertEquals("/?signin=failed", new Post().to(SignInSessions.SIGN_IN).location());
        assertEquals("/?signin=failed", signIn("alice@example.com", "").location());
        assertEquals("/?signin=failed", signIn("   ", "x").location());
        assertEquals("/?signin=failed", signIn("a".repeat(321), "x").location());
        Post malformed = new Post();
        malformed.body = "username=%zz&password=%".getBytes(StandardCharsets.UTF_8);
        assertEquals("/?signin=failed", malformed.to(SignInSessions.SIGN_IN).location());
        assertEquals(0, Accounts.CHECKED.get());
    }

    @Test
    @DisplayName("an attempt's text form cannot put a password in a log")
    void attemptHidesThePassword() {
        assertFalse(new SignInAttempt("a", "hunter2", Map.of("password", "hunter2"))
                .toString().contains("hunter2"));
    }

    @Test
    @DisplayName("the first value of a repeated field wins, so a form cannot be argued with")
    void repeatedFields() {
        Post post = new Post();
        post.body = "username=alice%40example.com&username=bob%40example.com&password=correct+horse"
                .getBytes(StandardCharsets.UTF_8);
        String token = tokenIn(post.to(SignInSessions.SIGN_IN));
        try (TestConnection browser = connect(token)) {
            assertEquals("alice@example.com", nameOn(browser));
        }
    }

    @Test
    @DisplayName("a name too long for a cookie is refused rather than set and silently dropped")
    void nameTooLongForACookie() {
        String name = "n".repeat(320);
        Accounts.PASSWORDS.put(name, "x");
        assertNotNull(tokenIn(signIn(name, "x")), "the longest name allowed fits");

        SignInAnswer answer = new Post().form("username", "alice@example.com",
                "password", "correct horse", "tenant", "t".repeat(4000)).to(SignInSessions.SIGN_IN);
        assertEquals("/?signin=error", answer.location());
        assertTrue(answer.cookies().isEmpty());
    }

    // ------------------------------------------------------------------ beside everything else

    @Test
    @DisplayName("with no handler the addresses do not exist and a cookie means nothing")
    void noHandler() {
        server.close();
        server = TestServer.builder().ignoringSystemProperties().named("off")
                .beans(WhoImpl.class).start();

        assertEquals(404, signIn("alice@example.com", "correct horse").status());
        assertEquals(404, new Post().to(SignInSessions.SIGN_OUT).status());
        try (TestConnection browser = connect("anything")) {
            assertNull(nameOn(browser));
        }
    }

    @Test
    @DisplayName("two handlers are refused, because picking one decides who can sign in")
    void twoHandlers() {
        server.close();
        server = TestServer.builder().ignoringSystemProperties().named("two")
                .beans(Accounts.class, OtherAccounts.class, WhoImpl.class)
                .set(ServerSettings.SIGN_IN_SECRET, SECRET).start();

        SignInAnswer answer = signIn("alice@example.com", "correct horse");
        assertEquals("/?signin=error", answer.location());
        assertTrue(answer.cookies().isEmpty());
        assertEquals(0, Accounts.CHECKED.get());
    }

    @Test
    @DisplayName("an unknown action is a 404")
    void unknownAction() {
        assertEquals(404, new Post().to("drop-tables").status());
    }

    @Test
    @DisplayName("an AuthenticationProvider goes first, and a provider that passes leaves the cookie to work")
    void besideAProvider() {
        String token = aliceToken();
        try (TestConnection browser = server.connectWithHeaders(Map.of(
                "Cookie", COOKIE + "=" + token, HeaderProvider.HEADER, "service-account"))) {
            assertEquals("service-account", nameOn(browser), "the explicit credential wins");
            assertNull(browser.getUserProperties().get(SignInSessions.SESSION_KEY));
            assertEquals("from-provider", browser.call(Who.class, "attribute", "origin"));
            clock.forward(Duration.ofHours(9));
            assertEquals(0, sessions().sweep(), "the cap is for connections on a session cookie");
            assertTrue(browser.isOpen());
        }
        clock.forward(Duration.ofHours(-9));
        try (TestConnection browser = connect(token)) {
            assertEquals("alice@example.com", nameOn(browser));
        }
    }

    // ------------------------------------------------------------------ application state

    @Test
    @DisplayName("what resume() attaches is on the connection, and a service can add more")
    void attributes() {
        try (TestConnection browser = connect(aliceToken())) {
            assertEquals("resolved:alice@example.com", browser.call(Who.class, "attribute", "caller"));
            assertNull(browser.call(Who.class, "attribute", "absent"));

            browser.call(Who.class, "keep", "cart", "3 items");
            assertEquals("3 items", browser.call(Who.class, "attribute", "cart"));

            browser.call(Who.class, "keep", "cart", null);
            assertNull(browser.call(Who.class, "attribute", "cart"));
        }
    }

    @Test
    @DisplayName("attributes belong to one connection")
    void attributesDoNotCross() {
        String token = aliceToken();
        try (TestConnection one = connect(token); TestConnection two = connect(token);
             TestConnection anonymous = server.connect()) {
            one.call(Who.class, "keep", "cart", "mine");
            assertNull(two.call(Who.class, "attribute", "cart"));
            assertNull(anonymous.call(Who.class, "attribute", "cart"));
            assertEquals("nobody", anonymous.call(Who.class, "who"));
        }
    }
}
