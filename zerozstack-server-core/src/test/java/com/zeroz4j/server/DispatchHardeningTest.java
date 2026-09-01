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
import com.zeroz4j.api.RmiService;
import com.zeroz4j.api.SyncFrameTypes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.weld.junit5.EnableWeld;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Four properties of the frame dispatcher that are not about any one feature.
 *
 * <ul>
 *   <li>The caller's identity is bound to the thread <b>before</b> the frame is decoded, so
 *       application code reached during decoding can see who is calling.</li>
 *   <li>An unplanned failure is not described to the caller, and the code the caller is given
 *       appears in the server log so the two can be matched.</li>
 *   <li>Keepalive pings are answered, but a flood of them is not.</li>
 *   <li>One connection can only have so many frames being decoded at once, and a connection that
 *       exceeds that does not slow anybody else down.</li>
 * </ul>
 */
@EnableWeld
public class DispatchHardeningTest {

    /** Records who was calling at the moment its fields were being read off the wire. */
    public static class Probe {
        public static volatile String principalSeenDuringDecode = "<not decoded>";
        private String text;
        public Probe() { }
        public Probe(String text) { this.text = text; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }

    @RmiService
    public interface HardeningService {
        String takeProbe(Probe probe);

        String blockUntilReleased();

        String quick();
    }

    @ApplicationScoped
    public static class HardeningServiceImpl implements HardeningService {

        /** Held open by a test so it can watch how many calls are in flight at once. */
        public static volatile CountDownLatch gate = new CountDownLatch(0);
        public static final AtomicInteger inFlight = new AtomicInteger();
        public static final AtomicInteger highWaterMark = new AtomicInteger();

        @Override
        public String takeProbe(Probe probe) {
            return probe.getText();
        }

        @Override
        public String blockUntilReleased() {
            int now = inFlight.incrementAndGet();
            highWaterMark.accumulateAndGet(now, Math::max);
            try {
                gate.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                inFlight.decrementAndGet();
            }
            return "done";
        }

        @Override
        public String quick() {
            return "quick";
        }
    }

    @WeldSetup
    public WeldInitiator weld = WeldInitiator.of(
            ServerRuntime.class,
            WasmRmiServerEngine.class,
            SyncEngine.class,
            ObjectMapperProducer.class,
            LiveMutexManager.class,
            HardeningServiceImpl.class
    );

    @Inject
    WasmRmiServerEngine engine;

    @Inject
    ObjectMapper mapper;

    private WasmRmiServerEngineTest.FakeSession session;
    private CapturingHandler logCapture;

    /** Collects what the engine wrote to the log, so a reference code can be looked for in it. */
    static class CapturingHandler extends Handler {
        final List<String> messages = Collections.synchronizedList(new ArrayList<>());
        @Override public void publish(LogRecord record) { messages.add(record.getMessage()); }
        @Override public void flush() { }
        @Override public void close() { }
    }

    @BeforeEach
    public void setup() {
        BinaryRegistry.register(Probe.class.getName(), Probe::new,
                new BinarySerializerDelegate<Probe>() {
                    @Override public void write(Probe obj, GrowableBuffer buffer, ObjectMapper m) {
                        BinarySerializer.writeString(buffer, obj.getText() == null ? "" : obj.getText());
                    }
                    @Override public void read(Probe obj, ByteBuffer buffer, ObjectMapper m) {
                        // Application code reached while the frame is being decoded. A custom lazy
                        // adapter or a validator is in exactly this position.
                        Principal caller = RmiRequestContext.getPrincipal();
                        Probe.principalSeenDuringDecode = caller == null ? "<none>" : caller.getName();
                        obj.setText(BinarySerializer.readString(buffer));
                    }
                });
        engine.scanServiceRegistry();
        engine.clearKeepaliveBudgetForTesting();
        HardeningServiceImpl.gate = new CountDownLatch(0);
        HardeningServiceImpl.inFlight.set(0);
        HardeningServiceImpl.highWaterMark.set(0);
        Probe.principalSeenDuringDecode = "<not decoded>";
        session = openSession("dispatch-1", "alice");
    }

    @AfterEach
    public void teardown() {
        HardeningServiceImpl.gate.countDown();
        if (logCapture != null) {
            Logger.getLogger(WasmRmiServerEngine.class.getName()).removeHandler(logCapture);
        }
        engine.onClose(session);
        System.clearProperty(WasmRmiServerEngine.MAX_QUEUED_FRAMES_PROPERTY);
        System.clearProperty(WasmRmiServerEngine.MAX_CONCURRENT_FRAMES_PROPERTY);
        System.clearProperty(WasmRmiServerEngine.PING_MIN_INTERVAL_PROPERTY);
    }

