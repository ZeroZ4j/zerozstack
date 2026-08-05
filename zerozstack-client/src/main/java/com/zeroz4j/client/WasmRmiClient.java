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
import com.zeroz4j.api.SyncFrameTypes;
import com.zeroz4j.api.GrowableBuffer;
import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import com.zeroz4j.api.RmiClientExecutor;
import com.zeroz4j.api.RmiSecurityContext;
import java.util.LinkedHashSet;
import java.util.Set;
import com.zeroz4j.api.ObjectMapper;

/**
 * Core runtime controller managing RMI network requests, TeaVM coroutine suspension/resumption,
 * server push message dispatching, and LiveSync object updates.
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>TeaVM Coroutine Integration:</b> {@link #executeCall(String, String, Object[])} is annotated with {@code @Async}.
 *       When called by an RMI stub, TeaVM suspends the browser coroutine. The underlying helper constructs a binary request packet
 *       (msg ID + interface name + method name + marshalled args) and transmits it via {@link WasmWebSocketChannel}.
 *   <li><b>Response Dispatch:</b> Incoming binary WebSocket frames are parsed by {@link #routeIncomingMessage(byte[])}.
 *       Matches correlation ID against {@code pendingRequests}. Calls {@code callback.complete(result)} to resume the suspended coroutine,
 *       or {@code callback.error(e)} if an exception occurred.</li>
 *   <li><b>Push & Auth Frames:</b> Handles 0x02 (PUSH) to trigger topic listeners, 0x03 (AUTH) to populate {@link RmiSecurityContext},
 *       and 0x10 (SUBSCRIBE) to deserialize inline LiveSync updates directly into {@link #MAPPER}.</li>
 * </ul>
 */
public class WasmRmiClient {
    /** Global {@link ObjectMapper} instance tracking object reference handles on the Wasm client. */
    public static final ObjectMapper MAPPER = new ObjectMapper();
    static final AtomicInteger messageIdGenerator = new AtomicInteger(0);
    static final Map<Integer, PendingRequest> pendingRequests = new ConcurrentHashMap<>();
    /** Max age of an unanswered request before its callback is failed; <= 0 disables the sweep. */
    static long requestTimeoutMs = 30_000;
    static final Map<String, List<PushListener<Object>>> pushListeners = new ConcurrentHashMap<>();
    static WasmWebSocketChannel networkChannel;
    private static PlatformScheduler uiScheduler;

    /**
     * The connection state as a signal, so UI reacts to it like any other state: read it in an
     * {@code Effect} and the effect re-runs on every transition. See {@link #connectionState()}.
     */
    private static final com.zeroz4j.signals.ValueSignal<WasmRmiClientChannel.State> connectionState =
            new com.zeroz4j.signals.ValueSignal<>(WasmRmiClientChannel.State.CONNECTING);

    /** Previous state, to tell a reconnect (RECONNECTING → CONNECTED) from the first open. */
    private static WasmRmiClientChannel.State lastState = WasmRmiClientChannel.State.CONNECTING;

    /**
     * Pluggable platform scheduler for dispatching push callbacks onto a main UI thread (if applicable).
     */
    public interface PlatformScheduler {
        /**
         * Schedules a runnable task for execution on the UI event thread.
         *
         * @param runnable the task to execute
         */
        void runLater(Runnable runnable);
    }

    /**
     * Configures the platform scheduler implementation for dispatching incoming push callbacks.
     *
     * @param scheduler the UI platform scheduler instance
     *
     * <p><b>Under the hood:</b> Sets static reference {@code uiScheduler = scheduler}.</p>
     */
    public static void setPlatformScheduler(PlatformScheduler scheduler) {
        uiScheduler = scheduler;
    }

    static PlatformScheduler getPlatformScheduler() {
        return uiScheduler;
    }

    /**
     * Initializes the Wasm RMI client engine with a WebSocket network channel and registers RMI client delegates.
     *
     * @param channel the active WebSocket channel
     *
     * <p><b>Under the hood:</b> Registers an anonymous {@link RmiClientExecutor.Executor} calling {@link #executeCall},
     * stores {@code networkChannel = channel}, and attaches {@link #routeIncomingMessage(byte[])} as the binary handler.</p>
     */
    public static void initialize(WasmWebSocketChannel channel) {
        // Register the executor delegate FIRST so stubs work immediately
        RmiClientExecutor.setInstance(
            (iface, method, args) -> executeCall(iface, method, args));
        networkChannel = channel;
        networkChannel.registerBinaryMessageHandler(WasmRmiClient::routeIncomingMessage);
        ClientSignalTransport.install();
        LiveMutations.install();
        ClientLazyAdapter.install();
        if (channel instanceof WasmRmiClientChannel) {
            ((WasmRmiClientChannel) channel).addStateListener(WasmRmiClient::onStateChange);
        }
    }

