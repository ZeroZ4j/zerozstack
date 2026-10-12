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

import com.zeroz4j.server.spi.ServerBinding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which server binding {@link Zeroz4jServer} starts, given what is on the classpath. The bindings
 * here are fakes that record the call; no server is started.
 */
class Zeroz4jServerBindingTest {

    /** A binding that remembers whether it was started and stopped. */
    private static final class FakeBinding implements ServerBinding {
        private final String name;
        private boolean started;
        private boolean closed;
        private String appName;

        FakeBinding(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public RunningServer start(int port, String appName) {
            this.started = true;
            this.appName = appName;
            return new RunningServer() {
                @Override
                public int port() {
                    return port + 1;
                }

                @Override
                public void close() {
                    closed = true;
                }
            };
        }
    }

    @Test
    void noBindingFailsNamingBothArtifactsAndTheWar() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> Zeroz4jServer.start(8080, "app", List.of(), null));

        assertTrue(failure.getMessage().contains("zerozstack-server-helidon"), failure.getMessage());
        assertTrue(failure.getMessage().contains("zerozstack-server-vidocq"), failure.getMessage());
        assertTrue(failure.getMessage().contains("A WAR does not call"), failure.getMessage());
    }

    @Test
    void oneBindingIsUsedAndTheHandlePassesThrough() {
        FakeBinding only = new FakeBinding("helidon");

        Zeroz4jServer server = Zeroz4jServer.start(8080, "My app", List.<ServerBinding>of(only), null);

        assertTrue(only.started);
        assertEquals("My app", only.appName);
        assertEquals(8081, server.port(), "the port is the one the binding reports, not the one asked for");
        server.close();
        assertTrue(only.closed);
    }

    @Test
    void twoBindingsFailNamingBothAndStartNeither() {
        FakeBinding first = new FakeBinding("helidon");
        FakeBinding second = new FakeBinding("vidocq");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> Zeroz4jServer.start(8080, "app", List.<ServerBinding>of(first, second), null));

        assertTrue(failure.getMessage().contains("helidon, vidocq"), failure.getMessage());
        assertTrue(failure.getMessage().contains("zeroz.server.binding"), failure.getMessage());
        assertFalse(first.started);
        assertFalse(second.started);
    }

    @Test
    void twoBindingsAndThePropertyStartTheNamedOne() {
        FakeBinding first = new FakeBinding("helidon");
        FakeBinding second = new FakeBinding("vidocq");

        Zeroz4jServer.start(8080, "app", List.<ServerBinding>of(first, second), " Vidocq ");

        assertFalse(first.started);
        assertTrue(second.started);
    }

    @Test
    void thePropertyNamingAnAbsentBindingFailsSayingWhatIsThere() {
        FakeBinding only = new FakeBinding("helidon");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> Zeroz4jServer.start(8080, "app", List.<ServerBinding>of(only), "vidocq"));

        assertTrue(failure.getMessage().contains("Found: helidon"), failure.getMessage());
        assertFalse(only.started);
    }

    @Test
    void theRealLookupFindsNoBindingInThisModule() {
        // zerozstack-server-core registers none, so the public call reaches the same sentence.
        assertThrows(IllegalStateException.class, () -> Zeroz4jServer.start(8080, "app"));
    }
}
