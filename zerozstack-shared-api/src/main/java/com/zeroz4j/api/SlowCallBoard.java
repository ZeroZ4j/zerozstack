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
package com.zeroz4j.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Every slow call one browser or user has, as the server last reported them.
 *
 * <p>Framework-internal. It travels on the ordinary shared-signal frames: a client subscribes to
 * {@value #SIGNAL_NAME}, and the server answers with {@code 0x17 SIGNAL_UPD} at once and after every
 * change. Results do not travel here, because the whole board goes out on every progress step. A
 * browser fetches a result with {@link SlowCallRpc#collect(String)}.</p>
 *
 * <p>Deliberately has no {@code equals}. A shared signal skips a value equal to the one it holds,
 * and the browser needs to hear the board that answers its re-subscription after a reconnect even
 * when nothing changed, because that is when it retries a fetch the drop cut off.</p>
 *
 * @since 0.11.0
 */
public final class SlowCallBoard {

    /** The wire name of the signal, which the server engine recognizes by name. */
    public static final String SIGNAL_NAME = "zeroz.slowcalls";

    /** One slow call on the board. */
    public static final class Entry {
        private final String id;
        private final String key;
        private final SlowCallStatus status;
        private final int percent;
        private final String text;
        private final String message;

        /**
         * @param id      this run of the work
         * @param key     what the application calls the work
         * @param status  {@link SlowCallStatus#RUNNING}, {@link SlowCallStatus#FINISHED},
         *                {@link SlowCallStatus#FAILED} or {@link SlowCallStatus#CANCELED}
         * @param percent how far along, or -1
         * @param text    what the work says it is doing, or null
         * @param message the failure sentence, or null
         */
        public Entry(String id, String key, SlowCallStatus status, int percent, String text,
                     String message) {
            this.id = id;
            this.key = key;
            this.status = status;
            this.percent = percent;
            this.text = text;
            this.message = message;
        }

        /** @return this run of the work */
        public String id() {
            return id;
        }

        /** @return what the application calls the work */
        public String key() {
            return key;
        }

        /** @return where it stands */
        public SlowCallStatus status() {
            return status;
        }

        /** @return how far along, or -1 */
        public int percent() {
            return percent;
        }

        /** @return what the work says it is doing, or null */
        public String progressText() {
            return text;
        }

        /** @return the failure sentence, or null */
        public String message() {
            return message;
        }
    }

    private List<Entry> entries = new ArrayList<>();

    /** For the wire. */
    public SlowCallBoard() {
    }

    /**
     * @param entries the slow calls on it
     */
    public SlowCallBoard(List<Entry> entries) {
        this.entries = new ArrayList<>(entries);
    }

    /** @return the slow calls on it */
    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    /**
     * @param key a key
     * @return the entry under it, or null
     */
    public Entry find(String key) {
        for (Entry entry : entries) {
            if (entry.key().equals(key)) {
                return entry;
            }
        }
        return null;
    }

    void replaceEntries(List<Entry> read) {
        this.entries = read;
    }
}
