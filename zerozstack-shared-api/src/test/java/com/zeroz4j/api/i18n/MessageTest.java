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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The forty lines that fill in a message's blanks, and the order words are looked for in.
 *
 * <p>Nothing here touches {@code java.text}. One call into it makes the browser download 43 percent
 * bigger, which is more than twenty languages of translated text put together, so positional
 * replacement is the whole mechanism and this is where it is proved to behave.</p>
 */
class MessageTest {

    @AfterEach
    void putTheFrameworkBackAsItWas() {
        Messages.useSource(null);
        Messages.useCurrentLanguage(null);
    }

    @Test
    @DisplayName("blanks are filled in, in order")
    void blanksAreFilledIn() {
        assertEquals("3 of 7 tasks left",
                Messages.substitute("{0} of {1} tasks left", new Object[] {3, 7}));
    }

    @Test
    @DisplayName("the same value can be used twice, and skipped")
    void aValueCanAppearTwice() {
        assertEquals("a a b",
                Messages.substitute("{0} {0} {1}", new Object[] {"a", "b"}));
        assertEquals("b",
                Messages.substitute("{1}", new Object[] {"a", "b"}));
    }

    @Test
    @DisplayName("a blank nobody passed a value for is left alone rather than throwing")
    void anUnfilledBlankIsLeftAlone() {
        assertEquals("one {1}", Messages.substitute("{0} {1}", new Object[] {"one"}));
    }

    @Test
    @DisplayName("anything else between braces is left alone")
    void otherBracesAreLeftAlone() {
        assertEquals("{a} {0,number} { } x",
                Messages.substitute("{a} {0,number} { } {0}", new Object[] {"x"}));
    }

    @Test
    @DisplayName("a value is written exactly as string concatenation would write it")
    void valuesReadTheSameAsConcatenation() {
        // This is what keeps English byte-identical to what the framework produced before there
        // were any catalogs: every sentence used to be built with + and now is built with {0}.
        Object nothing = null;
        assertEquals("x" + nothing, Messages.substitute("x{0}", new Object[] {null}));
        assertEquals("roles [a, b]",
                Messages.substitute("roles {0}", new Object[] {java.util.Arrays.asList("a", "b")}));
    }

    @Test
    @DisplayName("a message is a value, not words: two of them are equal when they say the same")
    void aMessageIsAValue() {
        Message one = new Message("i18n/app", "task.remaining", 3, 7);
        Message same = new Message("i18n/app", "task.remaining", 3, 7);
        Message other = new Message("i18n/app", "task.remaining", 4, 7);
        assertEquals(one, same);
        assertEquals(one.hashCode(), same.hashCode());
        assertNotEquals(one, other);
    }

    @Test
    @DisplayName("printing a message does not turn it into words")
    void printingAMessageDoesNotChooseALanguage() {
        // A message that rendered itself the moment it landed in a log line would pick a language
        // behind the writer's back, and the writer would never know which.
        String printed = new Message("i18n/app", "task.add").toString();
        assertTrue(printed.contains("i18n/app"));
        assertTrue(printed.contains("task.add"));
        assertFalse(printed.contains("Add task"));
    }

    @Test
    @DisplayName("the requested language wins, then the fallback file, then the compiled-in English")
    void wordsAreLookedForInOrder() {
        Map<String, String> german = new LinkedHashMap<>();
        german.put("greeting", "Guten Tag");
        Map<String, String> fallback = new LinkedHashMap<>();
        fallback.put("greeting", "Good day");
        fallback.put("farewell", "Goodbye");

        Messages.useSource((catalog, key, language) -> {
            if (!"i18n/app".equals(catalog)) {
                return null;
            }
            return "de".equals(language) ? german.get(key)
                    : language == null ? fallback.get(key) : null;
        });

        assertEquals("Guten Tag", new Message("i18n/app", "greeting").text("de"));
        assertEquals("Goodbye", new Message("i18n/app", "farewell").text("de"));
        assertEquals("Good day", new Message("i18n/app", "greeting").text("en"));
    }

    @Test
    @DisplayName("the framework's own English is there with nothing installed at all")
    void theFrameworkSpeaksEnglishWithNoSourceInstalled() {
        assertEquals("Access denied: requires role [approver] but user has []",
                FrameworkText.accessDenied("[approver]", "[]").text("de"));
    }

    @Test
    @DisplayName("a key nothing has comes back as the key, not as an empty space")
    void anUnknownKeyComesBackAsItself() {
        assertEquals("nobody.has.this", new Message("i18n/app", "nobody.has.this").text("en"));
    }

    @Test
    @DisplayName("the reader's language is asked for once, and defaults to English")
    void theCurrentLanguageIsPluggable() {
        assertEquals("en", Messages.currentLanguage());
        Messages.useCurrentLanguage(() -> "fr");
        assertEquals("fr", Messages.currentLanguage());
        Messages.useCurrentLanguage(() -> null);
        assertEquals("en", Messages.currentLanguage());
    }
}