    private WasmRmiServerEngineTest.FakeSession openSession(String id, String user) {
        WasmRmiServerEngineTest.FakeSession opened = new WasmRmiServerEngineTest.FakeSession(id);
        WasmRmiServerEngineTest.FakeEndpointConfig config =
                new WasmRmiServerEngineTest.FakeEndpointConfig();
        if (user != null) {
            config.getUserProperties().put(RmiEndpointConfigurator.PRINCIPAL_KEY,
                    (Principal) () -> user);
        }
        config.getUserProperties().put(RmiEndpointConfigurator.ROLES_KEY, Set.of("user"));
        engine.onOpen(opened, config);
        return opened;
    }

    private byte[] call(int messageId, String method, Object... args) {
        GrowableBuffer buffer = new GrowableBuffer();
        buffer.putInt(messageId);
        BinarySerializer.writeString(buffer, HardeningService.class.getName());
        BinarySerializer.writeString(buffer, method);
        buffer.putInt(args.length);
        for (Object arg : args) {
            BinarySerializer.writeValue(buffer, arg, new ObjectMapper());
        }
        return buffer.toByteArray();
    }

    private static byte[] ping() {
        GrowableBuffer buffer = new GrowableBuffer();
        buffer.putInt(0);
        BinarySerializer.writeString(buffer, SyncFrameTypes.KEEPALIVE_SERVICE);
        BinarySerializer.writeString(buffer, "ping");
        buffer.putInt(0);
        return buffer.toByteArray();
    }

    /**
     * Frames written so far. {@code sentBuffers()} waits for the connection's writer thread to
     * catch up first, because these assertions run while other threads are still producing frames.
     */
    private static List<ByteBuffer> framesOf(WasmRmiServerEngineTest.FakeSession session) {
        return new ArrayList<>(session.basic.sentBuffers());
    }

    private static int pongCount(WasmRmiServerEngineTest.FakeSession session) {
        int pongs = 0;
        for (ByteBuffer frame : framesOf(session)) {
            if (frame.limit() >= 5 && frame.get(4) == SyncFrameTypes.PONG) {
                pongs++;
            }
        }
        return pongs;
    }

    // ---------------------------------------------------------------- identity during decoding

    @Test
    @DisplayName("the caller's identity is visible to code running while the frame is decoded")
    public void thePrincipalIsSetBeforeArgumentsAreDecoded() throws Exception {
        engine.processIncomingBinaryPayload(
                ByteBuffer.wrap(call(700, "takeProbe", new Probe("hi"))), session);
        // Two frames: the AUTH frame the connection opened with, then the answer to this call.
        // Waiting for the count is what makes the wait mean the answer; see awaitFrames.
        assertEquals(2, session.basic.awaitFrames(2, 5_000).size(),
                WasmRmiServerEngineTest.diag(session));

        assertEquals("alice", Probe.principalSeenDuringDecode,
                "identity used to be bound after decoding, so a custom adapter or validator saw "
                        + "nothing, or worse, whatever the previous frame left behind");
    }

    // ---------------------------------------------------------------- error reference

    @Test
    @DisplayName("the code sent to the caller is in the log next to the real failure")
    public void theErrorReferenceIsInTheLog() throws Exception {
        logCapture = new CapturingHandler();
        Logger engineLog = Logger.getLogger(WasmRmiServerEngine.class.getName());
        engineLog.addHandler(logCapture);
        engineLog.setLevel(Level.ALL);

        // No such method: reaches the same catch-all as any unplanned failure.
        engine.processIncomingBinaryPayload(ByteBuffer.wrap(call(701, "noSuchThing")), session);
        // AUTH frame, then the refusal.
        assertEquals(2, session.basic.awaitFrames(2, 5_000).size(),
                WasmRmiServerEngineTest.diag(session));

        ByteBuffer response = session.basic.sentBuffers().get(1);
        response.getInt();
        assertEquals(SyncFrameTypes.RPC_ERROR, response.get());
        String message = BinarySerializer.readString(response);

        // An unknown method is one of the framework's own refusals, so its wording still travels.
        assertTrue(message.contains("noSuchThing"), message);

        String matching = null;
        for (String logged : new ArrayList<>(logCapture.messages)) {
            if (logged != null && logged.contains("[ref ")) {
                matching = logged;
            }
        }
        assertNotNull(matching, "every answered failure is logged under a reference code");
        assertTrue(matching.contains("noSuchThing"),
                "and the log line carries the real failure: " + matching);
    }

    // ---------------------------------------------------------------- keepalive

    @Test
    @DisplayName("a normal keepalive is answered")
    public void aNormalPingIsAnswered() {
        engine.processIncomingBinaryPayload(ByteBuffer.wrap(ping()), session);
        assertEquals(1, pongCount(session), "a connection that is not answered dies at the proxy");
    }

