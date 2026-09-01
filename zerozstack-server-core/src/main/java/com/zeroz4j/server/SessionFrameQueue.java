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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/**
 * One connection's incoming frames: handled one at a time, in the order they arrived.
 *
 * <h2>Why this exists</h2>
 * A WebSocket delivers one connection's messages in the order they were written, and the container
 * calls {@code @OnMessage} for one connection one message at a time. Before 0.8.0 the framework
 * gave that away: every frame was handed straight to a thread-per-task executor, so up to 32 frames
 * from one browser ran at once and finished in whatever order they happened to finish. Somebody
 * typing into a live-synced field and immediately pressing a button that calls a service could have
 * the button's call decided on the value they had already replaced.
 *
 * <p>Now the transport's order is kept: <b>a frame is handled only after the frame before it on the
 * same connection has been handled.</b> Connections are untouched by each other - each has a queue
 * of its own, and a slow call on one never delays another.</p>
 *
 * <h2>How</h2>
 * A frame is put on this connection's queue and, when its turn comes, handed to the executor. The
 * executor is still thread-per-task: every frame runs on a thread the {@link SessionThreads}
 * factory made for it, which is what lets a Jakarta EE container supply threads carrying its
 * naming, transaction and identity context. Only one such thread is alive for this connection at a
 * time.
 *
 * <h2>The bound is separate from the order</h2>
 * Ordering says <em>when</em> frames run relative to each other; it says nothing about how many may
 * pile up. A connection that writes faster than the server handles would queue without limit, and
 * decoding is where a small message becomes a large object graph. So the queue has a ceiling
 * ({@code zeroz.ws.maxQueuedFramesPerSession}, 32). A frame arriving at a full queue makes the
 * container's read thread wait until there is room - that connection is slowed to the speed the
 * server can handle it, nothing is dropped and no call fails. Other connections are unaffected:
 * each has its own queue, its own ceiling and its own read thread.
 *
 * <h2>The keepalive never comes here</h2>
 * The five-byte ping is answered inline on the read thread in
 * {@link WasmRmiServerEngine#processIncomingBinaryPayload}, before this class is reached, exactly
 * as before. A connection that stops answering pings is killed by the first proxy in the path, and
 * that would happen when it is busiest.
 *
 * <h2>Waiting for a lock does not hold the line</h2>
 * {@link LiveMutexRpcImpl#acquireLock} can wait up to 30 seconds for a lock somebody else holds.
 * Under strict ordering that would stall every later frame from the same person for half a minute -
 * and a lock request has done nothing at that point but read, so nothing it did could land out of
 * order. It therefore calls {@link #handOverBeforeWaiting()} just before it blocks, which lets the
 * frames behind it past and does not take its place back. Nothing else in the framework does this:
 * a service method that takes a lock itself keeps its place, because by then it may already have
 * changed something.
 *
 * <p>Framework-internal.</p>
 */
final class SessionFrameQueue {

    private static final Logger LOG = Logger.getLogger(SessionFrameQueue.class.getName());

    /**
     * The frame running on this thread, and whether it still owes the next frame its turn.
     *
     * <p>Set for the length of one frame. Present only on a thread this class started, so framework
     * code that calls {@link #handOverBeforeWaiting()} from anywhere else - a background virtual
     * thread, a scheduler, a test - does nothing at all.</p>
     */
    private static final ThreadLocal<Turn> TURN = new ThreadLocal<>();

    /** One frame's claim on its connection, handed on exactly once. */
    private static final class Turn {
        private final SessionFrameQueue queue;
        private boolean handedOn;

        Turn(SessionFrameQueue queue) {
            this.queue = queue;
        }

        /** @return true for the one caller that takes the claim; false for every later one */
        boolean take() {
            if (handedOn) {
                return false;
            }
            handedOn = true;
            return true;
        }
    }

    /** Threads for this connection's frames; one is alive at a time. */
    private final ExecutorService threads;

    /** How many frames may be waiting or running for this connection. */
    private final int capacity;

    /**
     * Guards the queue and the counter.
     *
     * <p>A {@link ReentrantLock} and not {@code synchronized}: on JDK 21 a virtual thread that
     * blocks inside a {@code synchronized} block cannot unmount and holds a real platform thread
     * for the whole wait. The read thread waits here when the queue is full, and Helidon runs
     * connections on virtual threads, so {@code synchronized} would turn one connection's
     * backpressure into a carrier thread lost to everybody - the same fault {@link WsWrites} was
     * written to avoid.</p>
     */
    private final ReentrantLock lock = new ReentrantLock();

