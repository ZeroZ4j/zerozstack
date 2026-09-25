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

    // When to try again after a drop is decided by ReconnectPolicy (0.9.1+): a backoff that only
    // starts again from the beginning once a connection has stayed open for 30 seconds, nothing
    // attempted while the page is hidden, an attempt at once when it is shown again or the network
    // comes back, and no more automatic attempts after ten failures in a row.

    /** Where the connection is, for anything that wants to tell a user about it. */
    public enum State {
        /** The first connection attempt is in flight. */
        CONNECTING,
        /** Open and usable. */
        CONNECTED,
        /** Dropped; an attempt to restore it is scheduled or in flight. */
        RECONNECTING,
        /**
         * Closed by the application, or given up after too many failed attempts in a row (0.9.1+;
         * {@link WasmRmiClientChannel#hasGivenUp()} tells the two apart). Nothing further will be
         * attempted until {@link WasmRmiClientChannel#reconnect()}.
         */
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
    private boolean opened;
    private boolean closedByApplication;
    /** When the current socket opened, for the console line written when it closes. */
    private long socketOpenedAt = -1L;
    private final ReconnectPolicy policy = new ReconnectPolicy(new ReconnectPolicy.Host() {
        @Override
        public long now() {
            return System.currentTimeMillis();
        }

        @Override
        public boolean pageHidden() {
            return isPageHidden();
        }

        @Override
        public void schedule(int delayMillis, Runnable task) {
            WasmRmiClientChannel.schedule(delayMillis, task::run);
        }

        @Override
        public void openSocket() {
            if (!closedByApplication) {
                connect();
            }
        }
    });

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
        // Waits for a hidden page to be shown, and for the network to come back, before trying
        // again - rather than reconnecting on a timer that a background tab throttles anyway.
        listenForPageShownAndOnline(policy::pageShown, policy::networkBack);
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
        policy.stop();
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

    /**
     * Whether automatic reconnecting stopped after too many failed attempts in a row (0.9.1+). The
     * state is then {@link State#CLOSED}, and the built-in connection bar asks the person to reload
     * the page. {@link #reconnect()} starts again.
     *
     * @return true once the channel has given up
     */
    public boolean hasGivenUp() {
        return policy.hasGivenUp() && !closedByApplication;
    }

    /**
     * Which socket is the current one. Every callback of a socket checks it and goes quiet once a
     * newer socket has replaced its own, so a socket given up on by {@link #dropUnresponsive} cannot
     * schedule a second reconnect, or deliver a late frame, when its close finally arrives.
     */
    private int socketGeneration;

    private void connect() {
        final int generation = ++socketGeneration;
        // How the previous connection ended rides on the handshake as query parameters, so the
        // server log says why this browser keeps coming back. A server before 0.9.1 ignores them.
        String target = policy.withPreviousClose(urlProvider != null ? urlProvider.get() : url);
        this.ws = new WasmWebSocket(target,
            data -> {
                if (generation != socketGeneration) {
                    return;
                }
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
                if (generation != socketGeneration) {
                    return;
                }
                socketOpenedAt = System.currentTimeMillis();
                policy.opened();
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
                if (generation != socketGeneration) {
                    return;
                }
                System.err.println("[zeroz4j] WebSocket error: " + errorMsg);
                if (connectionListener != null) connectionListener.onError(errorMsg);
            },
            (code, reason) -> {
                if (generation != socketGeneration) {
                    return;
                }
                long openFor = socketOpenedAt >= 0 ? System.currentTimeMillis() - socketOpenedAt : -1L;
                socketOpenedAt = -1L;
                System.err.println("[zeroz4j] WebSocket closed: code=" + code + " reason=" + reason
                        + (openFor >= 0 ? " after " + openFor + " ms" : " before it opened")
                        + (isPageHidden() ? " (page hidden)" : ""));
                if (connectionListener != null) connectionListener.onClose(code, reason);
                scheduleReconnect(code, reason);
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
        policy.reset();
        closedByApplication = false;
        setState(State.RECONNECTING);
        connect();
    }

    /**
     * Treats a socket that has stopped answering as dropped.
     *
     * <p>The old socket is disowned first - every callback it still has checks which socket is
     * current and ignores itself - then closed with code 4000, and then the ordinary drop path
     * runs: state goes to {@link State#RECONNECTING}, which fails every call in flight with a
     * {@code DisconnectedException}, and what follows is decided by {@link ReconnectPolicy}, as
     * for any other drop. Waiting for the old socket's own close event instead is what this exists
     * to avoid: on a network that went silent the browser may not report it for minutes.</p>
     *
     * <p>Does nothing unless the channel is {@link State#CONNECTED}: a socket that is already
     * reconnecting, or was closed by the application, has nothing to give up on.</p>
     *
     * @param reason why, for the console
     */
    @Override
    public void dropUnresponsive(String reason) {
        if (state != State.CONNECTED || closedByApplication) {
            return;
        }
        System.err.println("[zeroz4j] Giving up on the connection: " + reason);
        WasmWebSocket unresponsive = ws;
        socketGeneration++;
        socketOpenedAt = -1L;
        if (unresponsive != null) {
            unresponsive.closeUnresponsive();
        }
        scheduleReconnect(4000, reason);
    }

    /**
     * Decides, through {@link ReconnectPolicy}, what follows a close that the application did not
     * ask for.
     *
     * <p>The browser offers no reliable event for "the server is reachable again", so the only way
     * to find out is to try. Backoff stops that becoming a busy loop against a server that is down;
     * the cap on the delay keeps the wait short enough that somebody watching the page sees it
     * recover; and the cap on attempts stops a page nobody can fix by waiting from trying forever.
     */
    private void scheduleReconnect(int code, String reason) {
        if (closedByApplication) {
            return;
        }
        switch (policy.closed(code, reason)) {
            case RETRY_SCHEDULED:
                setState(State.RECONNECTING);
                System.out.println("[zeroz4j] Reconnecting in " + policy.lastDelay()
                        + "ms (attempt " + (policy.failures() + 1) + ")");
                break;
            case WAITING_FOR_VISIBLE:
                setState(State.RECONNECTING);
                System.out.println("[zeroz4j] The page is hidden; reconnecting when it is shown again.");
                break;
            case GAVE_UP:
                System.err.println("[zeroz4j] Stopped reconnecting after "
                        + ReconnectPolicy.MAX_CONSECUTIVE_FAILURES + " failed attempts in a row.");
                setState(State.CLOSED);
                break;
            default:
                break;
        }
    }

    @JSBody(params = {}, script = "return document.visibilityState === 'hidden';")
    private static native boolean isPageHidden();

    /**
     * Calls back when the page becomes visible and when the browser reports the network is back.
     * Registered once per channel.
     */
    @JSBody(params = { "onShown", "onOnline" }, script =
        "document.addEventListener('visibilitychange', function () {" +
        "  if (document.visibilityState === 'visible') { onShown(); }" +
        "});" +
        "window.addEventListener('online', function () { onOnline(); });")
    private static native void listenForPageShownAndOnline(WasmWebSocket.ConnectionHandler onShown,
                                                           WasmWebSocket.ConnectionHandler onOnline);

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
