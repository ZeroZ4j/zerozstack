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

import org.teavm.jso.JSBody;

/**
 * Concrete implementation of {@link WasmWebSocketChannel} wrapping a browser native {@link WasmWebSocket}.
 * Provides error, message, and close lifecycle event handling with automatic reconnection.
 *
 * <p><b>Reconnection.</b> A dropped socket re-establishes itself with exponential backoff, for as
 * long as the application has not closed the channel deliberately. What this deliberately does
 * <em>not</em> do is replay calls that were in flight when the socket dropped: the channel cannot
 * know whether a given RPC is safe to repeat, and repeating a non-idempotent one corrupts data
 * silently. Restoring the channel is this class's job; deciding what to do about a lost call
 * belongs to the application, which is what {@link StateListener} exists for.
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>TeaVM JSO Wrapping:</b> Delegates binary I/O to JS native WebSocket via {@link WasmWebSocket}.</li>
 *   <li><b>State Mutations:</b> Stores active {@link WasmWebSocket} instance and {@link BinaryMessageHandler} callback.</li>
 * </ul>
 */
public class WasmRmiClientChannel implements WasmWebSocketChannel {

    /** First delay before a reconnect attempt, in milliseconds. Doubles up to {@link #MAX_BACKOFF_MS}. */
    private static final int BASE_BACKOFF_MS = 500;

    /**
     * Ceiling for the backoff, in milliseconds.
     *
     * <p>Capped rather than unbounded, and retried indefinitely rather than a fixed number of
     * times: someone who leaves a tab open across a network outage should find a working page when
     * they return, whereas a client that gave up after five attempts leaves them looking at a page
     * that appears fine and does nothing.
     */
    private static final int MAX_BACKOFF_MS = 15000;

    /** Where the connection is, for anything that wants to tell a user about it. */
    public enum State {
        /** The first connection attempt is in flight. */
        CONNECTING,
        /** Open and usable. */
        CONNECTED,
        /** Dropped; an attempt to restore it is scheduled or in flight. */
        RECONNECTING,
        /** Closed by the application. Nothing further will be attempted. */
        CLOSED
    }

    /**
     * Listener interface for monitoring WebSocket lifecycle connection events (errors, closures).
     */
    public interface ConnectionListener {
        /**
         * Invoked when a WebSocket network error occurs.
         *
         * @param message error description
         */
        void onError(String message);

        /**
         * Invoked when the WebSocket connection is closed.
         *
         * @param code   status code integer
         * @param reason closure reason string
         */
        void onClose(int code, String reason);
    }

    /**
     * Notified on every connection state change.
     *
     * <p>This is what an application renders a "reconnecting" indicator from, and where it decides
     * what to do about work lost to the drop. The usual answer is not to replay the lost call but
     * to re-read authoritative state from the server on {@link State#CONNECTED} — which is correct
     * whether or not the call had already been applied, and needs no sequence numbers to work out
     * which.
     */
    public interface StateListener {
        /**
         * Invoked on each state transition, on the browser event loop.
         *
         * @param state the state just entered
         */
        void onStateChange(State state);
    }

    private WasmWebSocket ws;
    private BinaryMessageHandler messageHandler;
    private final String url;
    private final Runnable onOpen;
    private ConnectionListener connectionListener;
    private StateListener stateListener;
    private State state = State.CONNECTING;
    private int attempt;
    private boolean opened;
    private boolean closedByApplication;

    /**
     * Constructs and connects a new {@code WasmRmiClientChannel} for the specified WebSocket URL.
     *
     * @param url    the target WebSocket URL string
     * @param onOpen callback {@link Runnable} executed when the connection is <em>first</em> established
     *
     * <p><b>Under the hood:</b> Instantiates underlying {@link WasmWebSocket} and initiates network connection.</p>
     */
    public WasmRmiClientChannel(String url, Runnable onOpen) {
        this.url = url;
        this.onOpen = onOpen;
        connect();
    }

    /**
     * Sets a connection lifecycle listener for error and closure events.
     *
     * @param listener the lifecycle listener instance
     */
    public void setConnectionListener(ConnectionListener listener) {
        this.connectionListener = listener;
    }

