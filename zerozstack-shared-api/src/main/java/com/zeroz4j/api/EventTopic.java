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
 * Typed identifier for a server-to-client event channel.
 *
 * <p>An {@code EventTopic} binds a wire topic name to its payload type in one shared
 * declaration that both server and client compile against. The server publishes through
 * {@code EventPublisher.publish(topic, payload)} and the Wasm client subscribes through
 * {@code ServerEvents.on(topic, handler)}; the shared type parameter makes a payload
 * mismatch a compile error instead of a runtime cast failure.</p>
 *
 * <p>Terminology: an <em>event</em> is a discrete, fire-and-forget occurrence — distinct
 * from a client-side reactive {@code Signal}, which models continuous state. Events feed
 * signals through reducers on the client.</p>
 *
 * <p>Declare topics once in the shared API module of an application:</p>
 * <pre>{@code
 * public final class ChatEvents {
 *     public static final EventTopic<ChatMessage> MESSAGE_POSTED =
 *             EventTopic.of(ChatMessage.class, "chat.messagePosted");
 * }
 * }</pre>
 *
 * <p>Delivery semantics: broadcast to currently connected sessions, at most once,
 * no replay for late subscribers.</p>
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>Wire Identity:</b> Only {@link #name()} travels on the wire (inside 0x02 PUSH frames);
 *       the payload class is compile-time metadata. Equality is therefore name-based.</li>
 *   <li><b>Explicit Names:</b> Names are explicit strings rather than derived from class names,
 *       so refactoring a payload class never silently changes the wire protocol.</li>
 * </ul>
 *
 * @param <T> payload type carried on this topic; use {@link Void} for payload-less events
 */
public final class EventTopic<T> {

    private final String name;
    private final Class<T> payloadType;

    private EventTopic(Class<T> payloadType, String name) {
        this.payloadType = payloadType;
        this.name = name;
    }

    /**
     * Creates a typed topic descriptor.
     *
     * @param <T>         payload type
     * @param payloadType payload class carried on this topic; {@code Void.class} for payload-less events
     * @param name        unique wire topic name, e.g. {@code "chat.messagePosted"}
     * @return the topic descriptor
     * @throws IllegalArgumentException if {@code payloadType} is null or {@code name} is null or blank
     */
    public static <T> EventTopic<T> of(Class<T> payloadType, String name) {
        if (payloadType == null) {
            throw new IllegalArgumentException("payloadType must not be null");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("name must not be null or blank");
        }
        return new EventTopic<>(payloadType, name);
    }

    /**
     * Returns the wire topic name.
     *
     * @return topic name string
     */
    public String name() {
        return name;
    }

    /**
     * Returns the payload class carried on this topic.
     *
     * @return payload class
     */
    public Class<T> payloadType() {
        return payloadType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EventTopic)) {
            return false;
        }
        return name.equals(((EventTopic<?>) o).name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return "EventTopic[" + name + ": " + payloadType.getName() + "]";
    }
}
