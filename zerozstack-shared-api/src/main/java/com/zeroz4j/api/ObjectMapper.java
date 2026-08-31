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

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Object Mapper maintaining bidirectional mapping between unique string handles (UUIDs) and Java object instances.
 * Used across the zeroz4j framework to preserve strict reference identity during binary serialization
 * and real-time LiveSync push updates across sessions.
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>Reference Identity:</b> Objects are matched by reference address ({@code ==}), never by
 *       {@code equals()}, so two equal-but-separate instances get two handles.</li>
 *   <li><b>Held weakly:</b> the registry does not keep an object alive. Both directions hold
 *       {@link WeakReference}s, and an entry disappears once the application itself has let go of
 *       the object. Before 0.8.0 both maps held strong references and nothing ever removed an
 *       entry, so every object that had ever been sent stayed in memory for the life of the
 *       process.</li>
 *   <li><b>State Mutations:</b> Modifies bidirectional maps {@code idToObject} and
 *       {@code objectToId}. Thread safety is maintained via synchronized blocks on
 *       {@code objectToId} and {@link ConcurrentHashMap}.</li>
 *   <li><b>LiveSync Role:</b> Generates reference handles for the objects that need one. Inbound
 *       LiveSync frames use these handles to locate and update instances in-place.</li>
 *   <li><b>Three server-side hooks:</b> {@link ResolutionGuard} can veto resolving a handle while a
 *       client-proposed change is being decoded, {@link ModelGuard} is shown every model the decode
 *       builds or updates whether it has a handle or not, and {@link DisclosureRecorder} is told
 *       about every handle written toward a recipient. All three are optional and unused in the
 *       browser.</li>
 * </ul>
 *
 * <p><b>Not everything on the wire is registered.</b> A handle exists so a later frame can name the
 * same object again: a {@code @LiveSync} model and everything inside one. An ordinary value returned
 * from a call is written with a name that means nothing outside the message it traveled in, and
 * never reaches this class. See {@code BinarySerializer} and docs/PROTOCOL.md.</p>
 */
public class ObjectMapper {

    /**
     * A weak reference to a registered object that can also be used as an identity-keyed map key.
     *
     * <p>{@code WeakHashMap} is no use here: it matches keys with {@code equals}, and two equal
     * models are two separate objects on the wire. So the key is the weak reference itself, hashed
     * on {@link System#identityHashCode(Object)} and compared by {@code ==} on the referent. The
     * handle travels with the key so that a reference cleared by the collector can be found and
     * removed from both directions.</p>
     */
    private static final class Handle extends WeakReference<Object> {
        final String id;
        private final int hash;

        Handle(Object referent, String id, ReferenceQueue<Object> queue) {
            super(referent, queue);
            this.id = id;
            this.hash = System.identityHashCode(referent);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Handle)) {
                return false;
            }
            Object mine = get();
            return mine != null && mine == ((Handle) other).get();
        }
    }

    private final ReferenceQueue<Object> collected = new ReferenceQueue<>();
    private final Map<String, Handle> idToObject = new ConcurrentHashMap<>();
    private final Map<Handle, String> objectToId = new HashMap<>();

    /**
     * Removes the entries whose object the collector has taken.
     *
     * <p>Called from every operation that adds, counts or lists, so the registry stays the size of
     * what is actually still in use without a sweeper thread. In the browser this queue is fed by
     * the JavaScript engine's own {@code FinalizationRegistry}.</p>
     */
    private void purge() {
        Handle dead;
        while ((dead = (Handle) collected.poll()) != null) {
            synchronized (objectToId) {
                objectToId.remove(dead);
            }
            idToObject.remove(dead.id, dead);
        }
    }

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

    /**
     * Shown every model a decode builds or updates, with or without a handle.
     *
     * <p>{@link ResolutionGuard} can only speak about objects the registry already holds, so it
     * says nothing about a model the payload invents on the spot or about one whose name is only
     * meaningful inside that one message. The server installs this alongside it while applying a
     * client-proposed change, so the rule "a client may edit exactly the models you marked" is
     * decided by what the model is, not by whether it happened to carry a handle.</p>
     *
     * <p>Server-side only. The browser never installs one.</p>
     */
    public interface ModelGuard {
        /**
         * @param model the instance the decoder has just built or is about to write fields into
         * @param depth how deeply nested it is: 1 is the payload's outermost model
         * @throws RuntimeException to abort the whole decode
         */
        void checkModel(Object model, int depth);
    }

    /** Thread-confined: a guard belongs to the one decode it was installed for. */
    private static final ThreadLocal<ResolutionGuard> RESOLUTION_GUARD = new ThreadLocal<>();

    /** Thread-confined, like {@link #RESOLUTION_GUARD} and installed with it. */
    private static final ThreadLocal<ModelGuard> MODEL_GUARD = new ThreadLocal<>();

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
     * Installs or clears the guard shown every model decoded on this thread.
     *
     * <p>Always clear it in a {@code finally}, for the same reason as
     * {@link #setResolutionGuard(ResolutionGuard)}.</p>
     *
     * @param guard the guard, or null to clear
     */
    public static void setModelGuard(ModelGuard guard) {
        if (guard == null) {
            MODEL_GUARD.remove();
        } else {
            MODEL_GUARD.set(guard);
        }
    }

    /**
     * Shows a decoded model to the guard installed on this thread, if there is one.
     *
     * @param model the instance the decoder has just built or resolved
     * @param depth how deeply nested it is: 1 is the payload's outermost model
     */
    public static void checkDecodedModel(Object model, int depth) {
        ModelGuard guard = MODEL_GUARD.get();
        if (guard != null) {
            guard.checkModel(model, depth);
        }
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
        purge();
        String id;
        synchronized (objectToId) {
            String existing = objectToId.get(new Handle(obj, null, null));
            if (existing != null) {
                id = existing;
            } else {
                id = Ids.newId();
                Handle handle = new Handle(obj, id, collected);
                objectToId.put(handle, id);
                idToObject.put(id, handle);
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
        if (obj == null) {
            return null;
        }
        synchronized (objectToId) {
            return objectToId.get(new Handle(obj, null, null));
        }
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
        Handle handle = idToObject.get(id);
        return handle == null ? null : handle.get();
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
        purge();
        synchronized (objectToId) {
            Handle handle = new Handle(obj, id, collected);
            idToObject.put(id, handle);
            objectToId.put(handle, id);
        }
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
            String id = objectToId.remove(new Handle(obj, null, null));
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
        purge();
        return idToObject.size();
    }

    /**
     * A snapshot of every handle whose object is still in use.
     *
     * <p>This is what the client sends the server after a reconnect, so the server can re-send the
     * current state of what that browser still holds. "Still in use" is not a guess: the registry
     * holds objects weakly, so a handle is here only while something in the application other than
     * this registry still refers to the object. Anything the screen has dropped has already gone.</p>
     *
     * <p>A snapshot rather than a live view, because the caller serializes it while other code may
     * register.</p>
     *
     * @return the handle IDs at the moment of the call, in no particular order
     */
    public List<String> ids() {
        purge();
        List<String> live = new ArrayList<>(idToObject.size());
        for (Map.Entry<String, Handle> entry : idToObject.entrySet()) {
            if (entry.getValue().get() != null) {
                live.add(entry.getKey());
            }
        }
        return live;
    }
}
