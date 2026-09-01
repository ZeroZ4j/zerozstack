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

import com.zeroz4j.example.model.ChatMessage;
import com.zeroz4j.example.model.ChatTopic;
import com.zeroz4j.example.model.LiveChatState;
import com.zeroz4j.api.RmiService;
import com.zeroz4j.api.Secured;
import com.zeroz4j.api.RolesAllowed;
import java.util.List;

@RmiService
@Secured
public interface ChatService {
    LiveChatState getState();

    /**
     * The room's topic, which every browser may edit directly.
     *
     * <p>There is deliberately no {@code setTopic} beside it. The returned object is
     * {@code @ClientWritable}, so a browser changes the topic by calling {@code setText(...)} on
     * the copy it got from here and the framework carries the change up. State edits sync;
     * operations - {@code sendMessage}, {@code clearHistory} - call.</p>
     *
     * @return the shared topic object
     */
    ChatTopic getTopic();

    void sendMessage(String text);

    @RolesAllowed("admin")
    void clearHistory();
}

