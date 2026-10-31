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
package com.zeroz4j.server;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The browser error report: one log line per report, cut to length, limited per connection. */
class ClientErrorRpcImplTest {

    private final List<String> lines = new ArrayList<>();
    private final Logger log = Logger.getLogger("com.zeroz4j.client");
    private final Handler handler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            lines.add(new SimpleFormatter().formatMessage(record));
        }

        @Override
        public void flush() { }

        @Override
        public void close() { }
    };

    @Test
    void aReportIsOneLineWhateverTheBrowserSent() {
        log.addHandler(handler);
        try {
            String reference = new ClientErrorRpcImpl().report("error",
                    "boom\nWARNING: forged line\r done", "at a\nat b", "/page\n/other");
            assertNotNull(reference);
            assertEquals(1, lines.size());
            String line = lines.get(0);
            assertFalse(line.contains("\n") || line.contains("\r") || line.contains(" "));
            assertTrue(line.contains("[" + reference + "]"));
            assertTrue(line.contains("boom WARNING: forged line"));
        } finally {
            log.removeHandler(handler);
        }
    }

    @Test
    void textIsCutToLength() {
        String cut = ClientErrorRpcImpl.clean("x".repeat(5000), ClientErrorRpcImpl.MAX_MESSAGE);
        assertEquals(ClientErrorRpcImpl.MAX_MESSAGE + 3, cut.length());
        assertEquals("", ClientErrorRpcImpl.clean(null, 10));
    }

    @Test
    void oneConnectionMaySendOnlyItsShare() {
        ClientErrorRpcImpl rpc = new ClientErrorRpcImpl();
        int taken = 0;
        for (int idx = 0; idx < 30; idx++) {
            if (rpc.report("error", "m" + idx, "", "/") != null) {
                taken++;
            }
        }
        assertEquals(ClientErrorRpcImpl.DEFAULT_MAX_PER_CONNECTION, taken);
        assertNull(rpc.report("error", "more", "", "/"));
    }
}
