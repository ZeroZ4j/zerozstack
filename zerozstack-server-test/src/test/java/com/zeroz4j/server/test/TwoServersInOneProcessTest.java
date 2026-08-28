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
package com.zeroz4j.server.test;

import com.zeroz4j.api.BinaryRegistry;
import com.zeroz4j.api.BinarySerializer;
import com.zeroz4j.api.BinarySerializerDelegate;
import com.zeroz4j.api.EventTopic;
import com.zeroz4j.api.GrowableBuffer;
import com.zeroz4j.api.LiveMutexRpc;
import com.zeroz4j.api.ObjectMapper;
import com.zeroz4j.server.RmiRequestContext;
import com.zeroz4j.server.ServerSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The test this whole release exists to make possible: <b>two servers in one Java process, driven at
 * the same time, each with its own state and its own settings.</b>
 *
 * <h2>What went wrong before</h2>
 *
 * <p>The framework kept some of its state in fields belonging to the whole process — the list of
 * open connections above all. So a second server started beside the first shared the first one's
 * connections. An application that hit this had to run one test per process, and, before it worked
 * out why, two of its three browser tests <b>passed while asserting nothing</b>: they were watching
 * a connection somebody really was writing to, just not the server under test.</p>
 *
 * <p>Run against the code before this change, the first test below fails: a broadcast from the
 * second server arrives at the first server's connection, so the connection counts two frames where
 * it should count one. The rest could not be written at all, because there was no per-server
 * anything to write them against.</p>
 */
class TwoServersInOneProcessTest {

    private static final EventTopic<String> NOTICE = EventTopic.of(String.class, "test.notice");

    /** Something with a name of its own on the wire, so "was this sent to that browser" has meaning. */
    public static class Doc {
        private String text;

        public Doc() {
        }

        public Doc(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }

    @BeforeAll
    static void registerModel() {
        BinaryRegistry.register(Doc.class.getName(), Doc::new,
                new BinarySerializerDelegate<Doc>() {
                    @Override
                    public void write(Doc obj, GrowableBuffer buffer, ObjectMapper mapper) {
                        BinarySerializer.writeString(buffer, obj.getText() == null ? "" : obj.getText());
                    }

                    @Override
                    public void read(Doc obj, ByteBuffer buffer, ObjectMapper mapper) {
                        obj.setText(BinarySerializer.readString(buffer));
                    }
                });
    }

    @AfterEach
    void clearCallerIdentity() {
        RmiRequestContext.clear();
    }

    /** Binds this thread to a connection, the way frame dispatch does before calling a service. */
    private static void callingOn(TestConnection connection) {
        RmiRequestContext.setContext(null, Collections.emptySet(), connection.id(),
                null, connection.browserId());
    }

    // ================================================================= connections

    @Test
    @DisplayName("an event published on one server does not reach the other server's browser")
    void connectionsBelongToOneServer() {
        try (TestServer alpha = TestServer.builder().named("alpha").start();
             TestServer beta = TestServer.builder().named("beta").start();
             TestConnection onAlpha = alpha.connect();
             TestConnection onBeta = beta.connect()) {

            assertEquals(1, alpha.connectionCount(), "alpha has one connection of its own");
            assertEquals(1, beta.connectionCount(), "beta has one connection of its own");
            assertFalse(alpha.runtime().hasSession(onBeta),
                    "alpha must not hold beta's connection");

            alpha.events().publish(NOTICE, "for alpha only");

            assertEquals(1, onAlpha.pushCount(), "alpha's browser gets alpha's event");
            assertEquals(0, onBeta.pushCount(),
                    "beta's browser must not receive an event published on alpha");

            beta.events().publish(NOTICE, "for beta only");

            assertEquals(1, onAlpha.pushCount(), "alpha's browser is not disturbed by beta");
            assertEquals(1, onBeta.pushCount(), "beta's browser gets beta's event");
        }
    }

