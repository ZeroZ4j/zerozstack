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
package com.zeroz4j.example.client.showcase;

import com.zeroz4j.ui.component.*;
import com.zeroz4j.ui.layout.*;

public class ChatBubbleShowcase extends ComponentShowcase {

    public ChatBubbleShowcase() {
        addTitle("Chat Bubble");
        addDescription("Chat Bubbles are used to show conversational messages between multiple participants.");

        Div container = new Div();
        container.addClassName("w-full flex flex-col gap-4");

        Div chatStart = new Div();
        chatStart.addClassName("chat chat-start");
        
        Div chatHeaderStart = new Div("User A");
        chatHeaderStart.addClassName("chat-header mb-1 text-xs opacity-50");
        
        ChatBubble bubbleStart = new ChatBubble("Hello! How are you building the frontend in Java?");
        bubbleStart.addClassName("chat-bubble-primary");
        
        chatStart.add(chatHeaderStart, bubbleStart);

        Div chatEnd = new Div();
        chatEnd.addClassName("chat chat-end");
        
        Div chatHeaderEnd = new Div("User B");
        chatHeaderEnd.addClassName("chat-header mb-1 text-xs opacity-50");
        
        ChatBubble bubbleEnd = new ChatBubble("Hey! I am using zeroz4j UI components. It's super fast and easy to compile using TeaVM!");
        bubbleEnd.addClassName("chat-bubble-secondary");
        
        chatEnd.add(chatHeaderEnd, bubbleEnd);

        container.add(chatStart, chatEnd);

        addSection("Chat Bubble Conversation", container);
    }
}
