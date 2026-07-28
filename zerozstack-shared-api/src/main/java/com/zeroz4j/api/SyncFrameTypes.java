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
 * Wire protocol opcode constants for zeroz4j binary WebSocket frames.
 *
 * <p>Every binary WebSocket frame begins with a 4-byte correlation/handle ID followed by an opcode byte
 * identifying the frame payload type.</p>
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>RMI Opcodes (0x01-0x0F):</b> Cover RPC responses, server pushes, auth handshakes, and errors.</li>
 *   <li><b>LiveSync Opcodes (0x10-0x1F):</b> Cover real-time object graph subscriptions, snapshots, mutations, ACKs, and signals.</li>
 * </ul>
 */
public final class SyncFrameTypes {

    private SyncFrameTypes() {}

    // --- RMI frame types ---

    /** RPC success response byte tag (0x01). Payload: correlation ID + serialized return value. */
    public static final byte RPC_RESPONSE = 0x01;

    /** Server-initiated RPC push notification byte tag (0x02). Payload: topic string + serialized payload. */
    public static final byte RPC_PUSH     = 0x02;

    /** Authentication frame byte tag (0x03) sent on connect. Payload: username string + role set. */
    public static final byte AUTH         = 0x03;

    /** Reserved interface name for framework-internal RMI-shaped frames. The client requests a
     *  shared signal's retained value by "calling" {@code zeroz4j.signals#subscribe(name)};
     *  the server engine intercepts this before service dispatch. */
    public static final String SIGNALS_SERVICE = "zeroz4j.signals";

    /** Reserved interface name for framework-internal LiveSync mutation frames. The client
     *  proposes a state change via {@code zeroz4j.livesync#mutate(object)}; the server engine
     *  intercepts, authorizes (@ClientWritable + roles), validates, applies in place, and
     *  re-broadcasts — or answers the writer with a corrective sync. */
    public static final String LIVESYNC_SERVICE = "zeroz4j.livesync";

    /**
     * Reserved interface name for resolving lazy references, method {@code resolve} with a single
     * handle argument. Rides an RMI-shaped frame like the signals and livesync services, so a lazy
     * load is an ordinary suspending round trip from the client's point of view.
     */
    public static final String LAZY_SERVICE = "zeroz4j.lazy";

    /** RPC error response byte tag (0x0F). Payload: correlation ID + error message string. */
    public static final byte RPC_ERROR    = 0x0F;

    // --- LiveRef object sync ---

    /** Client -> Server: Subscribe to object graph changes (0x10). Payload: class FQCN. */
    public static final byte SUBSCRIBE   = 0x10;

    /** Server -> Client: Full snapshot state of subscribed object (0x11). Payload: handle ID + version + serialized object. */
    public static final byte SNAPSHOT    = 0x11;

    /** Client -> Server: Unsubscribe from object updates (0x12). Payload: handle ID. */
    public static final byte UNSUBSCRIBE = 0x12;

    /** Client -> Server: Propose state mutation for synced object (0x13). Payload: handle ID + baseVersion + serialized object. */
    public static final byte MUTATE      = 0x13;

    /** Server -> Client: Mutation accepted ACK (0x14). Payload: handle ID + newVersion. */
    public static final byte ACK         = 0x14;

    /** Server -> Client: Mutation rejected REJECT (0x15). Payload: handle ID + currentVersion + serialized object + reason. */
    public static final byte REJECT      = 0x15;

    // --- Reactive Signals ---

    /** Client -> Server: Subscribe to named reactive signal (0x16). Reserved — the current
     *  subscribe mechanism rides an RMI-shaped frame to {@link #SIGNALS_SERVICE}. */
    public static final byte SIGNAL_SUB  = 0x16;

    /** Server -> Client: Shared signal value (0x17). Payload: signal name string + serialized value.
     *  Sent as a broadcast on every server-side change and directly to a session on subscribe. */
    public static final byte SIGNAL_UPD  = 0x17;

    /** Server -> Client: One-shot push message (0x18). Payload: topic string + serialized payload. */
    public static final byte PUSH        = 0x18;
}
