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

import com.zeroz4j.api.ObjectMapper;
import com.zeroz4j.api.SlowCall;
import com.zeroz4j.api.SlowCallBoard;
import com.zeroz4j.api.SlowCallStatus;
import com.zeroz4j.api.i18n.FrameworkText;
import jakarta.websocket.Session;

import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This server's slow calls: who owns what, what is running, and what is kept for collection.
 *
 * <h2>Keyed by owner, never by connection</h2>
 *
 * <p>A connection is the wrong key for work that outlives it: every reconnect is a new session id.
 * The owner is the signed-in user within a tenant, or the browser id when nobody signed in. The
 * tenant is part of it because a user name is only unique within one.</p>
 *
 * <p>The board goes out by owner as well, which is why this does not sit on a
 * {@code Signals.scoped} family. {@code Scope.USER} matches on the name alone, so two tenants'
 * users of one name would read each other's boards, and a family keeps every target's value for
 * the life of the process. This is the same arrangement as the language signal: the server holds
 * no signal, and a subscription is answered here.</p>
 *
 * <h2>Bounded</h2>
 *
 * <p>Five limits, read from settings each time so a test can set them per server. Running work per
 * owner is what keeps one browser from starting an unbounded number of model calls. Running work
 * per server is there because an anonymous visitor can throw its cookie away and become a new
 * owner. Results kept per owner and the minutes they are kept for bound the memory. The time limit
 * is optional, and it is what gives a place back when work hangs and ignores its interrupt.</p>
 *
 * <h2>Locks</h2>
 *
 * <p>{@code this} guards the state, and is held only for map and field access. {@link #publishing}
 * orders the frames, so two boards cannot reach a connection newest first. Taken in that order
 * only: nothing takes {@link #publishing} while holding {@code this}.</p>
 *
 * @since 0.11.0
 */
final class SlowCallRegistry {

    private static final Logger LOG = Logger.getLogger(SlowCallRegistry.class.getName());

    static final int DEFAULT_KEEP_MINUTES = 60;
    static final int DEFAULT_MAX_KEPT_PER_CLIENT = 16;
    static final int DEFAULT_MAX_RUNNING_PER_CLIENT = 8;
    static final int DEFAULT_MAX_RUNNING = 1000;

    /** Longest key accepted. It is on every board, so it has to stay small. */
    static final int MAX_KEY_LENGTH = 200;

    /** Where a connection that subscribed to the board carries its owner. */
    private static final String SUBSCRIBED_KEY = "zeroz.slowcalls.owner";

    /** Shortest gap between two progress boards to one owner. */
    private static final long PROGRESS_GAP_MILLIS = 250L;

    /** How often expired results are looked for. */
    private static final long SWEEP_MILLIS = 60_000L;

    /** How often the time limit is looked at, when there is one. */
    private static final long LIMIT_CHECK_MILLIS = 5_000L;

    /** Who a slow call belongs to: a user within a tenant, or a browser. Never both. */
    record Owner(String tenant, String user, String browser) {

        /** @return the caller on this thread, or null when it has no sign-in and no browser id */
        static Owner ofCaller() {
            return of(RmiRequestContext.getPrincipal(), RmiRequestContext.getTenantId(),
                    RmiRequestContext.getClientId());
        }

        /** @return the party on a connection, or null when it has no sign-in and no browser id */
        static Owner ofSession(Session session) {
            Map<String, Object> properties = session.getUserProperties();
            return of((Principal) properties.get(RmiEndpointConfigurator.PRINCIPAL_KEY),
                    (String) properties.get(RmiEndpointConfigurator.TENANT_KEY),
                    (String) properties.get(RmiEndpointConfigurator.CLIENT_KEY));
        }

        private static Owner of(Principal principal, String tenant, String browser) {
            if (principal != null) {
                return new Owner(tenant, principal.getName(), null);
            }
            return browser != null ? new Owner(null, null, browser) : null;
        }
    }

    /** A piece of work with nothing to return, for the request scope to wrap. */
    @FunctionalInterface
    interface Body {
        void run() throws Exception;
    }

    /** Runs a {@link Body} in a CDI request scope. */
    @FunctionalInterface
    interface RequestScope {
        void run(Body body) throws Exception;
    }

    /** Everything one owner has. */
    private static final class Book {
        private final Map<String, Job> byKey = new LinkedHashMap<>();
        /**
         * The work holding a running slot. A canceled job stays here until its thread has really
         * ended, or until it passes the time limit, which is the one thing that frees a slot early.
         */
        private final Set<Job> holdingSlot = new HashSet<>();
        private long lastPublishedMillis;
        private boolean flushWaiting;
    }

    /** One run of the work. Its mutable fields are guarded by the registry. */
    private final class Job implements SlowCallJob {
        private final String id = UUID.randomUUID().toString();
        private final String key;
        private final Owner owner;
        private final Locale locale;
        private final long startedMillis = System.currentTimeMillis();
        private SlowCallStatus status = SlowCallStatus.RUNNING;
        private int percent = -1;
        private String text;
        private String message;
        private Object value;
        private Thread thread;
        private long endedMillis;
        private volatile boolean canceled;

        Job(String key, Owner owner, Locale locale) {
            this.key = key;
            this.owner = owner;
            this.locale = locale;
        }

        @Override
        public void progress(int newPercent) {
            progress(newPercent, null);
        }

        @Override
        public void progress(int newPercent, String newText) {
            reportProgress(this, newPercent < 0 ? -1 : Math.min(newPercent, 100), newText);
        }

        @Override
        public boolean isCanceled() {
            return canceled;
        }
    }

    private final ServerRuntime runtime;

    /** Guarded by {@code this}. */
    private final Map<Owner, Book> books = new HashMap<>();
    /** Guarded by {@code this}. */
    private int runningThreads;
    /** Guarded by {@code this}. */
    private Thread sweeper;
    /** Guarded by {@code this}. */
    private boolean shutDown;

    private final ReentrantLock publishing = new ReentrantLock();

    /** The board holds strings and numbers only, so it needs no server's handles. */
    private final ObjectMapper boardMapper = new ObjectMapper();

    SlowCallRegistry(ServerRuntime runtime) {
        this.runtime = runtime;
    }

    // ------------------------------------------------------------------ starting

    /**
     * @param key   what the work is called
     * @param work  the work
     * @param scope the CDI request scope to run it in
     * @return the handle
     * @see SlowCalls#start(String, SlowCallWork)
     */
    <T> SlowCall<T> start(String key, SlowCallWork<T> work, RequestScope scope) {
        if (key == null || key.isEmpty() || key.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("A slow call needs a key of 1 to " + MAX_KEY_LENGTH
                    + " characters; this one is "
                    + (key == null ? "null" : key.length() + " characters") + ".");
        }
        if (work == null) {
            throw new IllegalArgumentException("Slow call '" + key + "' was given no work.");
        }
        Owner owner = Owner.ofCaller();
        if (owner == null) {
            throw new IllegalStateException("Slow call '" + key + "' has no owner. Start it inside "
                    + "a service method, on a connection with a sign-in or a browser id. A result "
                    + "with no owner could never be collected.");
        }
        // Read here, on the caller's thread, because the work's own thread has none of this.
        Principal principal = RmiRequestContext.getPrincipal();
        Set<String> roles = RmiRequestContext.getRoles();
        String tenant = RmiRequestContext.getTenantId();
        String browser = RmiRequestContext.getClientId();
        Locale locale = RmiRequestContext.getLocale();

        Job job;
        synchronized (this) {
            if (shutDown) {
                throw new IllegalStateException("Slow call '" + key + "' cannot start: server '"
                        + runtime.name() + "' has been shut down.");
            }
            Book book = books.computeIfAbsent(owner, ignored -> new Book());
            Job existing = book.byKey.get(key);
            if (existing != null && existing.status == SlowCallStatus.RUNNING) {
                return new SlowCall<>(key, existing.id);
            }
            dropExpired(book, System.currentTimeMillis());
            if (book.holdingSlot.size() >= maxRunningPerClient()) {
                discardIfEmpty(owner, book);
                throw new ClientVisibleException(FrameworkText.slowCallTooMany());
            }
            if (runningThreads >= maxRunning()) {
                discardIfEmpty(owner, book);
                throw new ClientVisibleException(FrameworkText.slowCallServerBusy());
            }
            job = new Job(key, owner, locale);
            Job finalJob = job;
            Thread thread = SessionThreads.factory().newThread(() -> {
                RmiRequestContext.setContext(principal, roles, null, tenant, browser, locale);
                try {
                    runWork(finalJob, work, scope, locale);
                } finally {
                    RmiRequestContext.clear();
                }
            });
            job.thread = thread;
            book.byKey.remove(key);
            book.byKey.put(key, job);
            book.holdingSlot.add(job);
            runningThreads++;
            thread.start();
            if (maxMinutes() > 0) {
                startSweeperIfNeeded();
            }
        }
        // Before the service method returns, so the board reaches the browser ahead of the handle.
        publish(owner);
        return new SlowCall<>(key, job.id);
    }

    private <T> void runWork(Job job, SlowCallWork<T> work, RequestScope scope, Locale locale) {
        Object[] result = new Object[1];
        Throwable failure = null;
        try {
            scope.run(() -> result[0] = work.run(job));
        } catch (Throwable thrown) {
            failure = thrown;
        }
        // A cancel that arrived just as the work ended has nothing left to stop.
        Thread.interrupted();
        String sentence = failure == null ? null : describeFailure(job, failure, locale);
        synchronized (this) {
            job.thread = null;
            Book book = books.get(job.owner);
            if (book == null) {
                return; // Shut down while this ran, or past the limit and its owner has gone.
            }
            // False for work that passed the limit: its slot went back then, and may be somebody
            // else's by now. Its status is no longer RUNNING either, so this late return changes
            // nothing the browser can see.
            if (book.holdingSlot.remove(job)) {
                runningThreads--;
            }
            if (job.status == SlowCallStatus.RUNNING) {
                job.status = failure == null ? SlowCallStatus.FINISHED : SlowCallStatus.FAILED;
                job.value = failure == null ? result[0] : null;
                job.message = sentence;
                job.endedMillis = System.currentTimeMillis();
            }
            dropOverCeiling(book);
            startSweeperIfNeeded();
        }
        publish(job.owner);
    }

    /**
     * What the browser is told about a failure: the same decision and the same log line as a failed
     * call. Work that was asked to stop is expected to fail, and is not an error of anything.
     */
    private String describeFailure(Job job, Throwable failure, Locale locale) {
        if (job.canceled || isShutDown()) {
            LOG.fine("[zeroz4j] Slow call '" + job.key + "' ended after a stop: " + failure);
            return null;
        }
        String reference = WasmRmiServerEngine.newErrorReference();
        LOG.log(Level.SEVERE, "[zeroz4j] Slow call '" + job.key + "' failed [ref " + reference
                + "]: " + failure.getMessage(), failure);
        return WasmRmiServerEngine.clientSafeMessage(failure, reference, locale);
    }

    // ------------------------------------------------------------------ while it runs

    private void reportProgress(Job job, int percent, String text) {
        Owner sendNow = null;
        long wait = 0;
        synchronized (this) {
            if (job.status != SlowCallStatus.RUNNING
                    || (job.percent == percent && java.util.Objects.equals(job.text, text))) {
                return;
            }
            job.percent = percent;
            job.text = text;
            Book book = books.get(job.owner);
            if (book == null || book.flushWaiting) {
                return; // A send is already on its way and will carry this.
            }
            wait = book.lastPublishedMillis + PROGRESS_GAP_MILLIS - System.currentTimeMillis();
            if (wait <= 0) {
                sendNow = job.owner;
            } else {
                book.flushWaiting = true;
            }
        }
        if (sendNow != null) {
            publish(sendNow);
            return;
        }
        // The last progress must arrive even when nothing follows it for an hour, and a tight loop
        // of progress calls must not reach the 256 frames that close a connection.
        long delay = wait;
        Owner owner = job.owner;
        Thread.ofVirtual().name("zeroz-slowcall-progress").start(() -> {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
            }
            synchronized (this) {
                Book book = books.get(owner);
                if (book != null) {
                    book.flushWaiting = false;
                }
            }
            publish(owner);
        });
    }

    /**
     * Asks running work to stop.
     *
     * @param key   the key
     * @param owner who is asking
     */
    void cancel(String key, Owner owner) {
        Thread running;
        synchronized (this) {
            Book book = owner == null ? null : books.get(owner);
            Job job = book == null ? null : book.byKey.get(key);
            if (job == null || job.status != SlowCallStatus.RUNNING) {
                return;
            }
            job.canceled = true;
            job.status = SlowCallStatus.CANCELED;
            job.percent = -1;
            job.text = null;
            job.endedMillis = System.currentTimeMillis();
            running = job.thread;
            startSweeperIfNeeded();
        }
        if (running != null) {
            running.interrupt();
        }
        publish(owner);
    }

    /**
     * Hands over the value of finished work. The result stays, so another tab can fetch it too.
     *
     * @param id    the run
     * @param owner who is asking
     * @return what the work returned
     * @throws ClientVisibleException when that owner has no finished run under that id
     */
    Object collect(String id, Owner owner) {
        synchronized (this) {
            Book book = owner == null ? null : books.get(owner);
            if (book != null) {
                for (Job job : book.byKey.values()) {
                    if (job.id.equals(id) && job.status == SlowCallStatus.FINISHED) {
                        return job.value;
                    }
                }
            }
        }
        throw new ClientVisibleException(FrameworkText.slowCallLost());
    }

    // ------------------------------------------------------------------ the board

    /**
     * Answers a subscription and from now on keeps that connection told.
     *
     * @param session the connection that subscribed
     */
    void subscribe(Session session) {
        Owner owner = Owner.ofSession(session);
        publishing.lock();
        try {
            if (owner != null) {
                session.getUserProperties().put(SUBSCRIBED_KEY, owner);
            }
            WasmRmiServerEngine.sendSignalUpdate(session, SlowCallBoard.SIGNAL_NAME, boardOf(owner),
                    boardMapper);
        } finally {
            publishing.unlock();
        }
    }

    private void publish(Owner owner) {
        publishing.lock();
        try {
            SlowCallBoard board = boardOf(owner);
            for (Session session : runtime.sessions()) {
                if (owner.equals(session.getUserProperties().get(SUBSCRIBED_KEY))) {
                    WasmRmiServerEngine.sendSignalUpdate(session, SlowCallBoard.SIGNAL_NAME, board,
                            boardMapper);
                }
            }
        } finally {
            publishing.unlock();
        }
    }

    private synchronized SlowCallBoard boardOf(Owner owner) {
        Book book = owner == null ? null : books.get(owner);
        List<SlowCallBoard.Entry> entries = new ArrayList<>();
        if (book != null) {
            book.lastPublishedMillis = System.currentTimeMillis();
            for (Job job : book.byKey.values()) {
                entries.add(new SlowCallBoard.Entry(job.id, job.key, job.status, job.percent,
                        job.text, job.message));
            }
        }
        return new SlowCallBoard(entries);
    }

    // ------------------------------------------------------------------ housekeeping

    /** Drops results past their time from one owner. Guarded by {@code this}. */
    private boolean dropExpired(Book book, long nowMillis) {
        long cutoff = nowMillis - keepMillis();
        return book.byKey.values().removeIf(job -> job.status != SlowCallStatus.RUNNING
                && job.endedMillis <= cutoff);
    }

    /** Drops the oldest ended work past the ceiling. Guarded by {@code this}. */
    private void dropOverCeiling(Book book) {
        int ended = 0;
        for (Job job : book.byKey.values()) {
            if (job.status != SlowCallStatus.RUNNING) {
                ended++;
            }
        }
        int ceiling = maxKeptPerClient();
        // In start order, which is near enough to end order and needs no sort for a list this small.
        for (Iterator<Job> jobs = book.byKey.values().iterator();
                ended > ceiling && jobs.hasNext(); ) {
            if (jobs.next().status != SlowCallStatus.RUNNING) {
                jobs.remove();
                ended--;
            }
        }
    }

    /** Forgets an owner with nothing left. Guarded by {@code this}. */
    private void discardIfEmpty(Owner owner, Book book) {
        if (book.byKey.isEmpty() && book.holdingSlot.isEmpty()) {
            books.remove(owner, book);
        }
    }

    /**
     * Stops work past the time limit and frees its slot, whether or not the work takes any notice.
     * Guarded by {@code this}. The interrupts are left for the caller, outside the lock.
     *
     * @return true when this owner's board changed
     */
    private boolean stopOverLimit(Book book, long nowMillis, List<Thread> toInterrupt) {
        int minutes = maxMinutes();
        if (minutes <= 0) {
            return false;
        }
        long cutoff = nowMillis - minutes * 60_000L;
        boolean changed = false;
        for (Iterator<Job> jobs = book.holdingSlot.iterator(); jobs.hasNext(); ) {
            Job job = jobs.next();
            if (job.startedMillis > cutoff) {
                continue;
            }
            jobs.remove();
            runningThreads--;
            if (job.thread != null) {
                toInterrupt.add(job.thread);
            }
            if (job.status == SlowCallStatus.RUNNING) {
                job.canceled = true;
                job.status = SlowCallStatus.FAILED;
                job.percent = -1;
                job.text = null;
                job.message = ServerMessages.render(FrameworkText.slowCallTimedOut(), job.locale);
                job.endedMillis = nowMillis;
                changed = true;
                LOG.warning("[zeroz4j] Slow call '" + job.key + "' passed the limit of " + minutes
                        + " minutes (" + ServerSettings.SLOW_CALL_MAX_MINUTES + ") and was stopped.");
            }
        }
        return changed;
    }

    /**
     * Sweeps every owner, as of a moment in time.
     *
     * @param nowMillis the time to sweep as of
     * @return how many owners lost something or had work stopped
     */
    int sweep(long nowMillis) {
        List<Owner> changed = new ArrayList<>();
        List<Thread> toInterrupt = new ArrayList<>();
        synchronized (this) {
            for (Map.Entry<Owner, Book> entry : new ArrayList<>(books.entrySet())) {
                boolean stopped = stopOverLimit(entry.getValue(), nowMillis, toInterrupt);
                if (dropExpired(entry.getValue(), nowMillis) | stopped) {
                    changed.add(entry.getKey());
                }
                discardIfEmpty(entry.getKey(), entry.getValue());
            }
        }
        for (Thread thread : toInterrupt) {
            thread.interrupt();
        }
        for (Owner owner : changed) {
            publish(owner);
        }
        return changed.size();
    }

    /** One thread while anything is kept, none otherwise. Guarded by {@code this}. */
    private void startSweeperIfNeeded() {
        if (sweeper == null && !shutDown) {
            sweeper = Thread.ofVirtual().name("zeroz-slowcall-sweeper").start(this::sweepLoop);
        }
    }

    private void sweepLoop() {
        try {
            while (true) {
                Thread.sleep(maxMinutes() > 0
                        ? LIMIT_CHECK_MILLIS : Math.min(SWEEP_MILLIS, keepMillis()));
                sweep(System.currentTimeMillis());
                synchronized (this) {
                    if (books.isEmpty() || shutDown) {
                        sweeper = null;
                        return;
                    }
                }
            }
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        }
    }

    /** Stops all running work and forgets everything. Called when the server shuts down. */
    void shutDown() {
        List<Thread> running = new ArrayList<>();
        Thread sweeping;
        synchronized (this) {
            shutDown = true;
            for (Book book : books.values()) {
                for (Job job : book.holdingSlot) {
                    if (job.thread != null) {
                        running.add(job.thread);
                    }
                }
            }
            books.clear();
            runningThreads = 0;
            sweeping = sweeper;
            sweeper = null;
        }
        for (Thread thread : running) {
            thread.interrupt();
        }
        if (sweeping != null) {
            sweeping.interrupt();
        }
    }

    private synchronized boolean isShutDown() {
        return shutDown;
    }

    /** @return how many threads are running slow calls on this server */
    synchronized int runningCount() {
        return runningThreads;
    }

    /** @return the time limit in minutes; 0, written out or left unset, means none */
    private int maxMinutes() {
        String configured = runtime.config().get(ServerSettings.SLOW_CALL_MAX_MINUTES);
        return configured == null || "0".equals(configured.trim()) ? 0
                : runtime.config().positiveInt(ServerSettings.SLOW_CALL_MAX_MINUTES, 0);
    }

    private long keepMillis() {
        return runtime.config().positiveInt(ServerSettings.SLOW_CALL_KEEP_MINUTES,
                DEFAULT_KEEP_MINUTES) * 60_000L;
    }

    private int maxKeptPerClient() {
        return runtime.config().positiveInt(ServerSettings.SLOW_CALL_MAX_KEPT_PER_CLIENT,
                DEFAULT_MAX_KEPT_PER_CLIENT);
    }

    private int maxRunningPerClient() {
        return runtime.config().positiveInt(ServerSettings.SLOW_CALL_MAX_RUNNING_PER_CLIENT,
                DEFAULT_MAX_RUNNING_PER_CLIENT);
    }

    private int maxRunning() {
        return runtime.config().positiveInt(ServerSettings.SLOW_CALL_MAX_RUNNING,
                DEFAULT_MAX_RUNNING);
    }
}
