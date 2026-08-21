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
import jakarta.websocket.Session;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/**
 * The single outbound write path for every binary frame the server sends.
 *
 * <p>The Jakarta WebSocket API forbids two writes being in flight on one connection at the same
 * time, so writes to a connection have to be serialized somehow. Until 0.7.0 that was
 * {@code synchronized (session) { session.getBasicRemote().sendBinary(...) }} on whichever thread
 * produced the frame, and one client that stopped reading was enough to expose two faults in
 * it.</p>
 *
 * <p><b>Fault one: head-of-line blocking across connections.</b> A basic remote blocks until the
 * bytes have been handed to the operating system. When a client stops reading, TCP flow control
 * closes the window and that call never returns — while holding the monitor on the session. Every
 * other thread writing to that connection then piled up behind it for as long as the client cared
 * to stall. Worse, a broadcast walks the session list and writes to each connection in turn on one
 * thread, so the first stalled client in the list stopped the broadcast reaching anybody after
 * it.</p>
 *
 * <p><b>Fault two: it pinned carrier threads.</b> Frames are produced on virtual threads
 * ({@link SessionThreads}). On JDK 21 a virtual thread that blocks inside a {@code synchronized}
 * block cannot unmount, so it holds its carrier — a real platform thread — for the whole stall
 * (JEP 444; JEP 491 removed this in JDK 24). Measured on JDK 21.0.7: with the carrier pool set to
 * four, four virtual threads blocking inside {@code synchronized} on four <em>different</em>
 * monitors starved the scheduler completely, and an unrelated virtual thread never ran at all.
 * The carrier pool defaults to the number of processors, so a handful of clients that stop reading
 * could stop the server doing anything else.</p>
 *
 * <h2>What happens instead</h2>
 *
 * <p>Each connection gets one writer of its own: a bounded queue and a single virtual thread that
 * drains it. {@link #send(Session, byte[])} appends to that queue and returns; it never touches the
 * socket, so it cannot block on one. The writer thread is the only thing that ever calls
 * {@code sendBinary} for its connection, which keeps the API's one-write-at-a-time rule without a
 * lock being held across the write, and keeps frames in the order they were queued.</p>
 *
 * <p>A stalled client therefore parks exactly one virtual thread — its own writer — and nothing
 * else. No lock it holds is reachable from any other connection, and no producer waits on it.</p>
 *
 * <h2>What happens when a client cannot keep up</h2>
 *
 * <p>The queue is bounded, by frame count ({@value #MAX_PENDING_FRAMES_PROPERTY}) and by bytes
 * ({@value #MAX_PENDING_BYTES_PROPERTY}); an empty queue always accepts a frame, so the bounds
 * limit a backlog and never refuse a single large message. Reaching either bound closes it with
 * {@link CloseReason.CloseCodes#TRY_AGAIN_LATER}. That is the honest outcome: the client has
 * already missed frames it will never see, so its copy of the world is wrong either way, and the
 * client reconnects and re-syncs on its own. The alternatives are worse — waiting would let one
 * connection decide how long a server thread is occupied, and buffering without limit would let it
 * decide how much heap the server uses.</p>
 *
 * <p>The close is performed on a separate one-shot thread, because closing a connection whose send
 * window is shut can block too, and the point of all this is that nothing a slow client does ever
 * runs on a thread that matters to anybody else.</p>
 *
 * <h2>Thread-scoped context</h2>
 *
 * <p>Callers serialize into a {@code byte[]} first and only then call {@link #send}; the
 * {@code LazyHandles.setCurrentSession(...)} brackets in {@code SyncEngine} and
 * {@code WasmRmiServerEngine} all close before the call. Nothing thread-scoped is read here, so
 * moving the socket write to another thread carries no context with it.</p>
 */
final class WsWrites {

    private static final Logger LOG = Logger.getLogger(WsWrites.class.getName());

    /**
     * Most frames that may be waiting to go out on one connection; unset applies
     * {@link #DEFAULT_MAX_PENDING_FRAMES}.
     */
    static final String MAX_PENDING_FRAMES_PROPERTY = "zeroz.ws.maxPendingFramesPerSession";

    /**
     * Frames allowed to queue when {@link #MAX_PENDING_FRAMES_PROPERTY} is unset.
     *
     * <p>256 is far above any burst a working connection produces — a screen loading, a re-sync
     * flushing, a busy shared signal — and far below what it would take to matter. A connection
     * that is 256 frames behind is not slow, it has stopped reading.</p>
     */
    static final int DEFAULT_MAX_PENDING_FRAMES = 256;

    /**
     * Most bytes that may be waiting to go out on one connection; unset applies
     * {@link #DEFAULT_MAX_PENDING_BYTES}.
     */
    static final String MAX_PENDING_BYTES_PROPERTY = "zeroz.ws.maxPendingBytesPerSession";

