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

/**
 * Static hook through which APT-generated live subclasses report field changes.
 *
 * <p>Generated {@code <Model>_Live} setter overrides call {@link #fieldChanged(Object)}
 * after assigning. The Wasm client installs a {@link Listener} that forwards mutations to
 * the server; with no listener installed (server tier, plain unit tests) the hook is a
 * no-op. Framework-internal except for {@link #touch(Object)}, the application-facing
 * escape hatch for in-place collection edits the setters cannot observe.</p>
 */
public final class LiveMutationTracker {

    /** Receives change notifications for live instances. */
    @FunctionalInterface
    public interface Listener {
        /**
         * Invoked after a tracked field assignment.
         *
         * @param liveObject the mutated live instance
         */
        void changed(Object liveObject);
    }

    private static volatile Listener listener;
    private static final ThreadLocal<Boolean> applyingRemote = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * Live instances updated by the inbound sync currently being applied. Collected rather than
     * notified immediately so that an effect re-runs once, against a fully applied graph, instead of
     * once per object mid-update.
     */
    private static final ThreadLocal<java.util.List<Object>> remotelyUpdated =
            ThreadLocal.withInitial(java.util.ArrayList::new);

    private LiveMutationTracker() {}

    /**
     * Installs the mutation listener. Called by the client runtime, not applications.
     *
     * @param newListener the listener, or null to detach
     */
    public static void install(Listener newListener) {
        listener = newListener;
    }

    /**
     * Reports a field change on a live instance. Called by generated setter overrides.
     *
     * @param liveObject the mutated instance
     */
    public static void fieldChanged(Object liveObject) {
        if (applyingRemote.get()) {
            return; // change originated from an inbound sync — do not echo it back
        }
        Listener current = listener;
        if (current != null) {
            current.changed(liveObject);
        }
    }

    /**
     * Marks a live instance as changed without going through a setter — the escape hatch
     * for in-place collection mutations (e.g. after {@code obj.getTags().add(...)}).
     *
     * @param liveObject the mutated instance
     */
    public static void touch(Object liveObject) {
        fieldChanged(liveObject);
    }

    /**
     * Suppresses change reporting on the current thread while an inbound remote state is
     * applied through the setters. Framework-internal; always pair with
     * {@link #endRemoteApply()} in a finally block.
     */
    public static void beginRemoteApply() {
        applyingRemote.set(Boolean.TRUE);
    }

    /** Ends the suppression started by {@link #beginRemoteApply()}. */
    public static void endRemoteApply() {
        applyingRemote.set(Boolean.FALSE);
        flushRemoteUpdates();
    }

    /**
     * Records that an inbound sync has written new field values into an existing instance. Called by
     * the deserializer; applications do not call this.
     *
     * <p>Outside a remote apply this notifies immediately, so that a lazily resolved value also
     * re-runs the effects that read it.</p>
     *
     * @param liveObject the instance whose fields were updated
     */
    public static void remoteObjectUpdated(Object liveObject) {
        if (!(liveObject instanceof LiveObservable)) {
            return;
        }
        if (applyingRemote.get()) {
            java.util.List<Object> pending = remotelyUpdated.get();
            for (int i = 0; i < pending.size(); i++) {
                if (pending.get(i) == liveObject) {
                    return; // already recorded — identity, not equals
                }
            }
            pending.add(liveObject);
        } else {
            ((LiveObservable) liveObject).zeroz4jLiveChanged();
        }
    }

    /**
     * Notifies every instance touched by the sync that just finished, then clears the batch.
     *
     * <p>Effects run after the whole graph is applied, so an effect reading two objects from the same
     * frame sees both updated and runs once per object rather than against a half-applied state.</p>
     */
    private static void flushRemoteUpdates() {
        java.util.List<Object> pending = remotelyUpdated.get();
        if (pending.isEmpty()) {
            return;
        }
        java.util.List<Object> batch = new java.util.ArrayList<>(pending);
        pending.clear();
        for (Object updated : batch) {
            ((LiveObservable) updated).zeroz4jLiveChanged();
        }
    }
}
