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

import com.zeroz4j.api.BinarySerializer;
import com.zeroz4j.api.GrowableBuffer;
import com.zeroz4j.api.SyncFrameTypes;
import com.zeroz4j.signals.SharedValueSignal;
import com.zeroz4j.signals.SignalTransport;
import com.zeroz4j.signals.Signals;

import java.util.ArrayList;
import java.util.List;

/**
 * Wasm-client {@link SignalTransport}: shared signals here are read-only mirrors of the
 * server's authoritative instances.
 *
 * <p>When a shared signal is declared on the client, this transport requests its retained
 * value from the server (a framework-internal {@code zeroz4j.signals#subscribe} frame);
 * incoming 0x05 SIGNAL_UPDATE frames are applied to the mirror, which notifies local
 * {@code Effect}/{@code Computed} consumers like any other signal change. A client-side
 * {@code set()} on a shared signal fails — the server owns shared state in this release.</p>
 *
 * <p>Signals declared before the WebSocket channel is ready queue their subscribe
 * requests, which flush on {@link WasmRmiClient#initialize}.</p>
 */
final class ClientSignalTransport implements SignalTransport {

    private static final List<String> pendingSubscribes = new ArrayList<>();

    private ClientSignalTransport() {}

    /**
     * Installs the transport and flushes subscribe requests queued before the channel
     * became available. Called from {@link WasmRmiClient#initialize}.
     */
    static synchronized void install() {
        Signals.installTransport(new ClientSignalTransport());
        List<String> queued = new ArrayList<>(pendingSubscribes);
        pendingSubscribes.clear();
        for (String name : queued) {
            sendSubscribe(name);
        }
    }

    /**
     * Applies a SIGNAL_UPDATE frame's payload to the local mirror, if declared.
     */
    static void handleUpdate(String name, Object value) {
        SharedValueSignal<?> signal = Signals.lookup(name);
        if (signal != null) {
            signal.applyRemote(value);
        }
    }

    private static synchronized void sendSubscribe(String name) {
        if (WasmRmiClient.networkChannel == null) {
            pendingSubscribes.add(name);
            return;
        }
        try {
            GrowableBuffer buffer = new GrowableBuffer();
            buffer.putInt(0); // no correlation: fire-and-forget
            BinarySerializer.writeString(buffer, SyncFrameTypes.SIGNALS_SERVICE);
            BinarySerializer.writeString(buffer, "subscribe");
            buffer.putInt(1);
            BinarySerializer.writeValue(buffer, name, WasmRmiClient.MAPPER);
            WasmRmiClient.networkChannel.sendRawBytes(buffer.toByteArray());
        } catch (Exception e) {
            System.err.println("[zeroz4j] Failed to subscribe to shared signal '" + name + "': " + e.getMessage());
        }
    }

    @Override
    public void onSharedSignalCreated(SharedValueSignal<?> signal) {
        sendSubscribe(signal.name());
    }

    @Override
    public boolean canSet(SharedValueSignal<?> signal) {
        return signal.isClientWritable();
    }

    @Override
    public void afterSet(SharedValueSignal<?> signal, Object newValue) {
        // Optimistic write: the local mirror is already updated; send the value to the
        // authoritative server, which either broadcasts it (confirming echo) or answers
        // with a corrective update that snaps this mirror back.
        if (WasmRmiClient.networkChannel == null) {
            return;
        }
        try {
            GrowableBuffer buffer = new GrowableBuffer();
            buffer.putInt(0); // fire-and-forget
            BinarySerializer.writeString(buffer, SyncFrameTypes.SIGNALS_SERVICE);
            BinarySerializer.writeString(buffer, "set");
            buffer.putInt(2);
            BinarySerializer.writeValue(buffer, signal.name(), WasmRmiClient.MAPPER);
            BinarySerializer.writeValue(buffer, newValue, WasmRmiClient.MAPPER);
            WasmRmiClient.networkChannel.sendRawBytes(buffer.toByteArray());
        } catch (Exception e) {
            System.err.println("[zeroz4j] Failed to send write for shared signal '"
                    + signal.name() + "': " + e.getMessage());
        }
    }
}
