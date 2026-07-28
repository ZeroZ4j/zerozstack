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
package com.zeroz4j.example.server;

import com.zeroz4j.example.api.ChatService;
import com.zeroz4j.example.api.ChatEvents;
import com.zeroz4j.example.model.ChatMessage;
import com.zeroz4j.example.server.store.DataRoot;
import com.zeroz4j.server.RmiRequestContext;
import com.zeroz4j.server.EventPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class ChatServiceImpl implements ChatService {
    @Inject private EmbeddedStorageManager storage;
    @Inject private EventPublisher events;

    private DataRoot getRoot() {
        return (DataRoot) storage.root();
    }

    @Override
    public List<ChatMessage> getHistory() {
        return new ArrayList<>(getRoot().getMessages());
    }

    @Override
    public void sendMessage(String text) {
        String sender = RmiRequestContext.getPrincipal() != null ? RmiRequestContext.getPrincipal().getName() : "Anonymous";
        ChatMessage msg = new ChatMessage(sender, text, System.currentTimeMillis());
        getRoot().getMessages().add(msg);
        storage.store(getRoot().getMessages());
        events.publish(ChatEvents.MESSAGE_POSTED, msg);
    }

    @Override
    public void clearHistory() {
        getRoot().getMessages().clear();
        storage.store(getRoot().getMessages());
        events.publish(ChatEvents.HISTORY_CLEARED);
    }
}

