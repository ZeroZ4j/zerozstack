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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Who may lock an object, and what the lock table costs.
 *
 * <h2>What this protects</h2>
 *
 * <p>The lock service is reachable by any connected browser, signed in or not. Before this, it took
 * any name at all: a caller could lock objects it had never been shown — making everyone who really
 * needed them wait the full timeout and then fail — and every invented name left a permanent entry
 * in the server's lock table, so a loop over random names filled memory until the process died.</p>
 *
 * <p>Two things fix it. A lock request is granted only for an object the server actually sent that
 * browser, so an invented name never reaches the table. And an entry lives only while somebody holds
 * the lock or waits for it, so a finished edit leaves nothing behind.</p>
 *
 * <p>The eviction is the delicate half: an entry removed while somebody holds or awaits it would be
 * a correctness bug worse than the leak. That case is hammered concurrently below.</p>
 */
public class LiveMutexSecurityTest {

    private LiveMutexManager manager;
    private LiveMutexRpcImpl rpc;

    /**
     * One server per test. Static because the helpers below are, and they build connections on it;
     * each test replaces it, so nothing carries over.
     */
    private static ServerRuntime server;

    @BeforeEach
    public void setup() {
        server = new ServerRuntime();
        manager = new LiveMutexManager();
        manager.runtime = server;
        rpc = new LiveMutexRpcImpl();
        rpc.manager = manager;
        rpc.runtime = server;
        System.clearProperty(LiveMutexManager.WAIT_SECONDS_PROPERTY);
        System.clearProperty(LiveMutexRpcImpl.REQUIRE_AUTHENTICATION_PROPERTY);
    }

    @AfterEach
    public void teardown() {
        RmiRequestContext.clear();
        server.shutDown();
        System.clearProperty(LiveMutexManager.WAIT_SECONDS_PROPERTY);
        System.clearProperty(LiveMutexRpcImpl.REQUIRE_AUTHENTICATION_PROPERTY);
    }

    /** A connection carrying a browser id, the way a real one does. */
    private static WasmRmiServerEngineTest.FakeSession connection(String sessionId, String browserId) {
        WasmRmiServerEngineTest.FakeSession session =
                new WasmRmiServerEngineTest.FakeSession(sessionId);
        session.getUserProperties().put(RmiEndpointConfigurator.CLIENT_KEY, browserId);
        server.addSessionForTesting(session);
        server.disclosures().sessionOpened(session);
        return session;
    }

    /** Binds this thread to a connection, the way frame dispatch does before calling a service. */
    private static void callingAs(String sessionId, String browserId, Principal principal) {
        RmiRequestContext.setContext(principal, Collections.emptySet(), sessionId, null, browserId);
    }

    private static void callingAs(String sessionId, String browserId) {
        callingAs(sessionId, browserId, null);
    }

    // ------------------------------------------------------------- the entitlement rule

    @Test
    @DisplayName("a session that was never sent an object cannot lock it, and is told why")
    public void anUndisclosedObjectCannotBeLocked() {
        connection("s1", "browser-1");
        callingAs("s1", "browser-1");

        SecurityException refusal =
                assertThrows(SecurityException.class, () -> rpc.acquireLock("handle-nobody-sent-me"));

        assertTrue(refusal.getMessage().contains("never sent"),
                "the refusal must say what happened: " + refusal.getMessage());
        assertTrue(refusal.getMessage().contains("fetch the item again"),
                "and what to do about it: " + refusal.getMessage());
        assertTrue(refusal.getMessage().contains("handle-nobody-sent-me"),
                "and which item it was about: " + refusal.getMessage());
        assertEquals(0, manager.trackedLockCount(),
                "a name nobody was sent must never reach the lock table");
    }

    @Test
    @DisplayName("the refusal reaches the caller word for word")
    public void theRefusalIsReadableByTheCaller() {
        connection("s1", "browser-1");
        callingAs("s1", "browser-1");

        SecurityException refusal =
                assertThrows(SecurityException.class, () -> rpc.acquireLock("handle-x"));

        // SecurityException is one of the types the dispatcher lets through unchanged; anything else
        // is replaced with a generic sentence and a reference code, which would hide the reason.
        assertEquals(refusal.getMessage(),
                WasmRmiServerEngine.clientSafeMessage(refusal, "ref-1234"));
    }

    @Test
    @DisplayName("a lock request naming nothing is refused")
    public void anEmptyHandleIsRefused() {
        connection("s1", "browser-1");
        callingAs("s1", "browser-1");

        assertThrows(SecurityException.class, () -> rpc.acquireLock(null));
        assertThrows(SecurityException.class, () -> rpc.acquireLock(""));
        assertEquals(0, manager.trackedLockCount());
    }

