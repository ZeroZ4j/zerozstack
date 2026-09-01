/*
 * Copyright 2026 Franz Schöning
 * Project: https://www.zeroz4j.com
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An application's own memory of who reads what, for the tests.
 *
 * <p>Registered in {@code META-INF/services} so it is found the way a real one would be. It answers
 * for nobody until a test puts somebody in it, so every other test in this module runs as though no
 * store were registered at all.</p>
 */
public final class RememberedLanguages implements LocalePreferenceStore {

    /** Who reads what, filled in by whichever test cares. */
    public static final Map<String, Locale> BY_USER = new ConcurrentHashMap<>();

    @Override
    public Locale forUser(String userName) {
        return userName == null ? null : BY_USER.get(userName);
    }

    @Override
    public void remember(String userName, Locale locale) {
        if (userName != null && locale != null) {
            BY_USER.put(userName, locale);
        }
    }
}
