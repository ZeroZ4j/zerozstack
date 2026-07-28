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

import com.zeroz4j.example.api.JobSignals;
import com.zeroz4j.example.model.JobStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JobServiceImplTest {

    private JobServiceImpl jobService;

    @BeforeEach
    public void setup() {
        jobService = new JobServiceImpl();
    }

    @Test
    public void testStartJob() throws InterruptedException {
        JobStatus initialStatus = JobSignals.STATUS.get();
        assertNotNull(initialStatus);

        jobService.startJob();
        Thread.sleep(1500);

        JobStatus runningStatus = JobSignals.STATUS.get();
        assertNotNull(runningStatus);
        assertTrue(runningStatus.isRunning() || "Done".equals(runningStatus.getPhase()) || "Failed".equals(runningStatus.getPhase()));
    }
}
