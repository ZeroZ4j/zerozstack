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
 * long as the application has not closed the channel deliberately. On reconnect the framework
 * recovers by itself: queued offline writes and edits are sent, every shared signal re-subscribes,
 * and a re-sync request refreshes every object this client holds — see
 * {@code WasmRmiClient.onStateChange} for the choreography. What is deliberately <em>not</em>
 * replayed is RMI calls: a call in flight when the socket dropped fails immediately with a
 * {@code DisconnectedException}, because the channel cannot know whether repeating it is safe,
 * and repeating a non-idempotent one corrupts data silently. Deciding what to do about a lost
 * call belongs to the application, which is what {@link StateListener} exists for.
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
     * <p>Most applications need no listener at all: the built-in banner shows the outage, and
     * signals and live objects re-synchronize automatically on {@link State#CONNECTED}. Register
     * one to render a custom indicator (also available as a signal via
     * {@code WasmRmiClient.connectionState()}), or to redo work the framework cannot: re-register
     * with an application-level, session-keyed registry (the session id changed), or retry an RMI
     * call that was lost — re-reading authoritative state is correct whether or not the lost call
     * had been applied, and needs no sequence numbers.
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
    /** Recomputes the connect URL per attempt; null means the fixed {@link #url} is used. */
    private java.util.function.Supplier<String> urlProvider;
    private final Runnable onOpen;
    private ConnectionListener connectionListener;
    private final java.util.List<StateListener> stateListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
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
     * Adds a listener for connection state changes. Several can coexist: the framework registers
     * its own for re-synchronization and the built-in banner, without occupying the application's.
     *
     * <p>The current state is reported immediately, so a listener registered after the socket has
     * already opened is not left waiting for a transition that has been and gone.
     *
     * @param listener the state listener
     */
    public void addStateListener(StateListener listener) {
        if (listener == null) {
            return;
        }
        stateListeners.add(listener);
        listener.onStateChange(state);
    }

    /**
     * Removes a previously added state listener. Unknown listeners are ignored.
     *
     * @param listener the listener to remove
     */
    public void removeStateListener(StateListener listener) {
        stateListeners.remove(listener);
    }

    /** The one listener installed through {@link #setStateListener}, kept apart so it replaces. */
    private StateListener applicationStateListener;

    /**
     * Sets <em>the</em> application state listener, replacing any previous one set this way.
     *
     * <p>Replace rather than add, deliberately: applications register from view constructors, and
     * views are rebuilt on navigation. Under add-semantics every rebuilt view would leave its
     * predecessor's listener behind, each firing on every reconnect against a view no longer on
     * screen. Existing applications rely on replacement. Framework internals and code that manages
     * its own lifecycle use {@link #addStateListener}/{@link #removeStateListener} instead, which
     * this does not disturb.
     *
     * @param listener the state listener, or {@code null} to clear
     */
    public void setStateListener(StateListener listener) {
        if (applicationStateListener != null) {
            stateListeners.remove(applicationStateListener);
        }
        applicationStateListener = listener;
        addStateListener(listener);
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
        for (StateListener listener : stateListeners) {
            listener.onStateChange(next);
        }
    }

    /**
     * Whether the socket is open and a send will reach the wire.
     *
     * @return true only in {@link State#CONNECTED}
     */
    @Override
    public boolean isOpen() {
        return state == State.CONNECTED;
    }

    /**
     * Supplies the URL for each connection attempt, replacing the fixed one.
     *
     * <p>Reconnection would otherwise reuse the URL the channel was built with, which is wrong as
     * soon as that URL carries a credential: an access token valid at first connect has usually
     * expired by the time a long-lived session drops and recovers, so the reconnect would come back
     * anonymous and every secured call on it would start failing. {@code OidcClient} installs a
     * provider that appends whichever token is current.</p>
     *
     * <p>The provider is called on the reconnect path and must not block — it reads an already
     * refreshed token rather than fetching one.</p>
     *
     * @param provider computes the URL per attempt, or null to go back to the fixed URL
     */
    public void setConnectUrlProvider(java.util.function.Supplier<String> provider) {
        this.urlProvider = provider;
    }

    private void connect() {
        this.ws = new WasmWebSocket(urlProvider != null ? urlProvider.get() : url,
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
