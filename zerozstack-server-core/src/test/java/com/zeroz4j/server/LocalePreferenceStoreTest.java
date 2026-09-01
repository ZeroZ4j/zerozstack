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

import com.zeroz4j.api.ObjectMapper;
import com.zeroz4j.signals.Zeroz4jSignals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * An application can make the language follow a person, not just a browser.
 *
 * <h2>Why this seam exists at all</h2>
 *
 * <p>On its own the framework remembers the language in a cookie. That is right on the machine
 * somebody chose it on and wrong on their second one, and the framework cannot do better without
 * acquiring a place of its own to write user data - a much larger decision, deliberately not taken.
 * So an application that already has somewhere to put it says so, in one small interface.</p>
 *
 * <p>Where it sits in the order matters: after the language the browser asked for outright, before
 * the cookie. Somebody who has just picked a language on this machine gets what they picked; a
 * fresh browser gets what they chose last time on the other one.</p>
 */
class LocalePreferenceStoreTest {

    private static final ServerConfig ENGLISH_DEPLOYMENT = ServerConfig.fromSystemProperties();

    private ServerRuntime runtime;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        RememberedLanguages.BY_USER.clear();
        MessageCatalogs.forgetForTesting();
        LocaleResolution.resetPreferenceStoreForTesting();
        runtime = new ServerRuntime();
        mapper = new ObjectMapper();
        ServerSignalTransport.install(runtime, mapper);
    }

    @AfterEach
    void tearDown() {
        RememberedLanguages.BY_USER.clear();
        ServerSignalTransport.uninstall(runtime);
        LocaleResolution.resetPreferenceStoreForTesting();
        runtime.shutDown();
    }

    @Test
    @DisplayName("a signed-in person gets the language they chose on their other computer")
    void aStoredLanguageIsUsed() {
        RememberedLanguages.BY_USER.put("polly", Locale.GERMAN);

        assertEquals("de", LocaleResolution.atHandshake(
                ENGLISH_DEPLOYMENT, null, null, null, "polly"),
                "a fresh browser with no cookie still comes up in the language this person reads");
    }

    @Test
    @DisplayName("what the browser asked for outright still wins")
    void anExplicitChoiceBeatsWhatIsStored() {
        RememberedLanguages.BY_USER.put("polly", Locale.GERMAN);

        assertEquals("fr", LocaleResolution.atHandshake(
                ENGLISH_DEPLOYMENT, "fr", null, null, "polly"),
                "somebody who has just picked a language on this machine gets what they picked");
    }

    @Test
    @DisplayName("a stored language nobody translated is ignored, not half-applied")
    void aStoredLanguageIsStillNarrowed() {
        RememberedLanguages.BY_USER.put("polly", Locale.JAPANESE);

        assertEquals("en", LocaleResolution.atHandshake(
                ENGLISH_DEPLOYMENT, null, null, null, "polly"),
                "this deployment has no Japanese, and half a screen in Japanese is worse than a "
                        + "whole one in English");
    }

    @Test
    @DisplayName("an anonymous connection is never asked about")
    void nobodyIsLookedUpForAnAnonymousConnection() {
        RememberedLanguages.BY_USER.put("polly", Locale.GERMAN);

        assertEquals("en", LocaleResolution.atHandshake(
                ENGLISH_DEPLOYMENT, null, null, null, null));
    }

    @Test
    @DisplayName("choosing a language tells the application, so the next computer knows")
    void choosingALanguageIsRemembered() {
        WasmRmiServerEngineTest.FakeSession session =
                new WasmRmiServerEngineTest.FakeSession("s1");
        session.getUserProperties().put(RmiEndpointConfigurator.LOCALE_KEY, "en");
        session.getUserProperties().put(RmiEndpointConfigurator.CLIENT_KEY, "browser-a");
        Principal polly = () -> "polly";
        session.getUserProperties().put(RmiEndpointConfigurator.PRINCIPAL_KEY, polly);
        runtime.addSessionForTesting(session);

        ServerSignalTransport.handleClientSet(Zeroz4jSignals.LOCALE_NAME, "de", session);

        assertEquals(Locale.GERMAN.getLanguage(),
                RememberedLanguages.BY_USER.get("polly").getLanguage());
    }

    @Test
    @DisplayName("an anonymous person's choice is not written to somebody's account")
    void anAnonymousChoiceIsNotStored() {
        WasmRmiServerEngineTest.FakeSession session =
                new WasmRmiServerEngineTest.FakeSession("s1");
        session.getUserProperties().put(RmiEndpointConfigurator.LOCALE_KEY, "en");
        session.getUserProperties().put(RmiEndpointConfigurator.CLIENT_KEY, "browser-a");
        runtime.addSessionForTesting(session);

        ServerSignalTransport.handleClientSet(Zeroz4jSignals.LOCALE_NAME, "de", session);

        assertNull(RememberedLanguages.BY_USER.get("polly"));
        assertEquals("de", session.getUserProperties().get(RmiEndpointConfigurator.LOCALE_KEY),
                "the cookie and the connection still carry it; only the account does not");
    }
}
