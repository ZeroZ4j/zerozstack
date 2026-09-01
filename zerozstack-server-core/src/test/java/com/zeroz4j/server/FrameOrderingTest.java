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

import com.zeroz4j.api.BinarySerializer;
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
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One connection's messages are handled in the order the browser sent them.
 *
 * <h2>What this protects</h2>
 * A WebSocket already delivers one connection's messages in order, and the container already calls
 * the message handler for one connection one message at a time. Before 0.8.0 the framework threw
 * that away: every message went straight to a thread-per-task executor, so up to 32 from the same
 * browser ran at once and finished in whatever order they finished.
 *
 * <p>The application-visible consequence: somebody types into a live-synced field and immediately
 * presses a button that calls a service. The client sends the typing first, and it is on the wire
 * first - but the server could still decide the button's call on the value the person had already
 * replaced. It was luck, and it was observed going the lucky way in a browser.</p>
 *
 * <p>The first test here is that scenario in miniature: a slow message and then a fast one, and the
 * effects have to land in the order they were sent. It fails on the code before this change.</p>
 *
 * <h2>And what it must not cost</h2>
 * Ordering is per connection and nothing else. Two tests below hold the line on that: a slow call on
 * one connection must not delay another connection at all, and the five-byte keepalive must still be
 * answered straight away on a busy connection, because a connection that stops answering pings is
 * killed by the first proxy in the path exactly when it is busiest.
 */
@EnableWeld
public class FrameOrderingTest {

    /** How long the "slow" call takes. Long enough that a race would resolve the wrong way. */
    private static final long SLOW_MILLIS = 400L;

    @RmiService
    public interface OrderedService {
        /** Sleeps, then records that it ran. Stands in for a live edit that costs real work. */
        String slow(String mark);

        /** Records that it ran, immediately. Stands in for the button press behind the edit. */
        String fast(String mark);
    }

    @ApplicationScoped
    public static class OrderedServiceImpl implements OrderedService {

        /** What ran, in the order it finished. */
        public static final List<String> effects = Collections.synchronizedList(new ArrayList<>());

        /** When the slow call started, so a second connection's delay can be measured against it. */
        public static final AtomicLong slowStartedAt = new AtomicLong();

        /** Held open so a test can keep a connection busy for as long as it likes. */
        public static volatile CountDownLatch gate = new CountDownLatch(0);

        @Override
        public String slow(String mark) {
            slowStartedAt.compareAndSet(0L, System.nanoTime());
            try {
                Thread.sleep(SLOW_MILLIS);
                gate.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            effects.add(mark);
            return mark;
        }

        @Override
        public String fast(String mark) {
            effects.add(mark);
            return mark;
        }
    }

    @WeldSetup
    public WeldInitiator weld = WeldInitiator.of(
            ServerRuntime.class,
            WasmRmiServerEngine.class,
            SyncEngine.class,
            ObjectMapperProducer.class,
            LiveMutexManager.class,
            OrderedServiceImpl.class
    );

    @Inject
    WasmRmiServerEngine engine;

    @Inject
    ObjectMapper mapper;

    private WasmRmiServerEngineTest.FakeSession session;

    @BeforeEach
    public void setup() {
        engine.scanServiceRegistry();
        engine.clearKeepaliveBudgetForTesting();
        OrderedServiceImpl.effects.clear();
        OrderedServiceImpl.slowStartedAt.set(0L);
        OrderedServiceImpl.gate = new CountDownLatch(0);
        session = openSession("order-1", "alice");
    }

    @AfterEach
    public void teardown() {
        OrderedServiceImpl.gate.countDown();
        engine.onClose(session);
        System.clearProperty(WasmRmiServerEngine.MAX_QUEUED_FRAMES_PROPERTY);
        System.clearProperty(WasmRmiServerEngine.MAX_CONCURRENT_FRAMES_PROPERTY);
        System.clearProperty(WasmRmiServerEngine.PING_MIN_INTERVAL_PROPERTY);
        System.clearProperty(LiveMutexManager.WAIT_SECONDS_PROPERTY);
    }

    // ---------------------------------------------------------------- the guarantee

    @Test
    @DisplayName("a slow message and a fast one behind it land in the order they were sent")
    public void effectsLandInSendOrder() throws Exception {
        engine.processIncomingBinaryPayload(ByteBuffer.wrap(call(1, "slow", "edit")), session);
        engine.processIncomingBinaryPayload(ByteBuffer.wrap(call(2, "fast", "button")), session);

        awaitResponses(2, 10_000);

        assertEquals(List.of("edit", "button"), new ArrayList<>(OrderedServiceImpl.effects),
                "the browser sent the edit first and the button second, and the transport delivered "
                        + "them in that order; handling them out of order decides the button's call "
                        + "on a value the person had already replaced");
    }

