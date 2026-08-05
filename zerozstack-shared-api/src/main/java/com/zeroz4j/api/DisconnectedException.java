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
 * Thrown by an RMI call made while the connection to the server is down.
 *
 * <p>Two situations produce it. A call made while the socket is not open fails with it
 * immediately — the call is never queued, because the framework cannot know whether
 * repeating it later would be safe, and silently replaying "submit order" after an outage
 * is how an order gets placed twice. And a call that was in flight when the socket dropped
 * fails with it the moment the drop is detected, instead of hanging until a timeout.</p>
 *
 * <p>What an application should do with it: usually nothing beyond telling the user, since
 * the built-in reconnect banner is already showing. The connection restores itself, shared
 * signals and live objects re-synchronize automatically, and the user can retry the action.
 * An application that wants to prevent the exception rather than catch it can disable its
 * controls while the connection state signal is not {@code CONNECTED}.</p>
 */
public class DisconnectedException extends RuntimeException {

    /**
     * Creates the exception with a message describing which call was affected and why.
     *
     * @param message human-readable description
     */
    public DisconnectedException(String message) {
        super(message);
    }
}