    /** Signalled when a frame finishes, so a read thread waiting for room wakes up. */
    private final Condition roomFreed = lock.newCondition();

    /** Frames that have not started yet, oldest first. */
    private final Deque<Runnable> queued = new ArrayDeque<>();

    /** Frames waiting plus frames running. Never above {@link #capacity}. */
    private int inFlight;

    /** Whether a frame is running or has been handed to the executor. */
    private boolean busy;

    /** Set once the connection has gone; nothing more is accepted or started. */
    private boolean closed;

    /**
     * @param capacity how many frames may be waiting or running for this connection at once
     */
    SessionFrameQueue(int capacity) {
        this.capacity = Math.max(1, capacity);
        // Threads come from the resolved factory rather than being created here, so a deployment
        // inside a Jakarta EE server can supply a ManagedThreadFactory whose threads carry the
        // container's naming, transaction and identity context. With no provider registered this is
        // a virtual-thread factory, identical to newVirtualThreadPerTaskExecutor().
        this.threads = Executors.newThreadPerTaskExecutor(SessionThreads.factory());
    }

    /**
     * Adds one frame to the back of this connection's queue.
     *
     * <p>Called on the container's read thread. Waits there when the queue is full, which is the
     * backpressure: this one connection is slowed to the speed the server can handle it, and no
     * other connection notices.</p>
     *
     * @param frame the work to do for this frame
     * @return false when the frame was not accepted - the connection is closing, or the read thread
     *         was interrupted while waiting for room
     */
    boolean submit(Runnable frame) {
        lock.lock();
        try {
            while (inFlight >= capacity && !closed) {
                roomFreed.await();
            }
            if (closed) {
                return false;
            }
            inFlight++;
            queued.addLast(frame);
            if (busy) {
                return true;     // the frame running now will start this one when its turn comes
            }
            busy = true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            LOG.warning("[zeroz4j] Dropped incoming message: the read thread was interrupted.");
            return false;
        } finally {
            lock.unlock();
        }
        startNext();
        return true;
    }

    /**
     * Lets the frames behind this one past, without giving up its place in the bound.
     *
     * <p>For framework code that is about to block for a long time on the caller's behalf and has
     * changed nothing yet. Does nothing when called anywhere but inside a frame, and does nothing
     * the second time one frame calls it.</p>
     */
    static void handOverBeforeWaiting() {
        Turn turn = TURN.get();
        if (turn != null && turn.take()) {
            turn.queue.startNext();
        }
    }

    /** Starts the oldest waiting frame, or records that this connection has nothing to do. */
    private void startNext() {
        Runnable next;
        lock.lock();
        try {
            next = closed ? null : queued.pollFirst();
            if (next == null) {
                busy = false;
                return;
            }
        } finally {
            lock.unlock();
        }
        try {
            threads.execute(() -> run(next));
        } catch (RejectedExecutionException shuttingDown) {
            LOG.warning("[zeroz4j] Dropped incoming message because the server is shutting down.");
            finished();
        }
    }

    /** Runs one frame, then hands the connection on to the next. */
    private void run(Runnable frame) {
        Turn turn = new Turn(this);
        TURN.set(turn);
        try {
            frame.run();
        } finally {
            TURN.remove();
            finished();
            if (turn.take()) {
                startNext();
            }
        }
    }

    /** Gives this frame's place in the bound back, and wakes a read thread waiting for room. */
    private void finished() {
        lock.lock();
        try {
            if (inFlight > 0) {
                inFlight--;
            }
            roomFreed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * The connection has gone: throws away what has not started, stops what has, and releases a
     * read thread waiting for room.
     */
    void close() {
        lock.lock();
        try {
            closed = true;
            inFlight -= queued.size();
            queued.clear();
            if (inFlight < 0) {
                inFlight = 0;
            }
            roomFreed.signalAll();
        } finally {
            lock.unlock();
        }
        threads.shutdownNow();
    }

    /** Test support: how many frames are waiting or running for this connection. */
    int inFlight() {
        lock.lock();
        try {
            return inFlight;
        } finally {
            lock.unlock();
        }
    }
}