    @Test
    @DisplayName("a whole burst from one connection keeps its order")
    public void aBurstKeepsItsOrder() throws Exception {
        List<String> sent = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            String mark = "m" + i;
            sent.add(mark);
            // The first is slow so the rest have something real to overtake.
            byte[] frame = i == 0 ? call(100, "slow", mark) : call(100 + i, "fast", mark);
            engine.processIncomingBinaryPayload(ByteBuffer.wrap(frame), session);
        }

        awaitResponses(12, 15_000);

        assertEquals(sent, new ArrayList<>(OrderedServiceImpl.effects),
                "every message from one connection is handled after the one before it");
    }

    // ---------------------------------------------------------------- and what it must not cost

    @Test
    @DisplayName("a slow call on one connection does not delay another connection")
    public void connectionsStayIndependent() throws Exception {
        WasmRmiServerEngineTest.FakeSession other = openSession("order-2", "bob");
        try {
            OrderedServiceImpl.gate = new CountDownLatch(1);

            long start = System.nanoTime();
            engine.processIncomingBinaryPayload(ByteBuffer.wrap(call(10, "slow", "busy")), session);
            engine.processIncomingBinaryPayload(ByteBuffer.wrap(call(11, "fast", "neighbour")),
                    other);

            other.basic.awaitFrames(2, 5_000);
            long answeredAfterMillis = (System.nanoTime() - start) / 1_000_000L;

            assertEquals(1, responseCount(other),
                    "the second connection must be answered while the first is still working");
            // Measured at 19 ms on the development machine, while the other connection's call was
            // still blocked with no end in sight. The threshold is loose on purpose: what it has to
            // catch is a neighbour waiting for the slow call, which would be seconds, not tens of
            // milliseconds.
            assertTrue(answeredAfterMillis < SLOW_MILLIS / 2,
                    "the neighbour waited " + answeredAfterMillis + " ms behind a call that is "
                            + "still not finished; ordering is per connection, and one person's "
                            + "slow call must never be another person's wait");
            assertEquals(0, responseCount(session), "the slow call is still running");

            OrderedServiceImpl.gate.countDown();
        } finally {
            OrderedServiceImpl.gate.countDown();
            engine.onClose(other);
        }
    }

    @Test
    @DisplayName("the keepalive is answered while the connection is busy with a slow call")
    public void theKeepaliveDoesNotQueueBehindWork() throws Exception {
        OrderedServiceImpl.gate = new CountDownLatch(1);
        try {
            engine.processIncomingBinaryPayload(ByteBuffer.wrap(call(20, "slow", "busy")), session);
            // Three more behind it, so the connection has a real backlog.
            for (int i = 0; i < 3; i++) {
                engine.processIncomingBinaryPayload(
                        ByteBuffer.wrap(call(21 + i, "fast", "queued" + i)), session);
            }

            long start = System.nanoTime();
            engine.processIncomingBinaryPayload(ByteBuffer.wrap(ping()), session);
            long answeredAfterMillis = (System.nanoTime() - start) / 1_000_000L;

            assertEquals(1, pongCount(session),
                    "a ping is answered on the read thread before anything is queued; a connection "
                            + "that stops answering is cut by the first proxy in the path, and that "
                            + "would happen exactly when it is busiest");
            assertTrue(answeredAfterMillis < SLOW_MILLIS,
                    "the pong took " + answeredAfterMillis + " ms, so it queued behind work");
            assertEquals(0, responseCount(session), "the backlog has not moved");
        } finally {
            OrderedServiceImpl.gate.countDown();
        }
    }

    // ---------------------------------------------------------------- the bound

