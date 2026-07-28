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
import com.zeroz4j.example.api.JobService;
import com.zeroz4j.example.api.JobService_Stub;
import com.zeroz4j.example.api.JobSignals;
import com.zeroz4j.example.model.JobStatus;
import com.zeroz4j.signals.Effect;
import com.zeroz4j.ui.component.*;
import com.zeroz4j.ui.layout.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Reactive job monitor view driven entirely by the shared signal {@link JobSignals#STATUS}.
 * Uses {@link Effect} to bind state changes directly to UI components.
 */
public class JobMonitorView extends Card {

    private static final String[] PIPELINE_PHASES = {
            "Queued", "Building", "Testing", "Deploying", "Done"
    };

    private final JobService jobService = new JobService_Stub();
    private final List<Disposable> disposables = new ArrayList<>();

    private final Badge statusBadge;
    private final StatusDot statusDot;
    private final Span jobIdSpan;
    private final Span messageSpan;
    private final Span percentText;
    private final Progress progressBar;
    private final Steps steps;
    private final Button startButton;

    public JobMonitorView() {
        super();

        addClassName("max-w-4xl");
        addClassName("mx-auto");
        addClassName("p-6");

        // Header Row: Title + Status Dot + Badge
        HorizontalLayout headerRow = new HorizontalLayout();
        headerRow.addClassName("items-center");
        headerRow.addClassName("justify-between");
        headerRow.addClassName("mb-6");

        HorizontalLayout titleGroup = new HorizontalLayout();
        titleGroup.addClassName("items-center");
        titleGroup.addClassName("gap-3");

        CardTitle title = new CardTitle("Deployment Pipeline Monitor");
        statusDot = new StatusDot("PENDING");
        statusBadge = new Badge("Queued");

        titleGroup.add(title, statusDot, statusBadge);

        jobIdSpan = new Span();
        jobIdSpan.addClassName("text-sm");
        jobIdSpan.addClassName("text-base-content/60");

        headerRow.add(titleGroup, jobIdSpan);
        add(headerRow);

        // Progress Section: Percentage + Progress Bar
        VerticalLayout progressSection = new VerticalLayout();
        progressSection.addClassName("mb-8");

        HorizontalLayout progressHeader = new HorizontalLayout();
        progressHeader.addClassName("justify-between");
        progressHeader.addClassName("w-full");
        progressHeader.addClassName("mb-2");

        Span progressLabel = new Span("Pipeline Progress");
        progressLabel.addClassName("font-semibold");

        percentText = new Span("0%");
        percentText.addClassName("font-mono");
        percentText.addClassName("font-bold");

        progressHeader.add(progressLabel, percentText);

        progressBar = new Progress();
        progressBar.addClassName("w-full");
        progressBar.addClassName("h-4");

        progressSection.add(progressHeader, progressBar);
        add(progressSection);

        // Pipeline Steps Display
        steps = new Steps();
        steps.addClassName("w-full");
        steps.addClassName("mb-8");
        add(steps);

        // Message & Action Footer
        HorizontalLayout footerRow = new HorizontalLayout();
        footerRow.addClassName("items-center");
        footerRow.addClassName("justify-between");
        footerRow.addClassName("pt-4");
        footerRow.addClassName("border-t");
        footerRow.addClassName("border-base-300");

        messageSpan = new Span();
        messageSpan.addClassName("text-sm");
        messageSpan.addClassName("font-medium");

        startButton = new Button("Start job");
        startButton.addClassName("btn-primary");

        startButton.addClickListener(e -> {
            try {
                jobService.startJob();
            } catch (Exception ex) {
                // Handled server-side
            }
        });

        footerRow.add(messageSpan, startButton);
        add(footerRow);

        // Reactive Binding: Disable Start button while job is running
        disposables.add(Effect.create(() -> {
            JobStatus status = JobSignals.STATUS.get();
            boolean isRunning = status != null && status.isRunning();
            startButton.setEnabled(!isRunning);
        }));

        // Reactive Binding: Progress Bar & Percentage
        disposables.add(Effect.create(() -> {
            JobStatus status = JobSignals.STATUS.get();
            if (status == null) {
                return;
            }
            int pct = status.getPercent();
            percentText.setText(pct + "%");
            progressBar.getElement().setAttribute("value", String.valueOf(pct));
            progressBar.getElement().setAttribute("max", "100");

            progressBar.removeClassName("progress-primary");
            progressBar.removeClassName("progress-success");
            progressBar.removeClassName("progress-error");

            if ("Done".equals(status.getPhase())) {
                progressBar.addClassName("progress-success");
            } else if ("Failed".equals(status.getPhase())) {
                progressBar.addClassName("progress-error");
            } else {
                progressBar.addClassName("progress-primary");
            }
        }));

        // Reactive Binding: StatusDot & Badge & Header Info
        disposables.add(Effect.create(() -> {
            JobStatus status = JobSignals.STATUS.get();
            if (status == null) {
                return;
            }
            String phase = status.getPhase();
            boolean isRunning = status.isRunning();

            if (status.getJobId() > 0) {
                jobIdSpan.setText("Job #" + status.getJobId());
            } else {
                jobIdSpan.setText("No Active Job");
            }

            messageSpan.setText(status.getMessage() != null ? status.getMessage() : "");

            statusDot.setState(isRunning ? "RUNNING" : ("Done".equals(phase) ? "COMPLETED" : ("Failed".equals(phase) ? "KILLED" : "PENDING")));

            statusBadge.setText(phase);
            statusBadge.removeClassName("badge-primary");
            statusBadge.removeClassName("badge-success");
            statusBadge.removeClassName("badge-error");
            statusBadge.removeClassName("badge-neutral");

            if ("Done".equals(phase)) {
                statusBadge.addClassName("badge-success");
            } else if ("Failed".equals(phase)) {
                statusBadge.addClassName("badge-error");
            } else if (isRunning) {
                statusBadge.addClassName("badge-primary");
            } else {
                statusBadge.addClassName("badge-neutral");
            }
        }));

        // Reactive Binding: Pipeline Steps
        disposables.add(Effect.create(() -> {
            JobStatus status = JobSignals.STATUS.get();
            if (status == null) {
                return;
            }
            String currentPhase = status.getPhase();
            boolean isFailed = "Failed".equals(currentPhase);

            steps.getElement().setInnerHTML("");

            int activeIndex = -1;
            for (int i = 0; i < PIPELINE_PHASES.length; i++) {
                if (PIPELINE_PHASES[i].equalsIgnoreCase(currentPhase)) {
                    activeIndex = i;
                    break;
                }
            }

            for (int i = 0; i < PIPELINE_PHASES.length; i++) {
                StepItem stepItem = new StepItem(PIPELINE_PHASES[i]);
                stepItem.addClassName("step");

                if (isFailed) {
                    if (i < activeIndex) {
                        stepItem.addClassName("step-primary");
                    } else if (i == activeIndex) {
                        stepItem.addClassName("step-error");
                    }
                } else {
                    if (i <= activeIndex) {
                        stepItem.addClassName("step-primary");
                    }
                }

                steps.add(stepItem);
            }
        }));
    }

    /**
     * Releases all active signal effects. Call when this view is removed.
     */
    public void dispose() {
        for (Disposable disposable : disposables) {
            disposable.dispose();
        }
        disposables.clear();
    }

    private static class StepItem extends Component implements HasStyle {
        public StepItem(String text) {
            super("li");
            getElement().setTextContent(text);
        }

        @Override
        public Component getComponent() {
            return this;
        }
    }
}
