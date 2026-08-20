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

import jakarta.websocket.CloseReason;
import jakarta.websocket.Extension;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;
import java.nio.ByteBuffer;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The outbound write path, tested against clients that misbehave.
 *
 * <p>The property under test is that <b>one connection cannot hurt another</b>. Until 0.6.3 a write
 * happened on whichever thread produced the frame, inside {@code synchronized (session)}, so a
 * client that stopped reading held that thread and that monitor for as long as it liked — and a
 * broadcast, which writes to every connection from one thread, stopped at the first such client.
 * Every test here that involves a stalled client fails against that implementation.</p>
 */
public class WsWritesTest {

    /** Long enough that a blocked write in these tests never finishes by accident. */
    private static final long FOREVER_SECONDS = 30;

    private final List<StallableSession> opened = new ArrayList<>();

    @BeforeEach
    public void shortenRetirement() {
        // A writer normally keeps its thread for thirty seconds after the last frame. Tests that
        // watch it go away cannot wait that long.
        WsWrites.setRetireAfterMillisForTesting(1_000L);
    }

    @AfterEach
    public void releaseEverything() {
        for (StallableSession session : opened) {
            session.remote.release();
            session.closed = true;
        }
        opened.clear();
        System.clearProperty(WsWrites.MAX_PENDING_FRAMES_PROPERTY);
        System.clearProperty(WsWrites.MAX_PENDING_BYTES_PROPERTY);
        WsWrites.resetForTesting();
    }

    private StallableSession session(String id) {
        StallableSession session = new StallableSession(id);
        opened.add(session);
        return session;
    }

    // ---------------------------------------------------------------- the point of the exercise

    /**
     * The whole reason this class exists: a client that has stopped reading must not delay a frame
     * going to anybody else.
     */
    @Test
    public void aStalledClientDoesNotDelayAHealthyOne() throws Exception {
        StallableSession stalled = session("stalled");
        StallableSession healthy = session("healthy");
        stalled.remote.stall();

        WsWrites.send(stalled, frame(1));
        assertTrue(stalled.remote.entered.await(5, TimeUnit.SECONDS),
                "the stalled connection should be in its blocking write");

        long start = System.nanoTime();
        WsWrites.send(healthy, frame(2));
        long queuedMillis = (System.nanoTime() - start) / 1_000_000;

        assertTrue(healthy.remote.arrived.await(5, TimeUnit.SECONDS),
                "the healthy connection must receive its frame while the other is stuck");
        assertTrue(queuedMillis < 1_000,
                "queueing must not wait on anything; took " + queuedMillis + " ms");
        assertEquals(0, stalled.remote.sent().size(), "the stalled write has not completed");
    }

    /**
     * A broadcast walks the session list on one thread. One unresponsive client in the middle of
     * that list used to stop everybody after it being written to at all.
     */
    @Test
    public void aBroadcastFinishesWhenOneClientIsUnresponsive() throws Exception {
        List<StallableSession> everyone = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            everyone.add(session("s" + i));
        }
        StallableSession deaf = everyone.get(5);
        deaf.remote.stall();

        long start = System.nanoTime();
        for (StallableSession session : everyone) {
            WsWrites.send(session, frame(7));
        }
        long broadcastMillis = (System.nanoTime() - start) / 1_000_000;