    @Test
    @DisplayName("one connection may only have so many messages waiting")
    public void theBacklogIsBounded() throws Exception {
        SessionFrameQueue queue = new SessionFrameQueue(3);
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            queue.submit(() -> {
                running.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(running.await(5, TimeUnit.SECONDS), "the first frame started");
            queue.submit(() -> { });
            queue.submit(() -> { });
            assertEquals(3, queue.inFlight(), "one running plus two waiting is the ceiling");

            // A fourth would wait for room, which is the backpressure. Prove it waits rather than
            // being refused or dropped: it must not have been accepted while the queue is full, and
            // it must go through once the first frame finishes.
            CountDownLatch fourthAccepted = new CountDownLatch(1);
            Thread reader = new Thread(() -> {
                queue.submit(() -> { });
                fourthAccepted.countDown();
            });
            reader.setDaemon(true);
            reader.start();
            assertFalse(fourthAccepted.await(300, TimeUnit.MILLISECONDS),
                    "a connection that fills its queue is slowed down, not served");

            release.countDown();
            assertTrue(fourthAccepted.await(5, TimeUnit.SECONDS),
                    "and it goes through as soon as there is room: nothing is dropped");
        } finally {
            release.countDown();
            queue.close();
        }
    }

    @Test
    @DisplayName("the setting's name before 0.8.0 is still read")
    public void theOldSettingNameStillWorks() {
        System.setProperty(WasmRmiServerEngine.MAX_CONCURRENT_FRAMES_PROPERTY, "5");
        WasmRmiServerEngineTest.FakeSession configured = openSession("order-old-name", "carol");
        try {
            // Nothing observable names the ceiling from outside, so this asserts the connection
            // opened and works at all with only the old name set: reading neither name would have
            // been a silent fall back to 32.
            engine.processIncomingBinaryPayload(
                    ByteBuffer.wrap(call(30, "fast", "x")), configured);
            configured.basic.awaitFrames(2, 5_000);
            assertEquals(1, responseCount(configured));
        } finally {
            engine.onClose(configured);
        }
    }

    // ---------------------------------------------------------------- waiting for a lock

    @Test
    @DisplayName("a message waiting for a lock lets the messages behind it past")
    public void aLockWaitDoesNotHoldUpLaterFrames() throws Exception {
        ServerRuntime runtime = new ServerRuntime();
        LiveMutexManager manager = new LiveMutexManager();
        manager.runtime = runtime;
        LiveMutexRpcImpl rpc = new LiveMutexRpcImpl();
        rpc.manager = manager;
        rpc.runtime = runtime;

        WasmRmiServerEngineTest.FakeSession locked =
                new WasmRmiServerEngineTest.FakeSession("lock-1");
        locked.getUserProperties().put(RmiEndpointConfigurator.CLIENT_KEY, "browser-1");
        runtime.addSessionForTesting(locked);
        runtime.disclosures().sessionOpened(locked);
        runtime.disclosures().record("lock-1", "handle-a");

        // Somebody else is holding it, so the next request has to wait.
        manager.lock("handle-a", "session:someone-else");

        SessionFrameQueue queue = new SessionFrameQueue(8);
        CountDownLatch waiting = new CountDownLatch(1);
        CountDownLatch behindRan = new CountDownLatch(1);
        try {
            System.setProperty(LiveMutexManager.WAIT_SECONDS_PROPERTY, "5");
            queue.submit(() -> {
                RmiRequestContext.setContext(null, Collections.emptySet(), "lock-1", null,
                        "browser-1");
                waiting.countDown();
                try {
                    rpc.acquireLock("handle-a");
                } catch (RuntimeException expectedTimeout) {
                    // The point of the test is what happens behind it, not whether it gets the lock.
                } finally {
                    RmiRequestContext.clear();
                }
            });
            assertTrue(waiting.await(5, TimeUnit.SECONDS), "the lock request started");

            queue.submit(behindRan::countDown);

            assertTrue(behindRan.await(3, TimeUnit.SECONDS),
                    "a lock request waits up to 30 seconds by default. It has changed nothing at "
                            + "that point, so it lets the messages behind it past rather than "
                            + "stalling everything else that person does for half a minute");
        } finally {
            manager.unlock("handle-a", "session:someone-else");
            queue.close();
            runtime.shutDown();
            RmiRequestContext.clear();
        }
    }

    // ---------------------------------------------------------------- helpers

    private WasmRmiServerEngineTest.FakeSession openSession(String id, String user) {
        WasmRmiServerEngineTest.FakeSession opened = new WasmRmiServerEngineTest.FakeSession(id);
        WasmRmiServerEngineTest.FakeEndpointConfig config =
                new WasmRmiServerEngineTest.FakeEndpointConfig();
        config.getUserProperties().put(RmiEndpointConfigurator.PRINCIPAL_KEY,
                (Principal) () -> user);
        config.getUserProperties().put(RmiEndpointConfigurator.ROLES_KEY, Set.of("user"));
        engine.onOpen(opened, config);
        return opened;
    }

    private byte[] call(int messageId, String method, Object... args) {
        GrowableBuffer buffer = new GrowableBuffer();
        buffer.putInt(messageId);
        BinarySerializer.writeString(buffer, OrderedService.class.getName());
        BinarySerializer.writeString(buffer, method);
        buffer.putInt(args.length);
        for (Object arg : args) {
            BinarySerializer.writeValue(buffer, arg, mapper);
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

    /** Waits for the connection to have answered {@code count} calls. */
    private void awaitResponses(int count, long millis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < deadline && responseCount(session) < count) {
            Thread.sleep(10);
        }
        assertEquals(count, responseCount(session),
                "not every call was answered: " + WasmRmiServerEngineTest.diag(session));
    }

    private static int responseCount(WasmRmiServerEngineTest.FakeSession s) {
        int responses = 0;
        for (ByteBuffer frame : new ArrayList<>(s.basic.sentBuffers())) {
            if (frame.limit() >= 5 && frame.get(4) == SyncFrameTypes.RPC_RESPONSE) {
                responses++;
            }
        }
        return responses;
    }

    private static int pongCount(WasmRmiServerEngineTest.FakeSession s) {
        int pongs = 0;
        for (ByteBuffer frame : new ArrayList<>(s.basic.sentBuffers())) {
            if (frame.limit() >= 5 && frame.get(4) == SyncFrameTypes.PONG) {
                pongs++;
            }
        }
        return pongs;
    }
}
