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

import com.zeroz4j.signals.Effect;
import com.zeroz4j.signals.Zeroz4jSignals;

import org.teavm.jso.JSBody;

/**
 * Remembers, in this browser, which language somebody chose.
 *
 * <h2>Why the browser writes this cookie and the server does not</h2>
 *
 * <p>The browser-id cookie is written by the server, is {@code HttpOnly}, and the code that writes
 * it carries a caution: some containers hand out a response header map that cannot be changed, and
 * when that happens the id works for one connection and no longer.</p>
 *
 * <p>A language is not a secret. Nothing is protected by it and nothing is decided by it beyond
 * which words appear, so it needs none of that. Page script writes an ordinary cookie -
 * {@code SameSite=Lax}, a year, readable - the moment the language changes, and the server only
 * ever reads it on the next handshake. That works in every container, and if the browser refuses
 * the write anyway the language still works - only the remembering is lost.</p>
 *
 * <h2>What happens when the connection is down</h2>
 *
 * <p>The choice is written to the cookie either way, and the write to the server is queued like any
 * other. What does <em>not</em> happen is the words changing, because the words come from the
 * server and the server is not there. The screen stays in the language it is in until the
 * connection returns, and then changes. Reloading while still offline shows the compiled-in
 * fallback language, whatever the cookie says.</p>
 *
 * @since 0.9.0
 */
final class ClientLocale {

    /** The cookie the server reads at the next handshake. Its name is fixed on both tiers. */
    private static final String COOKIE = "zeroz-lang";

    /** A year, in seconds. Long enough that nobody has to choose twice. */
    private static final int LIFETIME_SECONDS = 31_536_000;

    private static boolean installed;

    /**
     * Whether this browser can be written to at all.
     *
     * <p>Checked once, by trying. A cookie is a convenience - it is what makes the choice survive a
     * reload - and it runs on the same frame that tells the application it may build its first
     * screen. A failure here must not take that frame down with it, so this fails quietly and the
     * connection carries on: the language still works, it just starts fresh next time.</p>
     */
    private static boolean writable = true;

    private ClientLocale() {
    }

    /**
     * Starts keeping the cookie in step with the language signal.
     *
     * <p>Called once, when the first answer from the server arrives - not at start-up, because
     * subscribing to the signal is what asks the server for its value, and there is no server to
     * ask before then.</p>
     */
    static void install() {
        if (installed) {
            return;
        }
        installed = true;
        // An Effect rather than a call in the selector: the cookie then follows every write to the
        // signal, wherever it came from, including a value the server corrected back.
        Effect.create(() -> writeCookie(Zeroz4jSignals.LOCALE.mine().get()));
    }

    /**
     * @param tag the language tag to remember, or null to leave the cookie alone
     */
    static void writeCookie(String tag) {
        if (tag == null || tag.isEmpty()) {
            return;
        }
        for (int at = 0; at < tag.length(); at++) {
            char here = tag.charAt(at);
            boolean allowed = (here >= 'a' && here <= 'z') || (here >= 'A' && here <= 'Z')
                    || (here >= '0' && here <= '9') || here == '-';
            if (!allowed) {
                // Never put an unchecked value into a Set-Cookie line. A tag is letters, digits and
                // hyphens; anything else is somebody trying to write a second cookie.
                return;
            }
        }
        if (!writable) {
            return;
        }
        try {
            setCookie(COOKIE + "=" + tag + "; Path=/; Max-Age=" + LIFETIME_SECONDS
                    + "; SameSite=Lax");
        } catch (RuntimeException | LinkageError cannotWrite) {
            writable = false;
            System.err.println("[zeroz4j] Could not remember the language in a cookie: "
                    + cannotWrite + ". The language still works; it will start fresh next time.");
        }
    }

    @JSBody(params = {"value"}, script = "document.cookie = value;")
    private static native void setCookie(String value);
}
