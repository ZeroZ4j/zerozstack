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
package com.zeroz4j.example.client;

import com.zeroz4j.api.Disposable;
import com.zeroz4j.example.api.TaskService;
import com.zeroz4j.example.api.TaskService_Stub;
import com.zeroz4j.example.model.Task;
import com.zeroz4j.ui.component.*;
import com.zeroz4j.ui.layout.*;
import com.zeroz4j.signals.Computed;
import com.zeroz4j.signals.Effect;
import com.zeroz4j.signals.ValueSignal;

import java.util.ArrayList;
import java.util.List;

/**
 * Task board demonstrating zeroz4j's reactive Signals in isolation.
 *
 * <p>All state lives in signals; everything the user sees is derived from them:</p>
 * <ul>
 *   <li>{@code tasks} and {@code filter} are the source {@link ValueSignal}s, changed only
 *       via immutable updates.</li>
 *   <li>{@code visibleTasks} and {@code remainingCount} are {@link Computed} — derived
 *       state that recomputes lazily when its dependencies change.</li>
 *   <li>{@link Effect}s render the list, the summary, and the filter buttons; no code path
 *       ever updates the DOM directly, so the UI can never drift from the state.</li>
 * </ul>
 *
 * <p>The server's only role is providing the initial list over RMI — this example is
 * deliberately network-quiet. For server-to-client events, see the {@code chat-events}
 * example.</p>
 */
public class TodoView extends Card {

    private static final String FILTER_ALL = "all";
    private static final String FILTER_ACTIVE = "active";
    private static final String FILTER_DONE = "done";

    private final TaskService taskService = new TaskService_Stub();

    // Source state
    private final ValueSignal<List<Task>> tasks = new ValueSignal<>(new ArrayList<>());
    private final ValueSignal<String> filter = new ValueSignal<>(FILTER_ALL);
    private final ValueSignal<String> errorMessage = new ValueSignal<>("");

    // Derived state
    private final Computed<List<Task>> visibleTasks = new Computed<>(() -> {
        List<Task> visible = new ArrayList<>();
        for (Task task : tasks.get()) {
            boolean matches = switch (filter.get()) {
                case FILTER_ACTIVE -> !task.isDone();
                case FILTER_DONE -> task.isDone();
                default -> true;
            };
            if (matches) {
                visible.add(task);
            }
        }
        return visible;
    });
    private final Computed<Integer> remainingCount = new Computed<>(() -> {
        int remaining = 0;
        for (Task task : tasks.get()) {
            if (!task.isDone()) {
                remaining++;
            }
        }
        return remaining;
    });

    private final List<Disposable> disposables = new ArrayList<>();
    private long nextId = 1_000; // client-assigned ids; seed ids come from the server

