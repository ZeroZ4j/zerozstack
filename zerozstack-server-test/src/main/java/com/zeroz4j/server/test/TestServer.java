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
package com.zeroz4j.server.test;

import com.zeroz4j.api.ObjectMapper;
import com.zeroz4j.server.Disclosures;
import com.zeroz4j.server.EventPublisher;
import com.zeroz4j.server.FileUploadRpcImpl;
import com.zeroz4j.server.LiveMutexManager;
import com.zeroz4j.server.LiveMutexRpcImpl;
import com.zeroz4j.server.ObjectMapperProducer;
import com.zeroz4j.server.ServerConfig;
import com.zeroz4j.server.ServerRuntime;
import com.zeroz4j.server.SyncEngine;
import com.zeroz4j.server.WasmRmiServerEngine;
import jakarta.websocket.CloseReason;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;

import java.nio.ByteBuffer;
import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One ZeroZ Stack server, started inside a test, in this process, in about a tenth of a second.
 *
 * <h2>What it is for</h2>
 *
 * <p>Testing an application built on this framework used to mean running one test per process. The
 * framework kept some of its state in fields belonging to the whole Java process, so a second
 * server started beside the first shared the first one's connections. That did not merely give
 * wrong answers, it gave <b>plausible</b> ones: a test would watch a connection, somebody really
 * was writing to it, and the test passed while proving nothing about the server it thought it was
 * testing.</p>
 *
 * <p>A {@code TestServer} is a whole server of its own — its own connections, its own record of
 * what it has sent to whom, its own object locks, and its own settings. Two of them in one test
 * are genuinely two servers.</p>
 *
 * <h2>Using it</h2>
 *
 * <pre>{@code
 * @Test
 * void anEventReachesTheBrowser() {
 *     try (TestServer server = TestServer.builder()
 *              .named("orders")
 *              .beans(OrderServiceImpl.class)
 *              .start();
 *          TestConnection browser = server.connect("alice", "admin")) {
 *
 *         server.bean(OrderService.class).approve(17L);
 *
 *         assertEquals(1, browser.pushCount());
 *     }
 * }
 * }</pre>
 *
 * <p>Settings belong to the server, not to the Java process, so a test can change one without
 * disturbing anything else running at the same time:</p>
 *
 * <pre>{@code
 * TestServer small = TestServer.builder()
 *         .set(ServerSettings.MAX_BINARY_MESSAGE_BYTES, 1024)
 *         .start();
 * }</pre>
 *
 * <h2>It complains rather than passing quietly</h2>
 *
 * <ul>
 *   <li>Driving a server after {@link #close()} throws.</li>
 *   <li>Handing one server's connection to another server throws, naming both.</li>
 *   <li>A connection built by hand, never opened on any server, throws the moment the framework is
 *       asked to write to it.</li>
 * </ul>
 *
 * <p>Close it when the test ends — a try-with-resources is the easiest way — or the bean container
 * behind it stays up.</p>
 *
 * @since 0.8.0
 */
public final class TestServer implements AutoCloseable {

    private static final AtomicInteger SERIAL = new AtomicInteger();

    private final String name;
    private final WeldContainer container;
    private final ServerRuntime runtime;
    private final WasmRmiServerEngine engine;
    private final List<TestConnection> connections = new ArrayList<>();
    private final AtomicInteger connectionSerial = new AtomicInteger();

    private boolean closed;

    private TestServer(String name, WeldContainer container, ServerRuntime runtime,
                       WasmRmiServerEngine engine) {
        this.name = name;
        this.container = container;
        this.runtime = runtime;
        this.engine = engine;
    }

    /**
     * Starts a server with the given application beans and nothing configured.
     *
     * @param beans the application's service implementations, and anything they inject
     * @return the running server
     */
    public static TestServer start(Class<?>... beans) {
        return builder().beans(beans).start();
    }

    /**
     * @return a builder, for a server that needs a name, settings, or a list of beans
     */
    public static Builder builder() {
        return new Builder();
    }

    // ------------------------------------------------------------------ what a test reaches for

    /**
     * @return the name this server was given, or a generated one
     */
    public String name() {
        return name;
    }

    /**
     * Opens a connection with no identity — an anonymous visitor.
     *
     * @return the connection
     */
    public TestConnection connect() {
        return connect(null);
    }

    /**
     * Opens a connection for a signed-in user.
     *
     * @param user  the user name, or null for an anonymous connection
     * @param roles the roles the sign-in granted
     * @return the connection
     */
    public TestConnection connect(String user, String... roles) {
        return connectAsTenant(user, null, roles);
    }

    /**
     * Opens a connection for a signed-in user of one tenant.
     *
     * @param user   the user name, or null for an anonymous connection
     * @param tenant the tenant, or null when the application has none
     * @param roles  the roles the sign-in granted
     * @return the connection
     */
    public TestConnection connectAsTenant(String user, String tenant, String... roles) {
        requireRunning();
        String connectionId = name + "-c" + connectionSerial.incrementAndGet();
        TestConnection connection = new TestConnection(this, connectionId);
        Principal principal = user == null ? null : (Principal) () -> user;
        Set<String> granted = new LinkedHashSet<>();
        if (roles != null) {
            for (String role : roles) {
                if (role != null) {
                    granted.add(role);
                }
            }
        }
        // Every connection carries a browser id, the way a real one does: it is what Scope.CLIENT
        // uses, and what the record of "we sent this browser that object" is kept under.
        String browserId = connectionId + "-browser";
        engine.onOpen(connection,
                connection.openingConfig(principal, granted, tenant, browserId));
        connections.add(connection);
        return connection;
    }

    /**
     * Closes one connection, the way a browser going away does.
     *
     * @param connection the connection to close
     */
    public void closeConnection(TestConnection connection) {
        if (connection.isOpen()) {
            connection.markClosed(new CloseReason(
                    CloseReason.CloseCodes.NORMAL_CLOSURE, "test closed the connection"));
        }
        engine.onClose(connection);
        connections.remove(connection);
    }

    /**
     * One of this server's beans.
     *
     * @param type the class or interface to look up
     * @param <T>  the bean type
     * @return this server's instance — never another server's
     */
    public <T> T bean(Class<T> type) {
        requireRunning();
        return container.select(type).get();
    }

    /**
     * @return this server's event publisher, for {@code publish(...)} from a test
     */
    public EventPublisher events() {
        requireRunning();
        return engine;
    }

    /**
     * @return this server's LiveSync engine, for {@code notifyChanged(...)} from a test
     */
    public SyncEngine sync() {
        return bean(SyncEngine.class);
    }

    /**
     * @return this server's object locks
     */
    public LiveMutexManager locks() {
        return bean(LiveMutexManager.class);
    }

    /**
     * @return this server's object mapper, which is what turns an object into a handle
     */
    public ObjectMapper mapper() {
        return bean(ObjectMapper.class);
    }

    /**
     * @return this server's record of which objects it has sent to which browser
     */
    public Disclosures disclosures() {
        return runtime().disclosures();
    }

    /**
     * @return this server's settings
     */
    public ServerConfig config() {
        return runtime().config();
    }

    /**
     * The object that owns everything this server has: its connections, its settings, its records.
     *
     * @return this server's runtime
     */
    public ServerRuntime runtime() {
        requireRunning();
        return runtime;
    }

    /**
     * @return how many connections are open on this server
     */
    public int connectionCount() {
        return runtime().sessionCount();
    }

    /**
     * @return the WebSocket endpoint, for a test that needs to drive a frame in by hand
     */
    public WasmRmiServerEngine engine() {
        requireRunning();
        return engine;
    }

    /** Shuts the server down. Every connection closes, and driving it afterwards throws. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (TestConnection connection : new ArrayList<>(connections)) {
            try {
                if (connection.isOpen()) {
                    connection.markClosed(new CloseReason(
                            CloseReason.CloseCodes.GOING_AWAY, "the test server is shutting down"));
                }
                engine.onClose(connection);
            } catch (RuntimeException alreadyGone) {
                // A connection the test already closed; nothing left to do for it.
            }
        }
        connections.clear();
        try {
            runtime.shutDown();
        } finally {
            container.shutdown();
        }
    }

    // ------------------------------------------------------------------ internals

    void deliverToServer(TestConnection connection, ByteBuffer frame) {
        requireRunning();
        engine.processIncomingBinaryPayload(frame, connection);
    }

    void awaitDelivered(TestConnection connection) {
        ServerRuntime.awaitWritesDelivered(connection);
    }

    private void requireRunning() {
        if (closed) {
            throw new IllegalStateException(
                    "Test server '" + name + "' has been closed, so it cannot be driven any more. "
                    + "A test that keeps working a closed server proves nothing about it.");
        }
    }

    /**
     * Collects what a server needs before it starts.
     *
     * <p>Settings have to be fixed before the first connection, so they are given here rather than
     * afterwards.</p>
     */
    public static final class Builder {

        private final List<Class<?>> beans = new ArrayList<>();
        private ServerConfig.Builder settings = ServerConfig.builder();
        private String name;

        private Builder() {
        }

        /**
         * Names the server, so a message about two of them says which is which.
         *
         * @param serverName a short name — the feature under test reads well
         * @return this builder
         */
        public Builder named(String serverName) {
            this.name = serverName;
            return this;
        }

        /**
         * Adds the application's own beans: service implementations, and anything they inject.
         *
         * <p>Discovery is off, so nothing is found by scanning. That is deliberate: a test says
         * exactly which beans its server has, and a bean it did not name cannot appear by
         * accident.</p>
         *
         * @param types the bean classes
         * @return this builder
         */
        public Builder beans(Class<?>... types) {
            if (types != null) {
                for (Class<?> type : types) {
                    if (type != null) {
                        beans.add(type);
                    }
                }
            }
            return this;
        }

        /**
         * Sets one setting for this server only. Names come from
         * {@code com.zeroz4j.server.ServerSettings}.
         *
         * @param setting the setting name
         * @param value   the value
         * @return this builder
         */
        public Builder set(String setting, String value) {
            settings.set(setting, value);
            return this;
        }

        /**
         * Sets one numeric setting for this server only.
         *
         * @param setting the setting name
         * @param value   the value
         * @return this builder
         */
        public Builder set(String setting, long value) {
            settings.set(setting, value);
            return this;
        }

        /**
         * Sets one yes-or-no setting for this server only.
         *
         * @param setting the setting name
         * @param value   the value
         * @return this builder
         */
        public Builder set(String setting, boolean value) {
            settings.set(setting, value);
            return this;
        }

        /**
         * Makes this server ignore the Java process's own settings entirely.
         *
         * <p>Worth doing whenever a test asserts on a limit: the result then depends only on what
         * the test set, and does not change because the build was run with a different {@code -D}
         * flag. Call it before {@link #set(String, String)}.</p>
         *
         * @return this builder
         */
        public Builder ignoringSystemProperties() {
            settings = settings.build().toIsolatedBuilder();
            return this;
        }

        /**
         * Gives the server settings built elsewhere, replacing anything set here.
         *
         * @param config the settings
         * @return this builder
         */
        public Builder settings(ServerConfig config) {
            this.settings = config.toBuilder();
            return this;
        }

        /**
         * Starts the server.
         *
         * @return the running server
         */
        public TestServer start() {
            String serverName = name != null && !name.trim().isEmpty()
                    ? name.trim() : "test-server-" + SERIAL.incrementAndGet();

            List<Class<?>> all = new ArrayList<>();
            all.add(ServerRuntime.class);
            all.add(WasmRmiServerEngine.class);
            all.add(SyncEngine.class);
            all.add(ObjectMapperProducer.class);
            all.add(LiveMutexManager.class);
            all.add(LiveMutexRpcImpl.class);
            all.add(FileUploadRpcImpl.class);
            all.addAll(beans);

            Weld weld = new Weld(serverName)
                    .disableDiscovery()
                    .addBeanClasses(all.toArray(new Class<?>[0]));
            WeldContainer container = weld.initialize();

            // The runtime first, and configured before anything else touches it: limits are read as
            // connections open, so a server that changed its settings halfway through would be two
            // different servers. The engine is resolved afterwards, which is when its start-up scan
            // runs and the settings become fixed.
            ServerRuntime runtime = container.select(ServerRuntime.class).get();
            runtime.name(serverName);
            runtime.configure(settings.build());

            WasmRmiServerEngine engine = container.select(WasmRmiServerEngine.class).get();
            // Touching the bean is what forces CDI to build it, which runs its start-up scan.
            engine.toString();

            return new TestServer(serverName, container, runtime, engine);
        }
    }
}
