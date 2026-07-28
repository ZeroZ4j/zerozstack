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
package com.zeroz4j.example.client;

import com.zeroz4j.api.Disposable;
import com.zeroz4j.api.RmiSecurityContext;
import com.zeroz4j.client.ServerEvents;
import com.zeroz4j.example.api.ChatEvents;
import com.zeroz4j.example.api.ChatService;
import com.zeroz4j.example.api.ChatService_Stub;
import com.zeroz4j.example.model.ChatMessage;
import com.zeroz4j.ui.component.*;
import com.zeroz4j.ui.layout.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Chat view driven by typed server events: {@link ChatEvents} topics deliver new messages
 * and clear notifications, and the view updates its components directly in the handlers.
 *
 * <p>Deliberately signal-free — this example isolates the events feature. See the
 * {@code todo-signals} example for reactive client-side state, and docs/SERVER_EVENTS.md
 * for when combining events with signals pays off.</p>
 */
public class ChatView extends Card {

    private final ChatService chatService = new ChatService_Stub();

    // Plain view state: every mutation must remember to call render().
    private final List<ChatMessage> messages = new ArrayList<>();

    private final Div messageListContainer;
    private final Badge countBadge;
    private final Div errorDiv;

    private final List<Disposable> subscriptions = new ArrayList<>();

    public ChatView() {
        super();

        addClassName("h-[600px]");
        addClassName("flex");
        addClassName("flex-col");

        HorizontalLayout titleRow = new HorizontalLayout();
        titleRow.addClassName("items-center");
        titleRow.addClassName("gap-2");
        titleRow.add(new CardTitle("Chat"));

        countBadge = new Badge();
        countBadge.addClassName("badge-primary");
        titleRow.add(countBadge);
        add(titleRow);

        messageListContainer = new Div();
        messageListContainer.addClassName("flex-1");
        messageListContainer.addClassName("bg-base-200");
        messageListContainer.addClassName("rounded-box");
        messageListContainer.addClassName("p-4");
        messageListContainer.addClassName("mb-4");
        messageListContainer.addClassName("overflow-y-auto");
        add(messageListContainer);

        HorizontalLayout inputLayout = new HorizontalLayout();
        inputLayout.addClassName("gap-2");
        inputLayout.addClassName("w-full");

        TextField inputField = new TextField("Type a message...");
        inputField.addClassName("flex-1");

        Button sendButton = new Button("Send");
        sendButton.addClassName("btn-primary");

        inputLayout.add(inputField, sendButton);
        add(inputLayout);

        errorDiv = new Div();
        errorDiv.addClassName("text-error");
        errorDiv.addClassName("mt-2");
        add(errorDiv);

        Runnable send = () -> {
            String text = inputField.getValue();
            if (text != null && !text.trim().isEmpty()) {
                inputField.setValue("");
                try {
                    chatService.sendMessage(text);
                    showError("");
                    // No manual refresh: our own MESSAGE_POSTED event updates the list.
                } catch (Exception ex) {
                    showError("Failed to send message: " + ex.getMessage());
                }
            }
        };
        sendButton.addClickListener(e -> send.run());
        inputField.addDomEventListener("keydown",
                (org.teavm.jso.dom.events.KeyboardEvent evt) -> {
                    if ("Enter".equals(evt.getKey())) {
                        send.run();
                    }
                });

        // Subscribe BEFORE fetching history so no message can fall into the gap between
        // the server building the snapshot and this client starting to listen.
        subscriptions.add(ServerEvents.on(ChatEvents.MESSAGE_POSTED, msg -> {
            if (!messages.contains(msg)) {
                messages.add(msg);
                render();
            }
        }));
        subscriptions.add(ServerEvents.on(ChatEvents.HISTORY_CLEARED, ignored -> {
            messages.clear();
            render();
        }));

        render();
        loadHistory();
    }

    private void loadHistory() {
        try {
            List<ChatMessage> history = chatService.getHistory();
            // Merge instead of replace: keep messages that arrived as events while the
            // snapshot was in flight (deduped via ChatMessage value equality).
            for (ChatMessage msg : history) {
                if (!messages.contains(msg)) {
                    messages.add(msg);
                }
            }
            messages.sort((a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));
            showError("");
            render();
        } catch (Exception ex) {
            showError("Failed to load chat history: " + ex.getMessage());
        }
    }

    private void render() {
        countBadge.setText(messages.size() + " messages");

        messageListContainer.getElement().setInnerHTML(""); // Clear
        String currentUser = RmiSecurityContext.getUsername();
        for (ChatMessage msg : messages) {
            Div msgDiv = new Div();
            msgDiv.addClassName("chat");

            boolean isMe = currentUser != null && currentUser.equals(msg.getSender());
            if (isMe) {
                msgDiv.addClassName("chat-end");
            } else {
                msgDiv.addClassName("chat-start");
            }

            Div header = new Div();
            header.addClassName("chat-header");
            header.setText(msg.getSender());

            ChatBubble bubble = new ChatBubble(msg.getText());
            if (isMe) {
                bubble.addClassName("chat-bubble-primary");
            } else {
                bubble.addClassName("chat-bubble-secondary");
            }

            msgDiv.add(header, bubble);
            messageListContainer.add(msgDiv);
        }
    }

    private void showError(String message) {
        errorDiv.setText(message);
    }

    /**
     * Releases all event subscriptions. Call when the view is permanently removed.
     */
    public void dispose() {
        for (Disposable subscription : subscriptions) {
            subscription.dispose();
        }
        subscriptions.clear();
    }
}
