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

    private Zeroz4jClient() {
        // Prevent instantiation
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
    public static void connect(String wsUrl, Runnable onReady) {
        BinaryRegistry.init();
        System.out.println("[zeroz4j] Connecting to " + wsUrl + "...");
        WasmRmiClientChannel channel = new WasmRmiClientChannel(wsUrl, onReady);
        WasmRmiClient.initialize(channel);
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