    /**
     * Bytes allowed to queue when {@link #MAX_PENDING_BYTES_PROPERTY} is unset: 8 MB.
     *
     * <p>A frame count on its own does not bound memory, because one frame carrying a large object
     * graph can be megabytes. Twice the 4 MB incoming message limit, so a connection can always
     * have one full-sized frame queued behind one being written.</p>
     */
    static final int DEFAULT_MAX_PENDING_BYTES = 8 * 1024 * 1024;

    /**
     * What the browser is told when its own backlog closed the connection. Well under the 123 bytes
     * a close reason allows, and written for whoever reads it in a browser console.
     */
    static final String OVERLOADED_CLOSE_REASON =
            "Server could not keep up with this connection; reconnect to re-sync.";

    /**
     * How often an idle writer looks up to see whether its connection has gone.
     *
     * <p>There is no callback for "this session closed" that this class can subscribe to without
     * reaching into the endpoint, so an idle writer checks. Half a second bounds how long a closed
     * connection's writer lives, and costs two wakeups a second on a virtual thread — only for
     * connections that were written to recently and are now silent.</p>
     */
    private static final long POLL_SLICE_MILLIS = 500L;

    /**
     * How long a writer with nothing to do waits before giving up its thread. Recreated on demand;
     * starting a virtual thread costs microseconds.
     */
    private static final long DEFAULT_RETIRE_AFTER_MILLIS = 30_000L;

    /** Overridden only by tests, which cannot wait thirty seconds to watch a writer retire. */
    private static volatile long retireAfterMillis = DEFAULT_RETIRE_AFTER_MILLIS;

    /**
     * Connection to its writer.
     *
     * <p>Keyed by the {@link Session} instance. Container session classes do not override
     * {@code equals}, so this is identity — which is what is wanted, since a reconnect is a
     * different connection even when the container reuses an id.</p>
     */
    private static final Map<Session, SessionWriter> WRITERS = new ConcurrentHashMap<>();

    /** Guards the one-time report of the configured bounds. */
    private static final AtomicBoolean BOUNDS_REPORTED = new AtomicBoolean();

    private WsWrites() {}

    /**
     * Queues one binary frame for a connection and returns immediately.
     *
     * <p>Never blocks on the socket and never waits for another connection. Frames queued for one
     * connection go out in the order they were queued.</p>
     *
     * @param session target WebSocket session; null or already-closed sessions are ignored
     * @param frame   binary frame payload, already serialized by the caller
     */
    static void send(Session session, byte[] frame) {
        if (session == null || frame == null || !session.isOpen()) {
            return;
        }
        Handoff handoff = new Handoff();
        WRITERS.compute(session, (key, existing) -> {
            SessionWriter writer = existing;
            if (writer == null) {
                writer = new SessionWriter(key);
                handoff.started = writer;
            }
            handoff.writer = writer;
            handoff.accepted = writer.offer(frame);
            return writer;
        });
        if (handoff.started != null) {
            handoff.started.start();
        }
        if (!handoff.accepted) {
            handoff.writer.overloaded();
        }
    }

    /** Carries the outcome of the mapping function out of {@code compute}. */
    private static final class Handoff {
        private SessionWriter writer;
        private SessionWriter started;
        private boolean accepted = true;
    }

    /**
     * Test support: waits until this connection has nothing queued and nothing in flight.
     *
     * <p>Writing is asynchronous, so a test that asserts on what a fake remote recorded has to wait
     * for the writer to catch up. Production code never needs this.</p>
     *
     * @param session the connection to wait for
     */
    static void awaitQuiet(Session session) {
        awaitQuiet(session, 5_000L);
    }

    /** Test support. See {@link #awaitQuiet(Session)}. */
    static void awaitQuiet(Session session, long millis) {
        SessionWriter writer = WRITERS.get(session);
        if (writer != null) {
            writer.awaitQuiet(millis);
        }
    }

    /** Test support: whether this connection still has a writer and a queue. */
    static boolean hasWriter(Session session) {
        return WRITERS.containsKey(session);
    }

    /** Test support: the writer's thread, so a test can assert it ended. Null once retired. */
    static Thread writerThread(Session session) {
        SessionWriter writer = WRITERS.get(session);
        return writer == null ? null : writer.thread;
    }

    /** Test support: how long a writer with nothing to do waits before giving up its thread. */
    static void setRetireAfterMillisForTesting(long millis) {
        retireAfterMillis = millis;
    }

    /** Test support: puts the retire delay back to the shipped default. */
    static void resetForTesting() {
        retireAfterMillis = DEFAULT_RETIRE_AFTER_MILLIS;
    }