    // ================================================================= settings

    @Test
    @DisplayName("two servers apply two different message-size limits at the same time")
    void eachServerHasItsOwnSettings() {
        try (TestServer small = TestServer.builder()
                     .named("small")
                     .ignoringSystemProperties()
                     .set(ServerSettings.MAX_BINARY_MESSAGE_BYTES, 1024)
                     .start();
             TestServer large = TestServer.builder()
                     .named("large")
                     .ignoringSystemProperties()
                     .set(ServerSettings.MAX_BINARY_MESSAGE_BYTES, 65536)
                     .start();
             TestConnection onSmall = small.connect();
             TestConnection onLarge = large.connect()) {

            assertEquals(1024, onSmall.getMaxBinaryMessageBufferSize(),
                    "the small server's limit is applied to its own connection");
            assertEquals(65536, onLarge.getMaxBinaryMessageBufferSize(),
                    "the large server's limit is applied to its own connection");
            assertNotEquals(onSmall.getMaxBinaryMessageBufferSize(),
                    onLarge.getMaxBinaryMessageBufferSize(),
                    "two servers in one process must be able to disagree about a limit");

            assertEquals("1024", small.config().get(ServerSettings.MAX_BINARY_MESSAGE_BYTES));
            assertEquals("65536", large.config().get(ServerSettings.MAX_BINARY_MESSAGE_BYTES));
        }
    }

    @Test
    @DisplayName("a server told to ignore the process's settings does exactly that")
    void isolatedSettingsIgnoreTheJvm() {
        System.setProperty(ServerSettings.LIVE_MUTEX_WAIT_SECONDS, "7");
        try (TestServer ignoring = TestServer.builder()
                     .named("ignoring")
                     .ignoringSystemProperties()
                     .start();
             TestServer following = TestServer.builder().named("following").start()) {

            assertNull(ignoring.config().get(ServerSettings.LIVE_MUTEX_WAIT_SECONDS),
                    "this server was told the process's settings do not apply to it");
            assertEquals("7", following.config().get(ServerSettings.LIVE_MUTEX_WAIT_SECONDS),
                    "this one still reads them, which is what every existing deployment relies on");
        } finally {
            System.clearProperty(ServerSettings.LIVE_MUTEX_WAIT_SECONDS);
        }
    }

    // ================================================= what was sent, and what may be locked

    @Test
    @DisplayName("an object one server sent cannot be read back, or locked, on the other")
    void whatOneServerSentIsNotTheOtherServersToGive() {
        try (TestServer alpha = TestServer.builder().named("alpha").start();
             TestServer beta = TestServer.builder().named("beta").start();
             TestConnection onAlpha = alpha.connect();
             TestConnection onBeta = beta.connect()) {

            // Alpha sends a document to its own browser, through the ordinary LiveSync path. That is
            // what writes the object's name down as disclosed to that browser.
            Doc doc = new Doc("alpha's document");
            String handle = alpha.mapper().register(doc);
            alpha.sync().notifyChanged(doc);

            assertTrue(alpha.disclosures().wasDisclosedToSession(onAlpha, handle),
                    "alpha remembers sending it");
            assertFalse(beta.disclosures().wasDisclosedToSession(onBeta, handle),
                    "beta was not the one that sent it, and must not answer for it");

            // Alpha's browser may lock what alpha sent it.
            callingOn(onAlpha);
            alpha.bean(LiveMutexRpc.class).acquireLock(handle);
            assertEquals("session:" + onAlpha.id(), alpha.locks().ownerOf(handle),
                    "alpha's browser holds alpha's lock");

            // Beta's browser may not: beta never sent it that object.
            callingOn(onBeta);
            SecurityException refused = assertThrows(SecurityException.class,
                    () -> beta.bean(LiveMutexRpc.class).acquireLock(handle));
            assertTrue(refused.getMessage().contains("never sent"),
                    "the refusal must say why: " + refused.getMessage());

            assertNull(beta.locks().ownerOf(handle),
                    "the lock alpha's browser holds does not exist on beta");
        }
    }

