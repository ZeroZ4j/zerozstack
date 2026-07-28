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
import java.util.UUID;
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
 * </ul>
 */
public class ObjectMapper {
    private final Map<String, Object> idToObject = new ConcurrentHashMap<>();
    private final Map<Object, String> objectToId = Collections.synchronizedMap(new IdentityHashMap<>());

    /**
     * Registers an object instance and returns its assigned string ID.
     * If the object was previously registered, returns its existing ID.
     *
     * @param obj the object to register (returns {@code null} if obj is null)
     * @return unique string reference handle ID
     *
     * <p><b>Under the hood:</b> Synchronizes on {@code objectToId}. Checks if {@code obj} exists in {@code objectToId}.
     * If present, returns existing ID string. Otherwise, generates a random UUID string via {@link UUID#randomUUID()},
     * stores bidirectional mapping in both {@code objectToId} and {@code idToObject}, and returns the new ID.</p>
     */
    public String register(Object obj) {
        if (obj == null) return null;
        synchronized (objectToId) {
            String existing = objectToId.get(obj);
            if (existing != null) {
                return existing;
            }
            String id = UUID.randomUUID().toString();
            objectToId.put(obj, id);
            idToObject.put(id, obj);
            return id;
        }
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
}
