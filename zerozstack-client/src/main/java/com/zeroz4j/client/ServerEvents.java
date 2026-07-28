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

import com.zeroz4j.api.Disposable;
import com.zeroz4j.api.EventTopic;
import com.zeroz4j.signals.ObservableSignal;
import com.zeroz4j.signals.ValueSignal;

import java.util.function.Consumer;

/**
 * Client-side subscription point for typed server events.
 *
 * <p>Subscribes typed handlers to {@link EventTopic} channels declared in the shared API
 * module. A handler is an ordinary callback — update components directly in it:</p>
 * <pre>{@code
 * Disposable sub = ServerEvents.on(ChatEvents.MESSAGE_POSTED, msg -> {
 *     messages.add(msg);
 *     render();
 * });
 * }</pre>
 *
 * <p>Signals are not required to consume events. When a view holds derived or
 * multiply-rendered state, reduce events into a {@code ValueSignal} with an immutable
 * update instead of rendering manually — see docs/SERVER_EVENTS.md ("Combining with
 * Signals"). For topics whose payload genuinely is state (e.g. a status broadcast),
 * {@link #latest} is the built-in bridge: it returns a signal holding the most recent
 * payload, usable directly inside {@code Effect} and {@code Computed}.</p>
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>Transport:</b> Rides {@link WasmRmiClient}'s 0x02 PUSH frame dispatch; handlers run on the
 *       platform UI scheduler when one is configured via {@link WasmRmiClient#setPlatformScheduler}.</li>
 *   <li><b>Lifecycle:</b> Every subscription returns an {@link Disposable}; views must dispose
 *       their subscriptions when permanently removed to avoid listener leaks.</li>
 * </ul>
 */
public final class ServerEvents {

    private ServerEvents() {}

    /**
     * Subscribes a typed handler to an event topic.
     *
     * @param <T>     payload type bound by the topic
     * @param topic   shared topic descriptor
     * @param handler callback invoked with each incoming payload (null for {@code EventTopic<Void>} events)
     * @return disposable handle that unsubscribes the handler
     */
    public static <T> Disposable on(EventTopic<T> topic, Consumer<T> handler) {
        WasmRmiClient.PushListener<T> listener = handler::accept;
        WasmRmiClient.registerPushListener(topic.name(), listener);
        return () -> WasmRmiClient.removePushListener(topic.name(), listener);
    }

    /**
     * Creates a signal holding the most recent payload received on a state-shaped topic.
     *
     * <p>Note the state semantics: consecutive equal payloads are deduplicated and late
     * subscribers see the last received value. For discrete event streams use
     * {@link #on(EventTopic, Consumer)} with a reducer instead.</p>
     *
     * @param <T>          payload type bound by the topic
     * @param topic        shared topic descriptor
     * @param initialValue value the signal holds until the first event arrives
     * @return network-bound signal; dispose it when no longer needed
     */
    public static <T> LatestSignal<T> latest(EventTopic<T> topic, T initialValue) {
        return new LatestSignal<>(topic, initialValue);
    }

    /**
     * An {@link ObservableSignal} tracking the most recent payload received on a topic.
     *
     * @param <T> payload type
     */
    public static final class LatestSignal<T> implements ObservableSignal<T> {

        private final ValueSignal<T> state;
        private final Disposable subscription;

        private LatestSignal(EventTopic<T> topic, T initialValue) {
            this.state = new ValueSignal<>(initialValue);
            this.subscription = on(topic, state::set);
        }

        @Override
        public T get() {
            return state.get();
        }

        @Override
        public void addListener(Consumer<T> listener) {
            state.addListener(listener);
        }

        @Override
        public void removeListener(Consumer<T> listener) {
            state.removeListener(listener);
        }

        /**
         * Unsubscribes this signal from the event topic. The signal keeps its last value
         * but no longer updates.
         */
        public void dispose() {
            subscription.dispose();
        }
    }
}
