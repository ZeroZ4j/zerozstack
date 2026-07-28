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
package com.zeroz4j.example.model;

import com.zeroz4j.api.DataModel;

import java.util.Objects;

/**
 * Shared job status model representing the state of a long-running deployment pipeline.
 */
@DataModel
public class JobStatus {

    private long jobId;
    private String phase;
    private int percent;
    private String message;
    private boolean running;

    public JobStatus() {
    }

    public JobStatus(long jobId, String phase, int percent, String message, boolean running) {
        this.jobId = jobId;
        this.phase = phase;
        this.percent = percent;
        this.message = message;
        this.running = running;
    }

    public static JobStatus idle() {
        return new JobStatus(0L, "Queued", 0, "Idle - click Start job to begin", false);
    }

    public long getJobId() {
        return jobId;
    }

    public void setJobId(long jobId) {
        this.jobId = jobId;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public int getPercent() {
        return percent;
    }

    public void setPercent(int percent) {
        this.percent = percent;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JobStatus jobStatus = (JobStatus) o;
        return jobId == jobStatus.jobId
                && percent == jobStatus.percent
                && running == jobStatus.running
                && Objects.equals(phase, jobStatus.phase)
                && Objects.equals(message, jobStatus.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jobId, phase, percent, message, running);
    }
}