    /**
     * Reads a positive integer system property.
     *
     * <p>Same rule as the incoming limits in {@code WasmRmiServerEngine}: an unusable setting is
     * logged and ignored rather than applied, because a zero or negative bound is not a smaller
     * bound, it is a broken one.</p>
     *
     * @return the value, or null when unset, unparseable or not positive
     */
    private static Integer positiveIntProperty(String name) {
        String configured = System.getProperty(name);
        if (configured == null || configured.trim().isEmpty()) {
            return null;
        }
        try {
            int value = Integer.parseInt(configured.trim());
            if (value <= 0) {
                LOG.warning("[zeroz4j] Ignoring " + name + "=" + configured
                        + ": it must be a positive number.");
                return null;
            }
            return value;
        } catch (NumberFormatException ex) {
            LOG.warning("[zeroz4j] Ignoring non-numeric " + name + "='" + configured + "'.");
            return null;
        }
    }

    /**
     * One connection's outbound queue and the single thread that drains it.
     *
     * <p><b>Lock order.</b> Producers reach {@link #offer} from inside
     * {@code ConcurrentHashMap.compute}, so they hold a map bin lock and then this writer's lock.
     * {@link #retire} does the same, in the same order. The drain loop takes only this writer's
     * lock, and never holds it across a socket write. There is therefore no path that takes the
     * two in the other order.</p>
     */
    private static final class SessionWriter implements Runnable {

        private final Session session;
        private final int maxFrames;
        private final int maxBytes;

        private final ReentrantLock lock = new ReentrantLock();
        /** Signalled when a frame is queued. */
        private final Condition arrived = lock.newCondition();
        /** Signalled when the queue empties and no write is in flight. */
        private final Condition quiet = lock.newCondition();

        private final ArrayDeque<byte[]> pending = new ArrayDeque<>();
        private long pendingBytes;
        private boolean writing;
        /** Set once the connection is being closed for falling behind; later frames are dropped. */
        private boolean discarding;

        private final AtomicBoolean closing = new AtomicBoolean();
        private volatile Thread thread;

        SessionWriter(Session session) {
            this.session = session;
            Integer frames = positiveIntProperty(MAX_PENDING_FRAMES_PROPERTY);
            Integer bytes = positiveIntProperty(MAX_PENDING_BYTES_PROPERTY);
            this.maxFrames = frames != null ? frames : DEFAULT_MAX_PENDING_FRAMES;
            this.maxBytes = bytes != null ? bytes : DEFAULT_MAX_PENDING_BYTES;
            if (BOUNDS_REPORTED.compareAndSet(false, true)) {
                LOG.info("[zeroz4j] Outbound queue per connection: at most " + this.maxFrames
                        + " frames and " + this.maxBytes + " bytes ("
                        + MAX_PENDING_FRAMES_PROPERTY + ", " + MAX_PENDING_BYTES_PROPERTY
                        + "). A connection that falls further behind than that is closed.");
            }
        }

        void start() {
            // Named after the connection, so a thread dump says whose write is stuck.
            Thread t = Thread.ofVirtual().name("zeroz-ws-writer-" + session.getId()).unstarted(this);
            thread = t;
            try {
                t.start();
            } catch (RuntimeException | Error ex) {
                // Nothing will drain the queue. Say so plainly: the symptom would otherwise be one
                // connection going quiet and then closing itself a few frames later.
                LOG.warning("[zeroz4j] Could not start the writer thread for session "
                        + session.getId() + ": " + ex.getMessage()
                        + ". Frames for that connection will not be sent.");
                throw ex;
            }
        }

