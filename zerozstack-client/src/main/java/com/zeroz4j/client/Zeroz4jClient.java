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

/**
 * Entry point utility for bootstrapping and connecting the zeroz4j WebAssembly client runtime to a backend WebSocket server.
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>Initialization Chain:</b> Invokes {@link BinaryRegistry#init()} to trigger SPI discovery of generated {@code BinaryRegistrar} serializers,
 *       constructs a {@link WasmRmiClientChannel}, and initializes {@link WasmRmiClient}.</li>
 *   <li><b>Side Effects:</b> Opens a persistent binary WebSocket connection to {@code wsUrl}. Registers network handlers.</li>
 * </ul>
 */
public final class Zeroz4jClient {

    private static WasmRmiClientChannel channel;
    private static boolean bannerEnabled = true;
    private static java.util.function.Supplier<String> urlProvider;

    private Zeroz4jClient() {
        // Prevent instantiation
    }

    /**
     * Turns the built-in "Connection lost — reconnecting…" bar on or off. On by default, so a
     * dropped connection is never invisible in an application that configured nothing. Turn it
     * off when the application renders its own indicator from
     * {@link WasmRmiClient#connectionState()} — two banners saying the same thing is worse
     * than either.
     *
     * @param enabled false to suppress the built-in bar
     */
    public static void showConnectionBanner(boolean enabled) {
        bannerEnabled = enabled;
        if (!enabled) {
            ConnectionBanner.hide();
        }
    }

    /**
     * The channel established by {@link #connect(String, Runnable)}, or {@code null} before it.
     *
     * <p>Most applications never need it: reconnection, the outage banner, and the
     * re-synchronization of shared signals and live objects are automatic, and the connection
     * state is available as a signal via {@code WasmRmiClient.connectionState()}. It is exposed
     * for the cases that remain the application's: retrying an RMI call that failed with a
     * {@code DisconnectedException}, and re-registering with server-side registries keyed by
     * session id, which changes on every reconnect.
     *
     * @return the active channel, or null if {@code connect} has not been called
     */
    public static WasmRmiClientChannel channel() {
        return channel;
    }

    /**
     * Connects the zeroz4j WebAssembly client to the specified WebSocket URL and registers a completion callback.
     *
     * @param wsUrl   the WebSocket endpoint URL (e.g., "ws://localhost:8080/wasm-rmi")
     * @param onReady callback {@link Runnable} executed when the WebSocket handshake succeeds
     *
     * <p><b>Under the hood:</b> Calls {@link BinaryRegistry#init()} to load serializers via SPI. Instantiates {@link WasmRmiClientChannel}
     * with {@code wsUrl} and {@code onReady}. Passes channel to {@link WasmRmiClient#initialize(WasmWebSocketChannel)}.</p>
     */
    /**
     * Recomputes the connect URL for every attempt, including reconnects.
     *
     * <p>Set by {@code OidcClient.appendToken} so a reconnect carries whichever access token is
     * current, rather than the one that happened to be valid when the page loaded. Applications
     * that manage their own credentials can use it for the same reason.</p>
     *
     * <p>Called on the reconnect path, so it must return promptly and must not block on the network.</p>
     *
     * @param provider computes the URL per attempt, or null to keep using the URL passed to
     *                 {@link #connect(String, Runnable)}
     */
    public static void setConnectUrlProvider(java.util.function.Supplier<String> provider) {
        urlProvider = provider;
        if (channel != null) {
            channel.setConnectUrlProvider(provider);
        }
    }

    /**
     * The WebSocket endpoint for this deployment, wherever it is deployed.
     *
     * <pre>{@code
     * Zeroz4jClient.connect(Zeroz4jClient.defaultWebSocketUrl(), () -> Router.start("app-root"));
     * }</pre>
     *
     * <p>Prefer it to a hand-written URL. The hand-written ones tend to be either
     * {@code location.host + "/wasm-rmi"}, which is wrong under a context path, or the last segment
     * stripped off {@code location.pathname}, which is right on the landing page and wrong on every
     * deep link.</p>
     *
     * @return e.g. {@code "wss://example.com/coachapp/wasm-rmi"}
     * @see AppBase
     */
    public static String defaultWebSocketUrl() {
        return AppBase.webSocketUrl();
    }

    public static void connect(String wsUrl, Runnable onReady) {
        BinaryRegistry.init();
        System.out.println("[zeroz4j] Connecting to " + wsUrl + "...");
        channel = new WasmRmiClientChannel(wsUrl, onReady);
        if (urlProvider != null) {
            channel.setConnectUrlProvider(urlProvider);
        }
        WasmRmiClient.initialize(channel);
        channel.addStateListener(state -> {
            if (!bannerEnabled) {
                return;
            }
            if (state == WasmRmiClientChannel.State.RECONNECTING) {
                ConnectionBanner.show("Connection lost — reconnecting…");
            } else if (state == WasmRmiClientChannel.State.CONNECTED) {
                ConnectionBanner.hide();
            }
        });
    }

    /**
     * Connects the zeroz4j WebAssembly client to the specified WebSocket URL without a completion callback.
     *
     * @param wsUrl the WebSocket endpoint URL
     *
     * <p><b>Under the hood:</b> Delegates to {@link #connect(String, Runnable)} passing an empty no-op Runnable.</p>
     */
    public static void connect(String wsUrl) {
        connect(wsUrl, () -> {});
    }
}
