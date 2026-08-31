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

import com.zeroz4j.example.api.ChatService;
import com.zeroz4j.example.api.ChatService_Stub;
import com.zeroz4j.example.model.ChatMessage;
import com.zeroz4j.example.model.ChatTopic;
import com.zeroz4j.example.model.LiveChatState;
import com.zeroz4j.ui.component.*;
import com.zeroz4j.ui.layout.*;
import com.zeroz4j.ui.theme.TextStyle;
import com.zeroz4j.signals.ValueSignal;
import com.zeroz4j.signals.Effect;

import java.util.ArrayList;
import java.util.List;
import com.zeroz4j.api.RmiSecurityContext;

public class ChatView extends Card {

    private final ChatService chatService;
    private final ValueSignal<List<ChatMessage>> messagesSignal = new ValueSignal<>(new ArrayList<>());
    private final ValueSignal<String> errorMessageSignal = new ValueSignal<>("");
    private LiveChatState liveState;
    private ChatTopic liveTopic;

    /** Every Effect created by this view, released together in {@link #dispose()}. */
    private final List<com.zeroz4j.api.Disposable> disposables = new ArrayList<>();

    private final Div messageListContainer;
    private final TextField topicField;
    private final Span topicDisplay;

    public ChatView() {
        super();
        chatService = new ChatService_Stub();

        addClassName("h-[600px]");
        addClassName("flex");
        addClassName("flex-col");

        add(new CardTitle("LiveSync Chat"));

        // The up direction. Typing here calls a setter on this browser's own copy of the topic,
        // and that is the whole write path - the framework carries it to the server, which checks
        // it, applies it and tells every other browser. Nothing here calls a service.
        topicField = new TextField().withLabel("Topic");
        topicField.setId("topic-input");
        topicField.setHelperText("Anyone can change this. Every other window follows along.");
        topicField.addClassName("mb-2");
        add(topicField);

        topicDisplay = new Span("");
        TextStyle.SECONDARY.applyTo(topicDisplay);
        topicDisplay.setId("topic-display");
        topicDisplay.addClassName("mb-4");
        add(topicDisplay);

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

        // Error message display
        Div errorDiv = new Div();
        errorDiv.addClassName("text-error");
        errorDiv.addClassName("mt-2");
        disposables.add(Effect.create(() -> {
            errorDiv.getElement().setInnerHTML(errorMessageSignal.get());
        }));
        add(errorDiv);

        Runnable send = () -> {
            String text = inputField.getValue();
            if (text != null && !text.trim().isEmpty()) {
                inputField.setValue("");
                try {
                    chatService.sendMessage(text);
                } catch (Exception ex) {
                    System.err.println("[zeroz4j] Chat error: " + ex.getMessage());
                    errorMessageSignal.set("Failed to send message: " + ex.getMessage());
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

        // Re-render messages when signal updates
        disposables.add(Effect.create(() -> {
            // removeAll, not setInnerHTML(""): every message row leaves the page properly.
            messageListContainer.removeAll();
            String currentUser = RmiSecurityContext.getUsername();
            for (ChatMessage msg : messagesSignal.get()) {
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
        }));

        initLiveSync();
    }

    private void initLiveSync() {
        try {
            // One RMI call establishes the object handle. From here the server's
            // syncEngine.notifyChanged(state) reaches this instance.
            liveState = chatService.getState();
            errorMessageSignal.set("");

            // A @LiveSync object is a reactive dependency: reading a getter inside an Effect
            // subscribes to it, so an inbound sync re-runs this automatically. No polling.
            disposables.add(Effect.create(() ->
                    messagesSignal.set(new ArrayList<>(liveState.getMessages()))));

            // The same object travels both ways. Reading its getter inside an Effect follows the
            // server's changes down; the listener below sends this window's edits up.
            liveTopic = chatService.getTopic();
            // Whether this person has the topic box open in front of them. What comes back from
            // the server is what the server had a moment ago, and writing that into a box somebody
            // is typing in deletes whatever they have typed since - one character, or a whole word.
            // So the box follows the server only while nobody is typing in it. The line of text
            // under it always follows, so the newest value is on the screen either way.
            boolean[] beingTypedIn = {false};
            topicField.addDomEventListener("focus", evt -> beingTypedIn[0] = true);
            topicField.addDomEventListener("blur", evt -> beingTypedIn[0] = false);
            disposables.add(Effect.create(() -> {
                String current = liveTopic.getText() == null ? "" : liveTopic.getText();
                topicDisplay.setText("Topic: " + (current.isEmpty() ? "(none yet)" : current));
                if (!beingTypedIn[0] && !current.equals(topicField.getValue())) {
                    topicField.setValue(current);
                }
            }));
            topicField.addValueChangeListener(event -> {
                String typed = event.getValue() == null ? "" : event.getValue();
                if (!typed.equals(liveTopic.getText())) {
                    liveTopic.setText(typed);   // the entire client write path
                }
            });

        } catch (Exception ex) {
            System.err.println("[zeroz4j] Chat error: " + ex.getMessage());
            errorMessageSignal.set("Failed to initialize LiveSync: " + ex.getMessage());
        }
    }

    /** Releases every Effect this view created. Call when the view is permanently removed. */
    public void dispose() {
        disposables.forEach(com.zeroz4j.api.Disposable::dispose);
        disposables.clear();
    }
}

