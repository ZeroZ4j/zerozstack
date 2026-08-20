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
package com.zeroz4j.api;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Object Mapper maintaining bidirectional mapping between unique string handles (UUIDs) and Java object instances.
 * Used across the zeroz4j framework to preserve strict reference identity during binary serialization
 * and real-time LiveSync push updates across sessions.
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>Reference Identity:</b> Uses an {@link IdentityHashMap} for {@code objectToId} lookup, matching instances by reference address ({@code ==}) rather than {@code equals()}.</li>
 *   <li><b>State Mutations:</b> Modifies bidirectional maps {@code idToObject} and {@code objectToId}. Thread safety is maintained via synchronized blocks on {@code objectToId} and {@link ConcurrentHashMap}.</li>
 *   <li><b>LiveSync Role:</b> Generates session-scoped reference handles for objects sent over RMI. Inbound LiveSync frames use these handles to locate and update instances in-place.</li>
 *   <li><b>Two server-side hooks:</b> {@link ResolutionGuard} can veto resolving a handle while a
 *       client-proposed change is being decoded, and {@link DisclosureRecorder} is told about every
 *       handle written toward a recipient. Both are optional and unused in the browser.</li>
 * </ul>
 */
public class ObjectMapper {
    private final Map<String, Object> idToObject = new ConcurrentHashMap<>();
    private final Map<Object, String> objectToId = Collections.synchronizedMap(new IdentityHashMap<>());

    /**
     * Vetoes the resolution of a handle while it is installed.
     *
     * <p>Installed by the server around a client-proposed change, so that decoding cannot reach a
     * canonical object the caller is not allowed to write. A guard refuses by throwing: returning
     * nothing would make the decoder build a fresh instance and claim the handle for it, which
     * replaces the canonical mapping and is worse than the read it was trying to prevent.</p>
     *
     * <p>Server-side only. The browser never installs one.</p>
     */
    public interface ResolutionGuard {
        /**
         * @param handleId the handle the decoder is about to resolve
         * @throws RuntimeException to abort the whole decode
         */
        void checkResolve(String handleId);
    }

    /**
     * Told about every handle written toward a recipient.
     *
     * <p>The server installs one so it can remember which objects it actually sent to whom, which is
     * what later decides whether that recipient may ask for the object again. Called for a handle
     * that already existed as well as a freshly minted one, because sending an object a second time
     * is still sending it.</p>
     *
     * <p>Server-side only. The browser never installs one.</p>
     */
    public interface DisclosureRecorder {
        /**
         * @param handleId the handle that has just been put on the wire
         */
        void handleDisclosed(String handleId);
    }

    /** Thread-confined: a guard belongs to the one decode it was installed for. */
    private static final ThreadLocal<ResolutionGuard> RESOLUTION_GUARD = new ThreadLocal<>();

    /** Process-wide: installed once at startup, and read on every write. */
    private static volatile DisclosureRecorder disclosureRecorder;

    /**
     * Installs or clears the guard consulted by {@link #getObject(String)} on this thread.
     *
     * <p>Always clear it in a {@code finally}: a guard left behind would veto unrelated decoding on
     * the same thread.</p>
     *
     * @param guard the guard, or null to clear
     */
    public static void setResolutionGuard(ResolutionGuard guard) {
        if (guard == null) {
            RESOLUTION_GUARD.remove();
        } else {
            RESOLUTION_GUARD.set(guard);
        }
    }

    /**
     * @return the guard installed on this thread, or null when decoding is unguarded
     */
    public static ResolutionGuard resolutionGuard() {
        return RESOLUTION_GUARD.get();
    }

    /**
     * Installs the recorder told about every handle written.
     *
     * @param recorder the recorder, or null to stop recording
     */
    public static void setDisclosureRecorder(DisclosureRecorder recorder) {
        disclosureRecorder = recorder;
    }

