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
package com.zeroz4j.signals;

import com.zeroz4j.api.Scope;

/**
 * The signals the framework itself declares. Applications read and write them like any other.
 *
 * @since 0.9.0
 */
public final class Zeroz4jSignals {

    /** The wire name of {@link #LOCALE}, which the server engine recognises by name. */
    public static final String LOCALE_NAME = "zeroz.locale";

    /**
     * The language this browser has asked to read, as an IETF language tag such as {@code "de"}.
     *
     * <p>Writing it is the whole of switching language:</p>
     *
     * <pre>{@code
     * Zeroz4jSignals.LOCALE.mine().set("de");
     * }</pre>
     *
     * <p>The client applies that at once, writes its own {@code zeroz-lang} cookie so the choice
     * survives a reload, and sends it up. The server narrows it to a language it actually has words
     * for, remembers it on the connection, sends the words down, and only then agrees. A language
     * nobody translated is refused the way any invalid write is refused: the writer is snapped back
     * to what the server does have.</p>
     *
     * <p><b>This is not what to read to show text.</b> It is what somebody asked for, which for a
     * moment after a switch is ahead of the words on screen. Reading a message with
     * {@code Message.text()} inside an {@code Effect} is how text follows the language, and that
     * reads {@code ClientMessages.language()} - the language the words are actually in.</p>
     *
     * <p>{@link Scope#CLIENT} because a language has to work with no login, and the browser id
     * already survives reconnects and reloads.</p>
     */
    public static final ScopedSignal<String> LOCALE =
            Signals.scopedWritable(LOCALE_NAME, "en", Scope.CLIENT);

    private Zeroz4jSignals() {
    }
}
