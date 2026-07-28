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
}