    /**
     * The connection to the server, as a read-only signal: {@code CONNECTING} during the first
     * attempt, {@code CONNECTED} when usable, {@code RECONNECTING} after a drop, {@code CLOSED}
     * after a deliberate close.
     *
     * <p>Read it inside an {@code Effect} to react — disable a form while not connected, render
     * an indicator — with no listener wiring. The built-in banner uses exactly this signal.</p>
     *
     * @return the connection state signal
     */
    public static com.zeroz4j.signals.ValueSignal<WasmRmiClientChannel.State> connectionState() {
        return connectionState;
    }

    /**
     * Reacts to a connection state transition. Called by the channel; the whole recovery
     * choreography hangs off this method:
     *
     * <ul>
     *   <li><b>Down</b> (RECONNECTING or CLOSED): every in-flight call fails now with a
     *       {@link com.zeroz4j.api.DisconnectedException} rather than hanging out its timeout,
     *       and held {@code LiveMutex} locks report themselves lost — the server released them
     *       when the session closed.</li>
     *   <li><b>Restored</b> (RECONNECTING → CONNECTED): what the user did while offline is sent
     *       first (queued shared-signal writes, then pending live-object edits), so the server
     *       truth fetched next already includes it. Then every shared signal re-subscribes,
     *       which snaps its mirror to the current retained value, and one re-sync request asks
     *       the server to re-send the current state of every object this client holds. The
     *       answers arrive as ordinary in-place updates. Re-serializing the objects also
     *       re-registers their lazy-field handles for the new session.</li>
     * </ul>
     */
    static void onStateChange(WasmRmiClientChannel.State state) {
        WasmRmiClientChannel.State previous = lastState;
        lastState = state;
        connectionState.set(state);

        if (state == WasmRmiClientChannel.State.RECONNECTING
                || state == WasmRmiClientChannel.State.CLOSED) {
            failAllPending("the connection to the server was lost");
            ClientLiveMutexProvider.connectionLost();
        } else if (state == WasmRmiClientChannel.State.CONNECTED
                && previous == WasmRmiClientChannel.State.RECONNECTING) {
            ClientSignalTransport.flushQueuedWrites();
            LiveMutations.flushPending();
            ClientSignalTransport.resubscribeAll();
            sendResyncRequest();
        }
    }

    /** Test support only: puts the connection-state tracking back to its initial value. */
    static void resetConnectionStateForTesting() {
        lastState = WasmRmiClientChannel.State.CONNECTING;
        connectionState.set(WasmRmiClientChannel.State.CONNECTING);
    }

    /**
     * Fails every in-flight request immediately. A suspended coroutine whose reply can no longer
     * arrive must be resumed with an error now; waiting out the request timeout just makes the
     * user stare at a stuck action for thirty seconds before learning what the framework already
     * knew.
     */
    private static void failAllPending(String reason) {
        for (Integer id : pendingRequests.keySet()) {
            PendingRequest pending = pendingRequests.remove(id);
            if (pending != null) {
                pending.callback.error(new com.zeroz4j.api.DisconnectedException(
                        "RMI request " + id + " failed: " + reason));
            }
        }
    }

    /**
     * Asks the server to re-send the current state of every object this client holds, by handle.
     * Fire-and-forget: the answers are ordinary 0x10 update frames applied in place, so there is
     * nothing to correlate. Objects the server no longer knows (it restarted) are logged there.
     */
    private static void sendResyncRequest() {
        List<String> handles = MAPPER.ids();
        if (handles.isEmpty()) {
            return;
        }
        try {
            GrowableBuffer buffer = new GrowableBuffer();
            buffer.putInt(0); // fire-and-forget
            BinarySerializer.writeString(buffer, SyncFrameTypes.RESYNC_SERVICE);
            BinarySerializer.writeString(buffer, "sync");
            buffer.putInt(1);
            BinarySerializer.writeValue(buffer, handles, MAPPER);
            networkChannel.sendRawBytes(buffer.toByteArray());
            System.out.println("[zeroz4j] Re-sync requested for " + handles.size() + " object(s)");
        } catch (Exception e) {
            System.err.println("[zeroz4j] Failed to request re-sync: " + e.getMessage());
        }
    }

