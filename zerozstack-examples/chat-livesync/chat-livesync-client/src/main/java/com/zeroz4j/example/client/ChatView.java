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

import com.zeroz4j.api.i18n.Message;
import com.zeroz4j.example.api.AppText_Text;
import com.zeroz4j.example.api.ChatService;
import com.zeroz4j.example.api.ChatService_Stub;
import com.zeroz4j.api.LiveMutationRefusals;
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
    /**
     * What went wrong, held as a message rather than as words.
     *
     * <p>A message carries no language of its own, so it can be put here at the moment the call
     * failed and turned into words later, in the effect that draws it. Words put here instead
     * would be the words of whatever language was on screen when the failure happened, and they
     * would still be those words after somebody switched.</p>
     */
    private final ValueSignal<Message> errorMessageSignal = new ValueSignal<>(null);

    /**
     * The reason the server gave for putting a topic edit back, or empty when nothing was refused.
     *
     * <p>Kept apart from {@link #errorMessageSignal}, which reports a call that failed. A refusal is
     * a different thing and reads differently: nothing broke, the server simply would not have the
     * value, and it has already sent the real one back.</p>
     */
    private final ValueSignal<String> refusalSignal = new ValueSignal<>("");
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

        CardTitle title = new CardTitle("");
        title.setId("chat-title");
        disposables.add(Effect.create(() -> title.setText(AppText_Text.chatTitle().text())));
        add(title);

        // The up direction. Typing here calls a setter on this browser's own copy of the topic,
        // and that is the whole write path - the framework carries it to the server, which checks
        // it, applies it and tells every other browser. Nothing here calls a service.
        topicField = new TextField();
        topicField.setId("topic-input");
        topicField.addClassName("mb-2");
        // The caption and the explanation are read inside an effect, so switching language
        // rewrites them and leaves whatever is typed in the box exactly where it is.
        disposables.add(Effect.create(() -> {
            topicField.setLabel(AppText_Text.chatTopicLabel().text());
            topicField.setHelperText(AppText_Text.chatTopicHelp().text());
        }));
        add(topicField);

        // Where a refused edit is told to the person. An edit is put on the screen the moment it is
        // typed and sent afterwards, so when the server will not have it the person is looking at a
        // value that does not exist anywhere else. This is the line that says so.
        Div refusalNotice = new Div();
        refusalNotice.setId("topic-refusal");
        refusalNotice.addClassName("mb-4");
        add(refusalNotice);
        disposables.add(Effect.create(() -> {
            String reason = refusalSignal.get();
            refusalNotice.removeAll();
            if (reason != null && !reason.isEmpty()) {
                refusalNotice.add(Alert.caution(AppText_Text.chatTopicRefused(reason).text()));
            }
        }));

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

        TextField inputField = new TextField();
        inputField.setId("message-input");
        inputField.addClassName("flex-1");

        Button sendButton = new Button();
        sendButton.setId("send-button");
        sendButton.addClassName("btn-primary");

        disposables.add(Effect.create(() -> {
            inputField.setPlaceholder(AppText_Text.chatMessagePlaceholder().text());
            sendButton.setText(AppText_Text.chatSend().text());
        }));
        
        inputLayout.add(inputField, sendButton);
        add(inputLayout);

        // Error message display
        Div errorDiv = new Div();
        errorDiv.addClassName("text-error");
        errorDiv.addClassName("mt-2");
        disposables.add(Effect.create(() -> {
            Message problem = errorMessageSignal.get();
            errorDiv.setText(problem == null ? "" : problem.text());
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
                    errorMessageSignal.set(AppText_Text.chatSendFailed(ex.getMessage()));
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
            errorMessageSignal.set(null);

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
                // Two reads in one effect: the topic, which the server changes, and the words
                // around it, which the language changes. Both are dependencies of this one line.
                topicDisplay.setText(AppText_Text.chatTopicIs(current.isEmpty()
                        ? AppText_Text.chatTopicNone().text() : current).text());
                if (!beingTypedIn[0] && !current.equals(topicField.getValue())) {
                    topicField.setValue(current);
                }
            }));
            topicField.addValueChangeListener(event -> {
                String typed = event.getValue() == null ? "" : event.getValue();
                if (!typed.equals(liveTopic.getText())) {
                    refusalSignal.set("");      // a new attempt clears the last refusal
                    liveTopic.setText(typed);   // the entire client write path
                }
            });

            // An edit that never reached the server is reported here, and with nothing listening it
            // goes to the browser console instead - which nobody reads. Two things can bring it: the
            // server refused the value, or the browser could not put it on the wire. Either way the
            // server's own value has already been sent back, so the screen is right by the time this
            // runs; what is left is telling the person.
            //
            // A refusal is easy to cause on purpose here: the topic is capped at 80 characters by an
            // annotation on the model, and the box does not stop you typing more. Type a long
            // sentence into it and this fires.
            disposables.add(LiveMutationRefusals.onRefused((model, reason) -> {
                refusalSignal.set(reason);
                // The topic box normally leaves itself alone while somebody is typing in it, so
                // that an incoming value does not delete what they have written. A refusal is the
                // one case where it must not: what is in the box is a value the server does not
                // have, and leaving it there would be leaving a lie on the screen.
                String serverValue = liveTopic.getText() == null ? "" : liveTopic.getText();
                topicField.setValue(serverValue);
            }));

        } catch (Exception ex) {
            System.err.println("[zeroz4j] Chat error: " + ex.getMessage());
            errorMessageSignal.set(AppText_Text.chatSendFailed(ex.getMessage()));
        }
    }

    /** Releases every Effect this view created. Call when the view is permanently removed. */
    public void dispose() {
        disposables.forEach(com.zeroz4j.api.Disposable::dispose);
        disposables.clear();
    }
}

