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
package com.zeroz4j.example.server;

import com.zeroz4j.api.BinarySerializer;
import com.zeroz4j.api.GrowableBuffer;
import com.zeroz4j.db.net.ZeroZDbNode;
import com.zeroz4j.example.api.ChatService;
import com.zeroz4j.example.model.ChatMessage;
import com.zeroz4j.example.server.store.DataRoot;
import com.zeroz4j.server.test.TestConnection;
import com.zeroz4j.server.test.TestServer;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The chat service, exercised through real connections rather than by calling the bean.
 *
 * <p>What this replaced is worth writing down. This file used to hold one test that asserted
 * {@code true} and a comment wondering how the service might be given a store. It passed on every
 * build and proved nothing — the same shape of failure this framework keeps finding, where nothing
 * happening and everything working look identical.</p>
 *
 * <p>A {@link TestServer} makes the real thing cheap. It starts a whole server in this process in
 * about a tenth of a second, {@link TestConnection} stands in for a browser, and the three
 * properties this example actually depends on can be checked for real:</p>
 *
 * <ul>
 *   <li>a message sent by one person is pushed to <em>every</em> open browser, which is what
 *       LiveSync is for;</li>
 *   <li>clearing the history needs the admin role, and a connection without it is refused;</li>
 *   <li>a visitor who never signed in is refused at every call, because the service is
 *       {@code @Secured}.</li>
 * </ul>
 *
 * <p>The harness comes from {@code zerozstack-server-test}, which the pom takes with <b>test
 * scope</b>: it starts a bean container and must never reach a production classpath. See
 * {@code docs/guides/testing.md}.
 */
class ChatServiceImplTest {

    /**
     * The store this test's server writes to.
     *
     * <p>The harness starts no store — that is the application's to arrange, because only the
     * application knows what it needs. Here it is an ordinary embedded node in a directory JUnit
     * deletes afterwards, handed to the server by the producer below.
     */
    @TempDir
    Path storeDir;

    private static ZeroZDbNode node;

    private TestServer server;
    private final AtomicInteger messageIds = new AtomicInteger(1);

    /**
     * A bean that gives the server its database node.
     *
     * <p>Discovery is off inside a {@code TestServer}, so a bean only exists if the test names it.
     * This is how anything a service injects gets there: write the producer, and list its class in
     * {@code beans(...)} beside the service.
     */
    public static class TestStore {

        /** @return the node this test opened */
        @Produces
        @Dependent
        public ZeroZDbNode node() {
            return node;
        }
    }

    @BeforeEach
    void startTheServer() {
        node = ZeroZDbNode.embedded(storeDir.resolve("store"), DataRoot::new);
        server = TestServer.builder()
                .named("chat")
                .beans(ChatServiceImpl.class, TestStore.class)
                .start();
    }

    @AfterEach
    void stopTheServer() {
        if (server != null) {
            server.close();
        }
        if (node != null) {
            node.close();
            node = null;
        }
    }

    @Test
    @DisplayName("a message one person sends is pushed to every open browser")
    void everyBrowserIsToldAboutANewMessage() {
        try (TestConnection alice = server.connect("alice", "user");
             TestConnection bob = server.connect("bob", "user")) {

            // Both browsers have to have been sent the state object before a change to it means
            // anything to them: an object gets its handle by going over the wire, and that handle
            // is what a later change is addressed to. That call is the one thing a LiveSync view
            // does on startup, and it has to be made the same way here - through the connection.
            // Calling the bean directly would return the object to this test and register nothing.
            callOverTheWire(alice, "getState");
            callOverTheWire(bob, "getState");

            callOverTheWire(alice, "sendMessage", "Anyone for coffee?");

            assertEquals(1, alice.countOf(TestConnection.OBJECT_UPDATE),
                    "the sender's own browser is told");
            assertEquals(1, bob.countOf(TestConnection.OBJECT_UPDATE),
                    "and so is everybody else's - that is the whole point of LiveSync");
        }
    }

    @Test
    @DisplayName("the message really is added, with the caller's name on it")
    void aMessageKeepsWhoSentIt() {
        try (TestConnection alice = server.connect("alice", "user")) {
            callOverTheWire(alice, "getState");
            callOverTheWire(alice, "sendMessage", "Anyone for coffee?");

            List<ChatMessage> messages = server.bean(ChatService.class).getState().getMessages();
            assertEquals(1, messages.size(), "the message was added");
            assertEquals("Anyone for coffee?", messages.get(0).getText());
            assertEquals("alice", messages.get(0).getSender(),
                    "the sender came from the connection, not from an argument");
            assertNotEquals(0, messages.get(0).getTimestamp());
        }
    }

