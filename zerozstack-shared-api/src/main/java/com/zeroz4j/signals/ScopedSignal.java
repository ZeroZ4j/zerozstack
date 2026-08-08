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

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A family of shared signals holding one value per tenant, user, browser or session.
 *
 * <p>{@link Signals#shared(String, Object)} is one value the whole server agrees on. A scoped signal
 * is the same idea narrowed: declared once, it holds a separate retained value for each target, and
 * a client only ever sees its own. Declare it in the shared module exactly like a shared signal:</p>
 *
 * <pre>{@code
 * // shared module
 * public final class BasketSignals {
 *     public static final ScopedSignal<Basket> BASKET =
 *             Signals.scoped("shop.basket", Basket.empty(), Scope.CLIENT);
 * }
 *
 * // server -- name the target explicitly, so who receives it is never accidental:
 * BasketSignals.BASKET.forTarget(RmiRequestContext.getClientId()).set(updated);
 *
 * // client -- indistinguishable from any other signal:
 * Effect.create(() -> badge.setText(BasketSignals.BASKET.mine().get().itemCount() + " items"));
 * }</pre>
 *
 * <p>Which scope to pick is a security decision, and {@link Scope} documents each one. The short
 * version: {@link Scope#CLIENT} needs no login and survives reconnects and reloads, making it the
 * default for an open application; {@link Scope#USER} and {@link Scope#TENANT} require
 * authentication and are the only ones that are a real boundary between people.</p>
 *
 * <h2>Which tier does what</h2>
 * <ul>
 *   <li><b>Server:</b> {@link #forTarget(String)} — every target's value is reachable, which is what
 *       lets a service update one tenant's state. {@link #mine()} throws, because the server has no
 *       single "own" value to return.</li>
 *   <li><b>Client:</b> {@link #mine()} — the connection's own signal, whose target the server
 *       resolves from the authenticated identity or client id. {@link #forTarget(String)} throws,
 *       because a browser naming someone else's target would be asking for data it is not entitled
 *       to.</li>
 *   <li><b>Neither (plain unit tests, no transport installed):</b> both work, over local instances.</li>
 * </ul>
 *
 * @param <T> value type; must be wire-serializable
 */
public final class ScopedSignal<T> {

    private final String name;
    private final T initialValue;
    private final Scope scope;
    private final boolean clientWritable;
    private final Set<String> writeRoles;

    private final Map<String, SharedValueSignal<T>> byTarget = new ConcurrentHashMap<>();

    ScopedSignal(String name, T initialValue, Scope scope, boolean clientWritable,
                 Set<String> writeRoles) {
        this.name = name;
        this.initialValue = initialValue;
        this.scope = scope;
        this.clientWritable = clientWritable;
        this.writeRoles = writeRoles;
    }

    /**
     * The value held for one target — a tenant id, user name, client id or session id, matching this
     * signal's {@link #scope()}.
     *
     * <p>Server-side. Setting it broadcasts to exactly the sessions matching that target and retains
     * the value, so a client connecting later receives it on subscribe.</p>
     *
     * @param target the target to address; must not be null or blank
     * @return the target's signal, created on first use
     * @throws IllegalArgumentException if {@code target} is null or blank
     * @throws IllegalStateException    when called on a client, where addressing another target
     *                                  would be a request for data this browser has no claim to
     */
    public ValueSignal<T> forTarget(String target) {
        if (target == null || target.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "forTarget(...) on scoped signal '" + name + "' needs a " + targetDescription()
                    + ". A null target usually means the connection has none -- an anonymous session "
                    + "has no user or tenant -- in which case there is nothing to address and the "
                    + "update should be skipped.");
        }
        SignalTransport transport = Signals.transport();
        if (transport != null && !transport.resolvesScopeTargets()) {
            throw new IllegalStateException(
                    "Scoped signal '" + name + "': a client cannot address a target directly. Use "
                    + "mine() for this connection's own value; the server decides which target that "
                    + "is from the authenticated identity or client id.");
        }
        return instanceFor(target);
    }

    /**
     * This connection's own signal.
     *
     * <p>Client-side, and deliberately parameterless: the browser never names its own target, the
     * server resolves it from the handshake. That is what stops a client asking for another
     * tenant's value by naming it.</p>
     *
     * <p>Named {@code mine()} rather than {@code get()} because it returns the <em>signal</em>, not
     * the value — {@code BASKET.mine().get()} reads correctly, where {@code BASKET.get().get()} did
     * not.</p>
     *
     * @return the local mirror, usable exactly like any other {@link ValueSignal}
     * @throws IllegalStateException when called on the server, which has no single own value
     */
    public ValueSignal<T> mine() {
        SignalTransport transport = Signals.transport();
        if (transport != null && transport.resolvesScopeTargets()) {
            throw new IllegalStateException(
                    "Scoped signal '" + name + "' holds one value per " + targetDescription()
                    + ", so there is no single value to read on the server. Name the target with "
                    + "forTarget(...) -- for example forTarget(RmiRequestContext."
                    + serverAccessorHint() + ").");
        }
        return Signals.mirrorFor(this);
    }

    /**
     * The name this family travels under, identical for every target — which is what stops a client
     * inferring that other targets exist.
     *
     * @return the base wire name
     */
    public String name() {
        return name;
    }

    /**
     * What this family is keyed by, and therefore who a change reaches.
     *
     * @return the scope chosen at declaration
     */
    public Scope scope() {
        return scope;
    }

    /**
     * Whether this family was declared with {@link Signals#scopedWritable}. A client write still has
     * to pass the role check and the value's validation annotations on the server, so this only says
     * writes are <em>possible</em>, not that any given one will be accepted.
     *
     * @return true when clients may write
     */
    public boolean isClientWritable() {
        return clientWritable;
    }

    /**
     * Which roles a client write requires. An empty set means <b>any</b> session may write,
     * anonymous ones included — that is the default, and rarely what you want for anything but
     * genuinely public state.
     *
     * @return the required write roles, possibly empty
     */
    public Set<String> writeRoles() {
        return writeRoles;
    }

    T initialValue() {
        return initialValue;
    }

    /**
     * Resolves a target's instance without the tier check.
     *
     * <p><b>Framework-internal</b>, like {@link SharedValueSignal#applyRemote(Object)}: it exists for
     * the server transport answering a subscribe or a write on behalf of a session, which has
     * already resolved the target from the handshake. Application code uses {@link #forTarget(String)},
     * whose tier check is the thing keeping a browser from naming someone else's target.</p>
     *
     * @param target the resolved target
     * @return that target's signal, created on first use
     */
    public SharedValueSignal<T> instanceFor(String target) {
        return byTarget.computeIfAbsent(target, key -> new SharedValueSignal<>(
                name + "@" + key, initialValue, clientWritable, writeRoles, name, scope, key));
    }

    /**
     * The targets that currently hold a value. A target absent here has never been written, and a
     * subscriber for it receives the family's initial value.
     *
     * @return the known targets
     */
    public Set<String> knownTargets() {
        return Collections.unmodifiableSet(byTarget.keySet());
    }

    private String targetDescription() {
        switch (scope) {
            case SESSION: return "session id";
            case CLIENT:  return "client id";
            case TENANT:  return "tenant id";
            default:      return "user name";
        }
    }

    private String serverAccessorHint() {
        switch (scope) {
            case SESSION: return "getSessionId()";
            case CLIENT:  return "getClientId()";
            case TENANT:  return "getTenantId()";
            default:      return "getPrincipal().getName()";
        }
    }
}
