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
package com.zeroz4j.server.helidon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetAddress;
import java.net.ServerSocket;
import org.junit.jupiter.api.Test;

class HelidonServerBindingTest {

    @Test
    void aPortInUseStopsTheStartWithASentence() throws Exception {
        try (ServerSocket taken = new ServerSocket(0, 1, InetAddress.getByName("0.0.0.0"))) {
            int port = taken.getLocalPort();

            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> new HelidonServerBinding().start(port, "test"));

            assertEquals("Could not listen on port " + port + ". Another program is using it.",
                    failure.getMessage());
        }
    }
}