    /**
     * Registers an object instance and returns its assigned string ID.
     * If the object was previously registered, returns its existing ID.
     *
     * @param obj the object to register (returns {@code null} if obj is null)
     * @return unique string reference handle ID
     *
     * <p><b>Under the hood:</b> Synchronizes on {@code objectToId}. Checks if {@code obj} exists in {@code objectToId}.
     * If present, returns existing ID string. Otherwise, generates a random UUID string via {@link Ids#newId()},
     * stores bidirectional mapping in both {@code objectToId} and {@code idToObject}, and returns the new ID.</p>
     */
    public String register(Object obj) {
        if (obj == null) return null;
        String id;
        synchronized (objectToId) {
            String existing = objectToId.get(obj);
            if (existing != null) {
                id = existing;
            } else {
                id = Ids.newId();
                objectToId.put(obj, id);
                idToObject.put(id, obj);
            }
        }
        // Outside the lock: the recorder keeps its own state and must not be able to deadlock a
        // serialization. An already-known handle is reported too - re-sending an object is still
        // disclosing it, and the recipient may be a different one this time.
        DisclosureRecorder recorder = disclosureRecorder;
        if (recorder != null) {
            recorder.handleDisclosed(id);
        }
        return id;
    }

    /**
     * Retrieves the string ID handle assigned to an object instance.
     *
     * @param obj the object to look up
     * @return string ID handle, or {@code null} if not registered
     *
     * <p><b>Under the hood:</b> Looks up reference key in {@code objectToId} map.</p>
     */
    public String getId(Object obj) {
        return objectToId.get(obj);
    }

    /**
     * Resolves an object instance from its unique string ID handle.
     *
     * @param id the unique string handle
     * @return object instance associated with the ID, or {@code null} if unmapped
     *
     * <p><b>Under the hood:</b> Performs key lookup in {@code idToObject} map.</p>
     */
    public Object getObject(String id) {
        if (id == null) return null;
        ResolutionGuard guard = RESOLUTION_GUARD.get();
        if (guard != null) {
            guard.checkResolve(id);
        }
        return idToObject.get(id);
    }

    /**
     * Directly registers an object with a specified explicit ID handle.
     * Called primarily during binary deserialization when receiving an object with a pre-assigned ID frame.
     *
     * @param id  the explicit handle ID
     * @param obj the object instance
     *
     * <p><b>Under the hood:</b> Inserts {@code (id, obj)} into {@code idToObject} and {@code (obj, id)} into {@code objectToId}.</p>
     */
    public void registerWithId(String id, Object obj) {
        if (id == null || obj == null) return;
        idToObject.put(id, obj);
        objectToId.put(obj, id);
    }

    /**
     * Removes an object from both tracking maps.
     *
     * @param obj the object to deregister
     *
     * <p><b>Under the hood:</b> Synchronizes on {@code objectToId}, removes {@code obj} from {@code objectToId},
     * and if an ID was present, removes that ID entry from {@code idToObject}.</p>
     */
    public void deregister(Object obj) {
        if (obj == null) return;
        synchronized (objectToId) {
            String id = objectToId.remove(obj);
            if (id != null) {
                idToObject.remove(id);
            }
        }
    }

    /**
     * Clears all object mappings. Useful for session teardown or cache invalidation.
     *
     * <p><b>Under the hood:</b> Synchronizes on {@code objectToId} and clears both {@code objectToId} and {@code idToObject} maps.</p>
     */
    public void clear() {
        synchronized (objectToId) {
            objectToId.clear();
            idToObject.clear();
        }
    }

    /**
     * Returns the current number of tracked objects.
     *
     * @return number of tracked instances
     *
     * <p><b>Under the hood:</b> Returns {@code idToObject.size()}.</p>
     */
    public int size() {
        return idToObject.size();
    }

    /**
     * A snapshot of every tracked handle ID.
     *
     * <p>This is what the client sends the server after a reconnect: the complete list of
     * objects it holds, so the server can re-send their current state. A snapshot rather
     * than a live view, because the caller serializes it while other code may register.</p>
     *
     * @return the handle IDs at the moment of the call, in no particular order
     */
    public java.util.List<String> ids() {
        return new java.util.ArrayList<>(idToObject.keySet());
    }
}
