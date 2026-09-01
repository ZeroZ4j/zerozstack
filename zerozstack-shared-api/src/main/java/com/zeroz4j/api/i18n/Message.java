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

import java.util.Arrays;

/**
 * One sentence, named rather than written out: which catalog it is in, which key, and the values
 * that fill its blanks.
 *
 * <p><b>A message is not words yet.</b> It carries no language of its own, which is what lets one
 * be created deep inside a service method that knows nothing about who is calling, travel out to
 * the edge of the server, and be turned into words there in the caller's language — while the same
 * value is written to the log in English.</p>
 *
 * <pre>{@code
 * throw new ClientVisibleException(AppText_Text.invoiceAlreadyApproved(invoiceNumber));
 * }</pre>
 *
 * <p>Turning it into words is a separate, explicit act:</p>
 *
 * <pre>{@code
 * String words = AppText_Text.taskAdd().text();
 * }</pre>
 *
 * <p>Do not build one by hand. The annotation processor writes a method per key from an
 * {@link MessageCatalog}, so a misspelled key and a wrong number of values are both compile
 * errors rather than something wrong on somebody's screen.</p>
 *
 * @since 0.9.0
 */
public final class Message {

    private static final Object[] NO_ARGUMENTS = new Object[0];

    private final String catalog;
    private final String key;
    private final Object[] arguments;

    /**
     * @param catalog   the catalog's base name, for example {@code "i18n/app"}
     * @param key       the key inside it, for example {@code "task.remaining"}
     * @param arguments the values that fill {@code {0}}, {@code {1}} and so on, in order
     */
    public Message(String catalog, String key, Object... arguments) {
        this.catalog = catalog;
        this.key = key;
        this.arguments = arguments == null || arguments.length == 0
                ? NO_ARGUMENTS : arguments.clone();
    }

    /**
     * @return the catalog's base name
     */
    public String catalog() {
        return catalog;
    }

    /**
     * @return the key inside the catalog
     */
    public String key() {
        return key;
    }

    /**
     * @return the values that fill the blanks, in order; never null
     */
    public Object[] arguments() {
        return arguments.length == 0 ? NO_ARGUMENTS : arguments.clone();
    }

    /**
     * The words, in the language of whoever this is being produced for.
     *
     * <p>On the server that is the caller's language, taken from the connection. In the browser it
     * is the language the person is reading.</p>
     *
     * @return the sentence, with every blank filled in
     */
    public String text() {
        return Messages.text(this);
    }

    /**
     * The words in one named language.
     *
     * <p>Use this where the language is not the reader's — writing a log line in English, most of
     * all.</p>
     *
     * @param language an IETF language tag such as {@code "de"}
     * @return the sentence, with every blank filled in
     */
    public String text(String language) {
        return Messages.text(this, language);
    }

    /**
     * A debugging description — <em>not</em> the words.
     *
     * <p>Deliberately not {@link #text()}: a message that turned itself into words the moment it
     * was concatenated into a log line would pick a language behind your back.</p>
     *
     * @return the catalog, the key, and how many values it carries
     */
    @Override
    public String toString() {
        return "Message[" + catalog + ':' + key
                + (arguments.length == 0 ? "" : " " + Arrays.toString(arguments)) + ']';
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Message)) {
            return false;
        }
        Message that = (Message) other;
        return eq(catalog, that.catalog) && eq(key, that.key)
                && Arrays.equals(arguments, that.arguments);
    }

    @Override
    public int hashCode() {
        int result = catalog == null ? 0 : catalog.hashCode();
        result = 31 * result + (key == null ? 0 : key.hashCode());
        return 31 * result + Arrays.hashCode(arguments);
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