        /**
         * Appends a frame if there is room.
         *
         * <p>An empty queue always takes the frame, however large it is. The bound is on the
         * backlog behind a connection, not on one message: an application that returns a large
         * object graph would otherwise have the connection closed under it, which is a different
         * fault wearing the same symptom.</p>
         *
         * @return false when a bound is reached, which the caller turns into a close
         */
        boolean offer(byte[] frame) {
            lock.lock();
            try {
                if (discarding) {
                    // The connection is already on its way out; dropping is not a new fault.
                    return true;
                }
                if (!pending.isEmpty()
                        && (pending.size() >= maxFrames || pendingBytes + frame.length > maxBytes)) {
                    return false;
                }
                pending.add(frame);
                pendingBytes += frame.length;
                arrived.signal();
                return true;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void run() {
            long idleDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(retireAfterMillis);
            while (true) {
                byte[] frame = null;
                boolean finished = false;
                lock.lock();
                try {
                    while (pending.isEmpty()
                            && session.isOpen()
                            && System.nanoTime() - idleDeadline < 0) {
                        arrived.await(POLL_SLICE_MILLIS, TimeUnit.MILLISECONDS);
                    }
                    if (!session.isOpen()) {
                        finished = true;
                    } else {
                        frame = pending.poll();
                        if (frame != null) {
                            pendingBytes -= frame.length;
                            writing = true;
                            idleDeadline = System.nanoTime()
                                    + TimeUnit.MILLISECONDS.toNanos(retireAfterMillis);
                        }
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    finished = true;
                } finally {
                    // Nothing below this point may run while the lock is held: retire() takes the
                    // map lock, and the one order those two are ever taken in is map then writer.
                    lock.unlock();
                }

                if (finished) {
                    retire(true);
                    return;
                }
                if (frame == null) {
                    if (retire(false)) {
                        return;
                    }
                    continue;
                }

                boolean sent = write(frame);
                lock.lock();
                try {
                    writing = false;
                    quiet.signalAll();
                } finally {
                    lock.unlock();
                }
                if (!sent && !session.isOpen()) {
                    retire(true);
                    return;
                }
            }
        }

        /** Performs the one write this connection is allowed to have in flight. */
        private boolean write(byte[] frame) {
            try {
                session.getBasicRemote().sendBinary(ByteBuffer.wrap(frame));
                return true;
            } catch (Exception e) {
                LOG.warning("[zeroz4j] WS send failed for session " + session.getId() + ": "
                        + e.getMessage());
                return false;
            }
        }

        /**
         * Gives up this writer, unless a frame arrived in the meantime.
         *
         * <p>The decision and the removal happen inside {@code computeIfPresent}, under the same
         * map lock a producer holds while it queues, so a frame cannot be queued into a writer that
         * has just decided to stop.</p>
         *
         * @param force true when the connection is finished either way, so anything still queued is
         *              undeliverable and is dropped
         * @return true when this thread should end
         */
        private boolean retire(boolean force) {
            boolean[] ended = {true};
            WRITERS.computeIfPresent(session, (key, current) -> {
                if (current != this) {
                    return current;
                }
                lock.lock();
                try {
                    if (!force && !pending.isEmpty() && session.isOpen()) {
                        ended[0] = false;
                        return current;
                    }
                    int dropped = pending.size();
                    pending.clear();
                    pendingBytes = 0;
                    writing = false;
                    quiet.signalAll();
                    if (dropped > 0) {
                        LOG.fine("[zeroz4j] Session " + session.getId() + " closed with " + dropped
                                + " frame(s) still queued; they were dropped.");
                    }
                    return null;
                } finally {
                    lock.unlock();
                }
            });
            return ended[0];
        }

        /**
         * Deals with a connection that has fallen past its bound: close it, once, off this thread.
         *
         * <p>The log line has to survive being read months later by somebody who has never seen
         * this class, so it says what was hit, what the setting is called, and what the client
         * does next.</p>
         */
        void overloaded() {
            if (!closing.compareAndSet(false, true)) {
                return;
            }
            int frames;
            long bytes;
            lock.lock();
            try {
                frames = pending.size();
                bytes = pendingBytes;
                discarding = true;
            } finally {
                lock.unlock();
            }
            LOG.warning("[zeroz4j] Closing session " + session.getId()
                    + ": this client is not reading what the server sends it. " + frames
                    + " frame(s) and " + bytes + " byte(s) are already waiting to go out, which is"
                    + " the limit for one connection (" + MAX_PENDING_FRAMES_PROPERTY + "="
                    + maxFrames + ", " + MAX_PENDING_BYTES_PROPERTY + "=" + maxBytes + ")."
                    + " Holding more would use server memory without limit, so the connection is"
                    + " closed with code " + CloseReason.CloseCodes.TRY_AGAIN_LATER.getCode()
                    + ". The client reconnects and re-syncs by itself; it has already missed"
                    + " updates, so continuing to buffer would not have saved it.");

            Thread closer = Thread.ofVirtual().name("zeroz-ws-closer-" + session.getId()).unstarted(() -> {
                try {
                    session.close(new CloseReason(
                            CloseReason.CloseCodes.TRY_AGAIN_LATER, OVERLOADED_CLOSE_REASON));
                } catch (Exception ex) {
                    LOG.warning("[zeroz4j] Could not close overloaded session " + session.getId()
                            + ": " + ex.getMessage());
                } finally {
                    lock.lock();
                    try {
                        pending.clear();
                        pendingBytes = 0;
                        arrived.signalAll();
                        quiet.signalAll();
                    } finally {
                        lock.unlock();
                    }
                }
            });
            // Closing a connection whose send window is shut can block, so it gets its own thread:
            // the producer that found the queue full must not wait for it.
            closer.start();
        }

        /** See {@link WsWrites#awaitQuiet(Session, long)}. */
        void awaitQuiet(long millis) {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
            lock.lock();
            try {
                while (!pending.isEmpty() || writing) {
                    long left = deadline - System.nanoTime();
                    if (left <= 0) {
                        return;
                    }
                    quiet.awaitNanos(left);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        }
    }
}
