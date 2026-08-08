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

import java.util.concurrent.ThreadFactory;

/**
 * Supplies the threads RMI calls are dispatched on.
 *
 * <p>Discovered through {@link java.util.ServiceLoader}, like {@link AuthenticationProvider}. Declare
 * an implementation in {@code META-INF/services/com.zeroz4j.server.SessionThreadFactoryProvider}.</p>
 *
 * <h2>Why this exists</h2>
 * <p>By default the framework creates its own virtual threads. Inside a Jakarta EE server that is a
 * problem: the container attaches thread-locals — the naming context behind {@code java:comp/env/…},
 * the transaction context, the security context — before calling application code, and a thread the
 * container did not create carries none of them. A service doing a JNDI lookup, or holding an
 * {@code @Resource} resolved lazily on the calling thread, then fails a long way from the cause.</p>
 *
 * <p>It cannot be repaired from inside such a thread by handing work to a
 * {@code ManagedExecutorService}: there is no context on that thread to capture and propagate. It has
 * to be right at thread <em>creation</em>, which is why this is a factory rather than an executor.</p>
 *
 * <pre>{@code
 * public final class ManagedThreadFactoryProvider implements SessionThreadFactoryProvider {
 *     @Override
 *     public ThreadFactory threadFactory() {
 *         return InitialContext.doLookup("java:comp/DefaultManagedThreadFactory");
 *     }
 * }
 * }</pre>
 *
 * <p>The framework's side of this contract is narrow and complete: <b>calls are dispatched on threads
 * this factory produced</b>. Whether a particular container's factory really carries naming,
 * transaction and security context is that container's contract, not this framework's.</p>
 *
 * <p><b>A container factory produces platform threads, not virtual ones.</b> That is the right trade
 * inside a server — container context matters more than cheap threads — but it is a trade, and worth
 * knowing rather than discovering.</p>
 */
public interface SessionThreadFactoryProvider {

    /**
     * The factory whose threads should run RMI calls. Called once, at first use.
     *
     * @return the thread factory; must not be null
     */
    ThreadFactory threadFactory();
}
