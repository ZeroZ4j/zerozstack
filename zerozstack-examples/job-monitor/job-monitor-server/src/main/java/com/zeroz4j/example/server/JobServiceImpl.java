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

import com.zeroz4j.example.api.JobService;
import com.zeroz4j.example.api.JobSignals;
import com.zeroz4j.example.model.JobStatus;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Server-side implementation of {@link JobService}. Simulates a long-running deployment
 * pipeline by updating the shared signal {@link JobSignals#STATUS}.
 */
@ApplicationScoped
public class JobServiceImpl implements JobService {

    private final AtomicLong jobIdGenerator = new AtomicLong(1000L);

    @Override
    public void startJob() {
        // Ignore startJob() while a job is already running
        if (JobSignals.STATUS.get() != null && JobSignals.STATUS.get().isRunning()) {
            return;
        }

        long jobId = jobIdGenerator.incrementAndGet();
        boolean willFail = ThreadLocalRandom.current().nextInt(4) == 0;
        int failAtStep = willFail ? ThreadLocalRandom.current().nextInt(3, 15) : -1;

        Thread.ofVirtual().start(() -> {
            try {
                // Phase 1: Queued (0%)
                updateStatus(jobId, "Queued", 0, "Job #" + jobId + " queued, waiting for runner...", true);
                Thread.sleep(1000);

                int stepCount = 0;

                // Phase 2: Building (5% -> 35%)
                String[] buildMsgs = {
                        "Resolving dependencies...",
                        "Compiling Java sources...",
                        "Packaging TeaVM Wasm client...",
                        "Building container image..."
                };
                for (int i = 0; i < buildMsgs.length; i++) {
                    stepCount++;
                    if (stepCount == failAtStep) {
                        updateStatus(jobId, "Failed", 5 + i * 8, "Build failed: compilation error in phase 'Building'", false);
                        return;
                    }
                    int percent = 5 + i * 8 + ThreadLocalRandom.current().nextInt(0, 3);
                    updateStatus(jobId, "Building", percent, buildMsgs[i], true);
                    Thread.sleep(1000 + ThreadLocalRandom.current().nextInt(0, 300));
                }

                // Phase 3: Testing (40% -> 75%)
                String[] testMsgs = {
                        "Executing unit test suite...",
                        "Running integration tests...",
                        "Performing security vulnerability scan...",
                        "Validating deployment configuration..."
                };
                for (int i = 0; i < testMsgs.length; i++) {
                    stepCount++;
                    if (stepCount == failAtStep) {
                        updateStatus(jobId, "Failed", 40 + i * 9, "Testing failed: 2 unit tests failed", false);
                        return;
                    }
                    int percent = 40 + i * 9 + ThreadLocalRandom.current().nextInt(0, 3);
                    updateStatus(jobId, "Testing", percent, testMsgs[i], true);
                    Thread.sleep(1000 + ThreadLocalRandom.current().nextInt(0, 300));
                }

                // Phase 4: Deploying (80% -> 95%)
                String[] deployMsgs = {
                        "Provisioning target environment...",
                        "Deploying Helidon server instance...",
                        "Performing health check probes..."
                };
                for (int i = 0; i < deployMsgs.length; i++) {
                    stepCount++;
                    if (stepCount == failAtStep) {
                        updateStatus(jobId, "Failed", 80 + i * 5, "Deployment failed: health probe timeout", false);
                        return;
                    }
                    int percent = 80 + i * 5 + ThreadLocalRandom.current().nextInt(0, 3);
                    updateStatus(jobId, "Deploying", percent, deployMsgs[i], true);
                    Thread.sleep(1000 + ThreadLocalRandom.current().nextInt(0, 300));
                }

                // Phase 5: Done (100%)
                updateStatus(jobId, "Done", 100, "Pipeline execution completed successfully!", false);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                updateStatus(jobId, "Failed", 0, "Job interrupted", false);
            }
        });
    }

    private void updateStatus(long jobId, String phase, int percent, String message, boolean running) {
        // ALWAYS set a NEW JobStatus instance — never mutate the current one
        JobSignals.STATUS.set(new JobStatus(jobId, phase, Math.min(100, percent), message, running));
    }
}
