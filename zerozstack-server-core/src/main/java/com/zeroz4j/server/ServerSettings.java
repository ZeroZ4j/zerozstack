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

/**
 * The names of every setting a ZeroZ Stack server reads.
 *
 * <p>Each name is also a system property. Setting it on the command line with {@code -D} is still
 * the normal way to configure a deployment, and nothing here changes that. The names are collected
 * in one place so that a <b>test</b> can set them on one server without touching the whole JVM:
 * hand them to {@link ServerConfig.Builder#set(String, String)} and only that server sees them.</p>
 *
 * <pre>{@code
 * ServerConfig small = ServerConfig.builder()
 *         .set(ServerSettings.MAX_BINARY_MESSAGE_BYTES, 1024)
 *         .build();
 * }</pre>
 *
 * <p>A value not given to a server is looked up as a system property, so a server built with no
 * settings at all behaves exactly as it always has.</p>
 *
 * @since 0.8.0
 */
public final class ServerSettings {

    private ServerSettings() {
    }

    // ------------------------------------------------------------------ the live connection

    /** Largest binary message accepted on one connection, in bytes. Default 4 MB. */
    public static final String MAX_BINARY_MESSAGE_BYTES = "zeroz.ws.maxBinaryMessageBytes";

    /** How long a silent connection is held, in minutes. Unset leaves the container's own value. */
    public static final String IDLE_TIMEOUT_MINUTES = "zeroz.ws.idleTimeoutMinutes";

    /**
     * How many messages from one connection may be waiting to be handled. Default 32.
     *
     * <p>One connection's messages are handled one at a time in the order they arrived, so this is
     * a backlog limit, not a concurrency limit. A connection that fills it is slowed down; nothing
     * is dropped.</p>
     */
    public static final String MAX_QUEUED_FRAMES_PER_SESSION = "zeroz.ws.maxQueuedFramesPerSession";

    /**
     * The name {@link #MAX_QUEUED_FRAMES_PER_SESSION} had before 0.8.0.
     *
     * <p>Still read when the current name is not set, so a deployment that configured it keeps its
     * value. The old name described a concurrency that no longer exists: from 0.8.0 exactly one
     * message per connection is handled at a time.</p>
     *
     * @deprecated use {@link #MAX_QUEUED_FRAMES_PER_SESSION}
     */
    @Deprecated
    public static final String MAX_CONCURRENT_FRAMES_PER_SESSION =
            "zeroz.ws.maxConcurrentFramesPerSession";

    /** Shortest gap between two answered keepalive pings on one connection, in milliseconds. */
    public static final String KEEPALIVE_MIN_INTERVAL_MILLIS = "zeroz.ws.keepaliveMinIntervalMillis";

    /** Most frames that may be waiting to go out on one connection. Default 256. */
    public static final String MAX_PENDING_FRAMES_PER_SESSION = "zeroz.ws.maxPendingFramesPerSession";

    /** Most bytes that may be waiting to go out on one connection. Default 8 MB. */
    public static final String MAX_PENDING_BYTES_PER_SESSION = "zeroz.ws.maxPendingBytesPerSession";

    // ------------------------------------------------------------------ who may connect

    /** Comma-separated page origins allowed to open a connection, or {@code *}. */
    public static final String ORIGINS = "zeroz.origins";

    /** Comma-separated host names this deployment answers for, or {@code *}. */
    public static final String HOSTS = "zeroz.hosts";

    /** Set to {@code dev} to turn on the built-in development logins. */
    public static final String SECURITY_MODE = "zeroz.security.mode";

    // ------------------------------------------------------------------ browser identity

    /** The key the browser-id cookie is signed with. Generated at startup when unset. */
    public static final String CLIENT_ID_SECRET = "zeroz.clientId.secret";

    /** How long a browser id stays valid, in days. */
    public static final String CLIENT_ID_TTL_DAYS = "zeroz.clientId.ttlDays";

    /** Forces the browser-id cookie's {@code Secure} flag on or off. */
    public static final String CLIENT_ID_SECURE_COOKIE = "zeroz.clientId.secureCookie";

    // ------------------------------------------------------------------ cookie sign-in

    /**
     * The key session cookies are signed with, at least 32 characters. Generated at startup when
     * unset, which signs everybody out at every restart. Keep it in the application's property file,
     * and never reuse {@link #CLIENT_ID_SECRET}.
     *
     * @since 0.11.0
     */
    public static final String SIGN_IN_SECRET = "zeroz.signin.secret";

    /**
     * The session cookie's name. Default {@code zeroz_session}. An application at the site root on
     * HTTPS can set {@code __Host-zeroz_session}, which stops a sibling subdomain planting one.
     *
     * @since 0.11.0
     */
    public static final String SIGN_IN_COOKIE_NAME = "zeroz.signin.cookieName";

    /**
     * {@code Strict}, the default, or {@code Lax}, which an application needs only when something
     * other than the page itself must see the cookie after a cross-site link.
     *
     * @since 0.11.0
     */
    public static final String SIGN_IN_SAME_SITE = "zeroz.signin.sameSite";