    @Test
    @DisplayName("a lock held on one server does not hold anybody up on the other")
    void locksDoNotCrossServers() {
        try (TestServer alpha = TestServer.builder().named("alpha").start();
             TestServer beta = TestServer.builder().named("beta").start()) {

            alpha.locks().lock("shared-name", "thread:one");

            assertEquals("thread:one", alpha.locks().ownerOf("shared-name"));
            assertNull(beta.locks().ownerOf("shared-name"),
                    "the same name on the other server is a different lock");

            // Taking it on beta returns at once. If the two servers shared a lock table this would
            // sit here for the whole configured wait and then throw.
            beta.locks().lock("shared-name", "thread:two");
            assertEquals("thread:two", beta.locks().ownerOf("shared-name"));

            alpha.locks().unlock("shared-name", "thread:one");
            beta.locks().unlock("shared-name", "thread:two");
        }
    }

    // ================================================================= loud failure

    @Test
    @DisplayName("driving one server's connection against the other fails, rather than passing")
    void theWrongInstanceIsRefused() {
        try (TestServer alpha = TestServer.builder().named("alpha").start();
             TestServer beta = TestServer.builder().named("beta").start();
             TestConnection onAlpha = alpha.connect()) {

            IllegalStateException wrongServer = assertThrows(IllegalStateException.class,
                    () -> beta.engine().sendPush(onAlpha, "test.notice", "wrong server"));

            assertTrue(wrongServer.getMessage().contains("alpha")
                            && wrongServer.getMessage().contains("beta"),
                    "the complaint must name both servers: " + wrongServer.getMessage());
        }
    }

    @Test
    @DisplayName("a connection nobody opened is refused, not quietly ignored")
    void anUnopenedConnectionIsRefused() {
        try (TestServer alpha = TestServer.builder().named("alpha").start()) {
            TestConnection strayConnection = alpha.connect();
            alpha.closeConnection(strayConnection);

            IllegalStateException orphan = assertThrows(IllegalStateException.class,
                    () -> com.zeroz4j.server.ServerRuntime.of(strayConnection));

            assertTrue(orphan.getMessage().contains("does not belong to any running"),
                    "the complaint must say what is wrong: " + orphan.getMessage());
        }
    }

    @Test
    @DisplayName("a closed server refuses to be driven any further")
    void aClosedServerRefusesToWork() {
        TestServer alpha = TestServer.builder().named("alpha").start();
        TestConnection onAlpha = alpha.connect();
        alpha.close();

        assertFalse(onAlpha.isOpen(), "closing the server closes its connections");

        IllegalStateException closed = assertThrows(IllegalStateException.class,
                () -> alpha.events().publish(NOTICE, "too late"));
        assertTrue(closed.getMessage().contains("closed") || closed.getMessage().contains("shut down"),
                "the complaint must say the server is gone: " + closed.getMessage());
    }

    // ================================================================= the ordinary case still works

    @Test
    @DisplayName("one server on its own behaves exactly as before")
    void oneServerStillWorks() {
        try (TestServer server = TestServer.start();
             TestConnection browser = server.connect("alice", "admin")) {

            assertNotNull(browser.browserId(), "every connection carries a browser id");
            assertEquals(1, browser.countOf(TestConnection.AUTH),
                    "a connection is told who the server decided it is");

            server.events().publish(NOTICE, "hello");
            assertEquals(1, browser.pushCount());

            Set<String> roles = castRoles(browser.getUserProperties().get("zeroz.roles"));
            assertTrue(roles.contains("admin"), "the roles the test asked for are on the connection");
        }
    }

    @SuppressWarnings("unchecked")
    private static Set<String> castRoles(Object roles) {
        return (Set<String>) roles;
    }
}
