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

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Logger;

/**
 * Resolves the {@link SessionThreadFactoryProvider} once, and falls back to virtual threads.
 *
 * <p>Framework-internal. Resolution mirrors {@code RmiEndpointConfigurator.provider()} exactly —
 * lazy, {@code volatile}, and more than one registered provider is a startup error rather than an
 * arbitrary pick, because which one wins decides what context every RMI call runs with.</p>
 */
final class SessionThreads {

    private static final Logger LOG = Logger.getLogger(SessionThreads.class.getName());

    /** Named so a thread dump says where the thread came from. */
    private static final ThreadFactory DEFAULT_FACTORY =
            Thread.ofVirtual().name("zeroz-rmi-", 0).factory();

    private static volatile ThreadFactory factory;
    private static volatile boolean resolved;

    private SessionThreads() {}

    /**
     * The factory RMI executors are built from.
     *
     * @return the provider's factory, or a virtual-thread factory when none is registered
     */
    static ThreadFactory factory() {
        if (!resolved) {
            synchronized (SessionThreads.class) {
                if (!resolved) {
                    factory = resolve();
                    resolved = true;
                }
            }
        }
        return factory;
    }

    private static ThreadFactory resolve() {
        List<SessionThreadFactoryProvider> found = new ArrayList<>();
        try {
            for (SessionThreadFactoryProvider provider
                    : ServiceLoader.load(SessionThreadFactoryProvider.class)) {
                found.add(provider);
            }
        } catch (Throwable t) {
            LOG.log(java.util.logging.Level.WARNING,
                    "[zeroz4j] Failed to load SessionThreadFactoryProvider, using virtual threads: "
                    + t.getMessage(), t);
            return DEFAULT_FACTORY;
        }
        return choose(found);
    }

    /**
     * Applies the resolution rules to whatever was discovered.
     *
     * <p>Separate from the {@link ServiceLoader} scan so the rules can be tested — a test cannot
     * register a service provider into its own running classpath.</p>
     *
     * @param found the discovered providers
     * @return the factory to use; never null
     */
    static ThreadFactory choose(List<SessionThreadFactoryProvider> found) {
        if (found.isEmpty()) {
            return DEFAULT_FACTORY;
        }
        if (found.size() > 1) {
            // Same reasoning as two authentication providers: picking one at random would decide,
            // invisibly, what context every call in the application runs with.
            StringBuilder names = new StringBuilder();
            for (SessionThreadFactoryProvider provider : found) {
                names.append(names.length() == 0 ? "" : ", ").append(provider.getClass().getName());
            }
            throw new IllegalStateException(
                    "Multiple SessionThreadFactoryProvider implementations found: " + names
                    + ". Exactly one must be registered in META-INF/services.");
        }
        SessionThreadFactoryProvider provider = found.get(0);
        ThreadFactory supplied;
        try {
            supplied = provider.threadFactory();
        } catch (RuntimeException ex) {
            // A container lookup that fails at startup should not take the application down; running
            // on virtual threads is degraded but working, and the warning names the cause.
            LOG.log(java.util.logging.Level.WARNING,
                    "[zeroz4j] " + provider.getClass().getName() + " failed to supply a thread "
                    + "factory, falling back to virtual threads: " + ex.getMessage(), ex);
            return DEFAULT_FACTORY;
        }
        if (supplied == null) {
            LOG.warning("[zeroz4j] " + provider.getClass().getName()
                    + " returned a null thread factory; falling back to virtual threads.");
            return DEFAULT_FACTORY;
        }
        LOG.info("[zeroz4j] RMI calls dispatch on threads from "
                + provider.getClass().getName() + ".");
        return supplied;
    }

    /** Test support: forces the provider to be looked up again. */
    static void resetForTesting() {
        synchronized (SessionThreads.class) {
            factory = null;
            resolved = false;
        }
    }
}
