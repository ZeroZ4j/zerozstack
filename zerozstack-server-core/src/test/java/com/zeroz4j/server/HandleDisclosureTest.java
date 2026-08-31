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

import com.zeroz4j.api.BinaryRegistry;
import com.zeroz4j.api.BinarySerializer;
import com.zeroz4j.api.BinarySerializerDelegate;
import com.zeroz4j.api.GrowableBuffer;
import com.zeroz4j.api.ObjectMapper;
import com.zeroz4j.api.Scope;
import com.zeroz4j.api.SyncFrameTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who is allowed to re-read an object by naming its handle.
 *
 * <h2>What this protects</h2>
 * Every object goes on the wire with its handle attached, including objects nested inside a
 * broadcast event or a shared signal. So "presenting a handle proves you were sent the object" was
 * false: a client that received an outer payload learned the handles of everything inside it, and
 * could ask for those objects afterwards — including after its access had been taken away.
 *
 * <p>The server now writes down what it actually sent to whom and answers a re-read only from that
 * record. The record is kept per browser rather than per connection, because a reconnect is a new
 * connection: keying it by connection would leave it empty exactly when re-sync needs it, which
 * would break reconnection outright. That reconnection case is pinned here too.
 */
public class HandleDisclosureTest {

    public static class Doc {
        private String text;
        public Doc() { }
        public Doc(String text) { this.text = text; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }

    @BeforeAll
    public static void registerModel() {
        // Doc stands in for a @LiveSync model: it is synced and re-read by handle. The generated
        // registrar marks those, and only those get a handle that outlives the message.
        BinaryRegistry.registerHandleBearing(Doc.class.getName());
        BinaryRegistry.register(Doc.class.getName(), Doc::new,
                new BinarySerializerDelegate<Doc>() {
                    @Override public void write(Doc obj, GrowableBuffer buffer, ObjectMapper mapper) {
                        BinarySerializer.writeString(buffer, obj.getText() == null ? "" : obj.getText());
                    }
                    @Override public void read(Doc obj, ByteBuffer buffer, ObjectMapper mapper) {
                        obj.setText(BinarySerializer.readString(buffer));
                    }
                });
    }

    private WasmRmiServerEngine engine;

    /** One server per test: its record of what was sent starts empty and cannot leak into the next. */
    private ServerRuntime server;

    @BeforeEach
    public void setup() {
        server = new ServerRuntime();
        engine = new WasmRmiServerEngine();
        engine.injectedRuntime = server;
        engine.mapper = new ObjectMapper();
        engine.syncEngine = new SyncEngine();
        engine.syncEngine.mapper = engine.mapper;
        engine.syncEngine.runtime = server;
        Disclosures.install();
    }

    @AfterEach
    public void teardown() {
        server.shutDown();
    }

    /** A connection carrying a browser id, the way a real one does. */
    private WasmRmiServerEngineTest.FakeSession browserSession(String sessionId, String browserId) {
        WasmRmiServerEngineTest.FakeSession session =
                new WasmRmiServerEngineTest.FakeSession(sessionId);
        session.getUserProperties().put(RmiEndpointConfigurator.CLIENT_KEY, browserId);
        server.addSessionForTesting(session);
        server.disclosures().sessionOpened(session);
        return session;
    }

    private static int frames(WasmRmiServerEngineTest.FakeSession session) {
        return session.basic.sentBuffers().size();
    }

    @Test
    @DisplayName("a session that was never sent an object cannot re-read it")
    public void anUndisclosedHandleIsNotServed() {
        Doc doc = new Doc("confidential");
        String handle = engine.mapper.register(doc);

        WasmRmiServerEngineTest.FakeSession insider = browserSession("s1", "browser-insider");
        WasmRmiServerEngineTest.FakeSession outsider = browserSession("s2", "browser-outsider");
        engine.syncEngine.addSession(insider);

        // Only the insider is sent the object.
        engine.syncEngine.notifyChanged(doc, Scope.SESSION, "s1");
        assertEquals(1, frames(insider), "the insider was sent it");

        engine.handleResync(List.of(handle), outsider);

        assertEquals(0, frames(outsider),
                "a handle learned some other way must not fetch the object");
        assertFalse(server.disclosures().wasDisclosedToSession(outsider, handle),
                "and the record must say so, because that is what a lock check will read too");
    }

    @Test
    @DisplayName("a session that was sent an object can re-read it")
    public void aDisclosedHandleIsServed() {
        Doc doc = new Doc("hello");
        String handle = engine.mapper.register(doc);

        WasmRmiServerEngineTest.FakeSession holder = browserSession("s1", "browser-holder");
        engine.syncEngine.addSession(holder);
        engine.syncEngine.notifyChanged(doc, Scope.SESSION, "s1");
        assertTrue(server.disclosures().wasDisclosedToSession(holder, handle));

        doc.setText("hello again");
        holder.basic.sentBuffers().clear();
        engine.handleResync(List.of(handle), holder);

        assertEquals(1, frames(holder), "the object it holds is re-sent");
        ByteBuffer frame = holder.basic.sentBuffers().get(0);
        assertEquals(0, frame.getInt());
        assertEquals(SyncFrameTypes.SUBSCRIBE, frame.get());
        Doc received = (Doc) BinarySerializer.readValue(frame, new ObjectMapper());
        assertEquals("hello again", received.getText(), "and it carries current state");
    }

