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
package com.zeroz4j.example.routing.api;

import com.zeroz4j.api.RmiService;
import com.zeroz4j.example.routing.model.Account;
import com.zeroz4j.example.routing.model.Project;
import com.zeroz4j.example.routing.model.Task;

import java.util.List;

/**
 * Everything the routing tour's views load.
 *
 * <p>Each method exists because exactly one route needs it — which is what "the route declares its
 * data" looks like once it reaches the server.</p>
 */
@RmiService
public interface TourService {

    /** Loaded by the shell layout, once per navigation, for every view underneath. */
    Account getAccount();

    /** Loaded by {@code /projects}. */
    List<Project> listProjects();

    /** Loaded by {@code /projects/:id}. */
    Project getProject(long id);

    /** Loaded by {@code /projects/:id} alongside the project itself. */
    List<Task> listTasks(long projectId);

    /** Loaded by {@code /projects/:projectId/tasks/:taskId}. */
    Task getTask(long taskId);

    /** Called by {@code /projects/new}; returns the id to navigate to. */
    long createProject(String name);
}
