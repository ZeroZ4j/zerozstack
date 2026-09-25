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
 * Decides when {@link WasmRmiClientChannel} tries to connect again after a connection closes
 * (0.9.1+).
 *
 * <h2>Why this exists</h2>
 * A phone kept a tab open in the background. The browser closed the connection 0.1 to 2.3 seconds
 * after every open, and the client opened a new one every 16 to 35 seconds for hours - about 6,100
 * connections in 27 hours - each one re-sending its sign-in and re-loading the page's data. Three
 * things made that possible, and each is changed here:
 * <ul>
 *   <li><b>A connection that opened reset the backoff.</b> An open-then-drop loop therefore never
 *       backed off. Now a connection only counts as good once it has stayed open for
 *       {@link #STABLE_AFTER_MS}; one that drops sooner is a failure like any other.</li>
 *   <li><b>A hidden page reconnected like a visible one.</b> Nobody is looking at a hidden page,
 *       and the browser throttles and closes its connections. Now nothing is attempted while the
 *       page is hidden, and the attempt is made at once when it is shown again, or when the browser
 *       reports the network is back.</li>
 *   <li><b>Retries never ended.</b> Now after {@link #MAX_CONSECUTIVE_FAILURES} failures in a row
 *       the client stops and the connection bar asks the person to reload the page.</li>
 * </ul>
 *
 * <h2>Plain Java on purpose</h2>
 * Everything the browser supplies - the clock, whether the page is hidden, a timer, opening a
 * socket - comes through {@link Host}, so the whole policy runs on the JVM in tests.
 *
 * <p>Framework-internal.</p>
 */
final class ReconnectPolicy {

    /** First delay before a reconnect attempt, in milliseconds. Doubles up to {@link #MAX_BACKOFF_MS}. */
    static final int BASE_BACKOFF_MS = 500;

    /** Ceiling for the backoff, in milliseconds. */
    static final int MAX_BACKOFF_MS = 15_000;

    /**
     * How long a connection has to stay open before it counts as good and the backoff starts again
     * from the beginning.
     */
    static final long STABLE_AFTER_MS = 30_000L;

    /**
     * How many failed connections in a row end automatic retrying. Ten, rather than a smaller
     * number, so that a server restart of about a minute is waited out: the delays before attempts
     * two to ten add up to 75.5 seconds.
     */
    static final int MAX_CONSECUTIVE_FAILURES = 10;

    /**
     * Query parameter names carrying the previous close to the server. The server reads the same
     * names in {@code com.zeroz4j.server.ConnectionDiagnostics}; a server before 0.9.1 ignores them.
     */
    static final String PARAM_CLOSE_CODE = "zerozCloseCode";
    static final String PARAM_CLOSE_AFTER_MS = "zerozCloseAfterMs";
    static final String PARAM_CLOSE_HIDDEN = "zerozCloseHidden";
    static final String PARAM_CLOSE_REASON = "zerozCloseReason";
    static final String PARAM_ATTEMPT = "zerozAttempt";

    /** The longest close reason sent back to the server. */
    private static final int MAX_REASON_CHARS = 60;

    /** What the policy needs from the browser. */
    interface Host {
        /** @return the time now, in milliseconds */
        long now();

        /** @return true while {@code document.visibilityState} is {@code 'hidden'} */
        boolean pageHidden();

        /** Runs a task once, after a delay. */
        void schedule(int delayMillis, Runnable task);

        /** Opens a new socket. */
        void openSocket();
    }

    /** What happens after a connection closed. */
    enum Outcome {
        /** An attempt is scheduled after a delay. */
        RETRY_SCHEDULED,
        /** The page is hidden; the attempt waits until it is shown again. */
        WAITING_FOR_VISIBLE,
        /** Too many failures in a row; nothing more is attempted by itself. */
        GAVE_UP,
        /** The channel was closed by the application; nothing is attempted. */
        STOPPED
    }

    private final Host host;

    /** Failed connections in a row. A connection that stayed open for the stable period resets it. */
    private int failures;

    /** When the current socket opened; -1 while it has not. */
    private long openedAt = -1L;

    private boolean waitingForVisible;
    private boolean timerPending;
    /** A scheduled attempt runs only while its token is still this one. */
    private int timerToken;
    private boolean gaveUp;
    private boolean stopped;
    private int lastDelay;

    // How the previous connection ended, sent to the server on the next handshake.
    private boolean haveLastClose;
    private int lastCode;
    private String lastReason;
    private long lastOpenMillis;
    private boolean lastHidden;

    ReconnectPolicy(Host host) {
        this.host = host;
    }

    /** The socket has opened. The backoff is not reset here; see {@link #STABLE_AFTER_MS}. */
    void opened() {
        openedAt = host.now();
        waitingForVisible = false;
    }

    /**
     * The socket has closed, and the channel was not closed by the application.
     *
     * @param code   the close code the browser reported
     * @param reason the close reason the browser reported
     * @return what happens next
     */
    Outcome closed(int code, String reason) {
        long now = host.now();
        boolean hidden = host.pageHidden();
        long openFor = openedAt >= 0 ? now - openedAt : -1L;
        openedAt = -1L;

        haveLastClose = true;
        lastCode = code;
        lastReason = reason;
        lastOpenMillis = openFor;
        lastHidden = hidden;

        if (stopped) {
            return Outcome.STOPPED;
        }
        if (gaveUp) {
            return Outcome.GAVE_UP;
        }
        if (openFor >= STABLE_AFTER_MS) {
            failures = 0;
        }
        if (hidden) {
            // Not counted as a failure: a hidden page's connection is usually closed by the
            // browser, not by the network or the server, and the next attempt waits for the
            // person to come back anyway.
            waitingForVisible = true;
            return Outcome.WAITING_FOR_VISIBLE;
        }
        failures++;
        if (failures >= MAX_CONSECUTIVE_FAILURES) {
            gaveUp = true;
            cancelTimer();
            return Outcome.GAVE_UP;
        }
        schedule(delayFor(failures));
        return Outcome.RETRY_SCHEDULED;
    }

    /** The page has become visible. Connects at once if an attempt is waiting. */
    void pageShown() {
        connectNowIfWaiting();
    }

    /** The browser reports the network is back. Connects at once if an attempt is waiting. */
    void networkBack() {
        connectNowIfWaiting();
    }

    private void connectNowIfWaiting() {
        if (stopped || gaveUp || host.pageHidden()) {
            return;
        }
        if (!waitingForVisible && !timerPending) {
            return;     // connected, or an attempt is already in flight
        }
        waitingForVisible = false;
        cancelTimer();
        host.openSocket();
    }

    /**
     * An explicit reconnect by the application or the person: forgets the failures, leaves the
     * given-up and stopped states, and cancels a scheduled attempt. The caller opens the socket.
     */
    void reset() {
        failures = 0;
        gaveUp = false;
        stopped = false;
        waitingForVisible = false;
        cancelTimer();
    }

    /** The application closed the channel. Nothing more is attempted until {@link #reset()}. */
    void stop() {
        stopped = true;
        waitingForVisible = false;
        cancelTimer();
    }

    private void schedule(int delay) {
        lastDelay = delay;
        timerPending = true;
        final int token = ++timerToken;
        host.schedule(delay, () -> {
            if (token != timerToken || stopped || gaveUp) {
                return;
            }
            timerPending = false;
            if (host.pageHidden()) {
                // Hidden while the timer ran: wait for the page to be shown rather than opening
                // a connection nobody is looking at.
                waitingForVisible = true;
                return;
            }
            host.openSocket();
        });
    }

    private void cancelTimer() {
        timerPending = false;
        timerToken++;
    }

    /**
     * The delay before the attempt that follows a given number of failures in a row: 500 ms after
     * the first, doubling, never more than {@link #MAX_BACKOFF_MS}.
     */
    static int delayFor(int failuresInARow) {
        int delay = BASE_BACKOFF_MS;
        for (int i = 1; i < failuresInARow && delay < MAX_BACKOFF_MS; i++) {
            delay *= 2;
        }
        return Math.min(delay, MAX_BACKOFF_MS);
    }

    /** @return failed connections in a row */
    int failures() {
        return failures;
    }

    /** @return the delay of the last scheduled attempt, in milliseconds */
    int lastDelay() {
        return lastDelay;
    }

    /** @return true once automatic retrying has stopped after too many failures */
    boolean hasGivenUp() {
        return gaveUp;
    }

    /** @return true while an attempt waits for the page to be shown */
    boolean isWaitingForVisible() {
        return waitingForVisible;
    }

    /**
     * Adds how the previous connection ended to a WebSocket URL, so the server can log it.
     *
     * @param url the URL to connect to
     * @return the URL with the previous close appended, or unchanged when there was none
     */
    String withPreviousClose(String url) {
        if (!haveLastClose || url == null) {
            return url;
        }
        StringBuilder out = new StringBuilder(url);
        out.append(url.indexOf('?') >= 0 ? '&' : '?');
        out.append(PARAM_CLOSE_CODE).append('=').append(lastCode);
        if (lastOpenMillis >= 0) {
            out.append('&').append(PARAM_CLOSE_AFTER_MS).append('=').append(lastOpenMillis);
        }
        out.append('&').append(PARAM_CLOSE_HIDDEN).append('=').append(lastHidden ? '1' : '0');
        if (failures > 0) {
            out.append('&').append(PARAM_ATTEMPT).append('=').append(failures);
        }
        if (lastReason != null && !lastReason.isEmpty()) {
            String reason = lastReason.length() > MAX_REASON_CHARS
                    ? lastReason.substring(0, MAX_REASON_CHARS) : lastReason;
            out.append('&').append(PARAM_CLOSE_REASON).append('=').append(encode(reason));
        }
        return out.toString();
    }

    /** Percent-encodes everything but letters, digits and {@code -._~}, as UTF-8. */
    static String encode(String text) {
        StringBuilder out = new StringBuilder();
        byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for (byte raw : bytes) {
            int value = raw & 0xFF;
            if ((value >= 'a' && value <= 'z') || (value >= 'A' && value <= 'Z')
                    || (value >= '0' && value <= '9')
                    || value == '-' || value == '.' || value == '_' || value == '~') {
                out.append((char) value);
            } else {
                out.append('%');
                out.append(Character.toUpperCase(Character.forDigit(value >> 4, 16)));
                out.append(Character.toUpperCase(Character.forDigit(value & 0xF, 16)));
            }
        }
        return out.toString();
    }
}
