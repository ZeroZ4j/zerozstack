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

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.weld.junit5.EnableWeld;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The endpoint the container is handed must be the CDI bean, not a bare {@code new}.
 *
 * <h2>Why this test exists</h2>
 * The Jakarta WebSocket API lets the container's default configurator decide how an endpoint is
 * created, and the containers disagree. WildFly's asks CDI; <b>Tomcat's is literally
 * {@code clazz.getConstructor().newInstance()}</b>. {@link WasmRmiServerEngine} injects three
 * collaborators, so on Tomcat it came up with three nulls and the first connection died in
 * {@code onOpen} with a {@code NullPointerException} on {@code syncEngine} — followed by a client
 * reconnecting for ever against a server that would fail it again every time.
 *
 * <p>Found by deploying a real application to embedded Tomcat, which is the only place it shows.
 */
@EnableWeld
class EndpointInstanceFromCdiTest {

    /** A bean that counts its own constructions, so "the same instance" is provable. */
    @ApplicationScoped
    public static class ManagedEndpoint {

        static final java.util.concurrent.atomic.AtomicInteger CONSTRUCTED =
                new java.util.concurrent.atomic.AtomicInteger();

        private final int serial;

        public ManagedEndpoint() {
            this.serial = CONSTRUCTED.incrementAndGet();
        }

        /** Called through the proxy, which is what forces the contextual instance to exist. */
        public int serial() {
            return serial;
        }
    }

    /** Not a bean in this container: what an application's own unmanaged endpoint looks like. */
    public static class UnmanagedEndpoint {

        public UnmanagedEndpoint() {
        }
    }

    @WeldSetup
    public WeldInitiator weld = WeldInitiator.of(ManagedEndpoint.class);

    private final RmiEndpointConfigurator configurator = new RmiEndpointConfigurator();

    @Test
    @DisplayName("a managed endpoint comes back as the container's own bean, injected")
    void managedEndpointComesFromCdi() throws Exception {
        ManagedEndpoint first = configurator.getEndpointInstance(ManagedEndpoint.class);
        ManagedEndpoint second = configurator.getEndpointInstance(ManagedEndpoint.class);

        assertNotNull(first);
        assertNotNull(second);
        // Application scoped: one contextual instance behind both proxies, which is also what stops
        // a per-connection copy of the engine - the thing that was null-injected - from existing.
        assertEquals(first.serial(), second.serial());
        assertEquals(1, ManagedEndpoint.CONSTRUCTED.get(),
                "the container constructed the endpoint itself instead of asking CDI");
    }

    /**
     * The other half, and the one that would be easy to get wrong: an endpoint CDI does not know
     * about must still be the CONTAINER'S to create.
     *
     * <p>There is no container in this JVM, so the delegation shows up as the API's own "Cannot
     * load platform configurator". That is the assertion: the call reached
     * {@code super.getEndpointInstance} rather than being answered with a {@code new} of our own or
     * - far worse - a null the container would then dereference.
     */
    @Test
    @DisplayName("an endpoint CDI does not know is left to the container, not answered here")
    void unmanagedEndpointFallsBackToTheContainer() {
        RuntimeException delegated = assertThrows(RuntimeException.class,
                () -> configurator.getEndpointInstance(UnmanagedEndpoint.class));

        assertTrue(delegated.getMessage() != null
                        && delegated.getMessage().contains("platform configurator"),
                "expected the container's own lookup to have been reached, got: " + delegated);
    }
}
