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

/**
 * Where an application remembers a person's language, so it follows them to another computer.
 *
 * <h2>What the framework does on its own</h2>
 *
 * <p>It remembers the choice in an ordinary cookie the browser writes, which survives reloads,
 * restarts and reconnects. That is right for nearly everybody and needs nothing implemented.</p>
 *
 * <p>What it cannot do is follow a person to a second computer, or into a private window, because
 * the framework has no user store of its own and acquiring one would mean deciding that the
 * framework writes to the application's database - a question deliberately left open.</p>
 *
 * <h2>Implementing it</h2>
 *
 * <p>Write one class, save the language wherever the application already keeps that person's
 * settings, and name it in
 * {@code META-INF/services/com.zeroz4j.server.LocalePreferenceStore}:</p>
 *
 * <pre>{@code
 * public final class UserLanguages implements LocalePreferenceStore {
 *     public Locale forUser(String userName) {
 *         Account account = accounts.byName(userName);
 *         return account == null ? null : account.language();
 *     }
 *     public void remember(String userName, Locale locale) {
 *         accounts.byName(userName).setLanguage(locale);
 *     }
 * }
 * }</pre>
 *
 * <p>Found by {@code ServiceLoader}, not by CDI, for the same reason
 * {@link AuthenticationProvider} is: a handshake runs before any bean exists.</p>
 *
 * <h2>When each method is called</h2>
 *
 * <p>{@link #forUser} is asked once, at the handshake, and only for a connection that signed in. It
 * comes after the language the client asked for outright and before the cookie, so somebody who has
 * just picked a language still gets what they picked.</p>
 *
 * <p>{@link #remember} is called whenever a signed-in person switches language.</p>
 *
 * <p>Both run on the connection's own thread, so both should be quick, and neither may throw
 * anything the caller has to handle: an exception is logged and the language falls through to the
 * next answer.</p>
 *
 * @since 0.9.0
 */
public interface LocalePreferenceStore {

    /**
     * The language this person last chose.
     *
     * @param userName the authenticated user name
     * @return their language, or null when nothing is stored for them
     */
    Locale forUser(String userName);

    /**
     * Records the language this person has just chosen.
     *
     * @param userName the authenticated user name
     * @param locale   the language they chose
     */
    void remember(String userName, Locale locale);
}
