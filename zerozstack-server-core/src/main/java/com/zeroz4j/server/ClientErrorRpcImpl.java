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

import com.zeroz4j.api.ClientErrorRpc;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Writes browser errors to the server log.
 *
 * <p>Everything the browser sends is untrusted text. It is cut to a fixed length and every control
 * character, line breaks included, becomes a space, so a report is always exactly one log line and
 * cannot forge another. One connection may send 20 reports unless
 * {@code zeroz.clienterrors.maxPerConnection} says otherwise, and the server as a whole 120 a
 * minute. {@code zeroz.clienterrors.enabled=false} turns it off.</p>
 *
 * @since 0.11.0
 */
@ApplicationScoped
public class ClientErrorRpcImpl implements ClientErrorRpc {

    static final int DEFAULT_MAX_PER_CONNECTION = 20;
    static final int MAX_PER_MINUTE = 120;
    static final int MAX_MESSAGE = 1000;
    static final int MAX_STACK = 3000;
    static final int MAX_PAGE = 200;
    private static final int MAX_TRACKED_CONNECTIONS = 10_000;
    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final Logger LOG = Logger.getLogger("com.zeroz4j.client");

    /** This server, which owns the settings. */
    @Inject
    ServerRuntime runtime;

    private final ConcurrentHashMap<String, AtomicInteger> perConnection = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private long minuteStart;
    private int inMinute;

    /**
     * Writes one browser error to the log.
     *
     * @param kind {@code error}, {@code rejection} or {@code start}; anything else is logged as
     *             {@code error}
     * @param message what the browser says went wrong
     * @param stack the browser stack trace, or empty
     * @param page the path of the page
     * @return the reference, or null when the report was not taken
     *
     * <p><b>Under the hood:</b> arrives on {@code 0x01 RPC_CALL}; the reference goes back on
     * {@code 0x01 RPC_RESPONSE}.</p>
     */
    @Override
    public String report(String kind, String message, String stack, String page) {
        if (runtime != null && !enabled()) {
            return null;
        }
        String session = String.valueOf(RmiRequestContext.getSessionId());
        if (perConnection.size() >= MAX_TRACKED_CONNECTIONS) {
            perConnection.clear();
        }
        AtomicInteger count = perConnection.computeIfAbsent(session, key -> new AtomicInteger());
        if (count.incrementAndGet() > maxPerConnection() || !serverWideRoom()) {
            return null;
        }
        String reference = newReference();
        String who = RmiRequestContext.getPrincipal() == null ? "anonymous"
                : clean(RmiRequestContext.getPrincipal().getName(), 80);
        LOG.log(Level.WARNING, "Browser {0} [{1}] user={2} page={3}: {4}{5}", new Object[] {
            cleanKind(kind), reference, who, clean(page, MAX_PAGE), clean(message, MAX_MESSAGE),
            stack == null || stack.isEmpty() ? "" : " | " + clean(stack, MAX_STACK)});
        return reference;
    }

    /**
     * Forgets a closed connection's count.
     *
     * @param event the connection that closed
     */
    void onSessionClosed(@Observes SessionClosedEvent event) {
        perConnection.remove(String.valueOf(event.sessionId()));
    }

    private boolean enabled() {
        String value = runtime.config().get(ServerSettings.CLIENT_ERRORS_ENABLED);
        return value == null || !"false".equalsIgnoreCase(value.trim());
    }

    private int maxPerConnection() {
        return runtime == null ? DEFAULT_MAX_PER_CONNECTION
                : runtime.config().positiveInt(ServerSettings.CLIENT_ERRORS_MAX_PER_CONNECTION,
                        DEFAULT_MAX_PER_CONNECTION);
    }

    private synchronized boolean serverWideRoom() {
        long now = System.currentTimeMillis();
        if (now - minuteStart >= 60_000) {
            minuteStart = now;
            inMinute = 0;
        }
        return ++inMinute <= MAX_PER_MINUTE;
    }

    private String newReference() {
        StringBuilder out = new StringBuilder(6);
        for (int idx = 0; idx < 6; idx++) {
            out.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return out.toString();
    }

    private static String cleanKind(String kind) {
        return "rejection".equals(kind) || "start".equals(kind) ? kind : "error";
    }

    /** Cuts to length and turns every control character, line breaks included, into a space. */
    static String clean(String text, int max) {
        if (text == null) {
            return "";
        }
        int length = Math.min(text.length(), max);
        StringBuilder out = new StringBuilder(length + 3);
        for (int idx = 0; idx < length; idx++) {
            char ch = text.charAt(idx);
            out.append(Character.isISOControl(ch) || ch == ' ' || ch == ' ' ? ' ' : ch);
        }
        if (text.length() > max) {
            out.append("...");
        }
        return out.toString();
    }
}
