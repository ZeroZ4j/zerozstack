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

import com.zeroz4j.api.i18n.Messages;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Decides, once per connection, what language the person on the other end reads.
 *
 * <h2>The order</h2>
 *
 * <ol>
 *   <li>The {@code lang} handshake parameter, when the browser sends one. It sends it when it
 *       already knows the answer from a choice made earlier.</li>
 *   <li>What a registered {@link LocalePreferenceStore} has stored for this person, when they
 *       signed in. Not registered - the usual case - and this step does not exist.</li>
 *   <li>The {@code zeroz-lang} cookie. This is what makes a choice survive a restart and a new
 *       connection.</li>
 *   <li>The {@code Accept-Language} header — the person's own browser setting, which is what an
 *       anonymous first visit should get with nothing stored anywhere.</li>
 *   <li>{@code zeroz.i18n.defaultLocale}, the deployment's own setting.</li>
 *   <li>English.</li>
 * </ol>
 *
 * <h2>Narrowing</h2>
 *
 * <p>Whatever is asked for is then narrowed to what this deployment actually has a catalog for.
 * {@code de-AT} becomes {@code de} when only {@code de} exists, and falls to the deployment's own
 * language when neither does. A browser asking for a language nobody translated must never produce
 * a half-translated screen.</p>
 *
 * <p>The deployment's own setting is never narrowed away: it is what a deployment said it speaks,
 * and its words are the file with no language suffix.</p>
 */
final class LocaleResolution {

    /** The setting that says what language this deployment answers in when nothing else is known. */
    static final String DEFAULT_LOCALE_PROPERTY = ServerSettings.I18N_DEFAULT_LOCALE;

    /** The handshake parameter a client sends when it already knows its own answer. */
    static final String LANGUAGE_PARAMETER = "lang";

    /** The cookie the client writes when somebody picks a language. The server only reads it. */
    static final String LANGUAGE_COOKIE = "zeroz-lang";

    private static final Logger LOG = Logger.getLogger(LocaleResolution.class.getName());

    /** The application's own memory of who reads what, or null when it registered none. */
    private static volatile LocalePreferenceStore preferences;
    private static volatile boolean preferencesResolved;

    private LocaleResolution() {
    }

    /**
     * The application's per-person language memory, looked up once.
     *
     * <p>{@code ServiceLoader} rather than CDI, because a handshake runs before beans exist. More
     * than one implementation is refused rather than picked between: which one wins would decide
     * what language somebody reads, and picking at random is not an answer.</p>
     *
     * @return the store, or null when the application registered none
     */
    static LocalePreferenceStore preferenceStore() {
        if (preferencesResolved) {
            return preferences;
        }
        synchronized (LocaleResolution.class) {
            if (preferencesResolved) {
                return preferences;
            }
            LocalePreferenceStore found = null;
            try {
                List<LocalePreferenceStore> all = new ArrayList<>();
                for (LocalePreferenceStore candidate
                        : ServiceLoader.load(LocalePreferenceStore.class)) {
                    all.add(candidate);
                }
                if (all.size() > 1) {
                    LOG.warning("[zeroz4j] More than one LocalePreferenceStore is registered ("
                            + all + "). None is used: which one wins would decide what language"
                            + " somebody reads. Register exactly one in META-INF/services.");
                } else if (all.size() == 1) {
                    found = all.get(0);
                    LOG.info("[zeroz4j] Language is remembered per person by "
                            + found.getClass().getName());
                }
            } catch (RuntimeException | ServiceConfigurationError broken) {
                LOG.log(Level.WARNING, "[zeroz4j] Could not load a LocalePreferenceStore;"
                        + " the language is remembered per browser only.", broken);
            }
            preferences = found;
            preferencesResolved = true;
            return found;
        }
    }

    /** Test support: forces the store to be looked up again. */
    static void resetPreferenceStoreForTesting() {
        synchronized (LocaleResolution.class) {
            preferences = null;
            preferencesResolved = false;
        }
    }

