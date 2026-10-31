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
package com.zeroz4j.server.jakarta;

import com.zeroz4j.server.AuthenticatedPrincipal;
import com.zeroz4j.server.ServerRuntime;
import com.zeroz4j.server.SignInAttempt;
import com.zeroz4j.server.SignInHandler;
import com.zeroz4j.server.SignInSession;
import com.zeroz4j.server.SignInSessions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jboss.weld.junit5.EnableWeld;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The WAR's entry to cookie sign-in. Every rule is {@code SignInSessionsTest}'s; what is proved here
 * is that this servlet reaches them, with the context path, with real CDI finding the handler. The
 * servlet request and response are faked the way {@link FileUploadServletTest} fakes them.
 */
@EnableWeld
class SignInServletTest {

    @WeldSetup
    public WeldInitiator weld = WeldInitiator.of(ServerRuntime.class, Accounts.class);

    /** Stands in for the application's own handler. */
    @ApplicationScoped
    public static class Accounts implements SignInHandler {
        @Override
        public AuthenticatedPrincipal signIn(SignInAttempt attempt) {
            return "right".equals(attempt.password())
                    ? new AuthenticatedPrincipal(attempt.username(), Set.of("user")) : null;
        }

        @Override
        public AuthenticatedPrincipal resume(SignInSession session) {
            return new AuthenticatedPrincipal(session.name(), Set.of("user"));
        }
    }

    private final SignInServlet servlet = new SignInServlet();

    @Test
    void itMapsItselfWhereTheFormPosts() {
        WebServlet mapping = SignInServlet.class.getAnnotation(WebServlet.class);
        assertEquals("/" + SignInSessions.PATH + "/*", mapping.urlPatterns()[0]);
    }

    @Test
    void aSignInRedirectsUnderTheContextPathAndSetsTheCookie() throws Exception {
        Recorded answer = post("/sign-in", "username=a&password=right&return=tasks%2F7",
                "http://localhost:8080");

        assertEquals(303, answer.status);
        assertEquals("/coachapp/tasks/7", answer.headers.get("Location"));
        assertEquals("no-store", answer.headers.get("Cache-Control"));
        assertEquals(1, answer.cookies.size());
        assertTrue(answer.cookies.get(0).startsWith("zeroz_session="), answer.cookies.toString());
        assertTrue(answer.cookies.get(0).contains("; Path=/coachapp;"), answer.cookies.toString());
    }

    @Test
    void aWrongPasswordComesBackToTheForm() throws Exception {
        Recorded answer = post("/sign-in", "username=b&password=wrong", "http://localhost:8080");

        assertEquals("/coachapp/?signin=failed", answer.headers.get("Location"));
        assertTrue(answer.cookies.isEmpty());
    }

    @Test
    void anotherSitesPageIsRefused() throws Exception {
        assertEquals(403, post("/sign-in", "username=a&password=right", "https://evil.example").status);
    }

    @Test
    void signOutSendsBothCookiesEachAsAHeaderOfItsOwn() throws Exception {
        assertEquals(2, post("/sign-out", "", "http://localhost:8080").cookies.size());
    }

    @Test
    void anUnknownActionIsNotFound() throws Exception {
        assertEquals(404, post("/nothing", "", "http://localhost:8080").status);
    }

    // ------------------------------------------------------- fake container

    /** What the servlet told the container. */
    private static final class Recorded {
        int status;
        final Map<String, String> headers = new HashMap<>();
        final List<String> cookies = new ArrayList<>();
    }

    private Recorded post(String pathInfo, String body, String origin) throws Exception {
        Map<String, String> headers = Map.of("Origin", origin, "Host", "localhost:8080",
                "Content-Type", "application/x-www-form-urlencoded");
        InvocationHandler onRequest = (proxy, method, args) -> switch (method.getName()) {
            case "getHeader" -> headers.get((String) args[0]);
            case "getPathInfo" -> pathInfo;
            case "getContextPath" -> "/coachapp";
            case "getScheme" -> "http";
            case "getInputStream" -> stream(body);
            default -> defaultFor(method.getReturnType());
        };
        Recorded recorded = new Recorded();
        InvocationHandler onResponse = (proxy, method, args) -> {
            switch (method.getName()) {
                case "setStatus" -> recorded.status = (Integer) args[0];
                case "setHeader" -> recorded.headers.put((String) args[0], (String) args[1]);
                case "addHeader" -> recorded.cookies.add((String) args[1]);
                default -> { }
            }
            return defaultFor(method.getReturnType());
        };
        servlet.doPost(
                (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                        new Class<?>[] {HttpServletRequest.class}, onRequest),
                (HttpServletResponse) Proxy.newProxyInstance(getClass().getClassLoader(),
                        new Class<?>[] {HttpServletResponse.class}, onResponse));
        return recorded;
    }

    private static Object defaultFor(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        return type == boolean.class ? (Object) false : (Object) 0;
    }

    private static ServletInputStream stream(String body) {
        ByteArrayInputStream bytes = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        return new ServletInputStream() {
            @Override public int read() throws IOException { return bytes.read(); }
            @Override public boolean isFinished() { return bytes.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(jakarta.servlet.ReadListener listener) { }
        };
    }
}
