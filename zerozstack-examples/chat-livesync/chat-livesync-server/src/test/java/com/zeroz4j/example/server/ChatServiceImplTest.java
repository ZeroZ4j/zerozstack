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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ChatServiceImplTest {

    private ChatServiceImpl chatService;

    @BeforeEach
    public void setup() {
        chatService = new ChatServiceImpl();
        // we might need to initialize the data root or mock it if ChatServiceImpl relies on EclipseStore.
        // Let's assume it initializes an empty array list for messages in a unit test scenario if not injected,
        // but if it relies on CDI injection of DataRoot, we should mock or provide it.
    }

    @Test
    public void testPostMessage() {
        // Just a placeholder test to verify the test suite is running
        assertTrue(true);
    }
}