    @Test
    @DisplayName("clearing the history needs the admin role, and says so when it is missing")
    void onlyAnAdminMayClearTheHistory() {
        try (TestConnection demo = server.connect("demo", "user");
             TestConnection admin = server.connect("admin", "user", "admin")) {

            callOverTheWire(admin, "getState");
            callOverTheWire(admin, "sendMessage", "Anyone for coffee?");
            assertEquals(1, server.bean(ChatService.class).getState().getMessages().size());

            String refusal = errorFrom(demo, "clearHistory");
            assertTrue(refusal.toLowerCase().contains("denied")
                            || refusal.toLowerCase().contains("role")
                            || refusal.toLowerCase().contains("access"),
                    "the refusal says the caller may not do this: " + refusal);
            assertEquals(1, server.bean(ChatService.class).getState().getMessages().size(),
                    "and nothing was cleared");

            callOverTheWire(admin, "clearHistory");
            assertEquals(0, server.bean(ChatService.class).getState().getMessages().size(),
                    "an admin may");
        }
    }

    @Test
    @DisplayName("somebody who never signed in is refused at every call")
    void anAnonymousVisitorIsRefused() {
        try (TestConnection visitor = server.connect()) {
            String refusal = errorFrom(visitor, "getState");
            assertTrue(refusal.toLowerCase().contains("authenticat")
                            || refusal.toLowerCase().contains("denied"),
                    "the service is @Secured, so this must be refused: " + refusal);
        }
    }

    // ------------------------------------------------------------------ driving the wire

    /**
     * Makes one call the way a browser makes it, and fails the test if the server refuses.
     *
     * @param browser the connection to call on
     * @param method  the method name
     * @param args    its arguments
     */
    private void callOverTheWire(TestConnection browser, String method, Object... args) {
        ByteBuffer answer = ByteBuffer.wrap(sendAndAwaitAnswer(browser, method, args));
        answer.getInt();
        byte opcode = answer.get();
        if (opcode == 0x0F) {
            throw new AssertionError("the server refused " + method + ": "
                    + BinarySerializer.readString(answer));
        }
        assertEquals(0x01, opcode, "a successful RMI answer to " + method);
    }

    /**
     * Makes one call that is expected to be refused, and hands back the reason.
     *
     * @param browser the connection to call on
     * @param method  the method name
     * @param args    its arguments
     * @return the sentence the server sent back
     */
    private String errorFrom(TestConnection browser, String method, Object... args) {
        ByteBuffer answer = ByteBuffer.wrap(sendAndAwaitAnswer(browser, method, args));
        answer.getInt();
        assertEquals(0x0F, answer.get(), "an error frame was expected from " + method);
        return BinarySerializer.readString(answer);
    }

    /**
     * Writes the frame a browser writes for an RMI call, puts it on the connection, and waits for
     * the answer.
     *
     * <p>Its shape is in {@code docs/PROTOCOL.md}: a message number, the interface name, the method
     * name, how many arguments there are, then the arguments. A frame a browser sends is queued and
     * handled on another thread, so the answer arrives a moment after {@code send} returns —
     * calling a bean through {@code server.bean(...)} needs none of this waiting.
     *
     * @param browser the connection
     * @param method  the method name
     * @param args    its arguments
     * @return the reply frame
     */
    private byte[] sendAndAwaitAnswer(TestConnection browser, String method, Object... args) {
        int messageId = messageIds.getAndIncrement();
        GrowableBuffer request = new GrowableBuffer();
        request.putInt(messageId);
        BinarySerializer.writeString(request, ChatService.class.getName());
        BinarySerializer.writeString(request, method);
        request.putInt(args == null ? 0 : args.length);
        if (args != null) {
            for (Object arg : args) {
                BinarySerializer.writeValue(request, arg, server.mapper());
            }
        }

        browser.clearFrames();
        browser.send(request.toByteArray());

        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            byte[] answer = answerTo(browser, messageId);
            if (answer != null) {
                return answer;
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("the server did not answer " + method + " within five seconds");
    }

    /**
     * Picks this call's answer out of everything the server has written.
     *
     * <p>The answer is not necessarily the last frame, and taking the last one is a real mistake:
     * a call that changes a synced object is followed by the object update it caused, which arrives
     * on the same connection a moment later. A browser tells them apart by the number it put on the
     * front of its request and by the opcode, and so does this.
     *
     * @param browser   the connection
     * @param messageId the number this call was sent under
     * @return the answer frame, or null if it has not arrived yet
     */
    private static byte[] answerTo(TestConnection browser, int messageId) {
        for (byte[] frame : browser.frames()) {
            if (frame.length < 5) {
                continue;
            }
            ByteBuffer header = ByteBuffer.wrap(frame);
            if (header.getInt() == messageId) {
                byte opcode = header.get();
                if (opcode == 0x01 || opcode == 0x0F) {
                    return frame;
                }
            }
        }
        return null;
    }
}
