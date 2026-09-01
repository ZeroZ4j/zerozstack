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
import com.zeroz4j.api.SyncFrameTypes;
import com.zeroz4j.api.i18n.FrameworkKeys;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The browser is sent its words on the frame that already tells it to start.
 *
 * <h2>What this protects</h2>
 *
 * <p>The browser cannot read a file, so the only way it gets translated words is over the wire. It
 * has to have them at the moment the first screen is built, or the first screen is drawn in English
 * and corrected a moment later, which is the exact thing language support is meant to avoid. So
 * they ride on the AUTH frame, which is already sent to every connection and is already what tells
 * an application it may mount its user interface.</p>
 *
 * <p>That changed the frame's shape, so the version byte moved from 2 to 3, and the two halves of
 * <b>both</b> mismatches are asserted here. This project has made this mistake once already: before
 * version 2 a client read a refused connection as a successful sign-in, because the frame did not
 * say and the client guessed.</p>
 */
class CatalogOnTheAuthFrameTest {

    private WasmRmiServerEngine engine;

    @BeforeEach
    void setUp() {
        engine = new WasmRmiServerEngine();
        engine.injectedRuntime = new ServerRuntime();
        engine.mapper = new com.zeroz4j.api.ObjectMapper();
        engine.syncEngine = new SyncEngine();
        MessageCatalogs.forgetForTesting();
    }

    @Test
    @DisplayName("the words for the connection's language ride on the AUTH frame")
    void theAuthFrameCarriesTheCatalog() {
        Frame frame = openConnectionReading("de");

        assertEquals(3, frame.version, "the AUTH frame's version byte says which shape it is");
        assertEquals("de", frame.language, "the frame says which language its words are in");
        assertTrue(frame.offered.contains("de"),
                "the languages on offer travel too, so a selector can never offer one the server "
                        + "would refuse. Got: " + frame.offered);

        Map<String, String> app = frame.catalogs.get("i18n/probe");
        assertNotNull(app, "the application's own catalog must be on the frame. Got: "
                + frame.catalogs.keySet());
        assertEquals("Guten Tag", app.get("probe.greeting"),
                "the words must be the ones for this connection's language");

        Map<String, String> framework = frame.catalogs.get("i18n/zeroz4j");
        assertNotNull(framework, "the framework's own words travel on the same frame, so a "
                + "deployment that translates them gets translated buttons as well as sentences");
        assertTrue(framework.containsKey(FrameworkKeys.ACCESS_DENIED),
                "the framework catalog must carry its refusals");
    }

    @Test
    @DisplayName("a language with a gap in it falls back key by key, not file by file")
    void aTranslationWithAGapStillReads() {
        // probe_fr.properties has the greeting; the framework catalog has no French at all. The
        // frame must still carry every framework key, in English, rather than leaving them out and
        // showing a browser the key names.
        Frame frame = openConnectionReading("fr");

        assertEquals("Bonjour", frame.catalogs.get("i18n/probe").get("probe.greeting"));
        assertEquals("Access denied: requires role {0} but user has {1}",
                frame.catalogs.get("i18n/zeroz4j").get(FrameworkKeys.ACCESS_DENIED),
                "an untranslated key falls back to the language with no suffix. A missing key is a "
                        + "blank area on somebody's screen.");
    }

    @Test
    @DisplayName("a client that only knows version 2 reads the name and the roles and stops")
    void anOlderClientStillReadsTheFrame() {
        Frame frame = openConnectionReading("de");
        ByteBuffer bytes = frame.raw.duplicate();

        // Exactly what zerozstack-client did before 0.9.0: correlation, frame type, version,
        // authenticated flag, name, role count, roles - and nothing after that.
        assertEquals(0, bytes.getInt());
        assertEquals(SyncFrameTypes.AUTH, bytes.get());
        byte version = bytes.get();
        boolean authenticated = version >= 2 && bytes.get() != 0;
        String name = BinarySerializer.readString(bytes);
        int roleCount = bytes.getInt();
        Set<String> roles = new LinkedHashSet<>();
        for (int at = 0; at < roleCount; at++) {
            roles.add(BinarySerializer.readString(bytes));
        }

        assertFalse(authenticated, "an anonymous connection still says so");
        assertEquals("anonymous", name);
        assertTrue(roles.isEmpty());
        assertTrue(bytes.hasRemaining(),
                "the words are still there; an older client simply never looks at them, which is "
                        + "what makes adding them safe");
    }

