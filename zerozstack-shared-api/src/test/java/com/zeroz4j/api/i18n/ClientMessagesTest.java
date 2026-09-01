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
package com.zeroz4j.api.i18n;

import com.zeroz4j.signals.Effect;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The words the browser holds, and what makes a label follow a language change.
 *
 * <h2>What this protects</h2>
 *
 * <p><b>Text redraws itself.</b> Reading a message inside an effect has to make that effect a
 * subscriber of the language, or every label on every screen stays in the language it was built in
 * and nothing says so. That is the whole mechanism of live switching, and it is one line of
 * plumbing that would be silently absent if it broke.</p>
 *
 * <p><b>The words arrive before the language name does.</b> Otherwise the first thing to redraw
 * reads the outgoing catalog under the incoming language's name, and everything on screen redraws
 * twice.</p>
 */
class ClientMessagesTest {

    @BeforeEach
    void setUp() {
        ClientMessages.forgetForTesting();
        ClientMessages.install();
    }

    @AfterEach
    void tearDown() {
        ClientMessages.forgetForTesting();
        Messages.useSource(null);
        Messages.useCurrentLanguage(null);
    }

    @Test
    @DisplayName("a label read inside an effect comes back in the new language")
    void switchingLanguageRedrawsText() {
        Message greeting = new Message("i18n/app", "greet");
        List<String> drawn = new ArrayList<>();
        Effect.create(() -> drawn.add(greeting.text()));

        assertEquals(Collections.singletonList("greet"), drawn,
                "nothing has arrived yet, so the key stands in - never a blank space");

        ClientMessages.apply("en", Arrays.asList("en", "de"), catalog("Good day"));
        ClientMessages.apply("de", null, catalog("Guten Tag"));

        assertEquals(Arrays.asList("greet", "Good day", "Guten Tag"), drawn,
                "the effect re-ran on its own both times. If it did not, every label in every "
                        + "application stays in the language it was built in.");
    }

    @Test
    @DisplayName("the same language arriving again still redraws")
    void aFreshCatalogUnderTheSameNameStillRedraws() {
        Message greeting = new Message("i18n/app", "greet");
        List<String> drawn = new ArrayList<>();

        ClientMessages.apply("en", Arrays.asList("en"), catalog("Good day"));
        Effect.create(() -> drawn.add(greeting.text()));
        ClientMessages.apply("en", Arrays.asList("en"), catalog("Good morning"));

        assertEquals(Arrays.asList("Good day", "Good morning"), drawn,
                "reconnecting delivers the catalog again under the same language name, and a "
                        + "signal that skipped an equal value would tell nobody");
    }

    @Test
    @DisplayName("what this build compiled in answers before anything has arrived")
    void theCompiledInFallbackAnswersFirst() {
        ClientMessages.useFallback("i18n/app",
                key -> "greet".equals(key) ? "Good day" : null);

        assertEquals("Good day", new Message("i18n/app", "greet").text(),
                "a screen drawn before the connection is up must be words, not keys");
    }

    @Test
    @DisplayName("the server's words win over the compiled-in ones")
    void whatTheServerSentWins() {
        ClientMessages.useFallback("i18n/app", key -> "Good day");
        ClientMessages.apply("de", Arrays.asList("en", "de"), catalog("Guten Tag"));

        assertEquals("Guten Tag", new Message("i18n/app", "greet").text());
    }

    @Test
    @DisplayName("a key the server did not send falls back rather than showing its own name")
    void aMissingKeyFallsBack() {
        ClientMessages.useFallback("i18n/app",
                key -> "farewell".equals(key) ? "Goodbye" : null);
        ClientMessages.apply("de", Arrays.asList("en", "de"), catalog("Guten Tag"));

        assertEquals("Goodbye", new Message("i18n/app", "farewell").text());
    }

    @Test
    @DisplayName("the framework's own English cannot fail to load")
    void theFrameworksOwnWordsAreAlwaysThere() {
        assertEquals("Language", FrameworkText.uiLanguage().text(),
                "a deployment that configures no language at all sees exactly what it saw before "
                        + "language support existed");
    }

    @Test
    @DisplayName("the languages on offer are the server's list, in the server's order")
    void theOfferedListIsTheServersList() {
        assertTrue(ClientMessages.offeredLanguages().isEmpty(),
                "empty until the connection has been answered, so a selector offers nothing rather "
                        + "than guessing");

        ClientMessages.apply("en", Arrays.asList("en", "de", "pt-br"), catalog("Good day"));

        assertEquals(Arrays.asList("en", "de", "pt-br"), ClientMessages.offeredLanguages());
    }

    private static Map<String, Map<String, String>> catalog(String greeting) {
        Map<String, String> words = new LinkedHashMap<>();
        words.put("greet", greeting);
        Map<String, Map<String, String>> catalogs = new LinkedHashMap<>();
        catalogs.put("i18n/app", words);
        return catalogs;
    }
}
