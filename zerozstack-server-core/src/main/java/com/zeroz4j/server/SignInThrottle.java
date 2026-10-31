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

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Counts sign-in attempts per name, so a password cannot be guessed at full speed.
 *
 * <p>An attempt is counted <b>before</b> the application checks it and forgiven on success, so a
 * burst of parallel guesses cannot all slip through while the first one is still being checked.
 * The key is what was typed, whether or not such a person exists, so being stopped reveals nothing
 * about which names are real.</p>
 *
 * <p>Held in memory, per server, with the least recently seen names dropped first once
 * {@value #MAX_NAMES} are tracked.</p>
 */
final class SignInThrottle {

    private static final int MAX_NAMES = 100_000;

    /** Name to {count, time of the last counted attempt}. */
    private final Map<String, long[]> attempts = new LinkedHashMap<>(256, 0.75f, true) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, long[]> eldest) {
            return size() > MAX_NAMES;
        }
    };

    /**
     * Counts one attempt.
     *
     * @param username    what was typed
     * @param now         the time, in milliseconds
     * @param maxAttempts attempts allowed in a row
     * @param lockMillis  how long after the last counted attempt the count starts again
     * @return true when this attempt may be checked; false when it is refused unseen
     */
    synchronized boolean attempt(String username, long now, int maxAttempts, long lockMillis) {
        String key = username.toLowerCase(Locale.ROOT);
        long[] seen = attempts.get(key);
        if (seen == null || now - seen[1] >= lockMillis) {
            attempts.put(key, new long[] {1L, now});
            return true;
        }
        if (seen[0] >= maxAttempts) {
            return false;    // not re-stamped: an attacker must not be able to extend the wait
        }
        seen[0]++;
        seen[1] = now;
        return true;
    }

    /** Forgives the count after a correct password. */
    synchronized void succeeded(String username) {
        attempts.remove(username.toLowerCase(Locale.ROOT));
    }
}
