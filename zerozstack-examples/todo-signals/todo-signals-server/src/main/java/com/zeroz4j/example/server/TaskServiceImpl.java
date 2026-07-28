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

import com.zeroz4j.example.api.TaskService;
import com.zeroz4j.example.model.Task;
import com.zeroz4j.example.server.store.DataRoot;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class TaskServiceImpl implements TaskService {
    @Inject private EmbeddedStorageManager storage;

    private DataRoot getRoot() {
        return (DataRoot) storage.root();
    }

    @Override
    public List<Task> getTasks() {
        DataRoot root = getRoot();
        if (root.getTasks().isEmpty()) {
            root.getTasks().add(new Task(1, "Read docs/SIGNALS.md", true));
            root.getTasks().add(new Task(2, "Explore the todo-signals example", false));
            root.getTasks().add(new Task(3, "Build a reactive view of your own", false));
            storage.store(root.getTasks());
        }
        return new ArrayList<>(root.getTasks());
    }
}