    @Test
    @DisplayName("re-sync still works across a reconnect: new connection, same browser")
    public void theRecordSurvivesAReconnect() {
        Doc doc = new Doc("kept");
        String handle = engine.mapper.register(doc);

        WasmRmiServerEngineTest.FakeSession first = browserSession("s1", "browser-A");
        engine.syncEngine.addSession(first);
        engine.syncEngine.notifyChanged(doc, Scope.SESSION, "s1");

        // The socket drops. A reconnect is a brand new session id; the browser id is the same,
        // because it lives in a cookie that outlives the connection.
        engine.syncEngine.removeSession("s1");
        server.disclosures().sessionClosed("s1");

        WasmRmiServerEngineTest.FakeSession reconnected = browserSession("s2", "browser-A");

        assertTrue(server.disclosures().wasDisclosedToSession(reconnected, handle),
                "keying the record by connection would make this false and break reconnection");
        engine.handleResync(List.of(handle), reconnected);
        assertEquals(1, frames(reconnected), "the reconnected client gets its objects back");
    }

    @Test
    @DisplayName("a different browser does not inherit the record")
    public void anotherBrowserDoesNotInheritTheRecord() {
        Doc doc = new Doc("kept");
        String handle = engine.mapper.register(doc);

        WasmRmiServerEngineTest.FakeSession first = browserSession("s1", "browser-A");
        engine.syncEngine.addSession(first);
        engine.syncEngine.notifyChanged(doc, Scope.SESSION, "s1");

        WasmRmiServerEngineTest.FakeSession other = browserSession("s2", "browser-B");
        assertFalse(server.disclosures().wasDisclosedToSession(other, handle));
        engine.handleResync(List.of(handle), other);
        assertEquals(0, frames(other));
    }

    /**
     * The exact shape of the hole: an object embedded in a broadcast event travels with its own
     * handle, so every recipient learns the handle. Learning it must not be enough.
     */
    @Test
    @DisplayName("a handle learned from someone else's payload is still refused")
    public void ahandleLearnedFromABroadcastIsNotEnough() {
        Doc secret = new Doc("not yours");
        String handle = engine.mapper.register(secret);

        WasmRmiServerEngineTest.FakeSession eavesdropper = browserSession("s9", "browser-E");

        // The eavesdropper knows the handle - it read it off a frame - but was never sent the object
        // itself by this server.
        engine.handleResync(List.of(handle), eavesdropper);

        assertEquals(0, frames(eavesdropper), "knowing the name is not being given the thing");
    }

    @Test
    @DisplayName("a connection with no browser id falls back to its own connection")
    public void aClientWithNoBrowserIdFallsBackToTheSession() {
        Doc doc = new Doc("hello");
        String handle = engine.mapper.register(doc);

        WasmRmiServerEngineTest.FakeSession headless =
                new WasmRmiServerEngineTest.FakeSession("s-headless");
        server.disclosures().sessionOpened(headless);
        engine.syncEngine.addSession(headless);
        engine.syncEngine.notifyChanged(doc, Scope.SESSION, "s-headless");

        assertTrue(server.disclosures().wasDisclosedToSession(headless, handle),
                "it still works within one connection");

        WasmRmiServerEngineTest.FakeSession afterReconnect =
                new WasmRmiServerEngineTest.FakeSession("s-headless-2");
        server.disclosures().sessionOpened(afterReconnect);
        assertFalse(server.disclosures().wasDisclosedToSession(afterReconnect, handle),
                "and such a client re-fetches after a reconnect instead of re-syncing, which is the "
                        + "documented trade for having no cookie");
    }

    @Test
    @DisplayName("the record is capped per browser")
    public void theRecordIsBounded() {
        System.setProperty(Disclosures.MAX_PER_CLIENT_PROPERTY, "5");
        try {
            server.disclosures().clear();
            WasmRmiServerEngineTest.FakeSession session = browserSession("s1", "browser-cap");
            for (int i = 0; i < 50; i++) {
                server.disclosures().record("s1", "handle-" + i);
            }
            assertEquals(5, server.disclosures().disclosedCount(session),
                    "an unbounded record would grow for the life of the process");
            assertTrue(server.disclosures().wasDisclosedToSession(session, "handle-49"), "the newest survive");
            assertFalse(server.disclosures().wasDisclosedToSession(session, "handle-0"), "the oldest are dropped");
        } finally {
            System.clearProperty(Disclosures.MAX_PER_CLIENT_PROPERTY);
        }
    }
}