        for (StallableSession session : everyone) {
            if (session == deaf) {
                continue;
            }
            assertTrue(session.remote.arrived.await(5, TimeUnit.SECONDS),
                    session.getId() + " should have been written to");
        }
        assertTrue(broadcastMillis < 1_000,
                "the broadcast loop must not wait on the deaf client; took "
                        + broadcastMillis + " ms");
    }

    // -------------------------------------------------------------------- correctness guarantees

    /**
     * The Jakarta WebSocket API forbids two writes being in flight on one connection, and a
     * corrupted stream is not a failure that shows up near its cause.
     */
    @Test
    public void neverTwoWritesInFlightOnOneConnection() throws Exception {
        StallableSession session = session("busy");
        session.remote.slowByMillis = 2;

        int producers = 8;
        int each = 25;
        runConcurrently(producers, p -> {
            for (int i = 0; i < each; i++) {
                WsWrites.send(session, frame(p * 1000 + i));
            }
        });

        WsWrites.awaitQuiet(session, 30_000);
        assertEquals(producers * each, session.remote.sent().size(), "every frame should be sent");
        assertEquals(1, session.remote.highWaterMark.get(),
                "two writes were in flight on one connection at the same time");
    }

    /** Correlation ids assume frames arrive in the order they were produced. */
    @Test
    public void framesArriveInTheOrderTheyWereQueued() throws Exception {
        StallableSession session = session("ordered");

        int producers = 4;
        int each = 50;
        runConcurrently(producers, p -> {
            for (int i = 0; i < each; i++) {
                WsWrites.send(session, frame(p * 1000 + i));
            }
        });

        WsWrites.awaitQuiet(session, 30_000);
        List<ByteBuffer> sent = session.remote.sent();
        assertEquals(producers * each, sent.size());

        int[] lastSeen = new int[producers];
        for (int i = 0; i < producers; i++) {
            lastSeen[i] = -1;
        }
        for (ByteBuffer buffer : sent) {
            int value = buffer.getInt(0);
            int producer = value / 1000;
            int sequence = value % 1000;
            assertTrue(sequence > lastSeen[producer],
                    "producer " + producer + " sent " + sequence + " after " + lastSeen[producer]);
            lastSeen[producer] = sequence;
        }
    }

    // -------------------------------------------------------------------------- the bound bites

    /** Past the frame bound the connection is closed rather than buffered without limit. */
    @Test
    public void reachingTheFrameBoundClosesTheConnection() throws Exception {
        System.setProperty(WsWrites.MAX_PENDING_FRAMES_PROPERTY, "8");
        StallableSession session = session("hoarder");
        session.remote.stall();

        WsWrites.send(session, frame(0));
        assertTrue(session.remote.entered.await(5, TimeUnit.SECONDS));

        for (int i = 0; i < 500; i++) {
            WsWrites.send(session, frame(i));
        }

        assertTrue(session.awaitClosed(5, TimeUnit.SECONDS),
                "a connection that cannot keep up must be closed, not buffered");
        assertNotNull(session.closeReason);
        assertEquals(CloseReason.CloseCodes.TRY_AGAIN_LATER, session.closeReason.getCloseCode());
        assertEquals(WsWrites.OVERLOADED_CLOSE_REASON, session.closeReason.getReasonPhrase());
        assertTrue(session.closeReason.getReasonPhrase().getBytes("UTF-8").length <= 123,
                "a close reason over 123 bytes makes the container throw instead of closing");
    }

    /**
     * A frame count alone does not bound memory: one frame carrying a large object graph can be
     * megabytes, so the byte bound has to bite on its own.
     */
    @Test
    public void reachingTheByteBoundClosesTheConnection() throws Exception {
        System.setProperty(WsWrites.MAX_PENDING_FRAMES_PROPERTY, "1000000");
        System.setProperty(WsWrites.MAX_PENDING_BYTES_PROPERTY, "65536");
        StallableSession session = session("fat");
        session.remote.stall();

        WsWrites.send(session, new byte[8192]);
        assertTrue(session.remote.entered.await(5, TimeUnit.SECONDS));

        for (int i = 0; i < 100; i++) {
            WsWrites.send(session, new byte[8192]);
        }

        assertTrue(session.awaitClosed(5, TimeUnit.SECONDS),
                "the byte bound must close the connection even when the frame count is far away");
        assertEquals(CloseReason.CloseCodes.TRY_AGAIN_LATER, session.closeReason.getCloseCode());
    }

    /** The connection being closed for hoarding must not take its neighbours with it. */
    @Test
    public void closingAHoarderLeavesItsNeighboursAlone() throws Exception {
        System.setProperty(WsWrites.MAX_PENDING_FRAMES_PROPERTY, "4");
        StallableSession hoarder = session("hoarder");
        StallableSession neighbour = session("neighbour");
        hoarder.remote.stall();

        WsWrites.send(hoarder, frame(0));
        assertTrue(hoarder.remote.entered.await(5, TimeUnit.SECONDS));
        for (int i = 0; i < 200; i++) {
            WsWrites.send(hoarder, frame(i));
        }
        assertTrue(hoarder.awaitClosed(5, TimeUnit.SECONDS));

        WsWrites.send(neighbour, frame(99));
        assertTrue(neighbour.remote.arrived.await(5, TimeUnit.SECONDS));
        assertFalse(neighbour.closed, "the neighbour must still be connected");
    }

    /**
     * The bound is on the backlog behind a connection, not on one message. An application that
     * returns a large object graph must not have its connection closed under it.
     */
    @Test
    public void oneFrameLargerThanTheBoundStillGoesOut() throws Exception {
        System.setProperty(WsWrites.MAX_PENDING_BYTES_PROPERTY, "1024");
        StallableSession session = session("chunky");

        WsWrites.send(session, new byte[64 * 1024]);

        assertTrue(session.remote.arrived.await(5, TimeUnit.SECONDS),
                "a single message over the queue's byte bound must still be sent");
        assertFalse(session.closed, "and must not close the connection");
        assertEquals(1, session.remote.sent().size());
    }

    // ------------------------------------------------------------------------------- cleaning up

    /** A connection that closes while a write is stuck must leave nothing behind. */
    @Test
    public void aSessionClosedMidWriteStrandsNothing() throws Exception {
        StallableSession session = session("dropped");
        session.remote.stall();

        WsWrites.send(session, frame(1));
        assertTrue(session.remote.entered.await(5, TimeUnit.SECONDS));
        for (int i = 0; i < 10; i++) {
            WsWrites.send(session, frame(i));
        }
        Thread writer = WsWrites.writerThread(session);
        assertNotNull(writer, "the connection should have a writer while it has frames queued");

        session.closed = true;
        session.remote.release();

        assertTrue(awaitTrue(() -> !writer.isAlive(), 5_000),
                "the writer thread must end when its connection goes");
        assertFalse(WsWrites.hasWriter(session), "the queue must not outlive the connection");
    }

    /** A connection that goes quiet gives its thread back rather than parking one forever. */
    @Test
    public void anIdleWriterGivesUpItsThread() throws Exception {
        StallableSession session = session("quiet");

        WsWrites.send(session, frame(1));
        assertTrue(session.remote.arrived.await(5, TimeUnit.SECONDS));
        Thread writer = WsWrites.writerThread(session);
        assertNotNull(writer);

        assertTrue(awaitTrue(() -> !WsWrites.hasWriter(session), 10_000),
                "an idle writer should retire");
        assertTrue(awaitTrue(() -> !writer.isAlive(), 5_000),
                "and its thread should end");

        // And a later frame still goes out: retiring is not the same as giving up.
        session.remote.arrived = new CountDownLatch(1);
        WsWrites.send(session, frame(2));
        assertTrue(session.remote.arrived.await(5, TimeUnit.SECONDS));
        assertEquals(2, session.remote.sent().size());
    }

    /** Nothing is sent to a connection that has already gone. */
    @Test
    public void aClosedSessionIsNotWrittenTo() throws Exception {
        StallableSession session = session("gone");
        session.closed = true;

        WsWrites.send(session, frame(1));

        assertFalse(WsWrites.hasWriter(session));
        assertEquals(0, session.remote.sent().size());
    }

    // ------------------------------------------------------------------------------------ helpers

    private static byte[] frame(int value) {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(value);
        return buffer.array();
    }

    private interface Producer {
        void produce(int index) throws Exception;
    }

    /** Starts {@code count} threads that begin together, and waits for all of them. */
    private static void runConcurrently(int count, Producer body) throws Exception {
        CyclicBarrier gate = new CyclicBarrier(count);
        List<Thread> threads = new ArrayList<>();
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        for (int i = 0; i < count; i++) {
            int index = i;
            Thread thread = Thread.ofVirtual().start(() -> {
                try {
                    gate.await();
                    body.produce(index);
                } catch (Throwable t) {
                    failures.add(t);
                }
            });
            threads.add(thread);
        }
        for (Thread thread : threads) {
            thread.join(30_000);
        }
        if (!failures.isEmpty()) {
            throw new AssertionError("producer failed", failures.get(0));
        }
    }

    private static boolean awaitTrue(java.util.function.BooleanSupplier condition, long millis)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        while (System.nanoTime() - deadline < 0) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(20);
        }
        return condition.getAsBoolean();
    }

    /**
     * A remote that can be told to behave like a client which has stopped reading: the write
     * blocks, exactly as a basic remote does when TCP flow control shuts the send window.
     */
    static class StallableRemote implements RemoteEndpoint.Basic {

        private final List<ByteBuffer> recorded = new CopyOnWriteArrayList<>();
        /** Counted down when a write has begun, so a test can be sure the connection is stuck. */
        final CountDownLatch entered = new CountDownLatch(1);
        /** Counted down when a frame has been written; replaced when a test wants a fresh one. */
        volatile CountDownLatch arrived = new CountDownLatch(1);
        /** Writes in flight right now, and the most there have ever been at once. */
        private final AtomicInteger inFlight = new AtomicInteger();
        final AtomicInteger highWaterMark = new AtomicInteger();

        private final CountDownLatch stalled = new CountDownLatch(1);
        private volatile boolean stalling;
        volatile long slowByMillis;

        void stall() {
            stalling = true;
        }

        void release() {
            stalling = false;
            stalled.countDown();
        }

        List<ByteBuffer> sent() {
            return new ArrayList<>(recorded);
        }

        @Override
        public void sendBinary(ByteBuffer data) {
            int now = inFlight.incrementAndGet();
            highWaterMark.accumulateAndGet(now, Math::max);
            try {
                if (stalling) {
                    entered.countDown();
                    try {
                        stalled.await(FOREVER_SECONDS, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                if (slowByMillis > 0) {
                    try {
                        Thread.sleep(slowByMillis);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                ByteBuffer copy = ByteBuffer.allocate(data.remaining());
                copy.put(data);
                copy.flip();
                recorded.add(copy);
                arrived.countDown();
            } finally {
                inFlight.decrementAndGet();
            }
        }

        @Override public void sendText(String text) {}
        @Override public void sendText(String partialMessage, boolean isLast) {}
        @Override public void sendBinary(ByteBuffer partialByte, boolean isLast) {}
        @Override public OutputStream getSendStream() { return null; }
        @Override public Writer getSendWriter() { return null; }
        @Override public void sendObject(Object data) {}
        @Override public void setBatchingAllowed(boolean allowed) {}
        @Override public boolean getBatchingAllowed() { return false; }
        @Override public void flushBatch() {}
        @Override public void sendPing(ByteBuffer applicationData) {}
        @Override public void sendPong(ByteBuffer applicationData) {}
    }

    /** A session whose remote can stall, and which records how it was closed. */
    static class StallableSession implements Session {

        private final String id;
        final StallableRemote remote = new StallableRemote();
        private final Map<String, Object> props = new HashMap<>();
        volatile boolean closed;
        volatile CloseReason closeReason;
        private final CountDownLatch closeLatch = new CountDownLatch(1);

        StallableSession(String id) {
            this.id = id;
        }

        boolean awaitClosed(long timeout, TimeUnit unit) throws InterruptedException {
            return closeLatch.await(timeout, unit);
        }

        @Override public void close() { closed = true; closeLatch.countDown(); }

        @Override public void close(CloseReason reason) {
            closed = true;
            closeReason = reason;
            closeLatch.countDown();
        }

        @Override public boolean isOpen() { return !closed; }
        @Override public String getId() { return id; }
        @Override public RemoteEndpoint.Basic getBasicRemote() { return remote; }
        @Override public Map<String, Object> getUserProperties() { return props; }

        @Override public WebSocketContainer getContainer() { return null; }
        @Override public void addMessageHandler(MessageHandler handler) {}
        @Override public <T> void addMessageHandler(Class<T> clazz, MessageHandler.Whole<T> h) {}
        @Override public <T> void addMessageHandler(Class<T> clazz, MessageHandler.Partial<T> h) {}
        @Override public Set<MessageHandler> getMessageHandlers() { return null; }
        @Override public void removeMessageHandler(MessageHandler handler) {}
        @Override public String getProtocolVersion() { return null; }
        @Override public String getNegotiatedSubprotocol() { return null; }
        @Override public List<Extension> getNegotiatedExtensions() { return null; }
        @Override public boolean isSecure() { return false; }
        @Override public long getMaxIdleTimeout() { return 0; }
        @Override public void setMaxIdleTimeout(long milliseconds) {}
        @Override public void setMaxBinaryMessageBufferSize(int length) {}
        @Override public int getMaxBinaryMessageBufferSize() { return 0; }
        @Override public void setMaxTextMessageBufferSize(int length) {}
        @Override public int getMaxTextMessageBufferSize() { return 0; }
        @Override public RemoteEndpoint.Async getAsyncRemote() { return null; }
        @Override public Map<String, List<String>> getRequestParameterMap() { return null; }
        @Override public String getQueryString() { return null; }
        @Override public Map<String, String> getPathParameters() { return null; }
        @Override public Principal getUserPrincipal() { return null; }
        @Override public Set<Session> getOpenSessions() { return null; }
        @Override public URI getRequestURI() { return null; }
    }
}