    public TodoView() {
        super();

        addClassName("w-[480px]");
        addClassName("flex");
        addClassName("flex-col");

        HorizontalLayout titleRow = new HorizontalLayout();
        titleRow.addClassName("items-center");
        titleRow.addClassName("gap-2");
        titleRow.add(new CardTitle("Tasks"));

        Badge remainingBadge = new Badge();
        remainingBadge.addClassName("badge-primary");
        titleRow.add(remainingBadge);
        add(titleRow);

        // Add-task input row
        HorizontalLayout inputLayout = new HorizontalLayout();
        inputLayout.addClassName("gap-2");
        inputLayout.addClassName("w-full");
        inputLayout.addClassName("mb-2");

        TextField inputField = new TextField("What needs doing?");
        inputField.addClassName("flex-1");

        Button addButton = new Button("Add");
        addButton.addClassName("btn-primary");
        addButton.addClickListener(e -> {
            String title = inputField.getValue();
            if (title != null && !title.trim().isEmpty()) {
                inputField.setValue("");
                Task task = new Task(nextId++, title.trim(), false);
                tasks.update(current -> {
                    List<Task> next = new ArrayList<>(current);
                    next.add(task);
                    return next;
                });
            }
        });

        inputLayout.add(inputField, addButton);
        add(inputLayout);

        // Filter buttons
        HorizontalLayout filterRow = new HorizontalLayout();
        filterRow.addClassName("gap-1");
        filterRow.addClassName("mb-2");
        addFilterButton(filterRow, "All", FILTER_ALL);
        addFilterButton(filterRow, "Active", FILTER_ACTIVE);
        addFilterButton(filterRow, "Done", FILTER_DONE);
        add(filterRow);

        // Task list container
        Div taskListContainer = new Div();
        taskListContainer.addClassName("bg-base-200");
        taskListContainer.addClassName("rounded-box");
        taskListContainer.addClassName("p-2");
        add(taskListContainer);

        Div errorDiv = new Div();
        errorDiv.addClassName("text-error");
        errorDiv.addClassName("mt-2");
        add(errorDiv);

        // Effects: the ONLY place the UI is written from state.
        disposables.add(Effect.create(() ->
                remainingBadge.setText(remainingCount.get() + " of " + tasks.get().size() + " open")));

        disposables.add(Effect.create(() -> errorDiv.setText(errorMessage.get())));

        disposables.add(Effect.create(() -> renderTasks(taskListContainer)));

        loadTasks();
    }

    private void addFilterButton(HorizontalLayout row, String label, String filterValue) {
        Button button = new Button(label);
        button.addClassName("btn-sm");
        button.addClickListener(e -> filter.set(filterValue));
        // Each button highlights itself reactively based on the filter signal.
        disposables.add(Effect.create(() -> {
            if (filterValue.equals(filter.get())) {
                button.addClassName("btn-active");
            } else {
                button.removeClassName("btn-active");
            }
        }));
        row.add(button);
    }

    private void renderTasks(Div container) {
        container.getElement().setInnerHTML(""); // Clear
        for (Task task : visibleTasks.get()) {
            HorizontalLayout rowLayout = new HorizontalLayout();
            rowLayout.addClassName("items-center");
            rowLayout.addClassName("gap-2");
            rowLayout.addClassName("p-1");

            Checkbox checkbox = new Checkbox();
            checkbox.setValue(task.isDone());
            checkbox.addValueChangeListener(event -> toggleTask(task.getId()));

            Span titleSpan = new Span(task.getTitle());
            titleSpan.addClassName("flex-1");
            if (task.isDone()) {
                titleSpan.addClassName("line-through");
                titleSpan.addClassName("opacity-50");
            }

            Button deleteButton = new Button("✕");
            deleteButton.addClassName("btn-ghost");
            deleteButton.addClassName("btn-xs");
            deleteButton.addClickListener(e -> removeTask(task.getId()));

            rowLayout.add(checkbox, titleSpan, deleteButton);
            container.add(rowLayout);
        }
    }

    private void toggleTask(long id) {
        tasks.update(current -> {
            List<Task> next = new ArrayList<>();
            for (Task task : current) {
                next.add(task.getId() == id
                        ? new Task(task.getId(), task.getTitle(), !task.isDone())
                        : task);
            }
            return next;
        });
    }

    private void removeTask(long id) {
        tasks.update(current -> {
            List<Task> next = new ArrayList<>();
            for (Task task : current) {
                if (task.getId() != id) {
                    next.add(task);
                }
            }
            return next;
        });
    }

    private void loadTasks() {
        try {
            tasks.set(new ArrayList<>(taskService.getTasks()));
            errorMessage.set("");
        } catch (Exception ex) {
            errorMessage.set("Failed to load tasks: " + ex.getMessage());
        }
    }

    /**
     * Releases all effects and computed subscriptions. Call when the view is permanently removed.
     */
    public void dispose() {
        for (Disposable disposable : disposables) {
            disposable.dispose();
        }
        disposables.clear();
        visibleTasks.dispose();
        remainingCount.dispose();
    }
}
