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

import jakarta.websocket.CloseReason;
import jakarta.websocket.Session;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * The words the server logs about a connection opening, closing and failing.
 *
 * <p>Added in 0.9.1, after a phone kept a background tab reconnecting every 16 to 35 seconds for
 * hours and nothing on the server said why each connection had ended. The close code is the one
 * fact that tells "the browser closed it" (1001, 1006) from "a proxy closed it" (1006 after a
 * timeout) from "the server closed it" (1008, 1013), so it is now on one line per connection,
 * with how long the connection lasted.</p>
 *
 * <p>The client also reports how its <em>previous</em> connection ended, as query parameters on
 * the next handshake, because a connection the browser drops mid-handshake or behind a proxy may
 * never reach the server's close handler at all. The parameter names are shared with
 * {@code com.zeroz4j.client.ReconnectPolicy}; a server before 0.9.1 ignores them, and a client
 * before 0.9.1 does not send them.</p>
 *
 * <p>Framework-internal.</p>
 */
final class ConnectionDiagnostics {

    /** Session property holding {@link System#nanoTime()} at open. */
    static final String OPENED_AT_KEY = "zeroz.openedAtNanos";

    /** The previous connection's close code. */
    static final String PARAM_CLOSE_CODE = "zerozCloseCode";
    /** How long the previous connection had been open, in milliseconds; absent when it never opened. */
    static final String PARAM_CLOSE_AFTER_MS = "zerozCloseAfterMs";
    /** "1" when the page was hidden at the moment the previous connection closed. */
    static final String PARAM_CLOSE_HIDDEN = "zerozCloseHidden";
    /** The previous connection's close reason, when the browser reported one. */
    static final String PARAM_CLOSE_REASON = "zerozCloseReason";
    /** How many connections in a row have failed before this one, counting the previous one. */
    static final String PARAM_ATTEMPT = "zerozAttempt";

    /** The longest piece of client-supplied text written to the log. */
    private static final int MAX_REPORTED_TEXT = 120;

    private ConnectionDiagnostics() {
    }

    /** Records when a connection opened, so its close can say how long it lasted. */
    static void markOpened(Session session) {
        session.getUserProperties().put(OPENED_AT_KEY, System.nanoTime());
    }

    /**
     * @return milliseconds since {@link #markOpened}, or -1 when the connection was never marked
     */
    static long openMillis(Session session) {
        Object opened = session.getUserProperties().get(OPENED_AT_KEY);
        if (!(opened instanceof Long)) {
            return -1L;
        }
        return (System.nanoTime() - (Long) opened) / 1_000_000L;
    }

    /** @return the signed-in user's name, or "anonymous" */
    static String userOf(Session session) {
        Object principal = session.getUserProperties().get(RmiEndpointConfigurator.PRINCIPAL_KEY);
        if (principal instanceof Principal) {
            return ((Principal) principal).getName();
        }
        return WasmRmiServerEngine.ANONYMOUS_USER;
    }

    /**
     * The one line logged when a connection closes, for example
     * {@code Connection closed: session 7f3a, user alice, code 1006 CLOSED_ABNORMALLY, no reason given, open 830 ms}.
     *
     * @param session the connection
     * @param reason  the close reason the container reported, or null when it reported none
     * @return the line, without the "[zeroz4j] " prefix
     */
    static String closeLine(Session session, CloseReason reason) {
        StringBuilder line = new StringBuilder("Connection closed: session ")
                .append(session.getId())
                .append(", user ").append(userOf(session));
        if (reason == null || reason.getCloseCode() == null) {
            line.append(", no close code");
        } else {
            CloseReason.CloseCode code = reason.getCloseCode();
            line.append(", code ").append(code.getCode());
            CloseReason.CloseCode known = knownCode(code.getCode());
            if (known != null) {
                line.append(' ').append(known);
            }
            String phrase = reason.getReasonPhrase();
            if (phrase == null || phrase.isEmpty()) {
                line.append(", no reason given");
            } else {
                line.append(", reason \"").append(clean(phrase)).append('"');
            }
        }
        long open = openMillis(session);
        if (open >= 0) {
            line.append(", open ").append(open).append(" ms");
        }
        return line.toString();
    }

    private static CloseReason.CloseCode knownCode(int code) {
        try {
            return CloseReason.CloseCodes.getCloseCode(code);
        } catch (IllegalArgumentException outsideTheStandardRange) {
            return null;
        }
    }

    /**
     * What the client said about how its previous connection ended, as a phrase for the
     * "Client connected" line, for example
     * {@code previous connection closed with code 1006 after 830 ms, page hidden, reconnect attempt 3}.
     *
     * @param parameters the handshake's query parameters; may be null
     * @return the phrase, or null when the client reported nothing (a first connection, or a
     *         client before 0.9.1)
     */
    static String previousClose(Map<String, List<String>> parameters) {
        if (parameters == null) {
            return null;
        }
        Integer code = number(first(parameters, PARAM_CLOSE_CODE));
        if (code == null) {
            return null;
        }
        Integer afterMillis = number(first(parameters, PARAM_CLOSE_AFTER_MS));
        String reason = first(parameters, PARAM_CLOSE_REASON);
        boolean hidden = "1".equals(first(parameters, PARAM_CLOSE_HIDDEN));
        Integer attempt = number(first(parameters, PARAM_ATTEMPT));

        StringBuilder phrase = new StringBuilder();
        if (afterMillis == null || afterMillis < 0) {
            phrase.append("previous attempt failed before it opened, code ").append(code);
        } else {
            phrase.append("previous connection closed with code ").append(code)
                    .append(" after ").append(afterMillis).append(" ms");
        }
        if (reason != null && !reason.isEmpty()) {
            phrase.append(", reason \"").append(clean(reason)).append('"');
        }
        phrase.append(hidden ? ", page hidden" : ", page visible");
        if (attempt != null && attempt > 0) {
            phrase.append(", reconnect attempt ").append(attempt);
        }
        return phrase.toString();
    }

    /**
     * Whether a failure is the ordinary sound of a peer that has gone away - a broken pipe, a reset,
     * a closed channel. Those are logged as one line; anything else keeps its stack trace.
     */
    static boolean isPeerGone(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.io.IOException) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    /** Whether an interrupt is anywhere in a failure's cause chain. */
    static boolean wasInterrupted(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof InterruptedException
                    || cause instanceof java.io.InterruptedIOException
                    || cause instanceof java.nio.channels.ClosedByInterruptException) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    private static String first(Map<String, List<String>> parameters, String name) {
        List<String> values = parameters.get(name);
        return values != null && !values.isEmpty() ? values.get(0) : null;
    }

    private static Integer number(String text) {
        if (text == null || text.isEmpty() || text.length() > 10) {
            return null;
        }
        try {
            return Integer.valueOf(text.trim());
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    /** Text from the network, made safe for one log line: no control characters, bounded length. */
    static String clean(String text) {
        StringBuilder out = new StringBuilder(Math.min(text.length(), MAX_REPORTED_TEXT));
        for (int i = 0; i < text.length() && out.length() < MAX_REPORTED_TEXT; i++) {
            char ch = text.charAt(i);
            out.append(Character.isISOControl(ch) || ch == '"' ? ' ' : ch);
        }
        return out.toString();
    }
}
