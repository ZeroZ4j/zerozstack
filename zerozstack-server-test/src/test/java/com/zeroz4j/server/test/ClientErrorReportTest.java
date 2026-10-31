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
package com.zeroz4j.server.test;

import com.zeroz4j.api.ClientErrorRpc;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** A browser error reaches the server through the real engine, and the settings apply. */
class ClientErrorReportTest {

    @Test
    void aReportIsAnsweredWithAReferenceUntilTheConnectionHasSentItsShare() {
        try (TestServer server = TestServer.builder().named("client-errors")
                     .set("zeroz.clienterrors.maxPerConnection", 2).start();
             TestConnection browser = server.connect()) {
            String first = browser.call(ClientErrorRpc.class, "report", "error", "boom", "", "/");
            assertNotNull(first);
            assertEquals(6, first.length());
            assertNotNull(browser.call(ClientErrorRpc.class, "report", "error", "again", "", "/"));
            assertNull(browser.call(ClientErrorRpc.class, "report", "error", "third", "", "/"));
        }
    }

    @Test
    void theServerCanTurnItOff() {
        try (TestServer server = TestServer.builder().named("client-errors-off")
                     .set("zeroz.clienterrors.enabled", false).start();
             TestConnection browser = server.connect()) {
            assertNull(browser.call(ClientErrorRpc.class, "report", "error", "boom", "", "/"));
        }
    }
}