    @Test
    @DisplayName("a newer client on an older server looks for no catalog and shows what it built in")
    void aNewerClientOnAnOlderServer() {
        assertTrue(WasmRmiServerEngine.AUTH_PROTOCOL_VERSION >= 3,
                "this server sends version 3 or later");

        // The other half of the mismatch lives in the browser, which cannot be run on a JVM at all,
        // so its source text is what is read. Without the version guard a 0.9.0 client on a 0.8.0
        // server would read past the end of the frame, throw, and report a broken connection where
        // the only thing actually missing is a translation.
        java.nio.file.Path client = repositoryRoot().resolve(java.nio.file.Paths.get(
                "zerozstack-client", "src", "main", "java", "com", "zeroz4j", "client",
                "WasmRmiClient.java"));
        assertTrue(java.nio.file.Files.isRegularFile(client), "expected to find " + client);
        String source;
        try {
            source = new String(java.nio.file.Files.readAllBytes(client),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException unreadable) {
            throw new java.io.UncheckedIOException(unreadable);
        }
        assertTrue(source.contains("if (protocolVersion >= 3) {"),
                "the client must read the catalog only when the version byte says it is there.");
        int guard = source.indexOf("if (protocolVersion >= 3) {");
        int read = source.indexOf("readCatalogBlock(buffer)", guard);
        int endOfGuard = source.indexOf("}", guard);
        assertTrue(read > guard && read < endOfGuard,
                "the catalog read has to be inside that guard, not merely after it.");
    }

    private static java.nio.file.Path repositoryRoot() {
        java.nio.file.Path here = java.nio.file.Paths.get("").toAbsolutePath();
        for (java.nio.file.Path at = here; at != null; at = at.getParent()) {
            if (java.nio.file.Files.isDirectory(at.resolve("zerozstack-client"))
                    && java.nio.file.Files.isRegularFile(at.resolve("pom.xml"))) {
                return at;
            }
        }
        throw new IllegalStateException("could not find the checkout root from " + here);
    }

    @Test
    @DisplayName("switching language sends the words on their own frame, before anything redraws")
    void switchingSendsTheWordsFirst() {
        WasmRmiServerEngineTest.FakeSession session = new WasmRmiServerEngineTest.FakeSession("s1");
        session.getUserProperties().put(RmiEndpointConfigurator.LOCALE_KEY, "en");

        WasmRmiServerEngine.sendCatalogFrame(session, "de");

        assertEquals(1, session.basic.sentBuffers().size());
        ByteBuffer sent = session.basic.sentBuffers().get(0);
        assertEquals(0, sent.getInt());
        assertEquals(SyncFrameTypes.CATALOG, sent.get(),
                "a frame of its own, so it can be written before the signal that makes the screen "
                        + "redraw. Reversed, every label would redraw once in the old language "
                        + "under the new language's name and again a moment later.");
        assertEquals("de", BinarySerializer.readString(sent));
    }

    // ---------------------------------------------------------------- reading the frame

    /** The AUTH frame the server writes to a connection resolved to one language. */
    private Frame openConnectionReading(String language) {
        WasmRmiServerEngineTest.FakeSession session = new WasmRmiServerEngineTest.FakeSession("s1");
        jakarta.websocket.EndpointConfig config = new jakarta.websocket.EndpointConfig() {
            private final Map<String, Object> properties = new LinkedHashMap<>();
            @Override public Map<String, Object> getUserProperties() { return properties; }
            @Override public java.util.List<Class<? extends jakarta.websocket.Encoder>> getEncoders() {
                return java.util.Collections.emptyList();
            }
            @Override public java.util.List<Class<? extends jakarta.websocket.Decoder>> getDecoders() {
                return java.util.Collections.emptyList();
            }
        };
        config.getUserProperties().put(RmiEndpointConfigurator.LOCALE_KEY, language);
        config.getUserProperties().put(RmiEndpointConfigurator.ROLES_KEY,
                java.util.Collections.emptySet());

        engine.onOpen(session, config);

        ByteBuffer auth = null;
        for (ByteBuffer sent : session.basic.sentBuffers()) {
            ByteBuffer look = sent.duplicate();
            look.getInt();
            if (look.get() == SyncFrameTypes.AUTH) {
                auth = sent.duplicate();
                break;
            }
        }
        assertNotNull(auth, "every connection is sent an AUTH frame, authenticated or not");
        return Frame.read(auth);
    }

    /** One AUTH frame, taken apart the way the browser takes it apart. */
    private static final class Frame {
        ByteBuffer raw;
        byte version;
        String language;
        java.util.List<String> offered = new java.util.ArrayList<>();
        Map<String, Map<String, String>> catalogs = new LinkedHashMap<>();

        static Frame read(ByteBuffer bytes) {
            Frame frame = new Frame();
            frame.raw = bytes.duplicate();
            bytes.getInt();
            bytes.get();                            // frame type
            frame.version = bytes.get();
            bytes.get();                            // authenticated flag
            BinarySerializer.readString(bytes);     // user name
            int roleCount = bytes.getInt();
            for (int at = 0; at < roleCount; at++) {
                BinarySerializer.readString(bytes);
            }
            if (frame.version < 3) {
                return frame;
            }
            frame.language = BinarySerializer.readString(bytes);
            int languageCount = bytes.getInt();
            for (int at = 0; at < languageCount; at++) {
                frame.offered.add(BinarySerializer.readString(bytes));
            }
            int catalogCount = bytes.getInt();
            for (int at = 0; at < catalogCount; at++) {
                String name = BinarySerializer.readString(bytes);
                int entryCount = bytes.getInt();
                Map<String, String> words = new LinkedHashMap<>();
                for (int entry = 0; entry < entryCount; entry++) {
                    words.put(BinarySerializer.readString(bytes),
                            BinarySerializer.readString(bytes));
                }
                frame.catalogs.put(name, words);
            }
            return frame;
        }
    }
}
