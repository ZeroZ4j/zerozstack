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
import com.zeroz4j.example.model.ChatMessage;
import com.zeroz4j.example.model.LiveChatState;
import com.zeroz4j.example.server.store.DataRoot;
import com.zeroz4j.server.RmiRequestContext;
import com.zeroz4j.server.SyncEngine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import com.zeroz4j.db.net.ZeroZDbNode;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class ChatServiceImpl implements ChatService {
        /**
     * The database node. Writes go inside a write-block so they commit atomically.
     *
     * <p>This example uses {@code db.localDb()}, the in-process engine, which is present because
     * the example runs {@code zeroz4j.store.mode=EMBEDDED}. That keeps the persistence code short
     * so it does not obscure what this example is really about. Code that must also run against a
     * database server sends {@code DbCommand} objects instead - see the inventory-crud example.</p>
     */
    @Inject private ZeroZDbNode db;
    @Inject private SyncEngine syncEngine;

    private DataRoot getRoot() {
        return (DataRoot) db.localDb().root();
    }

    @Override
    public LiveChatState getState() {
        return getRoot().getChatState();
    }

    @Override
    public void sendMessage(String text) {
        String sender = RmiRequestContext.getPrincipal() != null ? RmiRequestContext.getPrincipal().getName() : "Anonymous";
        ChatMessage msg = new ChatMessage(sender, text, System.currentTimeMillis());
        LiveChatState state = getRoot().getChatState();
        db.localDb().write(ctx -> {
            ctx.edit(state.getMessages());
            state.getMessages().add(msg);
        });
        syncEngine.notifyChanged(state);
    }

    @Override
    public void clearHistory() {
        LiveChatState state = getRoot().getChatState();
        db.localDb().write(ctx -> {
            ctx.edit(state.getMessages());
            state.getMessages().clear();
        });
        syncEngine.notifyChanged(state);
    }
}

