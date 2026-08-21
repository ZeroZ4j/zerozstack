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
package com.zeroz4j.server.jakarta;

import com.zeroz4j.server.SessionThreadFactoryProvider;

import javax.naming.InitialContext;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Logger;

/**
 * Hands the framework the container's own {@code ManagedThreadFactory}, so RMI calls run on threads
 * the application server created.
 *
 * <p>That is what makes {@code java:comp/env/…} resolvable, and what gives a service the container's
 * transaction context and the caller's identity. Threads the framework creates itself have none of
 * it, and the resulting failure looks like a wiring problem rather than a threading one.</p>
 *
 * <h2>Configuration</h2>
 * <p>The JNDI name defaults to the Jakarta EE standard
 * {@code java:comp/DefaultManagedThreadFactory}. Override it with
 * {@code -Dzeroz.threads.jndiName=java:jboss/ee/concurrency/factory/customName} for a container
 * whose default is elsewhere or a deployment wanting its own pool.</p>
 *
 * <p><b>These are platform threads, not virtual ones.</b> A Jakarta EE 10 {@code ManagedThreadFactory}
 * cannot produce virtual threads. Inside a container that is the right trade — context matters more
 * than cheap threads — but it is a trade, and worth knowing before a load test surprises you.</p>
 *
 * <p>Registered through {@code META-INF/services}, so adding this module is enough.</p>
 */
public class ManagedThreadFactoryProvider implements SessionThreadFactoryProvider {

    private static final Logger LOG = Logger.getLogger(ManagedThreadFactoryProvider.class.getName());

    /** Overrides the JNDI name looked up. */
    public static final String JNDI_NAME_PROPERTY = "zeroz.threads.jndiName";
    /** The Jakarta EE standard name every compliant container provides. */
    public static final String DEFAULT_JNDI_NAME = "java:comp/DefaultManagedThreadFactory";

    /** Instantiated by {@link java.util.ServiceLoader}, not by application code. */
    public ManagedThreadFactoryProvider() {
        // Discovered through META-INF/services; the lookup happens in threadFactory().
    }

    @Override
    public ThreadFactory threadFactory() {
        String name = System.getProperty(JNDI_NAME_PROPERTY, DEFAULT_JNDI_NAME);
        try {
            ThreadFactory factory = InitialContext.doLookup(name);
            LOG.info("[zeroz4j] RMI calls will run on container-managed threads from " + name + ".");
            return factory;
        } catch (Exception ex) {
            // Returning null rather than throwing: the framework falls back to virtual threads, and a
            // degraded-but-running application beats a deployment that will not start. The warning
            // says what was lost, because the consequence — a JNDI lookup failing inside a service —
            // surfaces far from here.
            LOG.warning("[zeroz4j] No ManagedThreadFactory at '" + name + "' (" + ex.getMessage()
                    + "). Falling back to virtual threads: RMI calls will NOT carry the container's "
                    + "naming, transaction or security context, so java:comp lookups inside a service "
                    + "will fail. Set " + JNDI_NAME_PROPERTY + " if your container publishes it "
                    + "elsewhere.");
            return null;
        }
    }
}
