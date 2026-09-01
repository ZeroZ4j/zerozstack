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
import jakarta.enterprise.inject.spi.CDI;
import jakarta.websocket.Session;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Everything one running server owns.
 *
 * <h2>The problem this solves</h2>
 *
 * <p>Until 0.8.0 the framework kept several pieces of its state in {@code static} fields — the list
 * of open connections above all. A {@code static} field belongs to the whole Java process, not to a
 * server, so <b>two servers started in one process shared it</b>. An application that tried to run
 * two of its tests in the same process found that the second server's broadcasts went to the first
 * server's connections. Worse than being wrong, it looked right: the tests passed, because they were
 * watching a connection that somebody was writing to. They were simply not watching the server they
 * thought they were.</p>
 *
 * <p>All of that state now lives here, on an object one server owns, so a second server has its
 * own.</p>
 *
 * <h2>How the connection finds it</h2>
 *
 * <p>The WebSocket endpoint is a problem of its own: a servlet container is allowed to build a new
 * endpoint object <b>for every connection</b>, so the state cannot simply be a field on the
 * endpoint. Three things together make sure every connection reaches the right runtime:</p>
 *
 * <ol>
 *   <li>{@link RmiEndpointConfigurator#getEndpointInstance(Class)} hands the container the CDI
 *       bean, so the endpoint <em>is</em> the one application-scoped engine, and the runtime is
 *       simply injected into it. This is the path every supported binding takes.</li>
 *   <li>If a container builds the endpoint itself anyway, the engine asks CDI for the runtime once
 *       and remembers it.</li>
 *   <li>When a connection opens, the runtime writes itself into that connection's own properties.
 *       Any code holding only a connection — the outbound writer, the signal transport — then finds
 *       the right runtime with {@link #of(Session)}, with no static field anywhere.</li>
 * </ol>
 *
 * <h2>It says so when something is wrong</h2>
 *
 * <p>The worst part of the original fault was not the shared state, it was that nothing complained.
 * So the answers here are loud:</p>
 *
 * <ul>
 *   <li>{@link #of(Session)} throws when a connection was never opened on a running server, instead
 *       of answering "nothing to do".</li>
 *   <li>{@link #attach(Session)} throws when a connection already belongs to a different server,
 *       which is exactly what "the test drove the wrong instance" looks like.</li>
 *   <li>Every entry point throws once the server has been shut down, so a test that keeps driving a
 *       closed server fails instead of quietly asserting nothing.</li>
 * </ul>
 *
 * @since 0.8.0
 */
@ApplicationScoped
public class ServerRuntime {

    private static final Logger LOG = Logger.getLogger(ServerRuntime.class.getName());

    /** Where a connection carries the runtime it was opened on. */
    static final String RUNTIME_KEY = "zeroz.runtime";

    private static final AtomicInteger SERIAL = new AtomicInteger();

    /** Distinguishes two runtimes in a message, and survives CDI's proxying. */
    private final String id = "server-" + SERIAL.incrementAndGet();

    private volatile String name = id;

    private volatile ServerConfig config = ServerConfig.fromSystemProperties();

    /** True once configuration is fixed: the first connection, or the engine's startup scan. */
    private final AtomicBoolean inUse = new AtomicBoolean();

    private final AtomicBoolean shutDown = new AtomicBoolean();

    /** Said once per server rather than once per connection. */
    private final AtomicBoolean limitsReported = new AtomicBoolean();

    /**
     * Every open connection on this server.
     *
     * <p>This is the field the whole class exists for. It used to be {@code static} because a
     * servlet container builds the endpoint per connection while pushes come from the
     * application-scoped bean — a per-endpoint set left every broadcast talking to an empty list.
     * That constraint has not gone away; it is satisfied by the endpoint being the CDI bean and by
     * every connection carrying a reference to this object, rather than by sharing one set across
     * the whole process.</p>
     */
    private final Set<Session> activeSessions = ConcurrentHashMap.newKeySet();

    /** Connection id to when it was last answered a keepalive ping. */
    private final java.util.Map<String, Long> keepaliveAnswered = new ConcurrentHashMap<>();

    private final Disclosures disclosures = new Disclosures(this);

    private final LazyHandles.Registry lazyHandles = new LazyHandles.Registry();

    /** Roles this server's services ask about, collected from its own beans at startup. */
    private final Set<String> knownRoles = ConcurrentHashMap.newKeySet();

    // ------------------------------------------------------------------ identity

    /**
     * @return a stable id for this server, unique within the process
     */
    public String id() {
        return id;
    }

    /**
     * @return the name used in log lines and error messages; the id unless one was given
     */
    public String name() {
        return name;
    }

    /**
     * Names this server, so a message about two of them says which is which.
     *
     * @param newName a short name, for example the test that started it
     */
    public void name(String newName) {
        this.name = newName == null || newName.trim().isEmpty() ? id : newName.trim();
    }

    // ------------------------------------------------------------------ settings

    /**
     * @return this server's settings; the system properties unless it was given others
     */
    public ServerConfig config() {
        return config;
    }

    /**
     * Gives this server its own settings.
     *
     * <p>Must be done before the server is used, because limits are read as connections open and a
     * server that changed its mind halfway through would be two different servers. Calling it later
     * fails rather than half-applying.</p>
     *
     * @param newConfig the settings
     * @throws IllegalStateException when the server has already started serving
     */
    public void configure(ServerConfig newConfig) {
        if (newConfig == null) {
            throw new IllegalArgumentException("configure(null): pass ServerConfig.fromSystemProperties() instead");
        }
        if (inUse.get()) {
            throw new IllegalStateException(
                    "Server '" + name + "' is already running, so its settings cannot be changed now. "
                    + "Configure it before the first connection is opened.");
        }
        this.config = newConfig;
    }

    // ------------------------------------------------------------------ lifecycle

    /** Marks the server as serving, which fixes its settings. Called by the engine at startup. */
    void markInUse() {
        inUse.set(true);
    }

    /**
     * @return whether this server has been shut down
     */
    public boolean isShutDown() {
        return shutDown.get();
    }

    /**
     * Shuts the server's own state down. Called when the CDI container stops, and by the test
     * harness.
     *
     * <p>Afterwards every entry point throws, so nothing can go on driving a server that is no
     * longer there and get a plausible-looking silence back.</p>
     */
    public void shutDown() {
        if (shutDown.compareAndSet(false, true)) {
            ServerSignalTransport.uninstall(this);
            for (Session session : activeSessions) {
                session.getUserProperties().remove(RUNTIME_KEY);
            }
            activeSessions.clear();
            keepaliveAnswered.clear();
            disclosures.clear();
            lazyHandles.clear();
            knownRoles.clear();
        }
    }

    /** Throws if this server has been shut down. */
    void requireRunning(String what) {
        if (shutDown.get()) {
            throw new IllegalStateException(
                    "Server '" + name + "' has been shut down, so it cannot " + what + ". "
                    + "A test that keeps driving a closed server proves nothing.");
        }
    }

    // ------------------------------------------------------------------ connections

    /**
     * Takes ownership of a newly opened connection.
     *
     * @param session the connection
     * @throws IllegalStateException when it already belongs to a different server
     */
    void attach(Session session) {
        requireRunning("accept a connection");
        markInUse();
        Object existing = session.getUserProperties().get(RUNTIME_KEY);
        if (existing instanceof ServerRuntime other && !id.equals(other.id())) {
            throw new IllegalStateException(
                    "Connection " + session.getId() + " was opened on server '" + other.name()
                    + "' and cannot also be opened on server '" + name + "'. Each server needs its "
                    + "own connections; driving one server's connection from another is what makes "
                    + "a test watch the wrong instance.");
        }
        session.getUserProperties().put(RUNTIME_KEY, this);
        activeSessions.add(session);
    }

    /**
     * Gives up a closed connection.
     *
     * @param session the connection
     */
    void detach(Session session) {
        activeSessions.remove(session);
        keepaliveAnswered.remove(session.getId());
        lazyHandles.sessionClosed(session.getId());
        session.getUserProperties().remove(RUNTIME_KEY);
    }

    /** Drops a connection from the active list without the rest of the close-down. */
    void forget(Session session) {
        activeSessions.remove(session);
    }

    /**
     * Every connection open on this server, in no particular order.
     *
     * @return the live set; iterating it is safe while connections come and go
     */
    public Iterable<Session> sessions() {
        return activeSessions;
    }

    /**
     * @return how many connections are open on this server
     */
    public int sessionCount() {
        return activeSessions.size();
    }

    /**
     * @param session a connection
     * @return whether this server has that connection open
     */
    public boolean hasSession(Session session) {
        return activeSessions.contains(session);
    }

    /** Test support: registers a connection without running the whole open sequence. */
    void addSessionForTesting(Session session) {
        attach(session);
    }

    // ------------------------------------------------------------------ finding the runtime

    /**
     * The server a connection belongs to.
     *
     * @param session the connection
     * @return the server that opened it, never null
     * @throws IllegalStateException when the connection was never opened on a running server
     */
    public static ServerRuntime of(Session session) {
        ServerRuntime runtime = ofOrNull(session);
        if (runtime == null) {
            throw new IllegalStateException(
                    "This connection does not belong to any running ZeroZ Stack server"
                    + (session == null ? "" : " (connection " + session.getId() + ")")
                    + ". Either the server was never started, or it has already been shut down, or "
                    + "the connection was built by hand and never opened. Nothing was sent.");
        }
        return runtime;
    }

    /**
     * The server a connection belongs to, or null.
     *
     * @param session the connection, which may be null
     * @return the server that opened it, or null when there is none
     */
    public static ServerRuntime ofOrNull(Session session) {
        if (session == null) {
            return null;
        }
        java.util.Map<String, Object> properties;
        try {
            properties = session.getUserProperties();
        } catch (RuntimeException closed) {
            return null;
        }
        Object stored = properties == null ? null : properties.get(RUNTIME_KEY);
        return stored instanceof ServerRuntime runtime ? runtime : null;
    }

    /**
     * The server this process's CDI container holds, for code the container built itself.
     *
     * @return the runtime bean, or null when there is no reachable CDI container
     */
    static ServerRuntime fromCdi() {
        try {
            jakarta.enterprise.inject.Instance<ServerRuntime> resolvable =
                    CDI.current().select(ServerRuntime.class);
            if (!resolvable.isUnsatisfied() && !resolvable.isAmbiguous()) {
                return resolvable.get();
            }
        } catch (RuntimeException | LinkageError noContainer) {
            LOG.fine("[zeroz4j] No CDI container to take the server runtime from: " + noContainer);
        }
        return null;
    }

    /**
     * Waits until everything queued for a connection has actually been written to it.
     *
     * <p>Test support. Writing leaves the calling thread — each connection has a queue and one
     * writer thread — so a test that asserted on what a connection received straight after making a
     * call would race that thread and see nothing. Production code never needs this.</p>
     *
     * @param session the connection to wait for
     */
    public static void awaitWritesDelivered(Session session) {
        WsWrites.awaitQuiet(session);
    }

    /**
     * The settings of the server a connection belongs to, falling back to the system properties.
     *
     * <p>Used by the outbound writer, which is handed a connection and nothing else, and must keep
     * working for a connection that has already been detached.</p>
     *
     * @param session the connection
     * @return that server's settings, or the system properties
     */
    static ServerConfig configFor(Session session) {
        ServerRuntime runtime = ofOrNull(session);
        return runtime != null ? runtime.config() : ServerConfig.fromSystemProperties();
    }

    // ------------------------------------------------------------------ owned state

    /**
     * What this server has sent to which browser.
     *
     * @return this server's record, never shared with another server
     */
    public Disclosures disclosures() {
        return disclosures;
    }

    /** @return this server's lazy-reference handles */
    LazyHandles.Registry lazyHandles() {
        return lazyHandles;
    }

    /** @return the roles this server's own services ask about */
    Set<String> knownRoles() {
        return knownRoles;
    }

    /** @return an unmodifiable copy of the known roles, for a caller outside the package */
    public Set<String> knownRoleNames() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(knownRoles));
    }

    /**
     * Answers whether a connection may be told the time again, and records that it was.
     *
     * @param sessionId       the connection
     * @param minIntervalMillis the shortest gap between two answers
     * @return true when the ping should be answered
     */
    boolean keepaliveAllowed(String sessionId, long minIntervalMillis) {
        long now = System.currentTimeMillis();
        Long previous = keepaliveAnswered.get(sessionId);
        if (previous != null && now - previous < minIntervalMillis) {
            return false;
        }
        keepaliveAnswered.put(sessionId, now);
        return true;
    }

    /** Test support: forgets every connection's keepalive budget. */
    void clearKeepaliveBudget() {
        keepaliveAnswered.clear();
    }

    /** @return whether this is the first time the limits are being reported for this server */
    boolean reportLimitsOnce() {
        return limitsReported.compareAndSet(false, true);
    }

    @Override
    public String toString() {
        return "ServerRuntime[" + name + ", " + activeSessions.size() + " connection(s)"
                + (shutDown.get() ? ", shut down" : "") + "]";
    }
}