    @Test
    @DisplayName("a flood of pings is not answered ping for ping")
    public void aPingFloodIsBounded() {
        for (int i = 0; i < 500; i++) {
            engine.processIncomingBinaryPayload(ByteBuffer.wrap(ping()), session);
        }
        assertEquals(1, pongCount(session),
                "the keepalive answers before any check, which makes it the cheapest thing to send "
                        + "in a loop; extra pings must cost nothing to ignore");
    }

    @Test
    @DisplayName("the connection is answered again once the interval has passed")
    public void pingsResumeAfterTheInterval() throws Exception {
        System.setProperty(WasmRmiServerEngine.PING_MIN_INTERVAL_PROPERTY, "1");
        engine.processIncomingBinaryPayload(ByteBuffer.wrap(ping()), session);
        Thread.sleep(30);
        engine.processIncomingBinaryPayload(ByteBuffer.wrap(ping()), session);
        assertEquals(2, pongCount(session), "a real keepalive must keep working");
    }

    // ---------------------------------------------------------------- in-flight bound

    @Test
    @DisplayName("one connection can only have so many frames waiting, and handles one at a time")
    public void framesInFlightAreBoundedPerSession() throws Exception {
        System.setProperty(WasmRmiServerEngine.MAX_QUEUED_FRAMES_PROPERTY, "4");
        WasmRmiServerEngineTest.FakeSession greedy = openSession("greedy", "mallory");
        WasmRmiServerEngineTest.FakeSession neighbour = openSession("neighbour", "bob");
        try {
            HardeningServiceImpl.gate = new CountDownLatch(1);

            // Twenty frames pushed as fast as a client could write them. Queueing blocks the
            // pushing thread once the backlog is full, which is why this runs off the test thread.
            CountDownLatch pushed = new CountDownLatch(1);
            Thread pusher = new Thread(() -> {
                for (int i = 0; i < 20; i++) {
                    engine.processIncomingBinaryPayload(
                            ByteBuffer.wrap(call(800 + i, "blockUntilReleased")), greedy);
                }
                pushed.countDown();
            });
            pusher.setDaemon(true);
            pusher.start();

            // Give the executor time to run everything it is allowed to run.
            Thread.sleep(300);
            assertEquals(1, HardeningServiceImpl.highWaterMark.get(),
                    "running at once: " + HardeningServiceImpl.highWaterMark.get()
                            + " - one connection's frames are handled one at a time, in the order "
                            + "they arrived, so that anything a browser sends after an edit is "
                            + "decided on that edit");

            // A second connection is not affected by the first one's backlog. Its AUTH frame is
            // already on the way, so this waits for two: counting any frame would be satisfied by
            // the AUTH frame and prove nothing about the call.
            engine.processIncomingBinaryPayload(ByteBuffer.wrap(call(900, "quick")), neighbour);
            assertEquals(2, neighbour.basic.awaitFrames(2, 5_000).size(),
                    "backpressure is per connection: one greedy client must not stall another - "
                            + WasmRmiServerEngineTest.diag(neighbour));

            HardeningServiceImpl.gate.countDown();
            assertTrue(pushed.await(15, TimeUnit.SECONDS), "the whole burst is served, not refused");

            long deadline = System.currentTimeMillis() + 15_000;
            while (responseCount(greedy) < 20 && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertEquals(20, responseCount(greedy),
                    "every frame is answered: the limit delays work, it never drops it");
            assertCorrelationIdsCoverTheBurst(greedy);
        } finally {
            HardeningServiceImpl.gate.countDown();
            engine.onClose(greedy);
            engine.onClose(neighbour);
        }
    }

    private static int responseCount(WasmRmiServerEngineTest.FakeSession session) {
        int responses = 0;
        for (ByteBuffer frame : framesOf(session)) {
            if (frame.limit() >= 5 && frame.get(4) == SyncFrameTypes.RPC_RESPONSE) {
                responses++;
            }
        }
        return responses;
    }

    /** Every frame is answered on its own correlation id, and no id is answered twice. */
    private static void assertCorrelationIdsCoverTheBurst(WasmRmiServerEngineTest.FakeSession s) {
        Set<Integer> answered = new java.util.HashSet<>();
        for (ByteBuffer frame : framesOf(s)) {
            if (frame.limit() >= 5 && frame.get(4) == SyncFrameTypes.RPC_RESPONSE) {
                assertTrue(answered.add(frame.getInt(0)),
                        "correlation id answered twice: " + frame.getInt(0));
            }
        }
        for (int i = 0; i < 20; i++) {
            assertTrue(answered.contains(800 + i), "no answer for correlation id " + (800 + i));
        }
    }
}
