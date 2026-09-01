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
package com.zeroz4j.api.i18n;

import com.zeroz4j.signals.ValueSignal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The words the browser is showing, and the language it is showing them in.
 *
 * <p>The browser cannot read a {@code .properties} file, so the server sends it one: the whole
 * catalog for the language this connection was resolved to rides on the frame that already says
 * "you can start". This class is where it lands, and it is what {@link Message#text()} reads on
 * the client.</p>
 *
 * <h2>Two languages, not one, and why that is not a mistake</h2>
 *
 * <p>{@code com.zeroz4j.signals.Zeroz4jSignals.LOCALE} is the language somebody <em>asked</em> for.
 * It is written the moment a selector is used, before the server has answered, because that is what
 * makes the selector feel instant.</p>
 *
 * <p>{@link #language()} is the language the screen is actually <em>in</em>, and it only moves when
 * the words for it are in hand. Without that separation there is a moment - short, and on every
 * single switch - where the language has changed and the words have not, and every label on the
 * screen redraws itself in the old language under the new language's name. Then the catalog lands
 * and they all redraw again. The person sees a flicker and the framework does twice the work.</p>
 *
 * <h2>Where a word comes from</h2>
 *
 * <ol>
 *   <li>The catalog the server sent for the language now on screen.</li>
 *   <li>The fallback language compiled into this build - the generated {@code AppText_Catalog},
 *       if the application registered it with {@link #useFallback}. This is what answers before the
 *       connection is up and after it has gone away.</li>
 *   <li>The framework's own English, which is compiled in and cannot fail to load.</li>
 *   <li>The key itself, so a missing word shows as {@code task.add} rather than a blank space.</li>
 * </ol>
 *
 * <p>Applications do not call {@link #apply}: the client runtime does, when the server's frame
 * arrives.</p>
 *
 * @since 0.9.0
 */
public final class ClientMessages {

    /**
     * How many times the words on screen have been replaced.
     *
     * <p>This is the signal, and the language itself is not, for two reasons that both matter.</p>
     *
     * <p>Reconnecting delivers the catalog again under the <em>same</em> language name, and a
     * signal that skips a value equal to the one it holds would tell nobody - the screen would keep
     * showing words from a catalog that has been replaced.</p>
     *
     * <p>And one write means one redraw. Two signals, written one after the other, would re-run
     * every effect on the screen twice for one switch, and the first of those two runs would be
     * against a half-applied state.</p>
     */
    private static final ValueSignal<Integer> GENERATION = new ValueSignal<>(Integer.valueOf(0));

    /** The language the words on screen are in. Changed only just before {@link #GENERATION}. */
    private static volatile String language = Messages.FALLBACK_LANGUAGE;

    /** The catalogs the server sent, by base name, for the language now on screen. */
    private static final Map<String, Map<String, String>> RECEIVED = new LinkedHashMap<>();

    /** The fallback language compiled into this build, by catalog base name. */
    private static final Map<String, Fallback> FALLBACKS = new LinkedHashMap<>();

    /** The languages this deployment can answer in, in the order the server listed them. */
    private static List<String> offered = Collections.emptyList();

    private static boolean installed;

    /**
     * One catalog's fallback language, compiled into this build.
     *
     * <p>The generated {@code AppText_Catalog.lookup} has exactly this shape, so registering one is
     * a method reference.</p>
     */
    public interface Fallback {
        /**
         * @param key the key
         * @return the pattern in the fallback language, or null when this catalog has no such key
         */
        String pattern(String key);
    }

    private ClientMessages() {
    }

    /**
     * Teaches {@link Messages} to read from this store. Called once by the client runtime.
     */
    public static void install() {
        if (installed) {
            return;
        }
        installed = true;
        Messages.useSource(ClientMessages::lookupPattern);
        Messages.useCurrentLanguage(ClientMessages::language);
    }

    /**
     * Registers the fallback language this build compiled in for one catalog.
     *
     * <pre>{@code
     * ClientMessages.useFallback(AppText_Catalog.BASE_NAME, AppText_Catalog::lookup);
     * }</pre>
     *
     * <p>Optional. Without it the screen still works - every word comes over the connection - but a
     * screen built before the connection is up, or rebuilt while it is down, shows keys instead of
     * words.</p>
     *
     * @param catalog the catalog's base name, for example {@code "i18n/app"}
     * @param words   where its fallback-language patterns come from
     */
    public static void useFallback(String catalog, Fallback words) {
        if (catalog == null || words == null) {
            return;
        }
        FALLBACKS.put(catalog, words);
    }

    /**
     * The language the words on screen are in.
     *
     * <p><b>Reading this inside an {@code Effect} subscribes that effect to the language.</b> It is
     * what {@link Message#text()} calls, which is why a label read inside an effect comes back in
     * the new language and one read at construction does not.</p>
     *
     * @return an IETF language tag; never null
     */
    public static String language() {
        // The read that subscribes. The tag itself is a plain field, so a lookup can compare
        // against it without subscribing to itself.
        GENERATION.get();
        return language;
    }

    /**
     * The languages this deployment can answer in, as the server listed them.
     *
     * <p>Empty until the connection has been answered. A language selector offers exactly this and
     * nothing else, so it can never offer one the server would refuse.</p>
     *
     * @return the language tags, in the server's order; never null
     */
    public static List<String> offeredLanguages() {
        return offered;
    }

    /**
     * Puts a language's words on screen: the catalogs first, then the language they are in.
     *
     * <p>That order is the point. The signal is what makes every label redraw, so publishing it
     * before the words are stored would redraw the whole screen against the catalog that is on its
     * way out.</p>
     *
     * @param newLanguage the language tag these catalogs are in
     * @param languages the languages the deployment can answer in, or null to leave the list alone
     * @param catalogs  the words, by catalog base name; entries replace what was there
     */
    public static void apply(String newLanguage, List<String> languages,
                             Map<String, Map<String, String>> catalogs) {
        if (languages != null) {
            offered = Collections.unmodifiableList(new ArrayList<>(languages));
        }
        if (catalogs != null) {
            RECEIVED.clear();
            for (Map.Entry<String, Map<String, String>> catalog : catalogs.entrySet()) {
                RECEIVED.put(catalog.getKey(), new LinkedHashMap<>(catalog.getValue()));
            }
        }
        ClientMessages.language = newLanguage == null || newLanguage.isEmpty()
                ? Messages.FALLBACK_LANGUAGE : newLanguage;
        // Last, and once. Everything above is in place before anything redraws.
        GENERATION.set(Integer.valueOf(GENERATION.get().intValue() + 1));
    }

    /** Forgets everything received, this store included. Test support only. */
    public static void forgetForTesting() {
        installed = false;
        RECEIVED.clear();
        FALLBACKS.clear();
        offered = Collections.emptyList();
        language = Messages.FALLBACK_LANGUAGE;
        GENERATION.set(Integer.valueOf(0));
    }

    private static String lookupPattern(String catalog, String key, String language) {
        if (catalog == null || key == null) {
            return null;
        }
        if (language != null) {
            // Asked for a named language: only the catalog actually on screen can answer, or a
            // language switch that had not landed yet would serve the outgoing language's words
            // under the incoming language's name.
            if (!language.equals(ClientMessages.language)) {
                return null;
            }
            Map<String, String> words = RECEIVED.get(catalog);
            return words == null ? null : words.get(key);
        }
        Fallback compiledIn = FALLBACKS.get(catalog);
        return compiledIn == null ? null : compiledIn.pattern(key);
    }
}
