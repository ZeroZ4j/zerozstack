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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Application-scoped CDI bean managing server-side distributed fair semaphores and lock ownership for {@code LiveMutex}.
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>Concurrency Control:</b> Maintains a {@link ConcurrentHashMap} of fair {@link Semaphore} instances (1 permit) per object handle ID.</li>
 *   <li><b>Virtual Threads:</b> Calling {@link #lock(String, String)} blocks/suspends the caller's Virtual Thread via {@link Semaphore#tryAcquire} with a 30-second timeout.</li>
 *   <li><b>Ownership & Teardown:</b> Maps object IDs to owner strings (e.g. {@code "session:<sessionId>"}). {@link #releaseAll(String)} cleans up locks when a WebSocket session closes.</li>
 * </ul>
 */
@ApplicationScoped
public class LiveMutexManager {
    private final ConcurrentHashMap<String, Semaphore> locks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> owners = new ConcurrentHashMap<>();

    /**
     * Acquires the fair lock for a specific object handle on behalf of an owner identifier.
     * Suspends the calling Virtual Thread if the lock is currently held by another owner.
     *
     * @param objectId the target object handle ID string
     * @param ownerId  the owner identifier string (e.g. session ID or thread ID)
     * @throws RuntimeException if the lock acquisition times out after 30 seconds
     *
     * <p><b>Under the hood:</b> Computes or retrieves a 1-permit fair {@link Semaphore} for {@code objectId}.
     * Executes {@code sem.tryAcquire(30, TimeUnit.SECONDS)}. On success, records {@code owners.put(objectId, ownerId)}.</p>
     */
    public void lock(String objectId, String ownerId) {
        Semaphore sem = locks.computeIfAbsent(objectId, k -> new Semaphore(1, true));
        boolean acquired = false;
        try {
            acquired = sem.tryAcquire(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (!acquired) {
            throw new RuntimeException("LiveMutex timeout: could not acquire lock for " + objectId);
        }
        owners.put(objectId, ownerId);
    }

    /**
     * Releases the lock for an object handle if the specified owner matches the current lock owner.
     *
     * @param objectId the target object handle ID string
     * @param ownerId  the requesting owner identifier string
     *
     * <p><b>Under the hood:</b> Checks {@code owners.get(objectId)}. If equal to {@code ownerId}, removes ownership entry
     * and calls {@code release()} on the semaphore.</p>
     */
    public void unlock(String objectId, String ownerId) {
        String currentOwner = owners.get(objectId);
        if (ownerId.equals(currentOwner)) {
            owners.remove(objectId);
            locks.get(objectId).release();
        }
    }

    /**
     * Releases all locks owned by a specified owner identifier (called during session disconnection).
     *
     * @param ownerId the owner identifier string to purge
     *
     * <p><b>Under the hood:</b> Iterates through {@code owners} map. For matching keys, removes ownership entry and releases the semaphore.</p>
     */
    public void releaseAll(String ownerId) {
        owners.forEach((objectId, currentOwner) -> {
            if (ownerId.equals(currentOwner)) {
                owners.remove(objectId);
                locks.get(objectId).release();
            }
        });
    }
}
