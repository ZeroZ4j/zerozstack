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
package com.zeroz4j.example.model;

import com.zeroz4j.api.ClientWritable;
import com.zeroz4j.api.DataModel;
import com.zeroz4j.api.LiveSync;
import com.zeroz4j.api.validation.Size;

/**
 * What the room is talking about - the example's demonstration of the <b>up</b> direction.
 *
 * <p>Everything else in this example travels down: the server changes the message list and every
 * browser is told. This one travels both ways. Anybody typing in the topic box calls
 * {@code setText(...)} on their own copy, and that is the whole write path - no service method, no
 * save button. The server checks it, applies it to its own instance and tells every other browser.</p>
 *
 * <p>It is a model of its own rather than a field on {@link LiveChatState} because
 * {@code @ClientWritable} is granted per model and a client edit replaces the whole object. Putting
 * it on the chat state would have let any browser rewrite the message history as well.</p>
 */
@DataModel
@LiveSync
@ClientWritable
public class ChatTopic {

    @Size(max = 80)
    private String text = "";

    /** @return the current topic */
    public String getText() {
        return text;
    }

    /**
     * Sets the topic. On a browser copy this is the entire client write path.
     *
     * @param text the new topic
     */
    public void setText(String text) {
        this.text = text;
    }
}
