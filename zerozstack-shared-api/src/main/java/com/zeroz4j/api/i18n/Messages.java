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

/**
 * Turns a {@link Message} into words, and holds the two things that decide how.
 *
 * <p>Applications rarely touch this. It is what {@link Message#text()} calls, and what the server
 * and the browser each plug themselves into once at startup.</p>
 *
 * <h2>Filling in the blanks</h2>
 *
 * <p>A pattern's blanks are {@code {0}}, {@code {1}} and so on, and <b>nothing else</b>. There is
 * no {@code {0,number,currency}}, no choice form and no date format, and that is a deliberate
 * limit rather than an omission: one call into {@code java.text.MessageFormat} makes the browser
 * download 43 percent bigger — more than twenty languages of translated text put together. Forty
 * lines of positional replacement cost nothing, and they mean the browser and the server render
 * the same message character for character, because they run the same forty lines.</p>
 *
 * <p>An application that wants a number written the local way formats the number itself and passes
 * the words in.</p>
 *
 * <h2>Where the words come from</h2>
 *
 * <p>In order, and the first answer wins:</p>
 *
 * <ol>
 *   <li>The installed {@link Source}, asked for the requested language. On a server that reads the
 *       {@code .properties} file for that language.</li>
 *   <li>The installed {@link Source}, asked for the fallback language, which is the file with no
 *       language suffix.</li>
 *   <li>The framework's own English, which is compiled in and cannot fail to load. This is what
 *       makes a deployment that configures no language behave exactly as it did before language
 *       support existed.</li>
 *   <li>Nothing found: the key itself is returned, so a screen shows {@code task.add} rather than
 *       an empty space.</li>
 * </ol>
 *
 * @since 0.9.0
 */
public final class Messages {

    /** The language assumed when nothing has said otherwise. */
    public static final String FALLBACK_LANGUAGE = "en";

    /**
     * Where translated text is read from.
     *
     * <p>The server reads {@code .properties} files off its classpath. The browser reads the map
     * the server sent it when the connection opened.</p>
     */
    public interface Source {
        /**
         * @param catalog  the catalog's base name, for example {@code "i18n/app"}
         * @param key      the key inside it
         * @param language an IETF language tag, or null for the fallback language
         * @return the pattern, or null when this source has no entry for that key
         */
        String pattern(String catalog, String key, String language);
    }

    /** What language the reader of this message is reading. */
    public interface CurrentLanguage {
        /**
         * @return an IETF language tag; never null
         */
        String get();
    }

    private static volatile Source source;
    private static volatile CurrentLanguage currentLanguage;

    private Messages() {
    }

    /**
     * Installs where translated text is read from. Called once, at startup, by the server or by the
     * client runtime.
     *
     * @param newSource the source, or null to go back to the compiled-in English only
     */
    public static void useSource(Source newSource) {
        source = newSource;
    }

    /**
     * Installs what decides the reader's language.
     *
     * @param supplier the supplier, or null to fall back to {@link #FALLBACK_LANGUAGE}
     */
    public static void useCurrentLanguage(CurrentLanguage supplier) {
        currentLanguage = supplier;
    }

    /**
     * @return the language the reader of the next message is reading; never null
     */
    public static String currentLanguage() {
        CurrentLanguage supplier = currentLanguage;
        if (supplier == null) {
            return FALLBACK_LANGUAGE;
        }
        String language = supplier.get();
        return language == null || language.isEmpty() ? FALLBACK_LANGUAGE : language;
    }

    /**
     * @param message the message
     * @return its words in the reader's language
     */
    public static String text(Message message) {
        return text(message, currentLanguage());
    }

    /**
     * @param message  the message
     * @param language an IETF language tag
     * @return its words in that language
     */
    public static String text(Message message, String language) {
        if (message == null) {
            return null;
        }
        String pattern = lookup(message.catalog(), message.key(), language);
        return substitute(pattern, message.arguments());
    }

    /**
     * Finds one pattern, without filling anything in.
     *
     * @param catalog  the catalog's base name
     * @param key      the key inside it
     * @param language an IETF language tag
     * @return the pattern, or the key itself when nothing has it
     */
    public static String lookup(String catalog, String key, String language) {
        Source installed = source;
        if (installed != null) {
            String found = installed.pattern(catalog, key, language);
            if (found != null) {
                return found;
            }
            found = installed.pattern(catalog, key, null);
            if (found != null) {
                return found;
            }
        }
        if (FrameworkText.CATALOG.equals(catalog)) {
            String built = FrameworkText.fallbackText(key);
            if (built != null) {
                return built;
            }
        }
        return key;
    }

    /**
     * Replaces {@code {0}}, {@code {1}} and so on with the values given, in order.
     *
     * <p>A blank whose number is beyond the values given is left alone, so a mismatched translation
     * shows the blank rather than throwing on somebody's screen. Anything else between braces is
     * left alone too.</p>
     *
     * @param pattern   the pattern
     * @param arguments the values, in order
     * @return the finished sentence
     */
    public static String substitute(String pattern, Object[] arguments) {
        if (pattern == null || arguments == null || arguments.length == 0) {
            return pattern;
        }
        if (pattern.indexOf('{') < 0) {
            return pattern;
        }
        StringBuilder out = new StringBuilder(pattern.length() + 16);
        int at = 0;
        int end = pattern.length();
        while (at < end) {
            char here = pattern.charAt(at);
            if (here != '{') {
                out.append(here);
                at++;
                continue;
            }
            int scan = at + 1;
            int index = 0;
            boolean sawDigit = false;
            while (scan < end) {
                char digit = pattern.charAt(scan);
                if (digit < '0' || digit > '9') {
                    break;
                }
                index = index * 10 + (digit - '0');
                sawDigit = true;
                scan++;
            }
            if (sawDigit && scan < end && pattern.charAt(scan) == '}' && index < arguments.length) {
                out.append(String.valueOf(arguments[index]));
                at = scan + 1;
            } else {
                out.append(here);
                at++;
            }
        }
        return out.toString();
    }
}