    /**
     * Sets a listener for connection state changes.
     *
     * <p>The current state is reported immediately, so a listener registered after the socket has
     * already opened is not left waiting for a transition that has been and gone.
     *
     * @param listener the state listener, or {@code null} to clear
     */
    public void setStateListener(StateListener listener) {
        this.stateListener = listener;
        if (listener != null) {
            listener.onStateChange(state);
        }
    }

    /**
     * The current connection state.
     *
     * @return the state as of this call
     */
    public State state() {
        return state;
    }

    /**
     * Closes the connection and stops reconnecting.
     *
     * <p>Without this there is no way to tell a deliberate shutdown from a network drop, and the
     * channel would go on trying to restore a connection the application has finished with.
     */
    public void close() {
        closedByApplication = true;
        setState(State.CLOSED);
        if (ws != null) {
            ws.close();
        }
    }

    private void setState(State next) {
        if (state == next) {
            return;
        }
        state = next;
        if (stateListener != null) {
            stateListener.onStateChange(next);
        }
    }

    private void connect() {
        this.ws = new WasmWebSocket(url,
            data -> {
                int len = data.getLength();
                byte[] bytes = new byte[len];
                for (int i = 0; i < len; i++) {
                    bytes[i] = data.get(i);
                }
                if (messageHandler != null) {
                    messageHandler.onMessage(bytes);
                }
            },
            () -> {
                attempt = 0;
                setState(State.CONNECTED);
                // onOpen is the application's bootstrap: it builds the UI. It must run once, on the
                // first connection only. Running it again after a reconnect would rebuild the page
                // from scratch and discard whatever the user was looking at — a worse outcome than
                // the dropped socket it was recovering from.
                if (!opened) {
                    opened = true;
                    if (onOpen != null) {
                        onOpen.run();
                    }
                }
            },
            errorMsg -> {
                System.err.println("[zeroz4j] WebSocket error: " + errorMsg);
                if (connectionListener != null) connectionListener.onError(errorMsg);
            },
            (code, reason) -> {
                System.err.println("[zeroz4j] WebSocket closed: code=" + code + " reason=" + reason);
                if (connectionListener != null) connectionListener.onClose(code, reason);
                scheduleReconnect();
            }
        );
    }

    /**
     * Attempts to re-establish the connection immediately, resetting the backoff.
     *
     * <p>Reconnection is automatic after an unexpected close, so this is for when the application
     * knows something the timer does not — a "try again" control, or the page becoming visible
     * again after a device woke up. It also revives a channel that was deliberately closed.
     *
     * <p><b>Under the hood:</b> Re-executes private {@code connect()}, instantiating a new native
     * {@link WasmWebSocket}.</p>
     */
    public void reconnect() {
        attempt = 0;
        closedByApplication = false;
        setState(State.RECONNECTING);
        connect();
    }

    /**
     * Queues the next attempt with exponential backoff.
     *
     * <p>The browser offers no event for "the network came back", so the only way to find out is to
     * try. Backoff stops that becoming a busy loop against a server that is down; the cap keeps the
     * wait short enough that somebody watching the page sees it recover rather than concluding it
     * is broken.
     */
    private void scheduleReconnect() {
        if (closedByApplication) {
            return;
        }
        setState(State.RECONNECTING);

        int delay = BASE_BACKOFF_MS;
        for (int i = 0; i < attempt && delay < MAX_BACKOFF_MS; i++) {
            delay *= 2;
        }
        if (delay > MAX_BACKOFF_MS) {
            delay = MAX_BACKOFF_MS;
        }
        attempt++;

        System.out.println("[zeroz4j] Reconnecting in " + delay + "ms (attempt " + attempt + ")");
        schedule(delay, () -> {
            if (!closedByApplication) {
                connect();
            }
        });
    }

    @JSBody(params = { "delayMs", "callback" },
            script = "window.setTimeout(function () { callback(); }, delayMs);")
    private static native void schedule(int delayMs, WasmWebSocket.ConnectionHandler callback);

    @Override
    public void registerBinaryMessageHandler(BinaryMessageHandler handler) {
        this.messageHandler = handler;
    }

    @Override
    public void sendRawBytes(byte[] bytes) {
        ws.send(bytes);
    }
}
