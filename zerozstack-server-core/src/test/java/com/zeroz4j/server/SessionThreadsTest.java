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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;
import java.util.concurrent.ThreadFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The framework's contract here is narrow and worth stating: <b>RMI calls run on threads produced by
 * the factory a provider supplied</b>. Whether a given container's {@code ManagedThreadFactory}
 * really carries naming, transaction and security context is that container's contract, verified by
 * the application deploying to it — not here.
 *
 * <p>{@link SessionThreads} resolves through {@link java.util.ServiceLoader}, which these tests
 * cannot register into at runtime, so they exercise the resolution rules and the executor shape
 * directly.</p>
 */
class SessionThreadsTest {

    @BeforeEach
    @AfterEach
    void reset() {
        SessionThreads.resetForTesting();
    }

    /** Records every thread it makes, which is what proves the executor used it. */
    private static final class RecordingFactory implements ThreadFactory {
        final List<String> created = new CopyOnWriteArrayList<>();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "recorded-" + created.size());
            created.add(thread.getName());
            return thread;
        }
    }

    @Test
    void withNoProviderRegisteredCallsRunOnVirtualThreads() throws Exception {
        boolean[] wasVirtual = { false };
        String[] name = { "" };

        try (ExecutorService executor =
                     Executors.newThreadPerTaskExecutor(SessionThreads.factory())) {
            executor.submit(() -> {
                wasVirtual[0] = Thread.currentThread().isVirtual();
                name[0] = Thread.currentThread().getName();
            }).get();
        }

        assertTrue(wasVirtual[0], "the default must stay virtual threads, as in 0.5.0");
        assertTrue(name[0].startsWith("zeroz-rmi-"),
                "threads should be named so a thread dump says where they came from: " + name[0]);
    }

    @Test
    void aSuppliedFactoryIsWhatTheExecutorUses() throws Exception {
        RecordingFactory factory = new RecordingFactory();
        String[] ranOn = { "" };

        // Exactly what onOpen does, given whatever SessionThreads resolved.
        try (ExecutorService executor = Executors.newThreadPerTaskExecutor(factory)) {
            executor.submit(() -> ranOn[0] = Thread.currentThread().getName()).get();
        }

        assertEquals(1, factory.created.size(), "the executor must ask the factory for its threads");
        assertEquals(factory.created.get(0), ranOn[0]);
        assertFalse(ranOn[0].startsWith("zeroz-rmi-"),
                "a supplied factory must replace the default, not sit alongside it");
    }

    @Test
    void aRegisteredProviderSuppliesTheFactory() {
        RecordingFactory supplied = new RecordingFactory();

        assertEquals(supplied, SessionThreads.choose(List.of(() -> supplied)));
    }

    @Test
    void noProviderMeansVirtualThreads() throws Exception {
        boolean[] wasVirtual = { false };

        try (ExecutorService executor =
                     Executors.newThreadPerTaskExecutor(SessionThreads.choose(List.of()))) {
            executor.submit(() -> wasVirtual[0] = Thread.currentThread().isVirtual()).get();
        }

        assertTrue(wasVirtual[0]);
    }

    @Test
    void twoRegisteredProvidersAreAStartupErrorNamingBoth() {
        RecordingFactory first = new RecordingFactory();
        RecordingFactory second = new RecordingFactory();

        IllegalStateException error = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> SessionThreads.choose(List.of(() -> first, () -> second)));

        // Choosing one at random would decide, invisibly, what context every call in the
        // application runs with — the same reasoning as two AuthenticationProviders.
        assertTrue(error.getMessage().contains("Multiple SessionThreadFactoryProvider"),
                error.getMessage());
        assertEquals(2, error.getMessage().split("SessionThreadsTest").length - 1,
                "both offenders should be named: " + error.getMessage());
    }

    @Test
    void aProviderReturningNullFallsBackRatherThanFailingTheConnection() throws Exception {
        // A container lookup can fail at startup; degraded-but-running beats an unusable server.
        boolean[] wasVirtual = { false };

        ThreadFactory resolved = SessionThreads.choose(List.of(() -> null));
        try (ExecutorService executor = Executors.newThreadPerTaskExecutor(resolved)) {
            executor.submit(() -> wasVirtual[0] = Thread.currentThread().isVirtual()).get();
        }

        assertTrue(wasVirtual[0], "a broken provider must fall back, not take the server down");
    }

    @Test
    void aProviderThatThrowsFallsBackToo() throws Exception {
        boolean[] wasVirtual = { false };

        ThreadFactory resolved = SessionThreads.choose(List.of(() -> {
            throw new IllegalStateException("no such JNDI name");
        }));
        try (ExecutorService executor = Executors.newThreadPerTaskExecutor(resolved)) {
            executor.submit(() -> wasVirtual[0] = Thread.currentThread().isVirtual()).get();
        }

        assertTrue(wasVirtual[0]);
    }

    @Test
    void theResolvedFactoryIsStable() {
        // Resolved once and cached: re-resolving per connection would repeat a JNDI lookup on every
        // handshake.
        assertEquals(SessionThreads.factory(), SessionThreads.factory());
    }
}