    @Test
    @DisplayName("a session that was sent an object can lock it")
    public void aDisclosedObjectCanBeLocked() {
        connection("s1", "browser-1");
        server.disclosures().record("s1", "handle-a");
        callingAs("s1", "browser-1");

        rpc.acquireLock("handle-a");

        assertEquals("session:s1", manager.ownerOf("handle-a"),
                "the connection that asked is the holder");
    }

    @Test
    @DisplayName("and can still lock it after a reconnect: new session id, same browser")
    public void aDisclosedObjectCanStillBeLockedAfterAReconnect() {
        connection("s1", "browser-1");
        server.disclosures().record("s1", "handle-a");
        callingAs("s1", "browser-1");
        rpc.acquireLock("handle-a");
        rpc.releaseLock("handle-a");

        // The socket drops. A reconnect is a brand-new session; the browser is the same.
        server.disclosures().sessionClosed("s1");
        connection("s2", "browser-1");
        callingAs("s2", "browser-1");

        rpc.acquireLock("handle-a");

        assertEquals("session:s2", manager.ownerOf("handle-a"),
                "the record is kept per browser, so a reconnect does not lose the entitlement");
    }

    @Test
    @DisplayName("one browser's objects are not lockable by another browser")
    public void oneBrowserCannotLockAnothersObject() {
        connection("s1", "browser-insider");
        connection("s2", "browser-outsider");
        server.disclosures().record("s1", "handle-a");

        callingAs("s2", "browser-outsider");
        assertThrows(SecurityException.class, () -> rpc.acquireLock("handle-a"));

        callingAs("s1", "browser-insider");
        rpc.acquireLock("handle-a");
        assertEquals("session:s1", manager.ownerOf("handle-a"));
    }

    @Test
    @DisplayName("a client with no browser id is remembered for its connection")
    public void aConnectionWithNoBrowserIdStillWorks() {
        // A non-browser client carries no cookie, so its record is kept under the session id.
        WasmRmiServerEngineTest.FakeSession headless =
                new WasmRmiServerEngineTest.FakeSession("s1");
        server.disclosures().sessionOpened(headless);
        server.disclosures().record("s1", "handle-a");
        callingAs("s1", null);

        rpc.acquireLock("handle-a");

        assertEquals("session:s1", manager.ownerOf("handle-a"));
    }

    @Test
    @DisplayName("releasing is protected by ownership, not by the disclosure record")
    public void releaseStillWorksWhenTheRecordIsGone() {
        connection("s1", "browser-1");
        server.disclosures().record("s1", "handle-a");
        callingAs("s1", "browser-1");
        rpc.acquireLock("handle-a");

        // The record expires or is evicted while the edit is in progress. Releasing must still work,
        // or the lock would be stranded until the session closed.
        server.disclosures().clear();

        rpc.releaseLock("handle-a");

        assertNull(manager.ownerOf("handle-a"), "the lock is free again");
        assertEquals(0, manager.trackedLockCount());
    }

    @Test
    @DisplayName("one session cannot release another session's lock")
    public void aReleaseFromANonHolderDoesNothing() {
        connection("s1", "browser-1");
        connection("s2", "browser-2");
        server.disclosures().record("s1", "handle-a");
        server.disclosures().record("s2", "handle-a");

        callingAs("s1", "browser-1");
        rpc.acquireLock("handle-a");

        callingAs("s2", "browser-2");
        rpc.releaseLock("handle-a");

        assertEquals("session:s1", manager.ownerOf("handle-a"),
                "the holder still holds it");
    }

    @Test
    @DisplayName("releasing something never locked does not blow up")
    public void releasingAnUnknownHandleIsHarmless() {
        connection("s1", "browser-1");
        callingAs("s1", "browser-1");

        rpc.releaseLock("handle-never-locked");   // used to be a null-pointer waiting to happen

        assertEquals(0, manager.trackedLockCount());
    }

    // ------------------------------------------------------------- the optional login gate

    @Test
    @DisplayName("locking without a login works by default, and can be switched off")
    public void authenticationIsOptIn() {
        connection("s1", "browser-1");
        server.disclosures().record("s1", "handle-a");
        callingAs("s1", "browser-1");

        rpc.acquireLock("handle-a");                 // anonymous, by default allowed
        rpc.releaseLock("handle-a");

        System.setProperty(LiveMutexRpcImpl.REQUIRE_AUTHENTICATION_PROPERTY, "true");
        SecurityException refusal =
                assertThrows(SecurityException.class, () -> rpc.acquireLock("handle-a"));
        assertTrue(refusal.getMessage().contains("Sign in"), refusal.getMessage());

        callingAs("s1", "browser-1", () -> "alice");
        rpc.acquireLock("handle-a");                 // signed in, allowed again
        assertEquals("session:s1", manager.ownerOf("handle-a"));
    }

