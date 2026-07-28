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

import com.zeroz4j.example.api.UserService;
import com.zeroz4j.example.model.UserInfo;
import com.zeroz4j.server.WasmRmiServerEngine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Example implementation of the UserService CDI bean running on Jakarta EE backend.
 */
@ApplicationScoped
public class UserServiceImpl implements UserService {

    private static final Logger LOG = Logger.getLogger(UserServiceImpl.class.getName());

    @Inject
    private WasmRmiServerEngine rmiEngine;

    private final Map<String, UserInfo> userDb = new ConcurrentHashMap<>();

    public UserServiceImpl() {
        userDb.put("franz", new UserInfo("franz", 800, true));
        userDb.put("antigravity", new UserInfo("antigravity", 9999, true));
    }

    @Override
    public UserInfo getUserInfo(String username) {
        LOG.info("[SERVER] getUserInfo called for: " + username);
        return userDb.getOrDefault(username, new UserInfo(username, 0, false));
    }

    @Override
    public void updateScore(String username, int newScore) {
        LOG.info("[SERVER] updateScore called for: " + username + " with score: " + newScore);
        UserInfo user = userDb.computeIfAbsent(username, name -> new UserInfo(name, 0, true));
        user.setScore(newScore);
        rmiEngine.broadcastPush("user-notifications", user);
        rmiEngine.broadcastPush("global-announcements",
            "User " + username + " reached a score of " + newScore + "!");
    }

    @Override
    public String systemDiagnostics(String category, int scanDepth) {
        LOG.info("[SERVER] systemDiagnostics called for: " + category + ", depth: " + scanDepth);
        return """
            --- zeroz4j System Diagnostics ---
            Component: %s
            Status: HEALTHY
            Scan Depth Checked: %d
            Active CDI Beans resolved: 12
            Virtual Thread Allocation: SUCCESS
            """.formatted(category, scanDepth);
    }
}