    /**
     * Native TeaVM {@code @Async} stub method interceptor. Calling this method from Java code
     * cooperatively suspends the TeaVM WebAssembly coroutine until the backend returns a binary response.
     *
     * @param interfaceName canonical interface name of the service
     * @param methodName    target method name
     * @param args          method arguments
     * @return return value returned by the server method
     *
     * <p><b>Under the hood:</b> Transpiled by TeaVM into an async continuation point. Invokes
     * {@link #executeCall(String, String, Object[], AsyncCallback)} passing the generated callback continuation handle.</p>
     */
    @Async
    public static native Object executeCall(String interfaceName, String methodName, Object[] args);

    /**
     * Configures how long an unanswered RMI request may stay pending before its suspended
     * coroutine is resumed with an error. Defaults to 30 seconds; pass 0 or a negative
     * value to disable timeouts.
     *
     * @param timeoutMs maximum pending age in milliseconds
     */
    public static void setRequestTimeout(long timeoutMs) {
        requestTimeoutMs = timeoutMs;
    }

    static void sweepStaleRequests() {
        if (requestTimeoutMs <= 0 || pendingRequests.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<Integer, PendingRequest> entry : pendingRequests.entrySet()) {
            if (now - entry.getValue().createdAtMs > requestTimeoutMs) {
                PendingRequest stale = pendingRequests.remove(entry.getKey());
                if (stale != null) {
                    stale.callback.error(new RuntimeException(
                        "RMI request " + entry.getKey() + " timed out after " + requestTimeoutMs + " ms"));
                }
            }
        }
    }

    static void executeCall(String interfaceName, String methodName, Object[] args,
                                     AsyncCallback<Object> callback) {
        sweepStaleRequests();

        // Fail fast while the connection is down. Writing into a dead socket either throws a
        // browser error or silently discards the frame; both leave the user guessing. Deliberately
        // NOT queued for replay: the framework cannot know whether repeating this call later is
        // safe, and silently replaying "submit order" after an outage places the order twice.
        if (networkChannel == null || !networkChannel.isOpen()) {
            callback.error(new com.zeroz4j.api.DisconnectedException(
                    "RMI call " + interfaceName + "#" + methodName
                    + " refused: not connected to the server"));
            return;
        }

        int msgId = messageIdGenerator.incrementAndGet() & 0x7FFFFFFF;
        pendingRequests.put(msgId, new PendingRequest(callback, System.currentTimeMillis()));

        try {
            GrowableBuffer buffer = new GrowableBuffer();
            buffer.putInt(msgId);
            BinarySerializer.writeString(buffer, interfaceName);
            BinarySerializer.writeString(buffer, methodName);

            if (args == null) {
                buffer.putInt(0);
            } else {
                buffer.putInt(args.length);
                for (Object arg : args) {
                    BinarySerializer.writeValue(buffer, arg, MAPPER);
                }
            }

            networkChannel.sendRawBytes(buffer.toByteArray());
        } catch (Exception e) {
            pendingRequests.remove(msgId);
            callback.error(e);
        }
    }

    static void routeIncomingMessage(byte[] rawPayload) {
        sweepStaleRequests();
        // Inbound deserialization populates live instances through their setters;
        // suppress mutation tracking so applied state is never echoed back as a write.
        com.zeroz4j.api.LiveMutationTracker.beginRemoteApply();
        try {
            routeIncomingMessageInternal(rawPayload);
        } finally {
            com.zeroz4j.api.LiveMutationTracker.endRemoteApply();
        }
    }

