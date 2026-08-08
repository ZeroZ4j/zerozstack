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

import com.zeroz4j.api.EventTopic;

/**
 * Server-side publisher for typed event topics.
 *
 * <p>Inject this interface into {@code @RmiService} implementations instead of the transport
 * engine, and publish events through a shared {@link EventTopic} declaration:</p>
 * <pre>{@code
 * @Inject EventPublisher events;
 * ...
 * events.publish(ChatEvents.MESSAGE_POSTED, msg);
 * }</pre>
 *
 * <p>Delivery semantics: fire-and-forget broadcast to all currently connected sessions,
 * at most once, no replay. Payloads must be wire-serializable ({@code @DataModel} classes
 * or types supported by {@code BinarySerializer}).</p>
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>Transport:</b> Implemented by {@link WasmRmiServerEngine}; publishes ride the
 *       existing 0x02 PUSH frame with {@code topic.name()} as the wire topic.</li>
 *   <li><b>Type Safety:</b> The payload parameter is bound to the topic's type parameter,
 *       so publishing the wrong payload type is a compile error.</li>
 * </ul>
 */
public interface EventPublisher {

    /**
     * Broadcasts a payload to all connected client sessions subscribed to the topic.
     *
     * @param <T>     payload type bound by the topic
     * @param topic   shared topic descriptor
     * @param payload the payload to broadcast; may be null for {@code EventTopic<Void>} events
     */
    <T> void publish(EventTopic<T> topic, T payload);

    /**
     * Broadcasts a payload-less event on a {@code Void} topic.
     *
     * @param topic shared topic descriptor carrying no payload
     */
    default void publish(EventTopic<Void> topic) {
        publish(topic, null);
    }

    /**
     * Publishes a payload to the sessions matching a scope.
     *
     * <p>{@link #publish(EventTopic, Object)} reaches <b>every connected session</b> with no principal
     * check, which is correct for genuinely public news and a data leak for anything else. Use this
     * overload whenever the payload belongs to somebody.</p>
     *
     * @param <T>     payload type bound by the topic
     * @param topic   shared topic descriptor
     * @param payload the payload; may be null for {@code EventTopic<Void>} events
     * @param scope   how far the event reaches
     * @param target  the session id for {@link com.zeroz4j.api.Scope#SESSION}, or the user name for
     *                {@link com.zeroz4j.api.Scope#USER}; ignored when the scope is
     *                {@link com.zeroz4j.api.Scope#GLOBAL}
     */
    <T> void publish(EventTopic<T> topic, T payload, com.zeroz4j.api.Scope scope, String target);

    /**
     * Publishes to every session of one authenticated user — their other tabs and devices, and nobody
     * else's.
     *
     * @param <T>           payload type bound by the topic
     * @param topic         shared topic descriptor
     * @param payload       the payload
     * @param principalName the authenticated user name to reach
     */
    default <T> void publishToUser(EventTopic<T> topic, T payload, String principalName) {
        publish(topic, payload, com.zeroz4j.api.Scope.USER, principalName);
    }

    /**
     * Publishes to a single WebSocket session.
     *
     * @param <T>       payload type bound by the topic
     * @param topic     shared topic descriptor
     * @param payload   the payload
     * @param sessionId the session to reach; typically {@code RmiRequestContext.getSessionId()}
     */
    default <T> void publishToSession(EventTopic<T> topic, T payload, String sessionId) {
        publish(topic, payload, com.zeroz4j.api.Scope.SESSION, sessionId);
    }

    /**
     * Publishes to every session of one browser — its other tabs, and no other browser.
     *
     * <p>The scope for an application with no login: unlike
     * {@link #publishToUser(EventTopic, Object, String)} it needs no authentication, and unlike
     * {@link #publishToSession(EventTopic, Object, String)} it survives a reconnect, because the
     * client id outlives the session id.</p>
     *
     * <p>A client id identifies a browser, not a person. Do not use this to deliver something only
     * one particular user may see.</p>
     *
     * @param <T>      payload type bound by the topic
     * @param topic    shared topic descriptor
     * @param payload  the payload
     * @param clientId the browser to reach; typically {@code RmiRequestContext.getClientId()}
     */
    default <T> void publishToClient(EventTopic<T> topic, T payload, String clientId) {
        publish(topic, payload, com.zeroz4j.api.Scope.CLIENT, clientId);
    }

    /**
     * Closes every connection belonging to one authenticated user.
     *
     * <p>The counterpart to {@link #publishToUser(EventTopic, Object, String)}, and the only way an
     * application can revoke a signed-in session. <b>Identity is fixed for the life of a
     * connection</b> — roles are read once at the handshake and never re-checked — so disabling an
     * account, revoking a role or ending a session has no effect at all on a socket that is already
     * open. Closing it is what makes the change take effect: the client's automatic reconnect
     * re-presents whatever credential it still has, the application's
     * {@link AuthenticationProvider} is asked again, and it either declines or answers with fresh
     * roles.</p>
     *
     * <p>Sessions are closed with {@link jakarta.websocket.CloseReason.CloseCodes#VIOLATED_POLICY},
     * which the client reports rather than treating as a network drop.</p>
     *
     * @param principalName the authenticated user whose connections should end; null or blank
     *                      closes nothing, because "close everything" must be asked for explicitly
     * @param reason        a short explanation delivered in the close frame; truncated to fit the
     *                      123-byte limit the WebSocket protocol places on a close reason
     * @return how many connections were closed
     */
    int disconnect(String principalName, String reason);

    /**
     * Closes one connection.
     *
     * @param sessionId the session to end; typically {@code RmiRequestContext.getSessionId()}
     * @param reason    a short explanation delivered in the close frame
     * @return true when a session with that id was open and has been closed
     */
    boolean disconnectSession(String sessionId, String reason);
}