    // ------------------------------------------------------------- contention and the wait

    @Test
    @DisplayName("the second session waits, then gets the lock when the first releases")
    public void theSecondSessionWaitsAndThenGetsIt() throws Exception {
        connection("s1", "browser-1");
        connection("s2", "browser-2");
        server.disclosures().record("s1", "handle-a");
        server.disclosures().record("s2", "handle-a");

        callingAs("s1", "browser-1");
        rpc.acquireLock("handle-a");

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch got = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread second = new Thread(() -> {
            callingAs("s2", "browser-2");
            started.countDown();
            try {
                rpc.acquireLock("handle-a");
                got.countDown();
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        second.start();

        assertTrue(started.await(5, TimeUnit.SECONDS));
        assertFalse(got.await(300, TimeUnit.MILLISECONDS),
                "the second session must wait while the first holds it");
        assertEquals("session:s1", manager.ownerOf("handle-a"));

        rpc.releaseLock("handle-a");                 // still on the first session's thread

        assertTrue(got.await(5, TimeUnit.SECONDS), "the waiter must be handed the lock");
        second.join(5000);
        assertNull(failure.get(), String.valueOf(failure.get()));
        assertEquals("session:s2", manager.ownerOf("handle-a"));
    }

    @Test
    @DisplayName("waiting too long fails with a message that says how long and how to change it")
    public void theWaitIsConfigurableAndTheTimeoutExplainsItself() {
        System.setProperty(LiveMutexManager.WAIT_SECONDS_PROPERTY, "1");
        connection("s1", "browser-1");
        server.disclosures().record("s1", "handle-a");
        callingAs("s1", "browser-1");

        manager.lock("handle-a", "thread:someone-else");   // held by server-side code

        long before = System.nanoTime();
        ClientVisibleException timeout =
                assertThrows(ClientVisibleException.class, () -> rpc.acquireLock("handle-a"));
        long waitedMillis = (System.nanoTime() - before) / 1_000_000L;

        assertTrue(waitedMillis >= 900, "it waited the configured second, not longer: " + waitedMillis);
        assertTrue(waitedMillis < 10_000, "and gave up rather than hanging: " + waitedMillis);
        assertTrue(timeout.getMessage().contains("after 1 second"), timeout.getMessage());
        assertTrue(timeout.getMessage().contains("Nothing was changed"), timeout.getMessage());
        assertTrue(timeout.getMessage().contains(LiveMutexManager.WAIT_SECONDS_PROPERTY),
                "the message names the property to change: " + timeout.getMessage());

        // ClientVisibleException is delivered word for word, so the caller reads this, not a code.
        assertEquals(timeout.getMessage(),
                WasmRmiServerEngine.clientSafeMessage(timeout, "ref-1234"));

        assertEquals(1, manager.trackedLockCount(),
                "the caller that gave up left nothing behind; only the real holder's entry remains");
    }

    @Test
    @DisplayName("the default wait is still thirty seconds")
    public void theDefaultWaitIsUnchanged() {
        assertEquals(30, LiveMutexManager.DEFAULT_WAIT_SECONDS);
        assertEquals(30, manager.configuredWaitSeconds(),
                "with nothing configured, the wait is what it always was");

        System.setProperty(LiveMutexManager.WAIT_SECONDS_PROPERTY, "5");
        assertEquals(5, manager.configuredWaitSeconds());

        System.setProperty(LiveMutexManager.WAIT_SECONDS_PROPERTY, "not a number");
        assertEquals(30, manager.configuredWaitSeconds(), "nonsense falls back");

        System.setProperty(LiveMutexManager.WAIT_SECONDS_PROPERTY, "0");
        assertEquals(30, manager.configuredWaitSeconds(), "so does zero");
    }

    // ------------------------------------------------------------- the bound on the table

    @Test
    @DisplayName("a released lock leaves no permanent entry")
    public void aReleasedLockLeavesNothingBehind() {
        connection("s1", "browser-1");
        callingAs("s1", "browser-1");
        for (int i = 0; i < 500; i++) {
            server.disclosures().record("s1", "handle-" + i);
        }

        for (int i = 0; i < 500; i++) {
            rpc.acquireLock("handle-" + i);
            rpc.releaseLock("handle-" + i);
        }

        assertEquals(0, manager.trackedLockCount(),
                "five hundred finished edits must leave an empty table");
    }

    @Test
    @DisplayName("a disconnect still releases everything that session held")
    public void aDisconnectReleasesEverything() {
        connection("s1", "browser-1");
        connection("s2", "browser-2");
        callingAs("s1", "browser-1");
        for (int i = 0; i < 20; i++) {
            server.disclosures().record("s1", "handle-" + i);
            rpc.acquireLock("handle-" + i);
        }
        server.disclosures().record("s2", "handle-99");
        callingAs("s2", "browser-2");
        rpc.acquireLock("handle-99");

        manager.releaseAll("session:s1");            // what the endpoint does on close

        for (int i = 0; i < 20; i++) {
            assertNull(manager.ownerOf("handle-" + i), "handle-" + i + " must be free");
        }
        assertEquals("session:s2", manager.ownerOf("handle-99"),
                "the other session keeps what it holds");
        assertEquals(1, manager.trackedLockCount(),
                "only the surviving lock is remembered");
    }

    /**
     * The dangerous case. Entries are removed when the last interested caller leaves, so a race
     * between "the last one leaves" and "a new one arrives" could either drop an entry somebody is
     * waiting on — stranding them for the full timeout — or hand two callers the same lock at once.
     *
     * <p>Many threads take and give up the same few locks as fast as they can. Two things are
     * asserted: never two holders of one lock at the same instant, and never a timeout, since no
     * hold here lasts more than a moment. The table must be empty when they all stop.</p>
     */
    @Test
    @DisplayName("eviction cannot strand a waiter or hand out a lock twice")
    public void evictionIsSafeUnderContention() throws Exception {
        System.setProperty(LiveMutexManager.WAIT_SECONDS_PROPERTY, "10");

        final int threads = 24;
        final int rounds = 400;
        final List<String> handles = List.of("handle-a", "handle-b", "handle-c");

        AtomicInteger[] insiders = new AtomicInteger[handles.size()];
        for (int i = 0; i < insiders.length; i++) {
            insiders[i] = new AtomicInteger();
        }
        AtomicInteger doubleHolds = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            final String owner = "session:hammer-" + t;
            Thread worker = new Thread(() -> {
                try {
                    go.await();
                    for (int r = 0; r < rounds; r++) {
                        int which = r % handles.size();
                        String handle = handles.get(which);
                        manager.lock(handle, owner);
                        if (insiders[which].incrementAndGet() != 1) {
                            doubleHolds.incrementAndGet();
                        }
                        Thread.yield();
                        insiders[which].decrementAndGet();
                        manager.unlock(handle, owner);
                        completed.incrementAndGet();
                    }
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                } finally {
                    done.countDown();
                }
            });
            worker.setDaemon(true);
            worker.start();
        }

        go.countDown();
        if (!done.await(120, TimeUnit.SECONDS)) {
            fail("threads are still waiting for locks that should have been handed over");
        }

        assertNull(failure.get(), "nobody timed out or failed: " + failure.get());
        assertEquals(0, doubleHolds.get(), "two owners must never hold one lock at the same time");
        assertEquals(threads * rounds, completed.get(), "every round finished");
        assertEquals(0, manager.trackedLockCount(),
                "and the table is empty again once nobody holds or awaits anything");
    }

    /**
     * The same race from the other side: one thread takes and drops a lock in a tight loop while
     * another waits for it. The waiter must always end up holding it, never be dropped because the
     * entry it is queued on was removed.
     */
    @Test
    @DisplayName("an entry is never removed while somebody is waiting on it")
    public void aWaiterIsNeverDropped() throws Exception {
        System.setProperty(LiveMutexManager.WAIT_SECONDS_PROPERTY, "10");

        AtomicInteger handovers = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(2);

        Runnable churn = () -> {
            try {
                String owner = "session:" + Thread.currentThread().getName();
                for (int i = 0; i < 3000; i++) {
                    manager.lock("handle-a", owner);
                    assertEquals(owner, manager.ownerOf("handle-a"));
                    manager.unlock("handle-a", owner);
                    handovers.incrementAndGet();
                }
            } catch (Throwable e) {
                failure.compareAndSet(null, e);
            } finally {
                done.countDown();
            }
        };

        Thread a = new Thread(churn, "a");
        Thread b = new Thread(churn, "b");
        a.setDaemon(true);
        b.setDaemon(true);
        a.start();
        b.start();

        if (!done.await(120, TimeUnit.SECONDS)) {
            fail("a handover was lost: somebody is still waiting");
        }
        assertNull(failure.get(), "no waiter was stranded: " + failure.get());
        assertEquals(6000, handovers.get());
        assertEquals(0, manager.trackedLockCount());
        assertNull(manager.ownerOf("handle-a"));
    }

    @Test
    @DisplayName("server-side locking is not entitlement-checked")
    public void serverSideCodeLocksWithoutADisclosure() {
        // Application code on the server holds the object already; there is no untrusted name to
        // test, and nothing was ever disclosed to anybody here.
        manager.lock("handle-a", "thread:1");
        assertNotNull(manager.ownerOf("handle-a"));
        manager.unlock("handle-a", "thread:1");
        assertEquals(0, manager.trackedLockCount());
    }
}
