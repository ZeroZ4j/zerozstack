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
package com.zeroz4j.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What leaves the page: a cap, no repeats, and nothing when it is turned off. */
class ClientErrorsTest {

    @BeforeEach
    void fresh() {
        ClientErrors.reset();
    }

    @Test
    void anIdenticalErrorTwiceRunningIsSentOnce() {
        assertTrue(ClientErrors.admit("error", "boom"));
        assertFalse(ClientErrors.admit("error", "boom"));
        assertTrue(ClientErrors.admit("error", "other"));
        assertTrue(ClientErrors.admit("error", "boom"));
    }

    @Test
    void aPageSendsNoMoreThanItsShare() {
        int sent = 0;
        for (int idx = 0; idx < 100; idx++) {
            if (ClientErrors.admit("error", "m" + idx)) {
                sent++;
            }
        }
        assertEquals(ClientErrors.MAX_PER_PAGE, sent);
    }

    @Test
    void nothingIsSentWhenTurnedOff() {
        ClientErrors.setReporting(false);
        assertFalse(ClientErrors.admit("error", "boom"));
    }

    @Test
    void longTextIsCut() {
        assertEquals(13, ClientErrors.trim("x".repeat(100), 10).length());
        assertEquals("", ClientErrors.trim(null, 10));
    }
}
