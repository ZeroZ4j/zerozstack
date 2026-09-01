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

import com.zeroz4j.api.i18n.Message;
import com.zeroz4j.api.i18n.Messages;

import java.util.Locale;

/**
 * Teaches {@link Messages} how to find words on a server, and how to tell whose words they are.
 *
 * <p>Two things are plugged in, once: text comes from {@code .properties} files on the classpath,
 * and the reader's language is the caller's language on the connection this call arrived on
 * ({@link RmiRequestContext#getLocale()}). After that,
 * {@code AppText_Text.invoiceApproved(n).text()} inside a service method produces the sentence in
 * the language of whoever asked, with nothing passed down through the call.</p>
 *
 * <p>This runs when the engine class is first loaded, which is before any connection exists.</p>
 */
public final class ServerMessages {

    private static boolean installed;

    private ServerMessages() {
    }

    /**
     * Plugs the server's catalogs and the caller's language into {@link Messages}.
     *
     * <p>Safe to call more than once; the second call does nothing.</p>
     */
    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        Messages.useSource(new MessageCatalogs());
        Messages.useCurrentLanguage(() -> tagOf(RmiRequestContext.getLocale()));
    }

    /**
     * The words of a message in one language.
     *
     * @param message  what to say
     * @param locale   who is being told
     * @return the sentence
     */
    public static String render(Message message, Locale locale) {
        install();
        return message == null ? null : message.text(tagOf(locale));
    }

    /**
     * The words of a message in English, which is what the server log always gets.
     *
     * @param message what to say
     * @return the English sentence
     */
    public static String inEnglish(Message message) {
        install();
        return message == null ? null : message.text(Messages.FALLBACK_LANGUAGE);
    }

    /**
     * The language a connection asking for one actually gets.
     *
     * <p>Narrowed to what this deployment has a catalog for: {@code de-AT} becomes {@code de} when
     * only {@code de} exists, and falls to the deployment's own language when neither does. A
     * browser asking for a language nobody translated must never produce a half-translated
     * screen.</p>
     *
     * <p>This is what the handshake does. It is public so a test harness can put a connection in
     * the state a real browser would be in.</p>
     *
     * @param settings  the deployment's settings
     * @param requested the language tag the connection asked for, or null
     * @return the language tag this connection will be answered in; never null
     */
    public static String languageFor(ServerConfig settings, String requested) {
        install();
        return LocaleResolution.atHandshake(settings, requested, null, null);
    }

    /**
     * @param locale a locale, or null
     * @return the language tag a catalog is looked up under; never null
     */
    static String tagOf(Locale locale) {
        if (locale == null) {
            return Messages.FALLBACK_LANGUAGE;
        }
        String tag = locale.toLanguageTag();
        if (tag == null || tag.isEmpty() || "und".equals(tag)) {
            return Messages.FALLBACK_LANGUAGE;
        }
        return tag.toLowerCase(Locale.ROOT);
    }
}
