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
            toSend = pendingMutations.toArray();
            pendingMutations.clear();
            flushScheduled = false;
        }
        for (Object liveObject : toSend) {
            sendMutation(liveObject);
        }
    }

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
            System.err.println("[zeroz4j] Failed to send live mutation for "
                    + liveObject.getClass().getName() + ": " + e.getMessage());
        }
    }
}
