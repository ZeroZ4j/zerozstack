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

/**
 * A {@link ValueSignal} bound to a wire identity, created via
 * {@link Signals#shared(String, Object)}. Applications interact with it purely through
 * the {@code ValueSignal} API — this subtype exists so transports can address the signal
 * by name and apply remote updates without triggering re-broadcast loops.
 *
 * <p>Framework-internal surface: {@link #name()} and {@link #applyRemote(Object)} are for
 * {@link SignalTransport} implementations, not application code.</p>
 *
 * @param <T> value type
 */
public final class SharedValueSignal<T> extends ValueSignal<T> {

    private final String name;
    private final boolean clientWritable;
    private final java.util.Set<String> writeRoles;
    /** Retained so a conflicting re-declaration of the same wire name can be detected and refused. */
    private final T declaredInitialValue;

    /** Base wire name of the {@link ScopedSignal} this instance belongs to; null when unscoped. */
    private final String scopeFamily;
    /** How far a change to this instance reaches; null when unscoped. */
    private final com.zeroz4j.api.Scope scope;
    /** The one tenant, user, client or session this instance holds the value for; null when unscoped. */
    private final String scopeTarget;

    SharedValueSignal(String name, T initialValue, boolean clientWritable, java.util.Set<String> writeRoles) {
        this(name, initialValue, clientWritable, writeRoles, null, null, null);
    }

    SharedValueSignal(String name, T initialValue, boolean clientWritable,
                      java.util.Set<String> writeRoles, String scopeFamily,
                      com.zeroz4j.api.Scope scope, String scopeTarget) {
        super(initialValue);
        this.name = name;
        this.clientWritable = clientWritable;
        this.writeRoles = writeRoles;
        this.declaredInitialValue = initialValue;
        this.scopeFamily = scopeFamily;
        this.scope = scope;
        this.scopeTarget = scopeTarget;
    }

    /**
     * Whether this instance holds one target's value of a {@link ScopedSignal} rather than a single
     * value shared by everyone.
     *
     * @return true when scoped
     */
    public boolean isScoped() {
        return scope != null;
    }

    /**
     * The wire name clients know this signal by. For a scoped instance this is the family's base
     * name, shared by every target — {@link #name()} is per-target and never goes on the wire.
     *
     * @return the base wire name, or null when unscoped
     */
    public String scopeFamily() {
        return scopeFamily;
    }

    /**
     * What the owning family is keyed by, which is what the transport filters sessions on when
     * broadcasting this instance.
     *
     * @return the scope, or null when this is an ordinary shared signal
     */
    public com.zeroz4j.api.Scope scope() {
        return scope;
    }

    /**
     * Whose value this instance holds — one tenant, user, browser or session, according to
     * {@link #scope()}.
     *
     * @return the target id, or null when this is an ordinary shared signal
     */
    public String scopeTarget() {
        return scopeTarget;
    }

    /**
     * The value passed at declaration, kept unchanged by later writes so a second declaration of the
     * same wire name can be compared against it and refused if it disagrees.
     *
     * @return the declared initial value
     */
    T declaredInitialValue() {
        return declaredInitialValue;
    }

    /**
     * Returns the wire name binding this signal across tiers.
     *
     * @return wire name
     */
    public String name() {
        return name;
    }

    /**
     * Returns whether clients may set this signal (writes are still enforced
     * server-side against {@link #writeRoles()} and validation annotations).
     *
     * @return true if declared client-writable
     */
    public boolean isClientWritable() {
        return clientWritable;
    }

    /**
     * Returns the roles allowed to write this signal from a client; empty means any
     * authenticated or anonymous session may write (subject to validation).
     *
     * @return required write roles
     */
    public java.util.Set<String> writeRoles() {
        return writeRoles;
    }

    @Override
    public void set(T newValue) {
        SignalTransport transport = Signals.transport();
        if (transport != null && !transport.canSet(this)) {
            throw new IllegalStateException("Shared signal '" + name + "' is server-authoritative: "
                    + "the client cannot set it. Declare it with Signals.sharedWritable(...) to "
                    + "allow client writes, change it on the server, or keep client-only state "
                    + "in a local ValueSignal.");
        }
        if (assignIfChanged(newValue)) {
            notifyListeners();
            if (transport != null) {
                transport.afterSet(this, newValue);
            }
        }
    }

    /**
     * Applies a value received from the remote tier: updates and notifies local listeners
     * without consulting {@link SignalTransport#canSet} or re-broadcasting.
     *
     * @param remoteValue the received value
     */
    @SuppressWarnings("unchecked")
    public void applyRemote(Object remoteValue) {
        if (assignIfChanged((T) remoteValue)) {
            notifyListeners();
        }
    }
}
