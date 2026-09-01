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
import com.zeroz4j.example.api.RegistrationService;
import com.zeroz4j.example.model.Registration;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The point this example is about, checked where it actually has to hold: at the server's edge.
 *
 * <p>{@code RegistrationValidationTest} beside this file calls the generated rules directly. That
 * is worth having — it says the constraints on the model mean what they look like they mean — but
 * it does not answer the question this example exists to raise, which is whether a browser that
 * ignores the rules gets anywhere. Client-side validation is feedback, never a boundary; the
 * boundary is the server, and a boundary nobody has pushed on is a guess.</p>
 *
 * <p>So these tests do not call the service. They build the bytes a browser sends, put them on a
 * connection, and read the answer — with a registration the rules forbid, and with one they
 * allow.</p>
 *
 * <p>The harness is {@code zerozstack-server-test}, taken with <b>test scope</b> because it starts
 * a bean container and must never reach a production classpath. See
 * {@code docs/guides/testing.md}.
 */
class RegistrationOverTheWireTest {

    /** A directory JUnit deletes afterwards. The harness starts no store; this test brings one. */
    @TempDir
    Path storeDir;

    private static ZeroZDbNode node;

    private TestServer server;
    private TestConnection browser;
    private final AtomicInteger messageIds = new AtomicInteger(1);

    /**
     * Gives the server the database node the service injects.
     *
     * <p>Nothing is discovered by scanning inside a {@code TestServer}, so anything a service needs
     * is named in {@code beans(...)} — including a small producer like this one.
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
                .named("signup")
                .beans(RegistrationServiceImpl.class, TestStore.class)
                .start();
        browser = server.connect();
    }

    @AfterEach
    void stopTheServer() {
        if (browser != null) {
            browser.close();
        }
        if (server != null) {
            server.close();
        }
        if (node != null) {
            node.close();
            node = null;
        }
    }

    @Test
    @DisplayName("a registration that keeps the rules is accepted and stored")
    void aGoodRegistrationGoesThrough() {
        Registration valid = new Registration(0L, "Jane Doe", "jane.doe@example.com", 8, "M", true,
                "Senior Java developer");

        callOverTheWire("register", valid);

        assertEquals(1, server.bean(RegistrationService.class).listRegistrations().size(),
                "it was stored");
        assertEquals("jane.doe@example.com",
                server.bean(RegistrationService.class).listRegistrations().get(0).getEmail());
    }

    @Test
    @DisplayName("a browser that ignores the rules is stopped at the server")
    void abadRegistrationIsRefusedBeforeTheServiceRuns() {
        // Every field here breaks its constraint. A browser could send exactly this: the rules on
        // the screen are the same rules, but nothing stops somebody driving the socket by hand.
        Registration invalid = new Registration(0L, "A", "a", 99, "", false, "x".repeat(450));

        String refusal = errorFrom("register", invalid);

        assertTrue(refusal.toLowerCase().contains("valid") || refusal.contains("fullName"),
                "the refusal names what was wrong: " + refusal);
        assertEquals(0, server.bean(RegistrationService.class).listRegistrations().size(),
                "and nothing was stored - the service never ran");
    }

    // ------------------------------------------------------------------ driving the wire

    /**
     * Makes one call the way a browser makes it, and fails the test if the server refuses.
     *
     * @param method the method name
     * @param args   its arguments
     */
    private void callOverTheWire(String method, Object... args) {
        ByteBuffer answer = ByteBuffer.wrap(sendAndAwaitAnswer(method, args));
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
     * @param method the method name
     * @param args   its arguments
     * @return the sentence the server sent back
     */
    private String errorFrom(String method, Object... args) {
        ByteBuffer answer = ByteBuffer.wrap(sendAndAwaitAnswer(method, args));
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
     * handled on another thread, so the answer arrives a moment after {@code send} returns, and it
     * is matched by the number the request was sent under rather than by being the last frame.
     *
     * @param method the method name
     * @param args   its arguments
     * @return the answer frame
     */
    private byte[] sendAndAwaitAnswer(String method, Object... args) {
        int messageId = messageIds.getAndIncrement();
        GrowableBuffer request = new GrowableBuffer();
        request.putInt(messageId);
        BinarySerializer.writeString(request, RegistrationService.class.getName());
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
            Thread.onSpinWait();
        }
        throw new AssertionError("the server did not answer " + method + " within five seconds");
    }
}
