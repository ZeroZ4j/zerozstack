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

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * One fair lock per object, so two editors of the same object take turns.
 *
 * <p>This is the mechanism behind {@code LiveMutex}. It does not decide <em>who</em> may lock what:
 * a request arriving from a browser is checked at the boundary it arrives on, in
 * {@link LiveMutexRpcImpl}, which refuses an object the caller was never sent. Code running on the
 * server calls this class directly and is not checked, because a server-side caller is not
 * presenting an untrusted name.</p>
 *
 * <h2>The table does not grow for ever</h2>
 *
 * <p>An entry exists only while somebody holds a lock or is waiting for one. Every caller entering
 * {@link #lock(String, String)} is counted against the entry before it starts waiting, and stops
 * being counted when it releases the lock or gives up. The entry is removed the moment the count
 * reaches zero.</p>
 *
 * <p>The counting and the removal both happen inside {@link ConcurrentHashMap#compute}, which holds
 * that key's own lock for the duration. So a caller arriving for the same object cannot slip between
 * "the count reached zero" and "the entry was removed": either it is counted first, and the entry
 * survives, or it is counted afterwards and builds a fresh entry with a fresh permit — which is
 * correct, because a count of zero means nobody held the old one. A waiter is never stranded on an
 * entry that has been thrown away, and a permit is never handed to an entry nobody can reach.</p>
 *
 * <p>The table therefore holds at most as many entries as there are locks held or waited for right
 * now, which is bounded by the number of connected sessions and the frames each may have in flight.
 * It is not bounded by how many names a caller invents.</p>
 *
 * <h2>How long a caller waits</h2>
 *
 * <p>Thirty seconds by default, changed with {@code zeroz.livemutex.waitSeconds}. A caller that
 * waits that long is told so in a sentence that reaches it word for word.</p>
 */
@ApplicationScoped
public class LiveMutexManager {

    private static final Logger LOG = Logger.getLogger(LiveMutexManager.class.getName());

    /** How long a caller waits for a lock somebody else holds, in seconds. */
    static final String WAIT_SECONDS_PROPERTY = "zeroz.livemutex.waitSeconds";

    /**
     * The wait applied when {@link #WAIT_SECONDS_PROPERTY} is not set.
     *
     * <p>Long enough that a lock handed over between two people editing the same record is not
     * noticed, short enough that a browser which took a lock and went away does not hold everyone
     * else past the point they would give up anyway.</p>
     */
    static final int DEFAULT_WAIT_SECONDS = 30;

    /**
     * One object's lock, plus how many callers currently care about it.
     *
     * <p>{@code users} is read and written only from inside a {@code compute} on the enclosing map,
     * so the map's per-key lock is what makes it safe; it needs no other synchronization.</p>
     */
    private static final class Entry {
        /** Fair, so callers are served in the order they arrived. */
        private final Semaphore permit = new Semaphore(1, true);
        /** Holders plus waiters. The entry is removed when this reaches zero. */
        private int users;
    }

    /** Object handle to its lock, for objects locked or waited for right now. */
    private final ConcurrentHashMap<String, Entry> locks = new ConcurrentHashMap<>();

    /** Object handle to who holds it, as {@code session:<id>} or {@code thread:<id>}. */
    private final ConcurrentHashMap<String, String> owners = new ConcurrentHashMap<>();

    /**
     * Takes the lock for an object, waiting if somebody else has it.
     *
     * <p>Blocks the calling virtual thread for up to {@code zeroz.livemutex.waitSeconds} (30 by
     * default). Callers are served in the order they arrived.</p>
     *
     * <p>This method does not ask whether the caller is entitled to lock this object. A request that
     * came from a browser has already been checked by {@link LiveMutexRpcImpl}.</p>
     *
     * @param objectId the object's handle
     * @param ownerId  who is taking it, as {@code session:<id>} or {@code thread:<id>}
     * @throws ClientVisibleException if the wait runs out, or the thread is interrupted while
     *                                waiting; the message travels to the caller unchanged
     */
    public void lock(String objectId, String ownerId) {
        if (objectId == null || ownerId == null) {
            throw new IllegalArgumentException("LiveMutex needs both an object handle and an owner.");
        }
        int waitSeconds = waitSeconds();
        Entry entry = join(objectId);
        boolean acquired = false;
        boolean interrupted = false;
        try {
            acquired = entry.permit.tryAcquire(waitSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            interrupted = true;
        } finally {
            if (!acquired) {
                // Stop being counted before throwing, or the entry stays behind for ever.
                leave(objectId);
            }
        }
        if (interrupted) {
            throw new ClientVisibleException(
                    "Interrupted while waiting to edit this item. Nothing was changed. Try again.");
        }
        if (!acquired) {
            throw new ClientVisibleException(
                    "Timed out after " + waitSeconds + (waitSeconds == 1 ? " second" : " seconds")
                    + " waiting to edit this item: "
                    + "someone else has been editing it that whole time. Nothing was changed. "
                    + "Try again in a moment. To allow a longer wait, set the server property "
                    + WAIT_SECONDS_PROPERTY + ".");
        }
        owners.put(objectId, ownerId);
    }

    /**
     * Gives up the lock for an object, if this owner is the one holding it.
     *
     * <p>Does nothing when somebody else holds the lock, or when nobody does. A caller cannot
     * release a lock it does not have, which is why this needs no separate entitlement check: the
     * only lock it can free is one it was allowed to take in the first place.</p>
     *
     * @param objectId the object's handle
     * @param ownerId  who is giving it up
     */
    public void unlock(String objectId, String ownerId) {
        if (objectId == null || ownerId == null) {
            return;
        }
        // Atomic: exactly one caller can win this, so exactly one permit is returned per lock taken.
        // A plain get-then-remove let a disconnect and an unlock both release the same lock, which
        // put two permits into a one-permit semaphore and let two owners edit at once.
        if (!owners.remove(objectId, ownerId)) {
            return;
        }
        Entry entry = locks.get(objectId);
        if (entry != null) {
            // Hand the permit on first. This caller is still counted, so the entry cannot be removed
            // underneath a waiter that is about to be woken by it.
            entry.permit.release();
        } else {
            LOG.warning("[zeroz4j] Released a lock on " + objectId
                    + " that had no entry left. This should not happen; please report it.");
        }
        leave(objectId);
    }

    /**
     * Gives up every lock one owner holds. Called when a WebSocket session closes.
     *
     * @param ownerId the owner to clear out
     */
    public void releaseAll(String ownerId) {
        if (ownerId == null) {
            return;
        }
        for (Map.Entry<String, String> held : owners.entrySet()) {
            if (ownerId.equals(held.getValue())) {
                unlock(held.getKey(), ownerId);
            }
        }
    }

    // ------------------------------------------------------------------ internals

    /**
     * Counts this caller against the object's lock, creating the entry if it is the first.
     *
     * @param objectId the object's handle
     * @return the entry, which cannot be removed until this caller calls {@code leave}
     */
    private Entry join(String objectId) {
        return locks.compute(objectId, (key, existing) -> {
            Entry entry = existing != null ? existing : new Entry();
            entry.users++;
            return entry;
        });
    }

    /**
     * Stops counting this caller, and removes the entry if it was the last.
     *
     * @param objectId the object's handle
     */
    private void leave(String objectId) {
        locks.computeIfPresent(objectId, (key, entry) -> {
            entry.users--;
            return entry.users <= 0 ? null : entry;
        });
    }

    /** @return the configured wait in seconds, or {@link #DEFAULT_WAIT_SECONDS} */
    private static int waitSeconds() {
        String configured = System.getProperty(WAIT_SECONDS_PROPERTY);
        if (configured == null || configured.trim().isEmpty()) {
            return DEFAULT_WAIT_SECONDS;
        }
        try {
            int value = Integer.parseInt(configured.trim());
            if (value <= 0) {
                LOG.warning("[zeroz4j] Ignoring " + WAIT_SECONDS_PROPERTY + "=" + configured
                        + ": it must be a positive number of seconds.");
                return DEFAULT_WAIT_SECONDS;
            }
            return value;
        } catch (NumberFormatException ex) {
            LOG.warning("[zeroz4j] Ignoring non-numeric " + WAIT_SECONDS_PROPERTY
                    + "='" + configured + "'.");
            return DEFAULT_WAIT_SECONDS;
        }
    }

    // ------------------------------------------------------------------ test support

    /** @return how many objects have a lock entry right now */
    int trackedLockCount() {
        return locks.size();
    }

    /**
     * @param objectId the object's handle
     * @return who holds that object's lock, or null when nobody does
     */
    String ownerOf(String objectId) {
        return owners.get(objectId);
    }

    /** @return how long a caller currently waits for a lock, in seconds */
    static int configuredWaitSeconds() {
        return waitSeconds();
    }
}
