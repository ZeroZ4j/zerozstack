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

import com.zeroz4j.api.ObjectMapper;
import com.zeroz4j.api.Scope;
import com.zeroz4j.signals.ScopedSignal;
import com.zeroz4j.signals.SharedValueSignal;
import com.zeroz4j.signals.SignalTransport;
import com.zeroz4j.signals.Signals;
import jakarta.websocket.Session;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Server-side {@link SignalTransport}: makes shared signals authoritative on this tier.
 *
 * <p>A {@code set()} on a shared signal broadcasts the new value to all connected sessions
 * (0x05 SIGNAL_UPDATE frames); the signal itself retains the latest value, which is
 * delivered directly to any session that subscribes — late joiners are always current.
 * Subscriptions for names not yet declared in this runtime are parked and flushed the
 * moment the declaring class loads.</p>
 *
 * <h2>One value, several servers (0.8.0)</h2>
 *
 * <p>A shared signal is declared as a {@code static final} field of an application class, so its
 * <b>value</b> is one per Java process by definition — that is what {@code Signals.shared} has
 * always meant. What used to be shared as well, and should not have been, was the delivery: the
 * first server to start installed itself as the one transport, so a second server's connections
 * were written to with the first server's object mapper, or not written to at all.</p>
 *
 * <p>Delivery is now per server. Every running server registers itself here, a value change is
 * delivered to each server's own connections with that server's own mapper, and a subscription is
 * answered by the server the asking connection belongs to. The value itself is still one per
 * process; the testing guide says what that means for a test.</p>
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>Installation:</b> {@link WasmRmiServerEngine} installs this transport during
 *       startup; applications never touch it.</li>
 *   <li><b>Authority:</b> {@link #canSet} always returns true on the server — the server
 *       owns shared state in this release.</li>
 * </ul>
 */
public final class ServerSignalTransport implements SignalTransport {

    private static final Logger LOG = Logger.getLogger(ServerSignalTransport.class.getName());

    /**
     * Every running server, by runtime id.
     *
     * <p>The signals library takes exactly one transport for the whole process, so this map is what
     * turns that one transport into "each server, separately". Keyed by runtime id rather than kept
     * in a list, so a server restarting in the same process replaces its own entry instead of
     * adding a second one.</p>
     */
    private static final Map<String, ServerSignalTransport> BY_RUNTIME = new ConcurrentHashMap<>();

    /** The single object the signals library holds; it hands every call to each running server. */
    private static final SignalTransport FANOUT = new Fanout();

    private final ServerRuntime runtime;
    private final ObjectMapper mapper;
    /** Signal name -> sessions waiting for a signal that has not been declared yet. */
    private final Map<String, Set<Session>> pendingSubscriptions = new ConcurrentHashMap<>();

    private ServerSignalTransport(ServerRuntime runtime, ObjectMapper mapper) {
        this.runtime = runtime;
        this.mapper = mapper;
    }

    /**
     * Registers one server with the signals library. Called by that server's engine at startup, and
     * safe to call again.
     *
     * @param runtime the server being started
     * @param mapper  that server's object mapper, used for value serialization
     */
    public static synchronized void install(ServerRuntime runtime, ObjectMapper mapper) {
        BY_RUNTIME.put(runtime.id(), new ServerSignalTransport(runtime, mapper));
        // Installed every time rather than once: a test that resets the signals library clears the
        // transport, and a server started after that must still be able to deliver.
        Signals.installTransport(FANOUT);
    }

    /**
     * Stops delivering shared-signal updates to one server. Called when it shuts down.
     *
     * @param runtime the server that is going away
     */
    public static void uninstall(ServerRuntime runtime) {
        if (runtime != null) {
            BY_RUNTIME.remove(runtime.id());
        }
    }

    /**
     * The transport for the server a connection belongs to.
     *
     * @param session the connection
     * @return that server's transport, or null when the connection belongs to no running server
     */
    private static ServerSignalTransport forSession(Session session) {
        ServerRuntime runtime = ServerRuntime.ofOrNull(session);
        return runtime == null ? null : BY_RUNTIME.get(runtime.id());
    }

    /**
     * Handles a client's subscribe request: sends the retained value if the signal is
     * declared, otherwise parks the session until it is.
     *
     * @param signalName wire name of the requested signal
     * @param session    requesting session
     */
    static void handleSubscribe(String signalName, Session session) {
        ServerSignalTransport transport = forSession(session);
        if (transport == null) {
            return;
        }
        SharedValueSignal<?> signal = Signals.lookup(signalName);
        if (signal != null) {
            WasmRmiServerEngine.sendSignalUpdate(session, signalName, signal.get(), transport.mapper);
            return;
        }
        ScopedSignal<?> family = Signals.lookupScoped(signalName);
        if (family != null) {
            // The client asked for "the basket"; which basket is this server's decision, taken from
            // the handshake. The wire name stays the family's, so the client never learns its target
            // and cannot ask for another.
            String target = targetFor(session, family.scope());
            if (target == null) {
                // No tenant on an anonymous session, no user when not logged in. Sending the initial
                // value would be a guess, and there is no other value that belongs here. Send nothing.
                LOG.fine("[zeroz4j] Session " + session.getId() + " subscribed to scoped signal '"
                        + signalName + "' but has no " + family.scope() + " target; nothing sent.");
                return;
            }
            WasmRmiServerEngine.sendSignalUpdate(session, signalName,
                    family.instanceFor(target).get(), transport.mapper);
            return;
        }
        transport.pendingSubscriptions
                .computeIfAbsent(signalName, k -> ConcurrentHashMap.newKeySet())
                .add(session);
    }

    /**
     * Resolves which target of a scoped family a session belongs to.
     *
     * <p>Read from the handshake, never from the frame: a client that could name its own target
     * could name anyone's.</p>
     *
     * @return the target, or null when this session has none — anonymous sessions have no user and
     *         no tenant, and must not be quietly folded into somebody else's value
     */
    static String targetFor(Session session, Scope scope) {
        switch (scope) {
            case SESSION:
                return session.getId();
            case CLIENT:
                return (String) session.getUserProperties().get(RmiEndpointConfigurator.CLIENT_KEY);
            case TENANT:
                return (String) session.getUserProperties().get(RmiEndpointConfigurator.TENANT_KEY);
            case USER:
                java.security.Principal principal = (java.security.Principal)
                        session.getUserProperties().get(RmiEndpointConfigurator.PRINCIPAL_KEY);
                return principal != null ? principal.getName() : null;
            default:
                return null;
        }
    }

    /**
     * Handles a client's write to a shared signal. The server stays authoritative:
     * the write is accepted only if the signal is declared client-writable, the session
     * holds a required write role (when roles are declared), and the value passes its
     * model validation annotations. Accepted writes broadcast to all sessions — the
     * writer's echo confirms its optimistic update. Rejected writes answer the writer
     * with the current retained value, snapping its mirror back to server truth.
     *
     * @param signalName wire name of the signal being written
     * @param newValue   proposed value
     * @param session    writing session
     */
    @SuppressWarnings("unchecked")
    static void handleClientSet(String signalName, Object newValue, Session session) {
        ServerSignalTransport transport = forSession(session);
        if (transport == null) {
            return;
        }
        SharedValueSignal<?> signal = Signals.lookup(signalName);
        boolean clientWritable;
        Set<String> writeRoles;

        if (signal != null) {
            clientWritable = signal.isClientWritable();
            writeRoles = signal.writeRoles();
        } else {
            ScopedSignal<?> family = Signals.lookupScoped(signalName);
            if (family == null) {
                return;
            }
            String target = targetFor(session, family.scope());
            if (target == null) {
                LOG.fine("[zeroz4j] Session " + session.getId() + " wrote scoped signal '"
                        + signalName + "' but has no " + family.scope() + " target; write dropped.");
                return;
            }
            // The target comes from the handshake, so a client writes only ever to its own value.
            signal = family.instanceFor(target);
            clientWritable = family.isClientWritable();
            writeRoles = family.writeRoles();
        }

        boolean allowed = clientWritable;
        if (allowed && !writeRoles.isEmpty()) {
            Set<String> userRoles = (Set<String>) session.getUserProperties().get(RmiEndpointConfigurator.ROLES_KEY);
            boolean hasRole = false;
            if (userRoles != null) {
                for (String required : writeRoles) {
                    if (userRoles.contains(required)) {
                        hasRole = true;
                        break;
                    }
                }
            }
            allowed = hasRole;
        }
        java.util.List<String> violations = allowed
                ? com.zeroz4j.api.validation.ValidationRegistry.validate(newValue)
                : java.util.Collections.emptyList();

        if (allowed && violations.isEmpty()) {
            // Server-side set: broadcasts to the sessions in scope, including the writer's echo.
            ((SharedValueSignal<Object>) signal).set(newValue);
        } else {
            // Corrective update: revert the writer's optimistic apply to server truth. Addressed by
            // the name the client knows, which for a scoped signal is the family's, not the instance's.
            WasmRmiServerEngine.sendSignalUpdate(session, signalName, signal.get(), transport.mapper);
        }
    }

    /**
     * Drops a closed session from any parked subscriptions.
     *
     * @param session the closed session
     */
    static void sessionClosed(Session session) {
        ServerSignalTransport transport = forSession(session);
        if (transport == null) {
            return;
        }
        for (Set<Session> sessions : transport.pendingSubscriptions.values()) {
            sessions.remove(session);
        }
    }

    @Override
    public void onSharedSignalCreated(SharedValueSignal<?> signal) {
        Set<Session> parked = pendingSubscriptions.remove(signal.name());
        if (parked != null) {
            for (Session session : parked) {
                WasmRmiServerEngine.sendSignalUpdate(session, signal.name(), signal.get(), mapper);
            }
        }
    }

    @Override
    public void onScopedFamilyCreated(ScopedSignal<?> family) {
        Set<Session> parked = pendingSubscriptions.remove(family.name());
        if (parked == null) {
            return;
        }
        for (Session session : parked) {
            String target = targetFor(session, family.scope());
            if (target != null) {
                WasmRmiServerEngine.sendSignalUpdate(session, family.name(),
                        family.instanceFor(target).get(), mapper);
            }
        }
    }

    @Override
    public boolean canSet(SharedValueSignal<?> signal) {
        return true;
    }

    @Override
    public boolean resolvesScopeTargets() {
        return true;
    }

    @Override
    public void afterSet(SharedValueSignal<?> signal, Object newValue) {
        if (signal.isScoped()) {
            // Addressed by the family's name, delivered only to the sessions matching this target.
            WasmRmiServerEngine.broadcastSignalUpdateScoped(runtime, signal.scopeFamily(), newValue,
                    signal.scope(), signal.scopeTarget(), mapper);
            return;
        }
        WasmRmiServerEngine.broadcastSignalUpdate(runtime, signal.name(), newValue, mapper);
    }

    /**
     * The one transport the signals library holds, which hands every call to each running server.
     *
     * <p>A shared signal's value is one per process, so a change is news for every server in it.
     * Each server delivers it to its own connections, with its own mapper.</p>
     */
    private static final class Fanout implements SignalTransport {

        @Override
        public void onSharedSignalCreated(SharedValueSignal<?> signal) {
            for (ServerSignalTransport transport : BY_RUNTIME.values()) {
                transport.onSharedSignalCreated(signal);
            }
        }

        @Override
        public void onScopedFamilyCreated(ScopedSignal<?> family) {
            for (ServerSignalTransport transport : BY_RUNTIME.values()) {
                transport.onScopedFamilyCreated(family);
            }
        }

        @Override
        public boolean canSet(SharedValueSignal<?> signal) {
            return true;
        }

        @Override
        public boolean resolvesScopeTargets() {
            return true;
        }

        @Override
        public void afterSet(SharedValueSignal<?> signal, Object newValue) {
            for (ServerSignalTransport transport : BY_RUNTIME.values()) {
                transport.afterSet(signal, newValue);
            }
        }
    }
}
