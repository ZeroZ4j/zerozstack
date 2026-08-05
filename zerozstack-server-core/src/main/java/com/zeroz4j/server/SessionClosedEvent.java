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

/**
 * CDI event fired when a WebSocket session closes, after the framework's own cleanup.
 *
 * <p>This exists because applications keep registries keyed by session id — "push the dashboard
 * to these sessions", "these sessions joined this room" — and before this event they had no way
 * to learn a session was gone. They coped with bounded collections and eviction heuristics.
 * Observe it and remove the id instead:
 *
 * <pre>{@code
 * void onSessionClosed(@Observes SessionClosedEvent event) {
 *     viewers.remove(event.sessionId());
 * }
 * }</pre>
 *
 * <p>Fired on every close, whether the user left or the connection dropped. Note that a client
 * whose socket dropped usually reconnects within seconds — as a <em>new</em> session with a new
 * id, re-running the handshake and {@code onOpen}. A registry entry removed here is re-created
 * by whatever application call registered it in the first place, which the client repeats after
 * reconnecting.
 *
 * @param sessionId     the closed session's id — the same value application code saw in
 *                      {@code RmiRequestContext.getSessionId()} while the session was alive
 * @param principalName the authenticated user's name, or {@code null} for an anonymous session
 */
public record SessionClosedEvent(String sessionId, String principalName) {
}