    /**
     * Tells the application that this person has chosen a language, when it asked to be told.
     *
     * @param userName the authenticated user name, or null for an anonymous connection
     * @param tag      the language tag they chose
     */
    static void remember(String userName, String tag) {
        if (userName == null || userName.isEmpty() || tag == null) {
            return;
        }
        LocalePreferenceStore store = preferenceStore();
        if (store == null) {
            return;
        }
        try {
            store.remember(userName, localeOf(tag));
        } catch (RuntimeException ex) {
            // A store that cannot write must not break the switch: the cookie has it either way.
            LOG.log(Level.WARNING, "[zeroz4j] LocalePreferenceStore could not remember the language"
                    + " for " + userName + "; the browser's own cookie still has it.", ex);
        }
    }

    /**
     * What the application has stored for this person.
     *
     * @param userName the authenticated user name, or null
     * @return a language tag, or null when nothing is stored or nothing is registered
     */
    private static String storedFor(String userName) {
        if (userName == null || userName.isEmpty()) {
            return null;
        }
        LocalePreferenceStore store = preferenceStore();
        if (store == null) {
            return null;
        }
        try {
            Locale stored = store.forUser(userName);
            return stored == null ? null : stored.toLanguageTag();
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "[zeroz4j] LocalePreferenceStore could not answer for "
                    + userName + "; falling through to the browser's own cookie.", ex);
            return null;
        }
    }

    /**
     * The language of a connection, from what the handshake carried.
     *
     * @param settings       the deployment's settings
     * @param parameter      the {@code lang} handshake parameter, or null
     * @param cookieHeader   the whole {@code Cookie} header, or null
     * @param acceptLanguage the {@code Accept-Language} header, or null
     * @return a language tag this deployment can actually answer in; never null
     */
    static String atHandshake(ServerConfig settings, String parameter, String cookieHeader,
                              String acceptLanguage) {
        return atHandshake(settings, parameter, cookieHeader, acceptLanguage, null);
    }

    /**
     * The language of a connection, from what the handshake carried and who signed in.
     *
     * @param settings       the deployment's settings
     * @param parameter      the {@code lang} handshake parameter, or null
     * @param cookieHeader   the whole {@code Cookie} header, or null
     * @param acceptLanguage the {@code Accept-Language} header, or null
     * @param userName       the authenticated user name, or null for an anonymous connection
     * @return a language tag this deployment can actually answer in; never null
     */
    static String atHandshake(ServerConfig settings, String parameter, String cookieHeader,
                              String acceptLanguage, String userName) {
        String deploymentDefault = deploymentDefault(settings);
        Set<String> offered = MessageCatalogs.offeredLanguages();

        String chosen = narrow(parameter, offered, deploymentDefault);
        if (chosen == null) {
            chosen = narrow(storedFor(userName), offered, deploymentDefault);
        }
        if (chosen == null) {
            chosen = narrow(fromCookie(cookieHeader), offered, deploymentDefault);
        }
        if (chosen == null) {
            for (String preferred : preferences(acceptLanguage)) {
                chosen = narrow(preferred, offered, deploymentDefault);
                if (chosen != null) {
                    break;
                }
            }
        }
        return chosen == null ? deploymentDefault : chosen;
    }

    /**
     * @param settings the deployment's settings
     * @return the language this deployment answers in when nothing better is known
     */
    static String deploymentDefault(ServerConfig settings) {
        String configured = settings == null ? null : settings.get(DEFAULT_LOCALE_PROPERTY);
        String tag = normalize(configured);
        return tag == null ? Messages.FALLBACK_LANGUAGE : tag;
    }

    /**
     * Narrows a language somebody asked for to one this deployment actually has words for.
     *
     * <p>Unlike the handshake, this answers null rather than the deployment's own language: a
     * connection asking for something impossible mid-session is refused and left where it was,
     * because moving it somewhere it did not ask for is worse than not moving it.</p>
     *
     * @param settings  the deployment's settings
     * @param requested the language tag asked for, or null
     * @return the tag to use, or null when this deployment has no words for it
     */
    static String offeredOrNull(ServerConfig settings, String requested) {
        return narrow(requested, MessageCatalogs.offeredLanguages(), deploymentDefault(settings));
    }

    /**
     * Turns a language tag into a {@link Locale}.
     *
     * @param tag a language tag, or null
     * @return the locale; English when the tag says nothing usable
     */
    static Locale localeOf(String tag) {
        String normalized = normalize(tag);
        if (normalized == null) {
            return Locale.ENGLISH;
        }
        Locale locale = Locale.forLanguageTag(normalized);
        return locale.getLanguage().isEmpty() ? Locale.ENGLISH : locale;
    }

    /**
     * Cuts a requested language down to one this deployment can answer in.
     *
     * @return the tag to use, or null when nothing here matches
     */
    private static String narrow(String requested, Set<String> offered, String deploymentDefault) {
        String tag = normalize(requested);
        if (tag == null) {
            return null;
        }
        if (tag.equals(deploymentDefault) || offered.contains(tag)) {
            return tag;
        }
        int region = tag.indexOf('-');
        if (region > 0) {
            String language = tag.substring(0, region);
            if (language.equals(deploymentDefault) || offered.contains(language)) {
                return language;
            }
        }
        return null;
    }

    /**
     * Reads {@code Accept-Language} in the order the browser put it, best first.
     *
     * <p>Quality values order the list and are then thrown away: what matters is which of them this
     * deployment has a catalog for, and the first one that does wins.</p>
     */
    private static List<String> preferences(String acceptLanguage) {
        List<String> tags = new ArrayList<>();
        if (acceptLanguage == null || acceptLanguage.isEmpty()) {
            return tags;
        }
        List<String> lower = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        for (String part : acceptLanguage.split(",")) {
            String entry = part.trim();
            if (entry.isEmpty()) {
                continue;
            }
            double weight = 1.0d;
            int semicolon = entry.indexOf(';');
            if (semicolon >= 0) {
                String parameters = entry.substring(semicolon + 1).trim();
                entry = entry.substring(0, semicolon).trim();
                int equals = parameters.indexOf('=');
                if (parameters.startsWith("q") && equals >= 0) {
                    try {
                        weight = Double.parseDouble(parameters.substring(equals + 1).trim());
                    } catch (NumberFormatException notANumber) {
                        weight = 1.0d;
                    }
                }
            }
            String tag = normalize(entry);
            if (tag == null || "*".equals(tag) || weight <= 0.0d) {
                continue;
            }
            int at = 0;
            while (at < weights.size() && weights.get(at) >= weight) {
                at++;
            }
            lower.add(at, tag);
            weights.add(at, weight);
        }
        tags.addAll(lower);
        return tags;
    }

    private static String fromCookie(String cookieHeader) {
        if (cookieHeader == null || cookieHeader.isEmpty()) {
            return null;
        }
        for (String part : cookieHeader.split(";")) {
            String entry = part.trim();
            int equals = entry.indexOf('=');
            if (equals > 0 && LANGUAGE_COOKIE.equals(entry.substring(0, equals).trim())) {
                return entry.substring(equals + 1).trim();
            }
        }
        return null;
    }

    /**
     * A language tag the rest of this class can compare: lower case, {@code -} between language and
     * region, and nothing that is not a letter, a digit or a separator.
     *
     * @return the tag, or null when there is nothing usable in it
     */
    static String normalize(String tag) {
        if (tag == null) {
            return null;
        }
        String trimmed = tag.trim().replace('_', '-').toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty() || trimmed.length() > 35) {
            return null;
        }
        for (int at = 0; at < trimmed.length(); at++) {
            char here = trimmed.charAt(at);
            boolean allowed = (here >= 'a' && here <= 'z') || (here >= '0' && here <= '9')
                    || here == '-';
            if (!allowed) {
                return null;
            }
        }
        return trimmed;
    }
}
