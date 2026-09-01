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

import com.zeroz4j.api.BinarySerializer;
import com.zeroz4j.api.ObjectMapper;
import com.zeroz4j.api.SyncFrameTypes;
import com.zeroz4j.signals.Zeroz4jSignals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Somebody picks a language, and the server puts them in it.
 *
 * <h2>What this protects</h2>
 *
 * <p>Two orderings and one refusal.</p>
 *
 * <p><b>The words go before the value.</b> The value is what makes every label on the screen redraw
 * itself, so if it arrived first the whole screen would redraw once against the language on its way
 * out and again a moment later. Frames on one connection are handled in the order they were
 * written, so writing them in this order is the whole of the fix.</p>
 *
 * <p><b>A language nobody translated is refused.</b> A selector only offers what the server has, so
 * this cannot happen through the interface - but a client can send anything, and half a screen in
 * one language is worse than a whole screen in another.</p>
 *
 * <p><b>Every tab of the same browser moves together.</b> The language belongs to the browser, not
 * to one tab.</p>
 */
class LanguageSwitchTest {

    private ServerRuntime runtime;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        runtime = new ServerRuntime();
        mapper = new ObjectMapper();
        MessageCatalogs.forgetForTesting();
        LocaleResolution.resetPreferenceStoreForTesting();
        ServerSignalTransport.install(runtime, mapper);
    }

    @AfterEach
    void tearDown() {
        ServerSignalTransport.uninstall(runtime);
        LocaleResolution.resetPreferenceStoreForTesting();
        runtime.shutDown();
    }

    @Test
    @DisplayName("choosing a language sends the words first and the value second")
    void theWordsGoFirst() {
        WasmRmiServerEngineTest.FakeSession session = connection("s1", "en", "browser-a");

        ServerSignalTransport.handleClientSet(Zeroz4jSignals.LOCALE_NAME, "de", session);

        List<Byte> kinds = frameKinds(session);
        assertEquals(2, kinds.size(), "two frames: the words, then the value");
        assertEquals(SyncFrameTypes.CATALOG, kinds.get(0).byteValue(),
                "the words must go first, or every label redraws twice");
        assertEquals(SyncFrameTypes.SIGNAL_UPD, kinds.get(1).byteValue());

        assertEquals("de", session.getUserProperties().get(RmiEndpointConfigurator.LOCALE_KEY),
                "the connection remembers it, so every later call is answered in it too");
    }

    @Test
    @DisplayName("a language this deployment has no words for is refused and nothing moves")
    void anUntranslatedLanguageIsRefused() {
        WasmRmiServerEngineTest.FakeSession session = connection("s1", "en", "browser-a");

        ServerSignalTransport.handleClientSet(Zeroz4jSignals.LOCALE_NAME, "xx", session);

        List<Byte> kinds = frameKinds(session);
        assertEquals(1, kinds.size(), "one frame: the correction, and no words");
        assertEquals(SyncFrameTypes.SIGNAL_UPD, kinds.get(0).byteValue());
        assertEquals("en", session.getUserProperties().get(RmiEndpointConfigurator.LOCALE_KEY),
                "the connection stays where it was rather than being moved somewhere nobody asked "
                        + "for");
    }

    @Test
    @DisplayName("both tabs of one browser change together")
    void everyTabOfTheSameBrowserMoves() {
        WasmRmiServerEngineTest.FakeSession first = connection("s1", "en", "browser-a");
        WasmRmiServerEngineTest.FakeSession second = connection("s2", "en", "browser-a");
        WasmRmiServerEngineTest.FakeSession somebodyElse = connection("s3", "en", "browser-b");

        ServerSignalTransport.handleClientSet(Zeroz4jSignals.LOCALE_NAME, "de", first);

        assertEquals("de", first.getUserProperties().get(RmiEndpointConfigurator.LOCALE_KEY));
        assertEquals("de", second.getUserProperties().get(RmiEndpointConfigurator.LOCALE_KEY),
                "the language belongs to the browser, not to the tab that was clicked in");
        assertEquals("en", somebodyElse.getUserProperties().get(RmiEndpointConfigurator.LOCALE_KEY),
                "and to that browser only");
        assertTrue(somebodyElse.basic.sentBuffers().isEmpty(),
                "another browser is not written to at all");
    }

    @Test
    @DisplayName("subscribing to the language is answered from the connection, not from a store")
    void subscribingReadsTheConnection() {
        WasmRmiServerEngineTest.FakeSession session = connection("s1", "pt-br", "browser-a");

        ServerSignalTransport.handleSubscribe(Zeroz4jSignals.LOCALE_NAME, session);

        assertEquals(1, session.basic.sentBuffers().size());
        ByteBuffer sent = session.basic.sentBuffers().get(0);
        sent.getInt();
        assertEquals(SyncFrameTypes.SIGNAL_UPD, sent.get());
        assertEquals(Zeroz4jSignals.LOCALE_NAME, BinarySerializer.readString(sent));
        assertEquals("pt-br", BinarySerializer.readValue(sent, mapper),
                "the connection already holds the language and is answered in it. Keeping a second "
                        + "copy in a signal would be two things to keep in step.");
    }

    // ---------------------------------------------------------------- helpers

    private WasmRmiServerEngineTest.FakeSession connection(String id, String language,
                                                           String browser) {
        WasmRmiServerEngineTest.FakeSession session = new WasmRmiServerEngineTest.FakeSession(id);
        session.getUserProperties().put(RmiEndpointConfigurator.LOCALE_KEY, language);
        session.getUserProperties().put(RmiEndpointConfigurator.CLIENT_KEY, browser);
        runtime.addSessionForTesting(session);
        return session;
    }

    private static List<Byte> frameKinds(WasmRmiServerEngineTest.FakeSession session) {
        List<Byte> kinds = new ArrayList<>();
        for (ByteBuffer sent : session.basic.sentBuffers()) {
            ByteBuffer look = sent.duplicate();
            look.getInt();
            kinds.add(Byte.valueOf(look.get()));
        }
        return kinds;
    }
}
