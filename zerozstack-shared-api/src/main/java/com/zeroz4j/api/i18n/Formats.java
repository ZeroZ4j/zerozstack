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

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Numbers, money and dates written the way the reader writes them.
 *
 * <h2>Read this before the first call. It is the most expensive line in the framework.</h2>
 *
 * <p>Nothing else in ZeroZ Stack touches {@code java.text}, and that is deliberate. The first call
 * from a client module into any method on this class pulls the whole of {@code java.text} into the
 * browser bundle: <b>233 KB more to compile and 43 KB more to download, gzipped</b>, measured on
 * the {@code todo-signals} example. Every locale named in the build's
 * {@code java.util.Locale.available} then costs about <b>36 KB more, 6 KB gzipped</b>.</p>
 *
 * <p>To put that in proportion: translating the interface into twenty languages costs the browser
 * <b>nothing at all</b>, because the words travel over the connection. Formatting one number the
 * German way costs more than every language of text the project will ever ship, several times
 * over.</p>
 *
 * <p>It lives in a class of its own for that reason. Nothing reaches it by accident, so nobody pays
 * for it by accident, and one import in one file is the whole of the decision.</p>
 *
 * <h2>Turning it on</h2>
 *
 * <p>Calling it is not enough. TeaVM compiles in locale data only for the locales the build names,
 * and the default is one. In the client module's {@code teavm-maven-plugin} configuration:</p>
 *
 * <pre>{@code
 * <properties>
 *   <java.util.Locale.available>en_US,de_DE,fr_FR</java.util.Locale.available>
 *   <java.util.Locale.default>en_US</java.util.Locale.default>
 * </properties>
 * }</pre>
 *
 * <p><b>{@code java.util.Locale.default} must contain an underscore.</b> TeaVM splits it on one and
 * a value without it fails at class initialization with nothing useful to read.</p>
 *
 * <h2>What it does not do</h2>
 *
 * <ul>
 *   <li><b>A language with no locale data formats as the fallback.</b> No error and no warning: the
 *       numbers are simply grouped the English way. The two lists - languages you translated,
 *       locales whose data you compiled in - live in different files and nothing compares them.</li>
 *   <li><b>Time zones are off unless the build asks.</b> TeaVM's {@code java.util.TimeZone.autodetect}
 *       defaults to false, so the browser's own zone is not detected. A timestamp translated into
 *       German and shown in UTC is still the wrong time.</li>
 *   <li><b>It is not a message format.</b> There is no {@code {0,number,currency}} anywhere in this
 *       framework. Format the value here, pass the words in:
 *       {@code AppText_Text.invoiceTotal(Formats.currency().format(amount))}.</li>
 * </ul>
 *
 * <h2>Both tiers, one call site</h2>
 *
 * <p>The locale is the reader's: the language on screen in the browser, the caller's language on
 * the server. So the same line is right in a component and in a service method, and nothing has to
 * be passed down.</p>
 *
 * <p>Reading it in the browser does <em>not</em> subscribe to the language. Formatting inside an
 * {@code Effect} that also reads a message is the ordinary way to have a number follow a switch.</p>
 *
 * @since 0.9.0
 */
public final class Formats {

    private Formats() {
    }

    /**
     * The reader's locale: the language on screen in a browser, the caller's language on a server.
     *
     * @return the locale; never null
     */
    public static Locale locale() {
        return localeOf(Messages.currentLanguage());
    }

    /**
     * Plain numbers - grouping and decimal separator the reader's way.
     *
     * @return a fresh format; not shared, because these are not safe to use from two threads
     */
    public static NumberFormat number() {
        return NumberFormat.getInstance(locale());
    }

    /**
     * Whole numbers with no decimals.
     *
     * @return a fresh format
     */
    public static NumberFormat integer() {
        return NumberFormat.getIntegerInstance(locale());
    }

    /**
     * Percentages, written the reader's way.
     *
     * @return a fresh format
     */
    public static NumberFormat percent() {
        return NumberFormat.getPercentInstance(locale());
    }

    /**
     * Money, with the symbol and placement of the reader's locale.
     *
     * <p><b>The locale decides the currency,</b> which is almost never what an application wants: a
     * German reader looking at a dollar invoice must see dollars. Set the currency explicitly on
     * the returned format when the amount has one of its own.</p>
     *
     * @return a fresh format
     */
    public static NumberFormat currency() {
        return NumberFormat.getCurrencyInstance(locale());
    }

    /**
     * Dates, in the reader's usual order and length.
     *
     * @return a fresh format
     */
    public static DateFormat date() {
        return DateFormat.getDateInstance(DateFormat.MEDIUM, locale());
    }

    /**
     * A date and a time together.
     *
     * @return a fresh format
     */
    public static DateFormat dateTime() {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale());
    }

    /**
     * Turns a language tag into a locale without {@code Locale.forLanguageTag}, which pulls in more
     * of the class library than this needs.
     *
     * @param tag a tag such as {@code "de"} or {@code "pt-BR"}, or null
     * @return the locale
     */
    @SuppressWarnings("deprecation")
    static Locale localeOf(String tag) {
        if (tag == null || tag.isEmpty()) {
            return Locale.ENGLISH;
        }
        String normalized = tag.replace('_', '-');
        int dash = normalized.indexOf('-');
        if (dash < 0) {
            return new Locale(normalized);
        }
        return new Locale(normalized.substring(0, dash), normalized.substring(dash + 1));
    }
}
