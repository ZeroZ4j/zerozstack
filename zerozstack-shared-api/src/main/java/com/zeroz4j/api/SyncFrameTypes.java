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

    /**
     * Authentication-result frame byte tag (0x03), sent by the server on every connect — including
     * anonymous and refused ones, since silence cannot be told apart from a slow network.
     *
     * <p>Payload: protocol version byte (currently 3), authenticated flag byte, username string,
     * role count int, role strings. The flag is the server's decision and nothing else stands in for
     * it: a refused connection still carries a name, and an authenticated user may hold no roles.</p>
     *
     * <p>Version 3 appends the language this connection is answered in, the languages the
     * deployment can answer in, and the translated text itself: language tag string, offered-count
     * int, that many tag strings, catalog count int, and for each catalog its base name, an entry
     * count, and that many key/value string pairs. It rides here because this frame is already sent
     * to everybody and the browser mounts its first screen when it arrives - so there is never a
     * moment where English is drawn and then corrected.</p>
     *
     * <p>Older readers stop after the roles and ignore the rest, which is why the addition needed
     * no new frame: a 0.8.0 client on a 0.9.0 server simply gets no catalog and shows the words its
     * own build compiled in.</p>
     */
    public static final byte AUTH         = 0x03;

    /**
     * Message-catalog frame byte tag (0x04), sent by the server when the language changes.
     *
     * <p>Payload: language tag string, then the same catalog block the {@link #AUTH} frame carries
     * from protocol version 3 on — catalog count, and for each catalog its base name, an entry
     * count, and that many key/value string pairs.</p>
     *
     * <p>The words for the new language reach the browser <b>before</b> the signal saying the
     * language changed, so the first thing that redraws already has them. That ordering is the
     * whole reason this frame exists rather than the client fetching a catalog for itself.</p>
     *
     * <p>A client from before 0.9.0 never asks for a language, so it is never sent one of these.
     * One that received it anyway would print an unknown-frame line and carry on.</p>
     */
    public static final byte CATALOG      = 0x04;

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

    // --- Keepalive ---

    /**
     * Server -&gt; Client: the answer to a keepalive ping (0x19). No payload.
     *
     * <p>It exists so that traffic flows in BOTH directions. A proxy times a tunnel out on the
     * direction it is reading, and nginx uses separate timers for each
     * ({@code proxy_read_timeout} upstream-to-client, {@code proxy_send_timeout} the other way), so
     * a ping the server merely swallowed would leave one of the two timers running.
     */
    public static final byte PONG        = 0x19;

    /**
     * Reserved service name for the keepalive ({@code zeroz4j.keepalive}).
     *
     * <p>The client sends {@code ping} with no arguments and a correlation id of 0 - fire and
     * forget, because nothing waits for the answer. The server replies with one {@link #PONG}
     * frame and does nothing else: no service lookup, nothing checked beyond the connection
     * already being open, no request context.
     *
     * <p><b>Why the framework does this at all.</b> A WebSocket that carries nothing is closed by
     * whatever proxy in the path has the shortest idle timeout - nginx defaults to 60 seconds,
     * Cloudflare cuts at 100 and is not the application's to configure. Measured in a real
     * deployment on 2026-08-17: sockets opened, authenticated, and died at exactly 60 seconds, over
     * and over, each reconnect re-sending a growing pile of objects. Browsers do not expose
     * WebSocket ping frames to page script, so an application cannot fix this without inventing a
     * meaningless service method whose only purpose is to make a byte travel - which is what the
     * application in question did, and why this now lives here instead.
     */
    public static final String KEEPALIVE_SERVICE = "zeroz4j.keepalive";

    /**
     * Reserved service name for re-synchronization after a reconnect ({@code zeroz4j.resync}).
     *
     * <p>The client calls {@code sync} with one argument: the list of object handles it holds.
     * The server answers with one {@link #SUBSCRIBE} (0x10) frame per handle it still knows,
     * carrying that object's current state, which the client applies in place — the same frame
     * and the same apply path as an ordinary LiveSync update, so re-synchronization needs no
     * decoding logic of its own. Handles the server does not know (it restarted since the client
     * fetched them) are counted and logged server-side; no frame is sent for them.</p>
     */
    public static final String RESYNC_SERVICE = "zeroz4j.resync";

    /**
     * The most handles one re-sync request may carry ({@value}).
     *
     * <p>This is a ceiling on a list, not a target. The server answers a handle only if its own
     * record says it sent that object to this browser, and that record holds at most
     * {@code zeroz.disclosure.maxHandlesPerClient} — 10,000 by default — so a longer list could not
     * be answered anyway.</p>
     *
     * <p><b>Why a ceiling exists at all.</b> Before 0.8.0 a browser kept every object it had ever
     * been sent, so a tab left open on a screen that refreshes itself built a list of millions.
     * Sending it produced a message far larger than the 4 MB a connection accepts, the server closed
     * the connection for being over the limit, the client reconnected and sent the same list again,
     * and the list never got shorter — a tab in that state could never connect again. A client that
     * finds itself over this ceiling now throws its list away and starts clean instead of sending
     * it, which ends that loop with no action from anybody.</p>
     */
    public static final int MAX_RESYNC_HANDLES = 10_000;
}
