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

/**
 * Concrete implementation of {@link WasmWebSocketChannel} wrapping a browser native {@link WasmWebSocket}.
 * Provides error, message, and close lifecycle event handling with reconnection support.
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>TeaVM JSO Wrapping:</b> Delegates binary I/O to JS native WebSocket via {@link WasmWebSocket}.</li>
 *   <li><b>State Mutations:</b> Stores active {@link WasmWebSocket} instance and {@link BinaryMessageHandler} callback.</li>
 * </ul>
 */
public class WasmRmiClientChannel implements WasmWebSocketChannel {
    private WasmWebSocket ws;
    private BinaryMessageHandler messageHandler;
    private final String url;
    private final Runnable onOpen;
    private ConnectionListener connectionListener;

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
     * Constructs and connects a new {@code WasmRmiClientChannel} for the specified WebSocket URL.
     *
     * @param url    the target WebSocket URL string
     * @param onOpen callback {@link Runnable} executed when connection is established
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
                if (onOpen != null) onOpen.run();
            },
            errorMsg -> {
                System.err.println("[zeroz4j] WebSocket error: " + errorMsg);
                if (connectionListener != null) connectionListener.onError(errorMsg);
            },
            (code, reason) -> {
                System.err.println("[zeroz4j] WebSocket closed: code=" + code + " reason=" + reason);
                if (connectionListener != null) connectionListener.onClose(code, reason);
            }
        );
    }

    /**
     * Attempts to re-establish the WebSocket connection.
     *
     * <p><b>Under the hood:</b> Re-executes private {@code connect()} method, instantiating a new native {@link WasmWebSocket}.</p>
     */
    public void reconnect() {
        connect();
    }

    @Override
    public void registerBinaryMessageHandler(BinaryMessageHandler handler) {
        this.messageHandler = handler;
    }

    @Override
    public void sendRawBytes(byte[] bytes) {
        ws.send(bytes);
    }
}