    private static void routeIncomingMessageInternal(byte[] rawPayload) {
        ByteBuffer buffer;
        int correlationId;
        byte frameType;

        try {
            buffer = ByteBuffer.wrap(rawPayload);
            correlationId = buffer.getInt();
            frameType = buffer.get();
        } catch (Exception e) {
            System.err.println("[zeroz4j] Malformed response frame: " + e.getMessage());
            return;
        }

        try {
            if (frameType == SyncFrameTypes.RPC_RESPONSE) {
                Object result = BinarySerializer.readValue(buffer, MAPPER);
                PendingRequest pending = pendingRequests.remove(correlationId);
                if (pending != null) {
                    pending.callback.complete(result);
                }
            } else if (frameType == SyncFrameTypes.RPC_ERROR) {
                String errMsg = BinarySerializer.readString(buffer);
                PendingRequest pending = pendingRequests.remove(correlationId);
                if (pending != null) {
                    pending.callback.error(new RuntimeException(errMsg));
                }
            } else if (frameType == SyncFrameTypes.RPC_PUSH) {
                String topic = BinarySerializer.readString(buffer);
                Object payload = BinarySerializer.readValue(buffer, MAPPER);
                dispatchPushMessage(topic, payload);
            } else if (frameType == SyncFrameTypes.SIGNAL_UPD) {
                String signalName = BinarySerializer.readString(buffer);
                Object signalValue = BinarySerializer.readValue(buffer, MAPPER);
                Runnable apply = () -> ClientSignalTransport.handleUpdate(signalName, signalValue);
                if (uiScheduler != null) {
                    uiScheduler.runLater(apply);
                } else {
                    apply.run();
                }
            } else if (frameType == SyncFrameTypes.AUTH) {
                // AUTH frame
                byte protocolVersion = buffer.get(); // Read protocol version
                String username = BinarySerializer.readString(buffer);
                int roleCount = buffer.getInt();
                Set<String> roles = new LinkedHashSet<>();
                for (int i = 0; i < roleCount; i++) {
                    roles.add(BinarySerializer.readString(buffer));
                }
                RmiSecurityContext.populate(username, roles);
                System.out.println("[zeroz4j] Authenticated as: " + username + " roles=" + roles + " (protocol v" + protocolVersion + ")");
            } else if (frameType == SyncFrameTypes.SUBSCRIBE) {
                // Sync notification from server (formerly SNAPSHOT)
                // We just deserialize it, which will update the mapper instance inline!
                BinarySerializer.readValue(buffer, MAPPER);
            } else {
                System.err.println("[zeroz4j] Unknown frame type: 0x" + Integer.toHexString(frameType & 0xFF));
            }
        } catch (Exception e) {
            System.err.println("[zeroz4j] Error processing response: " + e.getMessage());
            PendingRequest pending = pendingRequests.remove(correlationId);
            if (pending != null) {
                pending.callback.error(new RuntimeException(
                    "Failed to deserialize server response: " + e.getMessage(), e));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void dispatchPushMessage(String topic, Object payload) {
        List<PushListener<Object>> listeners = pushListeners.get(topic);
        if (listeners != null) {
            for (PushListener<Object> listener : listeners) {
                Runnable action = () -> {
                    try {
                        listener.onPush(payload);
                    } catch (Exception e) {
                        System.err.println("[zeroz4j] Push listener error for '" + topic + "': " + e.getMessage());
                    }
                };
                if (uiScheduler != null) {
                    uiScheduler.runLater(action);
                } else {
                    action.run();
                }
            }
        }
    }

    /**
     * Registers a listener callback for server-initiated push notifications on a specific topic string.
     *
     * @param <T>      payload object type
     * @param topic    topic name string
     * @param listener listener callback instance
     *
     * <p><b>Under the hood:</b> Inserts {@code listener} into {@code pushListeners} thread-safe list associated with {@code topic}.</p>
     */
    @SuppressWarnings("unchecked")
    public static <T> void registerPushListener(String topic, PushListener<T> listener) {
        pushListeners.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>())
                     .add((PushListener<Object>) listener);
    }

    /**
     * Removes a single previously registered push listener for the specified topic string.
     *
     * @param topic    target topic name string
     * @param listener the listener instance to remove
     *
     * <p><b>Under the hood:</b> Removes {@code listener} from the topic's listener list and
     * drops the map entry once the list is empty.</p>
     */
    public static void removePushListener(String topic, PushListener<?> listener) {
        List<PushListener<Object>> listeners = pushListeners.get(topic);
        if (listeners != null) {
            listeners.remove(listener);
            if (listeners.isEmpty()) {
                pushListeners.remove(topic, listeners);
            }
        }
    }

    /**
     * Clears all registered push listeners for the specified topic string.
     *
     * @param topic target topic name string
     *
     * <p><b>Under the hood:</b> Removes key entry from {@code pushListeners} map.</p>
     */
    public static void clearPushListeners(String topic) {
        pushListeners.remove(topic);
    }

    /**
     * Callback interface for handling server-initiated push notification events.
     *
     * @param <T> payload type
     */
    public interface PushListener<T> {
        /**
         * Invoked when a server push message arrives for the subscribed topic.
         *
         * @param payload deserialized message payload
         */
        void onPush(T payload);
    }

    static class PendingRequest {
        final AsyncCallback<Object> callback;
        final long createdAtMs;
        PendingRequest(AsyncCallback<Object> callback, long createdAtMs) {
            this.callback = callback;
            this.createdAtMs = createdAtMs;
        }
    }
}
