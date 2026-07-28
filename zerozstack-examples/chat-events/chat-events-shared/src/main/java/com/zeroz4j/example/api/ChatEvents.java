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
package com.zeroz4j.example.api;

import com.zeroz4j.api.EventTopic;
import com.zeroz4j.example.model.ChatMessage;

/**
 * Event topics shared by the chat server and client.
 *
 * <p>Each topic binds its wire name to its payload type once, here in the shared API module.
 * The server publishes via {@code EventPublisher} and the client subscribes via
 * {@code ServerEvents}; both compile against these declarations, so a payload type mismatch
 * is a compile error.</p>
 */
public final class ChatEvents {

    /** Broadcast after a message was accepted and persisted. */
    public static final EventTopic<ChatMessage> MESSAGE_POSTED =
            EventTopic.of(ChatMessage.class, "chat.messagePosted");

    /** Broadcast after an admin cleared the chat history. */
    public static final EventTopic<Void> HISTORY_CLEARED =
            EventTopic.of(Void.class, "chat.historyCleared");

    private ChatEvents() {}
}
