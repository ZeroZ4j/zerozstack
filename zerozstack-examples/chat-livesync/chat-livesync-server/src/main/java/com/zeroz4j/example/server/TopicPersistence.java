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

import com.zeroz4j.db.net.ZeroZDbNode;
import com.zeroz4j.example.model.ChatTopic;
import com.zeroz4j.server.LiveMutationListener;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.security.Principal;
import java.util.logging.Logger;

/**
 * Writes an edited topic to disk, and says who changed it to what.
 *
 * <p>The framework does not know an application's storage root, so a client edit is applied to the
 * server's object in memory and then handed here. This is where an application persists and
 * audits it - and, in this example, where the log line proving a keystroke arrived comes from.</p>
 */
@ApplicationScoped
public class TopicPersistence implements LiveMutationListener {

    private static final Logger LOG = Logger.getLogger(TopicPersistence.class.getName());

    @Inject private ZeroZDbNode db;

    @Override
    public void onMutated(Object model, Principal principal) {
        if (!(model instanceof ChatTopic)) {
            return;
        }
        ChatTopic topic = (ChatTopic) model;
        // A write-block, so the change commits atomically and is on disk when this returns.
        db.localDb().write(ctx -> ctx.store(topic));
        LOG.info("[chat-livesync] Topic set to \"" + topic.getText() + "\" by "
                + (principal != null ? principal.getName() : "an anonymous connection"));
    }
}
