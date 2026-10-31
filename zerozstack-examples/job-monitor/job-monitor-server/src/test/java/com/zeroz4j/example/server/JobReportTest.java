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

import com.zeroz4j.api.SlowCall;
import com.zeroz4j.api.SlowCallState;
import com.zeroz4j.example.api.JobService;
import com.zeroz4j.example.model.JobReport;
import com.zeroz4j.server.test.TestConnection;
import com.zeroz4j.server.test.TestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The report, driven as a browser drives it, with the steps shortened. */
class JobReportTest {

    private long stepMillis;

    @BeforeEach
    void shortenTheSteps() {
        stepMillis = JobServiceImpl.reportStepMillis;
    }

    @AfterEach
    void restoreTheSteps() {
        JobServiceImpl.reportStepMillis = stepMillis;
    }

    @Test
    void theReportReachesTheBrowser() {
        JobServiceImpl.reportStepMillis = 1L;
        try (TestServer server = TestServer.start(JobServiceImpl.class);
             TestConnection browser = server.connect("demo", "user")) {

            SlowCall<JobReport> call = browser.call(JobService.class, "writeReport");
            SlowCallState<JobReport> end = browser.awaitSlowCall(call, 5_000L);

            assertEquals(2, end.value().findings());
        }
    }

    @Test
    void theReportCanBeCanceled() {
        try (TestServer server = TestServer.start(JobServiceImpl.class);
             TestConnection browser = server.connect("demo", "user")) {

            SlowCall<JobReport> call = browser.call(JobService.class, "writeReport");
            browser.cancelSlowCall(call);

            assertTrue(browser.awaitSlowCall(call, 5_000L).isCanceled());
        }
    }
}