    /**
     * Forces the session cookie's {@code Secure} flag on or off. Unset, it is on everywhere except
     * a page on this machine.
     *
     * @since 0.11.0
     */
    public static final String SIGN_IN_SECURE_COOKIE = "zeroz.signin.secureCookie";

    /**
     * How long a cookie stays good with no renewal, in days. Default 30. Sliding: each renewal
     * starts this again.
     *
     * @since 0.11.0
     */
    public static final String SIGN_IN_DAYS = "zeroz.signin.days";

    /**
     * How long a session can be renewed for, in days from the password. Default 365.
     *
     * @since 0.11.0
     */
    public static final String SIGN_IN_MAX_DAYS = "zeroz.signin.maxDays";

    /**
     * How old a cookie must be before it is renewed, in minutes. Default 60.
     *
     * @since 0.11.0
     */
    public static final String SIGN_IN_RENEW_MINUTES = "zeroz.signin.renewMinutes";

    /**
     * How long a signed-in connection may stay open, in minutes, before the server closes it and
     * the client reconnects, which checks the cookie and the roles again. Default 480.
     *
     * @since 0.11.0
     */
    public static final String SIGN_IN_CONNECTION_MINUTES = "zeroz.signin.connectionMinutes";

    /**
     * Sign-in attempts allowed in a row for one name. Default 10.
     *
     * @since 0.11.0
     */
    public static final String SIGN_IN_MAX_ATTEMPTS = "zeroz.signin.maxAttempts";

    /**
     * How long after the last counted attempt the count starts again, in minutes. Default 15.
     *
     * @since 0.11.0
     */
    public static final String SIGN_IN_LOCK_MINUTES = "zeroz.signin.lockMinutes";

    // ------------------------------------------------------------------ what was sent to whom

    /** Most object handles remembered per browser. Default 10,000. */
    public static final String DISCLOSURE_MAX_HANDLES_PER_CLIENT =
            "zeroz.disclosure.maxHandlesPerClient";

    /** How long a browser's record of what it was sent survives with no activity, in hours. */
    public static final String DISCLOSURE_IDLE_HOURS = "zeroz.disclosure.idleHours";

    // ------------------------------------------------------------------ object locking

    /** How long a caller waits for a lock somebody else holds, in seconds. Default 30. */
    public static final String LIVE_MUTEX_WAIT_SECONDS = "zeroz.livemutex.waitSeconds";

    /** Whether taking a lock requires an authenticated connection. */
    public static final String LIVE_MUTEX_REQUIRE_AUTHENTICATION =
            "zeroz.livemutex.requireAuthentication";

    // ------------------------------------------------------------------ file upload

    /** The largest file the upload address accepts, in bytes. Default 25 MB. */
    public static final String UPLOAD_MAX_BYTES = "zeroz.upload.maxBytes";

    /** How long an issued upload pass stays usable, in seconds. Default 60. */
    public static final String UPLOAD_PASS_SECONDS = "zeroz.upload.passSeconds";

    /** Where a part-received upload is written. Defaults to the JVM temp directory. */
    public static final String UPLOAD_TEMP_DIR = "zeroz.upload.tempDir";

    // ------------------------------------------------------------------ slow calls

    /**
     * How long a slow call's result is kept after the work ends, in minutes. Default 60.
     *
     * @since 0.11.0
     */
    public static final String SLOW_CALL_KEEP_MINUTES = "zeroz.slowcall.keepMinutes";

    /**
     * Most ended slow calls kept for one browser or user, oldest dropped first. Default 16.
     *
     * @since 0.11.0
     */
    public static final String SLOW_CALL_MAX_KEPT_PER_CLIENT = "zeroz.slowcall.maxKeptPerClient";

    /**
     * Most slow calls one browser or user may run at once. Default 8. One more is refused with a
     * sentence.
     *
     * @since 0.11.0
     */
    public static final String SLOW_CALL_MAX_RUNNING_PER_CLIENT =
            "zeroz.slowcall.maxRunningPerClient";

    /**
     * Most slow calls this server runs at once, across everybody. Default 1,000. One more is
     * refused with a sentence.
     *
     * @since 0.11.0
     */
    public static final String SLOW_CALL_MAX_RUNNING = "zeroz.slowcall.maxRunning";

    /**
     * How long one slow call may run, in minutes. Default 0, which means no limit.
     *
     * <p>Work past the limit is stopped the way a cancel stops it, its running slot is freed even if
     * it ignores the interrupt, and the page shows it failed. Checked every few seconds.</p>
     *
     * @since 0.11.0
     */
    public static final String SLOW_CALL_MAX_MINUTES = "zeroz.slowcall.maxMinutes";

    // ------------------------------------------------------------------ language

    /**
     * What language this deployment answers in when the browser has not said what it reads.
     *
     * <p>A language tag such as {@code de} or {@code pt-BR}. Default {@code en}. Deliberately not
     * the machine's own locale: a server in Frankfurt has a German JVM locale that has nothing to
     * do with whoever is calling it.</p>
     *
     * @since 0.9.0
     */
    public static final String I18N_DEFAULT_LOCALE = "zeroz.i18n.defaultLocale";
}
