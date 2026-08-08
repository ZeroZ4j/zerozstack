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
package com.zeroz4j.signals;

import com.zeroz4j.api.Scope;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory and registry for shared signals.
 *
 * <p>Zeroz4j has exactly one signal abstraction. A signal created with
 * {@code new ValueSignal<>(x)} is local to wherever it lives — a client view or a server
 * service. A signal created with {@link #shared(String, Object)} is the same type, bound
 * to a wire identity; declare it once as a constant in the shared API module and reference
 * it from both tiers:</p>
 * <pre>{@code
 * // shared module
 * public final class JobSignals {
 *     public static final ValueSignal<JobStatus> STATUS =
 *             Signals.shared("job.status", JobStatus.idle());
 * }
 *
 * // server:  JobSignals.STATUS.set(next);        — broadcast, retention: framework's job
 * // client:  Effect.create(() -> bar.setValue(JobSignals.STATUS.get().getPercent()));
 * }</pre>
 *
 * <p>Because the shared module compiles into both tiers, each tier gets its own instance
 * of the constant bound to the same name; the installed {@link SignalTransport} gives it
 * its role. The server instance broadcasts on {@code set()} and retains the latest value;
 * the client instance mirrors it, receiving the retained value on subscribe. With no
 * transport installed (plain unit tests), a shared signal behaves as a local signal.</p>
 *
 * <p>Shared signals are server-authoritative in this release: a client-side {@code set()}
 * fails with {@link IllegalStateException}. Payloads must be wire-serializable
 * ({@code @DataModel} classes or types supported by {@code BinarySerializer}).</p>
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>One Declaration:</b> The shared constant IS the signal — there is no separate
 *       topic object and no per-tier subscribe/publish call.</li>
 *   <li><b>Idempotent by Name:</b> Calling {@link #shared(String, Object)} twice with the
 *       same name returns the same instance.</li>
 * </ul>
 */
public final class Signals {

    private static final Map<String, SharedValueSignal<?>> registry = new ConcurrentHashMap<>();
    private static final Map<String, ScopedSignal<?>> scopedFamilies = new ConcurrentHashMap<>();
    private static volatile SignalTransport transport;

    private Signals() {}

    /**
     * Declares (or returns the existing) shared signal named after the value's class.
     *
     * <p>The wire name defaults to {@code initialValue.getClass().getName()} — the same
     * runtime class identity the binary serializer already writes on the wire. One default
     * signal exists per payload type; declare additional signals of the same type with
     * {@link #shared(String, Object)} and explicit names.</p>
     *
     * @param <T>          value type; must be wire-serializable
     * @param initialValue value both tiers hold until the first synchronization; non-null,
     *                     since the name derives from its class
     * @return the shared signal — use it exactly like any other {@link ValueSignal}
     * @throws IllegalArgumentException if {@code initialValue} is null
     */
    public static <T> ValueSignal<T> shared(T initialValue) {
        if (initialValue == null) {
            throw new IllegalArgumentException(
                    "shared(initialValue) derives the wire name from the value's class and needs a "
                    + "non-null initial value; use shared(name, initialValue) to start from null");
        }
        return shared(initialValue.getClass().getName(), initialValue);
    }

    /**
     * Declares (or returns the existing) shared signal bound to the given wire name.
     *
     * @param <T>          value type; must be wire-serializable
     * @param name         unique wire name, e.g. {@code "job.status"}
     * @param initialValue value both tiers hold until the first synchronization
     * @return the shared signal — use it exactly like any other {@link ValueSignal}
     * @throws IllegalArgumentException if {@code name} is null or blank
     */
    public static <T> ValueSignal<T> shared(String name, T initialValue) {
        return declare(name, initialValue, false, java.util.Collections.emptySet());
    }

    /**
     * Declares a shared signal that clients may also set, named after the value's class.
     *
     * <p>Client writes are optimistic: the local mirror updates immediately, the write is
     * sent to the server, and the server — which stays authoritative — either accepts it
     * (broadcasting to everyone, confirming the writer via the echo) or rejects it
     * (role check or validation annotations), in which case a corrective update snaps the
     * writer back to server truth. Last accepted write wins.</p>
     *
     * <p>Any session may write; to restrict writes to roles, use
     * {@link #sharedWritable(String, Object, String...)} with an explicit name.</p>
     *
     * @param <T>          value type; must be wire-serializable
     * @param initialValue value both tiers hold until the first synchronization; non-null
     * @return the shared signal
     */
    public static <T> ValueSignal<T> sharedWritable(T initialValue) {
        if (initialValue == null) {
            throw new IllegalArgumentException(
                    "sharedWritable(initialValue) derives the wire name from the value's class and needs a "
                    + "non-null initial value; use sharedWritable(name, initialValue) to start from null");
        }
        return sharedWritable(initialValue.getClass().getName(), initialValue);
    }

    /**
     * Declares a client-writable shared signal bound to the given wire name.
     *
     * @param <T>          value type; must be wire-serializable
     * @param name         unique wire name
     * @param initialValue value both tiers hold until the first synchronization
     * @param writeRoles   roles allowed to write from a client; empty allows any session
     * @return the shared signal
     */
    public static <T> ValueSignal<T> sharedWritable(String name, T initialValue, String... writeRoles) {
        java.util.Set<String> roles = new java.util.LinkedHashSet<>(java.util.Arrays.asList(writeRoles));
        return declare(name, initialValue, true, java.util.Collections.unmodifiableSet(roles));
    }

    /**
     * Declares (or returns the existing) family of signals holding one value per target.
     *
     * <p>Where {@link #shared(String, Object)} is a single value the whole server agrees on, this is
     * one value per tenant, user, browser or session — see {@link ScopedSignal} for how each tier
     * uses it, and {@link Scope} for what each scope means and which ones survive without a login.</p>
     *
     * @param <T>          value type; must be wire-serializable
     * @param name         unique wire name, e.g. {@code "shop.basket"}
     * @param initialValue the value a target holds until something sets it
     * @param scope        which targets the family is keyed by
     * @return the scoped signal
     * @throws IllegalArgumentException if the name is blank, the scope is null, or the scope is
     *                                  {@link Scope#GLOBAL}
     */
    public static <T> ScopedSignal<T> scoped(String name, T initialValue, Scope scope) {
        return declareScoped(name, initialValue, scope, false, java.util.Collections.emptySet());
    }

    /**
     * Declares a scoped family that clients may also write, with the same optimistic-write and
     * server-authority rules as {@link #sharedWritable(String, Object, String...)}.
     *
     * <p>A client write lands on that client's own target, never another's: the server resolves the
     * target from the writing session, and the wire frame carries no target at all.</p>
     *
     * @param <T>          value type; must be wire-serializable
     * @param name         unique wire name
     * @param initialValue the value a target holds until something sets it
     * @param scope        which targets the family is keyed by
     * @param writeRoles   roles allowed to write from a client; empty allows any session
     * @return the scoped signal
     */
    public static <T> ScopedSignal<T> scopedWritable(String name, T initialValue, Scope scope,
                                                     String... writeRoles) {
        java.util.Set<String> roles = new java.util.LinkedHashSet<>(java.util.Arrays.asList(writeRoles));
        return declareScoped(name, initialValue, scope, true,
                java.util.Collections.unmodifiableSet(roles));
    }

    @SuppressWarnings("unchecked")
    private static synchronized <T> ScopedSignal<T> declareScoped(String name, T initialValue,
                                                                  Scope scope, boolean clientWritable,
                                                                  java.util.Set<String> writeRoles) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("scoped signal name must not be null or blank");
        }
        if (scope == null) {
            throw new IllegalArgumentException("scoped signal '" + name + "' needs a scope");
        }
        if (scope == Scope.GLOBAL) {
            throw new IllegalArgumentException(
                    "Scoped signal '" + name + "' cannot use Scope.GLOBAL: a global signal is one "
                    + "value everyone shares, which is what Signals.shared(\"" + name + "\", ...) "
                    + "already is. Pick CLIENT, SESSION, USER or TENANT.");
        }
        // The family is checked before the plain registry deliberately: once a client has called
        // get(), the family's own mirror sits in the plain registry under this same name, and
        // checking that first would make re-running the declaration collide with itself.
        ScopedSignal<?> existing = scopedFamilies.get(name);
        if (existing == null && registry.containsKey(name)) {
            throw new IllegalStateException(
                    "'" + name + "' is already declared as a plain shared signal. One wire name "
                    + "cannot be both a single global value and one value per target -- give the "
                    + "scoped signal its own name.");
        }
        if (existing != null) {
            boolean sameShape = existing.scope() == scope
                    && existing.isClientWritable() == clientWritable
                    && existing.writeRoles().equals(writeRoles)
                    && java.util.Objects.equals(existing.initialValue(), initialValue);
            if (!sameShape) {
                throw new IllegalStateException(
                        "Conflicting declaration of scoped signal '" + name + "'. It is already "
                        + "declared with a different scope, initial value, writability or role set.");
            }
            return (ScopedSignal<T>) existing;
        }
        ScopedSignal<T> family = new ScopedSignal<>(name, initialValue, scope, clientWritable, writeRoles);
        scopedFamilies.put(name, family);
        SignalTransport current = transport;
        if (current != null) {
            current.onScopedFamilyCreated(family);
        }
        return family;
    }

    /**
     * Returns the client's local mirror of a scoped family, creating and subscribing it on first
     * use. The mirror is an ordinary shared signal bound to the family's base name — the server
     * answers its subscribe with whichever target this session belongs to.
     */
    @SuppressWarnings("unchecked")
    static synchronized <T> ValueSignal<T> mirrorFor(ScopedSignal<T> family) {
        SharedValueSignal<?> existing = registry.get(family.name());
        if (existing != null) {
            return (ValueSignal<T>) existing;
        }
        SharedValueSignal<T> mirror = new SharedValueSignal<>(family.name(), family.initialValue(),
                family.isClientWritable(), family.writeRoles());
        registry.put(family.name(), mirror);
        SignalTransport current = transport;
        if (current != null) {
            current.onSharedSignalCreated(mirror);
        }
        return mirror;
    }

    /**
     * Looks up a scoped family by wire name.
     *
     * @param name base wire name
     * @return the family, or null if not declared in this runtime yet
     */
    public static ScopedSignal<?> lookupScoped(String name) {
        return scopedFamilies.get(name);
    }

    @SuppressWarnings("unchecked")
    private static synchronized <T> ValueSignal<T> declare(String name, T initialValue,
                                                           boolean clientWritable, java.util.Set<String> writeRoles) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("shared signal name must not be null or blank");
        }
        if (scopedFamilies.containsKey(name)) {
            throw new IllegalStateException(
                    "'" + name + "' is already declared as a scoped signal, which holds one value "
                    + "per target rather than one value for everyone. Declaring it shared as well "
                    + "would quietly give it two meanings -- give one of them another name.");
        }
        SharedValueSignal<?> existing = registry.get(name);
        if (existing != null) {
            // Re-running the same declaration is fine; two DIFFERENT declarations colliding on one
            // wire name is not. Previously the second was silently discarded, so a signal quietly
            // carried another declaration's initial value, writability and roles. The usual cause is
            // the default wire name -- the payload's class name -- giving one shared signal per type.
            boolean sameShape = existing.isClientWritable() == clientWritable
                    && existing.writeRoles().equals(writeRoles == null
                            ? java.util.Collections.<String>emptySet() : writeRoles)
                    && java.util.Objects.equals(existing.declaredInitialValue(), initialValue);
            if (!sameShape) {
                throw new IllegalStateException(
                        "Conflicting declaration of shared signal '" + name + "'. It is already "
                        + "declared with a different initial value, writability or role set. The "
                        + "default wire name is the payload's class name, so two signals of the same "
                        + "type collide -- give them explicit names, e.g. "
                        + "Signals.shared(\"" + name + ".something\", initialValue).");
            }
            return (ValueSignal<T>) existing;
        }
        SharedValueSignal<T> signal = new SharedValueSignal<>(name, initialValue, clientWritable, writeRoles);
        registry.put(name, signal);
        SignalTransport current = transport;
        if (current != null) {
            current.onSharedSignalCreated(signal);
        }
        return signal;
    }

    /**
     * Installs the tier-specific transport. Called by the framework runtime (server engine
     * or Wasm client bootstrap), not by applications. Signals declared before installation
     * are replayed to the new transport.
     *
     * @param newTransport the transport, or null to detach
     */
    public static synchronized void installTransport(SignalTransport newTransport) {
        transport = newTransport;
        if (newTransport != null) {
            for (SharedValueSignal<?> signal : registry.values()) {
                newTransport.onSharedSignalCreated(signal);
            }
            for (ScopedSignal<?> family : scopedFamilies.values()) {
                newTransport.onScopedFamilyCreated(family);
            }
        }
    }

    /**
     * Looks up a shared signal by wire name.
     *
     * @param name wire name
     * @return the shared signal, or null if not declared in this runtime yet
     */
    public static SharedValueSignal<?> lookup(String name) {
        return registry.get(name);
    }

    static SignalTransport transport() {
        return transport;
    }

    /**
     * Clears the registry and transport. Test support only.
     */
    public static synchronized void resetForTesting() {
        registry.clear();
        scopedFamilies.clear();
        transport = null;
    }
}
