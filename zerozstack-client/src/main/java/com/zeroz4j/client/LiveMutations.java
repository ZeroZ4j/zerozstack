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
package com.zeroz4j.client;

import com.zeroz4j.api.BinaryRegistry;
import com.zeroz4j.api.BinarySerializer;
import com.zeroz4j.api.GrowableBuffer;
import com.zeroz4j.api.LiveMutationRefusals;
import com.zeroz4j.api.LiveMutationTracker;
import com.zeroz4j.api.SyncFrameTypes;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Client half of two-way LiveSync: forwards field changes on {@code @ClientWritable}
 * live instances to the server.
 *
 * <p>Deserialization instantiates the APT-generated {@code <Model>_Live} subclasses,
 * whose setters report through {@link LiveMutationTracker}. Changes coalesce per UI
 * microtask (a burst of setter calls sends one mutation per object) and travel as
 * whole-object {@code zeroz4j.livesync#mutate} frames. The server authorizes, validates,
 * applies, and re-broadcasts — or answers with a corrective sync that reverts this
 * client's instance in place.</p>
 */
final class LiveMutations {

    private static final Set<Object> pendingMutations = new LinkedHashSet<>();
    private static boolean flushScheduled = false;

    private LiveMutations() {}

    /** Test support only: drops retained mutations. */
    static synchronized void resetForTesting() {
        pendingMutations.clear();
        flushScheduled = false;
    }

    /**
     * Enables live instantiation and installs the mutation listener.
     * Called from {@link WasmRmiClient#initialize}.
     */
    static void install() {
        BinaryRegistry.setPreferLiveInstances(true);
        LiveMutationTracker.install(LiveMutations::onChanged);
    }

    private static synchronized void onChanged(Object liveObject) {
        pendingMutations.add(liveObject);
        if (flushScheduled) {
            return;
        }
        flushScheduled = true;
        WasmRmiClient.PlatformScheduler scheduler = WasmRmiClient.getPlatformScheduler();
        if (scheduler != null) {
            scheduler.runLater(LiveMutations::flush);
        } else {
            flush();
        }
    }

    private static void flush() {
        Object[] toSend;
        synchronized (LiveMutations.class) {
            if (WasmRmiClient.networkChannel != null && !WasmRmiClient.networkChannel.isOpen()) {
                // The connection is down. Keep the pending set instead of sending into a dead
                // socket, where the edits would be silently lost while the user's screen shows
                // them applied — the worst failure this class can produce. flushPending() sends
                // them the moment the connection is restored.
                flushScheduled = false;
                return;
            }
            toSend = pendingMutations.toArray();
            pendingMutations.clear();
            flushScheduled = false;
        }
        for (Object liveObject : toSend) {
            sendMutation(liveObject);
        }
    }

    /**
     * Sends every mutation retained while the connection was down. Called on reconnect, before
     * the re-sync request: the edits reach the server first, so the object state the re-sync
     * answers with already includes them (and any concurrent change is settled by the server's
     * usual last-write-wins broadcast).
     */
    static void flushPending() {
        flush();
    }

    /**
     * Puts one object's current state on the wire, or — if that cannot be done — puts the screen
     * back to the truth and says so.
     *
     * <p>A failure here means the person has typed something the server will never have. Dropping it
     * with a console line is what let the whole up direction of LiveSync stay broken for a version,
     * so it is handled the way a server refusal is: the object is asked for again, which overwrites
     * the optimistic change in place, and the reason goes to
     * {@link LiveMutationRefusals} for the application to show.</p>
     *
     * @param liveObject the edited live instance
     */
    private static void sendMutation(Object liveObject) {
        if (WasmRmiClient.networkChannel == null) {
            return;
        }
        try {
            GrowableBuffer buffer = new GrowableBuffer();
            buffer.putInt(0); // fire-and-forget
            BinarySerializer.writeString(buffer, SyncFrameTypes.LIVESYNC_SERVICE);
            BinarySerializer.writeString(buffer, "mutate");
            buffer.putInt(1);
            BinarySerializer.writeValue(buffer, liveObject, WasmRmiClient.MAPPER);
            WasmRmiClient.networkChannel.sendRawBytes(buffer.toByteArray());
        } catch (Exception e) {
            String model = BinaryRegistry.wireNameOf(liveObject.getClass().getName());
            boolean reverted = WasmRmiClient.requestResyncOf(liveObject);
            LiveMutationRefusals.report(model, "This change could not be sent to the server ("
                    + e.getMessage() + ")."
                    + (reverted
                        ? " The value shown is being put back to what the server has."
                        : " The screen still shows it, and the server does not have it."));
        }
    }
}
