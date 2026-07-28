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

import com.zeroz4j.example.api.RegistrationService;
import com.zeroz4j.example.model.Registration;
import com.zeroz4j.example.server.store.DataRoot;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import com.zeroz4j.db.net.ZeroZDbNode;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class RegistrationServiceImpl implements RegistrationService {

    /**
     * The database node. Writes go inside a write-block so they commit atomically.
     *
     * <p>This example uses {@code db.localDb()}, the in-process engine, which is present because
     * the example runs {@code zeroz4j.store.mode=EMBEDDED}. That keeps the persistence code short
     * so it does not obscure what this example is really about. Code that must also run against a
     * database server sends {@code DbCommand} objects instead - see the inventory-crud example.</p>
     */
    @Inject
    private ZeroZDbNode db;

    private DataRoot getRoot() {
        return (DataRoot) db.localDb().root();
    }

    @Override
    public synchronized void register(Registration r) {
        if (r == null) {
            throw new IllegalArgumentException("Registration must not be null");
        }
        DataRoot root = getRoot();
        for (Registration existing : root.getRegistrations()) {
            if (existing.getEmail() != null && existing.getEmail().equalsIgnoreCase(r.getEmail())) {
                throw new IllegalArgumentException("Email address already registered: " + r.getEmail());
            }
        }
        db.localDb().write(ctx -> {
            ctx.edit(root.getRegistrations());
            root.getRegistrations().add(r);
        });
    }

    @Override
    public synchronized List<Registration> listRegistrations() {
        DataRoot root = getRoot();
        return new ArrayList<>(root.getRegistrations());
    }
}
