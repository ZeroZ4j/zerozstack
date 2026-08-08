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
package com.zeroz4j.example.routing.server;

import com.zeroz4j.example.routing.api.TourService;
import com.zeroz4j.example.routing.model.Account;
import com.zeroz4j.example.routing.model.Project;
import com.zeroz4j.example.routing.model.Task;
import com.zeroz4j.server.RmiRequestContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory data for the tour. Deliberately not persisted: the example is about routing, and a
 * store would only add noise.
 */
@ApplicationScoped
public class TourServiceImpl implements TourService {

    private final List<Project> projects = new ArrayList<>();
    private final List<Task> tasks = new ArrayList<>();
    private final AtomicLong nextProjectId = new AtomicLong(3);
    private final AtomicLong nextTaskId = new AtomicLong(100);

    public TourServiceImpl() {
        projects.add(new Project(1, "Orbital relay", "Ground station scheduling and telemetry.", 2));
        projects.add(new Project(2, "Alpine survey", "Terrain capture for the northern ridge.", 1));

        tasks.add(new Task(11, 1, "Calibrate the dish",
                "Azimuth drift of 0.3 degrees since the last pass.", false));
        tasks.add(new Task(12, 1, "Schedule the November window",
                "Three candidate passes; pick one and notify the crew.", false));
        tasks.add(new Task(13, 1, "Archive October telemetry",
                "Rolled up and moved to cold storage.", true));
        tasks.add(new Task(21, 2, "Fly the north ridge",
                "Weather window opens Tuesday.", false));
    }

    @Override
    public Account getAccount() {
        String name = RmiRequestContext.getPrincipal() != null
                ? RmiRequestContext.getPrincipal().getName() : "anonymous";
        boolean admin = RmiRequestContext.getRoles().contains("admin");
        return new Account(name, admin ? "admin" : "standard");
    }

    @Override
    public List<Project> listProjects() {
        return new ArrayList<>(projects);
    }

    @Override
    public Project getProject(long id) {
        for (Project project : projects) {
            if (project.getId() == id) {
                return project;
            }
        }
        // Thrown rather than returning null: the route's loader is what fails, so the router
        // reports it through its error handler instead of rendering a view around nothing.
        throw new IllegalArgumentException("No project with id " + id);
    }

    @Override
    public List<Task> listTasks(long projectId) {
        List<Task> forProject = new ArrayList<>();
        for (Task task : tasks) {
            if (task.getProjectId() == projectId) {
                forProject.add(task);
            }
        }
        return forProject;
    }

    @Override
    public Task getTask(long taskId) {
        for (Task task : tasks) {
            if (task.getId() == taskId) {
                return task;
            }
        }
        throw new IllegalArgumentException("No task with id " + taskId);
    }

    @Override
    public long createProject(String name) {
        long id = nextProjectId.incrementAndGet();
        projects.add(new Project(id, name == null || name.isBlank() ? "Untitled" : name,
                "Created from /projects/new.", 0));
        tasks.add(new Task(nextTaskId.incrementAndGet(), id, "First task",
                "Every new project starts with one.", false));
        return id;
    }
}
