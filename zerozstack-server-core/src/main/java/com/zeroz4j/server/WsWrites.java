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
package com.zeroz4j.server;

import jakarta.websocket.Session;

import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Helper class for synchronized binary WebSocket writes per session.
 *
 * <p>Prevents concurrent write exceptions (e.g. Tomcat's {@code BINARY_FULL_WRITING}) when multiple virtual threads
 * attempt to transmit RMI responses, push messages, or LiveSync updates over the same WebSocket connection.</p>
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>Synchronization Lock:</b> Synchronizes directly on the {@code session} instance to guarantee serial execution of {@code session.getBasicRemote().sendBinary(...)}.</li>
 * </ul>
 */
final class WsWrites {

    private static final Logger LOG = Logger.getLogger(WsWrites.class.getName());

    private WsWrites() {}

    /**
     * Transmits a binary frame payload across a WebSocket session synchronously under a session lock.
     *
     * @param session target WebSocket session
     * @param frame   binary frame payload byte array
     *
     * <p><b>Under the hood:</b> Checks {@code session.isOpen()}. Acquires monitor lock on {@code session} object.
     * Wraps {@code frame} in {@link ByteBuffer} and invokes {@code session.getBasicRemote().sendBinary(ByteBuffer)}.</p>
     */
    static void send(Session session, byte[] frame) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            synchronized (session) {
                session.getBasicRemote().sendBinary(ByteBuffer.wrap(frame));
            }
        } catch (Exception e) {
            LOG.warning("[zeroz4j] WS send failed for session " + session.getId() + ": " + e.getMessage());
        }
    }
}
