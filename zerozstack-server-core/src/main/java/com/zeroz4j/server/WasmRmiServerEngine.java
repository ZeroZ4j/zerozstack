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

import com.zeroz4j.api.BinarySerializer;
import com.zeroz4j.api.DataModel;
import com.zeroz4j.api.GrowableBuffer;
import com.zeroz4j.api.ObjectMapper;
import com.zeroz4j.api.Scope;
import com.zeroz4j.api.SyncFrameTypes;
import com.zeroz4j.api.ClientWritable;
import com.zeroz4j.api.EventTopic;
import com.zeroz4j.api.RmiService;
import com.zeroz4j.api.i18n.FrameworkText;
import com.zeroz4j.api.i18n.Message;
import com.zeroz4j.api.validation.ValidationRegistry;
import com.zeroz4j.api.RolesAllowed;
import com.zeroz4j.api.Secured;
import jakarta.annotation.PostConstruct;
import java.util.logging.Logger;
import java.util.logging.Level;
import jakarta.inject.Inject;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.security.Principal;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.Arrays;
import java.util.HashSet;

/**
 * Server-side Jakarta EE WebSocket endpoint for zeroz4j.
 * Listens on '/wasm-rmi' and uses Project Loom's Virtual Threads to dispatch
 * binary RPC method invocations to CDI beans.
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>CDI Discovery &amp; Scanning:</b> At startup ({@link #scanServiceRegistry()}), scans the CDI bean manager for beans implementing {@link RmiService}
 *       interfaces, builds service/method reflection registries, and populates security whitelists ({@code @Secured}, {@code @RolesAllowed}).</li>
 *   <li><b>Frame Ordering:</b> Per-session {@link SessionFrameQueue} handles one connection's frames one at a time, in the order the transport delivered them, on threads from {@link SessionThreads}. Different connections stay fully concurrent.</li>
 *   <li><b>Frame Dispatch:</b> Operates on incoming binary frames in {@link #processIncomingBinaryPayload(ByteBuffer, Session)}. Reads correlation ID, interface name, method name, unmarshals arguments using {@link BinarySerializer}, enforces security, populates {@link RmiRequestContext}, invokes method via reflection, and writes return value (0x01 SUCCESS) or exception (0x0F ERROR).</li>
 * </ul>
 */
@ServerEndpoint(value = "/wasm-rmi", configurator = RmiEndpointConfigurator.class)
@ApplicationScoped
public class WasmRmiServerEngine implements EventPublisher {

    private static final Logger LOG = Logger.getLogger(WasmRmiServerEngine.class.getName());

    static {
        // Teach Message.text() where words come from on a server, and whose language to use.
        // Here because this class is loaded before any connection exists, and a service method may
        // render a message the moment the first call arrives.
        ServerMessages.install();
    }

    /**
     * AUTH frame layout version. Version 2 added the explicit {@code authenticated} flag; version 1
     * carried only a name and roles, which could not distinguish a refused connection from a real
     * sign-in with no roles.
     */
    static final byte AUTH_PROTOCOL_VERSION = 2;

    /** The name reported for a connection with no accepted identity. */
    static final String ANONYMOUS_USER = "anonymous";

    /**
     * User-property key naming <em>which</em> handshake check refused a connection.
     *
     * <p>Set by the handshake configurator alongside
     * {@link RmiEndpointConfigurator#REJECTED_KEY}, to one of {@link #REFUSED_BY_ORIGIN} or
     * {@link #REFUSED_BY_HOST}. Absent means the configurator did not say, and the connection is
     * closed with a reason naming both checks.</p>
     */
    static final String REFUSED_BY_KEY = "zeroz.rejectedCheck";

    /** The page that opened the connection is not an allowed origin. */
    static final String REFUSED_BY_ORIGIN = "origin";

    /** This deployment does not answer for the host name the connection asked for. */
    static final String REFUSED_BY_HOST = "host";

    /**
     * What a browser is told when the origin check refused it.
     *
     * <p>Under the 123-byte limit a close reason has, and free of anything about the deployment: it
     * names the check and the setting to look at, and nothing else. A developer meets this message
     * with no stack trace to help them, so it has to point somewhere.</p>
     */
    static final String ORIGIN_REFUSED_REASON =
            "Refused: this page's origin is not allowed. Check zeroz.origins on the server.";

    /** What a browser is told when the host-name check refused it. */
    static final String HOST_REFUSED_REASON =
            "Refused: this server does not answer for that host name. See the server log.";

    /** What a browser is told when the configurator did not say which check refused it. */
    static final String HANDSHAKE_REFUSED_REASON =
            "Refused: the origin or host name of this connection is not allowed. See the server log.";

    /**
     * Largest binary message this endpoint will accept; unset applies
     * {@link #DEFAULT_MAX_BINARY_BYTES}.
     */
    static final String MAX_BINARY_BYTES_PROPERTY = "zeroz.ws.maxBinaryMessageBytes";
    /**
     * Message size accepted when {@link #MAX_BINARY_BYTES_PROPERTY} is not set: 4 MB, the same
     * default gRPC uses.
     *
     * <p>There is a default at all because the container's own is not a safe one. The framework
     * uses the Jakarta WebSocket API, which Helidon 4.0.8 implements by embedding Tyrus 2.1.5;
     * Tyrus initialises a session's binary message limit from
     * {@code TyrusServerContainer.getDefaultMaxBinaryMessageBufferSize()}, whose field default is
     * {@code Integer.MAX_VALUE}, and Helidon never sets it. A client sending a fragmented message
     * could therefore make the server assemble roughly 2 GB before any framework code ran.</p>
     */
    static final int DEFAULT_MAX_BINARY_BYTES = 4 * 1024 * 1024;
    /** How long a silent connection is held; unset leaves the container's own timeout. */
    static final String IDLE_TIMEOUT_MINUTES_PROPERTY = "zeroz.ws.idleTimeoutMinutes";

    /**
     * Everything this server owns: its open connections, what it has sent to whom, its settings.
     *
     * <p>Injected, which is what makes it <em>this</em> server's rather than the process's. See
     * {@link #runtime()} for what happens when a container builds this endpoint itself instead of
     * asking CDI for it.</p>
     */
    @Inject
    ServerRuntime injectedRuntime;

    /** Resolved from CDI once when {@link #injectedRuntime} is null. */
    private volatile ServerRuntime resolvedRuntime;

    /** This server's own bean container, so a second server in the same process is not consulted. */
    @Inject
    BeanManager beanManager;

    /** Every bean of this server, for the look-ups that were {@code CDI.current()} before 0.8.0. */
    @Inject
    @Any
    Instance<Object> beans;

    /** Fired when a connection closes, into this server's container only. */
    @Inject
    Event<SessionClosedEvent> sessionClosed;

    @Inject
    SyncEngine syncEngine;

    @Inject
    ObjectMapper mapper;

    @Inject
    LiveMutexManager liveMutexManager;

    /*
     * The set of open connections used to live here, in a static field, with a comment explaining
     * that it had to be static: a servlet container instantiates a @ServerEndpoint per connection
     * while pushers obtain the CDI @ApplicationScoped instance, so a per-endpoint set left every
     * broadcast talking to an empty list.
     *
     * That constraint is real and is still met. What has changed is where the set lives. It belongs
     * to ServerRuntime -- one application-scoped object per server -- and this endpoint reaches it
     * three ways, in order: it IS the application-scoped bean, because
     * RmiEndpointConfigurator.getEndpointInstance hands the container the CDI bean; failing that it
     * asks CDI once and remembers the answer; and every connection carries a reference to its own
     * runtime, so code holding only a connection finds the right one. What has gone is the sharing:
     * two servers in one process no longer broadcast into each other's connections.
     */
    /** Interface FQCN -> CDI bean instance */
    private final Map<String, Object> serviceRegistry = new ConcurrentHashMap<>();
    /** Interface FQCN -> { methodName -> Method } */
    private final Map<String, Map<String, Method>> methodRegistry = new ConcurrentHashMap<>();
    /** Interface FQCN -> whether the interface has @Secured */
    private final Map<String, Boolean> securedInterfaces = new ConcurrentHashMap<>();
    /** Interface FQCN -> interface-level @RolesAllowed roles (empty if not set) */
    private final Map<String, Set<String>> interfaceRoles = new ConcurrentHashMap<>();
    /** "interfaceFQCN#methodName" -> method-level @RolesAllowed roles */
    private final Map<String, Set<String>> methodRoles = new ConcurrentHashMap<>();
    /** "interfaceFQCN#methodName" -> whether method has @Secured */
    private final Map<String, Boolean> securedMethods = new ConcurrentHashMap<>();

    /**
     * Each WebSocket session's frames, handled one at a time in the order they arrived.
     *
     * <p>See {@link SessionFrameQueue}: one queue and one live thread per connection, and no shared
     * state between connections, so a slow call on one never delays another.</p>
     */
    private final Map<String, SessionFrameQueue> sessionQueues = new ConcurrentHashMap<>();

    /**
     * How many frames from one connection may be waiting or being handled at once.
     *
     * <p>The container delivers one message at a time per connection and the framework now keeps
     * that order, so this is no longer a concurrency limit: exactly one frame per connection is
     * handled at a time. What it still bounds is the <em>backlog</em>. A connection that writes
     * faster than the server handles would queue without limit, and decoding is where a small
     * message becomes a large object graph, so an unbounded queue multiplies the worst case a
     * message-size limit is meant to cap.</p>
     *
     * <p>Thirty-two, and a connection that fills the queue waits rather than being refused. Nothing
     * is dropped and no call fails, so the number does not have to exceed any burst an application
     * can produce - a screen firing a dozen calls on load, or a reconnect flushing queued edits, is
     * simply served one after another. Waiting happens on that one connection's read loop, which is
     * the backpressure wanted; other connections are untouched.</p>
     *
     * <p>{@code zeroz.ws.maxConcurrentFramesPerSession} is still read, so a deployment that set it
     * before 0.8.0 keeps working; the name it had described a concurrency that no longer exists.</p>
     */
    static final String MAX_QUEUED_FRAMES_PROPERTY = "zeroz.ws.maxQueuedFramesPerSession";

    /** The name this setting had before 0.8.0, still read. */
    static final String MAX_CONCURRENT_FRAMES_PROPERTY = "zeroz.ws.maxConcurrentFramesPerSession";

    static final int DEFAULT_MAX_QUEUED_FRAMES = 32;

    /**
     * Shortest gap between two answered keepalive pings on one connection.
     *
     * <p>The client pings after 25 seconds of silence, so a working connection is nowhere near this.
     * A connection that pings faster is not keeping itself alive, it is asking the server to do work
     * in a loop, and the extra pings are dropped without an answer. One second, not zero, because
     * answering has to stay the cheapest thing the server does.</p>
     */
    static final String PING_MIN_INTERVAL_PROPERTY = "zeroz.ws.keepaliveMinIntervalMillis";
    static final long DEFAULT_PING_MIN_INTERVAL_MILLIS = 1000L;

    /**
     * This server's runtime.
     *
     * <p>Normally simply the injected one: the configurator hands the container the CDI bean, so
     * this endpoint object <em>is</em> the application-scoped engine and its collaborators are
     * injected. A container that builds the endpoint itself anyway leaves the field null, and the
     * runtime is then asked of CDI once and remembered.</p>
     *
     * <p>If neither works there is no server to serve on, and saying so is far better than the
     * {@code NullPointerException} that used to come out of the first connection.</p>
     *
     * @return the runtime, never null
     * @throws IllegalStateException when no runtime can be reached
     */
    ServerRuntime runtime() {
        ServerRuntime injected = injectedRuntime;
        if (injected != null) {
            return injected;
        }
        ServerRuntime cached = resolvedRuntime;
        if (cached != null) {
            return cached;
        }
        ServerRuntime found = ServerRuntime.fromCdi();
        if (found == null) {
            throw new IllegalStateException(
                    "This WebSocket endpoint is not attached to a running ZeroZ Stack server. The "
                    + "container built it itself instead of taking it from CDI, and there is no CDI "
                    + "container to ask either. Register the endpoint with "
                    + "RmiEndpointConfigurator, or start the server through one of the supported "
                    + "bindings. Nothing on this connection will work until that is fixed.");
        }
        resolvedRuntime = found;
        return found;
    }

    /**
     * The runtime a connection is being handled on, checked against this engine's own.
     *
     * <p>A connection that belongs to a <b>different</b> server is refused out loud. That is the
     * shape of the fault this release exists to fix: a test drove one server while watching another
     * server's connection, and everything passed because somebody really was writing to it.</p>
     *
     * @param session the connection
     * @return this server's runtime
     * @throws IllegalStateException when the connection belongs to another server
     */
    private ServerRuntime runtimeOf(Session session) {
        ServerRuntime own = runtime();
        ServerRuntime carried = ServerRuntime.ofOrNull(session);
        if (carried != null && !carried.id().equals(own.id())) {
            throw new IllegalStateException(
                    "Connection " + session.getId() + " belongs to server '" + carried.name()
                    + "' and was handed to server '" + own.name() + "'. Nothing was done with it. "
                    + "Each server has its own connections; watching one while driving the other is "
                    + "how a test comes to assert nothing at all.");
        }
        return own;
    }

    /** @return this server's settings */
    private ServerConfig config() {
        return runtime().config();
    }

    /**
     * Looks a bean up in this server's container.
     *
     * <p>Was {@code CDI.current()}, which picks a container by thread context class loader and
     * therefore answered with whichever server had started first when two ran in one process.</p>
     *
     * @param type what to look up
     * @param <T>  the bean type
     * @return every matching bean of this server
     */
    private <T> Instance<T> lookup(Class<T> type) {
        Instance<Object> own = beans;
        if (own != null) {
            return own.select(type);
        }
        return CDI.current().select(type);
    }

    /** @return this server's bean manager, falling back to the ambient container */
    private BeanManager beanManager() {
        BeanManager own = beanManager;
        return own != null ? own : CDI.current().getBeanManager();
    }

    /**
     * Scans CDI container for beans implementing {@code @RmiService}-annotated interfaces
     * and registers them in the service/method whitelist. Also collects security
     * annotations and populates the known roles for the handshake configurator.
     *
     * <p><b>Under the hood:</b> Executed automatically via {@code @PostConstruct}. Queries {@link BeanManager}
     * with {@code getBeans(Object.class)}.
     * Populates {@code serviceRegistry}, {@code methodRegistry}, {@code securedInterfaces}, and {@code interfaceRoles}.</p>
     */
    @PostConstruct
    public void scanServiceRegistry() {
        try {
            BeanManager bm = beanManager();
            for (Bean<?> bean : bm.getBeans(Object.class)) {
                Class<?> beanClass = bean.getBeanClass();
                for (Class<?> iface : beanClass.getInterfaces()) {
                    if (iface.isAnnotationPresent(RmiService.class)) {
                        String ifaceName = iface.getName();
                        Object instance = lookup(iface).get();
                        serviceRegistry.put(ifaceName, instance);

                        // Interface-level security
                        boolean ifaceSecured = iface.isAnnotationPresent(Secured.class);
                        securedInterfaces.put(ifaceName, ifaceSecured);

                        RolesAllowed ifaceRolesAnn = iface.getAnnotation(RolesAllowed.class);
                        if (ifaceRolesAnn != null) {
                            Set<String> roles = new HashSet<>(Arrays.asList(ifaceRolesAnn.value()));
                            interfaceRoles.put(ifaceName, roles);
                            runtime().knownRoles().addAll(roles);
                            securedInterfaces.put(ifaceName, true); // @RolesAllowed implies @Secured
                        } else {
                            interfaceRoles.put(ifaceName, Collections.emptySet());
                        }

                        Map<String, Method> methods = new ConcurrentHashMap<>();
                        for (Method m : iface.getDeclaredMethods()) {
                            methods.put(m.getName(), m);
                            String methodKey = ifaceName + "#" + m.getName();

                            // Method-level security
                            securedMethods.put(methodKey, m.isAnnotationPresent(Secured.class));
                            RolesAllowed methodRolesAnn = m.getAnnotation(RolesAllowed.class);
                            if (methodRolesAnn != null) {
                                Set<String> roles = new HashSet<>(Arrays.asList(methodRolesAnn.value()));
                                methodRoles.put(methodKey, roles);
                                runtime().knownRoles().addAll(roles);
                                securedMethods.put(methodKey, true);
                            }
                        }
                        methodRegistry.put(ifaceName, methods);

                        LOG.info("[zeroz4j] Registered RMI service: " + ifaceName
                            + " -> " + beanClass.getName()
                            + (ifaceSecured ? " [SECURED]" : ""));
                    }
                }
            }
        } catch (Exception e) {
            LOG.warning("[zeroz4j] Warning: CDI service scan deferred: " + e.getMessage());
        }
        runtime().markInUse();
        ServerSignalTransport.install(runtime(), mapper);
        Disclosures.install();
    }

    /**
     * Shuts down all virtual thread executors gracefully upon bean destruction.
     *
     * <p><b>Under the hood:</b> Executed via {@code @PreDestroy}. Closes every connection's frame
     * queue, which throws away what has not started and interrupts what has.</p>
     */
    @PreDestroy
    public void shutdown() {
        for (SessionFrameQueue frames : sessionQueues.values()) {
            frames.close();
        }
        sessionQueues.clear();
        // The runtime goes down with the engine, so anything that keeps driving this server
        // afterwards is told so rather than quietly doing nothing.
        ServerRuntime runtime = injectedRuntime != null ? injectedRuntime : resolvedRuntime;
        if (runtime != null) {
            runtime.shutDown();
        }
    }

    /**
     * Handles WebSocket connection open lifecycle events.
     *
     * @param session the newly connected WebSocket session
     * @param config  the endpoint configuration containing handshake user properties
     *
     * <p><b>Under the hood:</b> Takes ownership of the connection in {@link ServerRuntime}, creates the session's ordered frame queue in {@code sessionQueues},
     * transmits an AUTH frame (0x03) with principal and roles to the client, and registers the session with {@code syncEngine}.</p>
     */
    @OnOpen
    @SuppressWarnings("unchecked")
    public void onOpen(Session session, EndpointConfig config) {
        // A handshake refused by OriginPolicy is closed here: the upgrade cannot be failed from the
        // configurator in a container-independent way, so the connection is accepted and then shut
        // immediately, before it is registered anywhere or told anything about itself.
        if (Boolean.TRUE.equals(config.getUserProperties().get(RmiEndpointConfigurator.REJECTED_KEY))) {
            try {
                session.close(new jakarta.websocket.CloseReason(
                        jakarta.websocket.CloseReason.CloseCodes.VIOLATED_POLICY,
                        truncate(refusalReason(config))));
            } catch (Exception e) {
                LOG.warning("[zeroz4j] Failed to close a refused handshake: " + e.getMessage());
            }
            return;
        }

        ServerRuntime runtime = runtime();
        runtime.requireRunning("open a connection");
        // Takes ownership, and refuses a connection that already belongs to another server. That
        // refusal is the whole point: a test driving one server's connection against another used
        // to pass while proving nothing.
        runtime.attach(session);
        applyWebSocketLimits(runtime, session);
        // This connection's frames, handled one at a time in the order they arrived, and bounded so
        // a connection that outruns the server queues no more than it is allowed to.
        sessionQueues.put(session.getId(), new SessionFrameQueue(maxQueuedFrames()));

        // Propagate principal and roles from handshake
        Principal principal = (Principal) config.getUserProperties().get(RmiEndpointConfigurator.PRINCIPAL_KEY);
        Set<String> roles = (Set<String>) config.getUserProperties().get(RmiEndpointConfigurator.ROLES_KEY);
        if (roles == null) roles = Collections.emptySet();

        // Anonymous connections have no principal; Tomcat's user-properties map NPEs on null values.
        if (principal != null) {
            session.getUserProperties().put(RmiEndpointConfigurator.PRINCIPAL_KEY, principal);
        }
        session.getUserProperties().put(RmiEndpointConfigurator.ROLES_KEY, roles);

        // Tenant and client id ride along the same way: both are decided at handshake, and both are
        // what their scopes filter on. The client id is present even with no authentication at all.
        String tenantId = (String) config.getUserProperties().get(RmiEndpointConfigurator.TENANT_KEY);
        if (tenantId != null) {
            session.getUserProperties().put(RmiEndpointConfigurator.TENANT_KEY, tenantId);
        }
        String clientId = (String) config.getUserProperties().get(RmiEndpointConfigurator.CLIENT_KEY);
        if (clientId != null) {
            session.getUserProperties().put(RmiEndpointConfigurator.CLIENT_KEY, clientId);
        }
        // The language this connection reads, decided once at the handshake. Kept on the
        // connection because it belongs to the person on the other end, not to any one call.
        Object language = config.getUserProperties().get(RmiEndpointConfigurator.LOCALE_KEY);
        if (language != null) {
            session.getUserProperties().put(RmiEndpointConfigurator.LOCALE_KEY, language);
        }

        // Before the first byte is written to this connection: everything the server sends it is
        // recorded against its browser, and that record is what later decides whether it may ask for
        // an object again. Keyed by browser rather than by connection, so a reconnect can re-sync.
        runtime.disclosures().sessionOpened(session);

        // Send AUTH frame (0x03) to client. A connection whose credentials the application's
        // AuthenticationProvider declined has no principal, and the frame must say so: reporting it
        // as an ordinary connection named "anonymous" is indistinguishable from a real sign-in
        // unless the client also inspects the roles, which defeated login gates built the
        // documented way.
        boolean authenticated = principal != null;
        String username = authenticated ? principal.getName() : ANONYMOUS_USER;
        sendAuthFrame(session, username, roles, authenticated);

        LOG.info("[zeroz4j] Client connected: " + username + " roles=" + roles
                + (authenticated ? "" : " (not authenticated)"));

        // LiveSync: add session to SyncEngine
        syncEngine.addSession(session);
    }

    /**
     * Handles WebSocket connection closure lifecycle events.
     *
     * @param session the closing WebSocket session
     *
     * <p><b>Under the hood:</b> Gives the connection up in {@link ServerRuntime}, closes the session's frame queue,
     * releases all live mutex locks owned by the session via {@link LiveMutexManager#releaseAll}, and unregisters session from {@code syncEngine}.</p>
     */
    @OnClose
    public void onClose(Session session) {
        ServerRuntime runtime = runtimeOf(session);

        // Drop everything this connection had queued, and interrupt the frame it was handling.
        SessionFrameQueue frames = sessionQueues.remove(session.getId());
        if (frames != null) {
            frames.close();
        }

        // Release any distributed locks held by this session
        if (liveMutexManager != null) {
            liveMutexManager.releaseAll("session:" + session.getId());
        }

        // LiveSync: clean up the SyncSession
        syncEngine.removeSession(session.getId());

        // Shared signals: drop parked subscriptions
        ServerSignalTransport.sessionClosed(session);

        // The record of what this browser was sent survives on purpose: the next connection is the
        // same browser and must still be able to re-sync. Only the connection mapping goes.
        runtime.disclosures().sessionClosed(session.getId());

        // Drops the connection from this server: its active-session entry, its keepalive budget,
        // its lazy-reference handles, and the back-reference the connection carried.
        runtime.detach(session);

        // Tell the application, after framework cleanup: apps keep registries keyed by session id
        // (scoped pushes, rooms) and previously had no way to learn a session was gone.
        try {
            Principal principal = (Principal) session.getUserProperties().get(RmiEndpointConfigurator.PRINCIPAL_KEY);
            SessionClosedEvent closed =
                    new SessionClosedEvent(session.getId(), principal != null ? principal.getName() : null);
            if (sessionClosed != null) {
                sessionClosed.fire(closed);
            } else {
                beanManager().getEvent().select(SessionClosedEvent.class).fire(closed);
            }
        } catch (Exception e) {
            // An observer threw, or the CDI container is already shutting down. Either way the
            // close itself must complete; the observer's problem is logged, not propagated.
            LOG.warning("[zeroz4j] SessionClosedEvent observer failed: " + e.getMessage());
        }
    }

    /**
     * Tells the client who the server decided it is.
     *
     * <p>Sent on every connection, including a refused one — silence would leave a login screen
     * unable to tell a wrong password from a slow network. The {@code authenticated} flag is carried
     * separately from the name and roles because neither of those can stand in for it: a refused
     * connection still has a name, and a real user may hold no application roles at all.</p>
     *
     * @param session       the connection
     * @param username      the user name, or {@link #ANONYMOUS_USER}
     * @param roles         granted roles; empty when not authenticated
     * @param authenticated whether an identity was actually accepted
     */
    /**
     * The one sentence a refused handshake is closed with.
     *
     * <p>Two different checks refuse a handshake, and they are fixed in two different places, so the
     * message has to say which one it was. Telling someone whose <em>host name</em> is not answered
     * for that their origin was rejected sends them to read origin configuration that was never the
     * problem.</p>
     *
     * <p>What it must not do is describe the deployment. This text goes to whoever opened the
     * connection, so it names the check and the setting to look at, never the configured values.
     * The full explanation, with the configured list, is in the server log, which only the operator
     * can read.</p>
     *
     * @param config the handshake configuration, carrying what the configurator decided
     * @return the close reason, within the 123 bytes the protocol allows
     */
    private static String refusalReason(EndpointConfig config) {
        Object refusedBy = config.getUserProperties().get(REFUSED_BY_KEY);
        if (REFUSED_BY_ORIGIN.equals(refusedBy)) {
            return ORIGIN_REFUSED_REASON;
        }
        if (REFUSED_BY_HOST.equals(refusedBy)) {
            return HOST_REFUSED_REASON;
        }
        return HANDSHAKE_REFUSED_REASON;
    }

    /**
     * Applies the configured message-size and idle limits to a new connection.
     *
     * <p><b>Message size.</b> {@link #MAX_BINARY_BYTES_PROPERTY} wins when it is set, so a
     * deployment that has already tuned this keeps its number. Unset, the framework applies
     * {@link #DEFAULT_MAX_BINARY_BYTES} — 4 MB — rather than leaving the container's value, because
     * on the runtime this framework recommends the container's value is roughly 2 GB and a client
     * can reach it: Helidon 4.0.8 implements the Jakarta WebSocket API by embedding Tyrus 2.1.5,
     * and Tyrus initialises the per-session binary limit from
     * {@code TyrusServerContainer.getDefaultMaxBinaryMessageBufferSize()}, whose field default is
     * {@code Integer.MAX_VALUE}. Helidon never overrides it, and Helidon's own native WebSocket
     * path with its 1 MB {@code maxFrameLength} is not the path in use. Tyrus's network read
     * buffer, {@code incomingBufferSize}, caps a single unfragmented chunk at 4,194,315 bytes, but
     * a fragmented message is assembled past that up to the session limit.</p>
     *
     * <p>{@code session.setMaxBinaryMessageBufferSize(...)} does reach Tyrus and is genuinely
     * enforced — {@code TyrusEndpointWrapper} checks it in {@code BinaryBuffer.resetBuffer} and
     * raises {@code MessageTooBigException} — so the property and the default both bite.</p>
     *
     * <p><b>What exceeding it looks like.</b> {@code @OnMessage} here takes a whole
     * {@link ByteBuffer} — there is no partial-message handling — so an over-sized message never
     * reaches framework code and there is no error response. The connection closes. The client
     * reconnects automatically, which is why this is worth logging: otherwise the only symptom is a
     * socket that drops whenever one particular call is made.</p>
     *
     * <p><b>Idle timeout.</b> Unset leaves the container's own, since an abandoned connection costs
     * a session rather than memory, and containers differ widely in what a sensible value is.</p>
     */
    private static void applyWebSocketLimits(ServerRuntime runtime, Session session) {
        ServerConfig config = runtime.config();
        Integer configuredMaxBinary = config.positiveInt(MAX_BINARY_BYTES_PROPERTY);
        int maxBinaryBytes = configuredMaxBinary != null ? configuredMaxBinary : DEFAULT_MAX_BINARY_BYTES;
        session.setMaxBinaryMessageBufferSize(maxBinaryBytes);

        Integer idleMinutes = config.positiveInt(IDLE_TIMEOUT_MINUTES_PROPERTY);
        if (idleMinutes != null) {
            session.setMaxIdleTimeout(idleMinutes * 60_000L);
        }

        if (runtime.reportLimitsOnce()) {
            LOG.info("[zeroz4j] Largest binary message accepted: " + maxBinaryBytes + " bytes ("
                    + (configuredMaxBinary != null ? "set by " : "framework default; change with ")
                    + MAX_BINARY_BYTES_PROPERTY + "). A message over that closes the connection"
                    + " without an error response.");
        }
    }

    private void sendAuthFrame(Session session, String username, Set<String> roles,
                               boolean authenticated) {
        try {
            GrowableBuffer buffer = new GrowableBuffer();
            buffer.putInt(0); // no correlation ID
            buffer.put(SyncFrameTypes.AUTH); // AUTH frame type
            buffer.put(AUTH_PROTOCOL_VERSION);
            buffer.put((byte) (authenticated ? 1 : 0));
            BinarySerializer.writeString(buffer, username);
            buffer.putInt(roles.size());
            for (String role : roles) {
                BinarySerializer.writeString(buffer, role);
            }
            WsWrites.send(session, buffer.toByteArray());
        } catch (Exception e) {
            LOG.warning("[zeroz4j] Failed to send AUTH frame: " + e.getMessage());
        }
    }

    /**
     * Broadcasts a server-initiated push notification to all active client sessions.
     *
     * @param topic   the notification topic string
     * @param payload the message payload object
     *
     * <p><b>Under the hood:</b> Iterates this server's own connections and calls {@link #sendPush(Session, String, Object)} for each.</p>
     */
    public void broadcastPush(String topic, Object payload) {
        // Serialize once up front so an unserializable payload reaches the CALLER. Previously the
        // failure was caught per session and logged, so publish() appeared to succeed while the event
        // reached nobody -- the single most confusing failure in the framework.
        assertSerializable(payload, "event payload for topic '" + topic + "'");
        ServerRuntime runtime = runtime();
        runtime.requireRunning("publish an event");
        for (Session session : runtime.sessions()) {
            sendPush(session, topic, payload);
        }
    }

    /**
     * Fails fast when a value cannot be put on the wire.
     *
     * @param value       the value about to be broadcast
     * @param description what the value is, for the error message
     * @throws IllegalArgumentException if the value is not serializable, naming the offending type
     */
    private static void assertSerializable(Object value, String description) {
        if (value == null) {
            return;
        }
        try {
            BinarySerializer.writeValue(new GrowableBuffer(64), value, new ObjectMapper());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(
                    "Cannot serialize " + description + ": " + ex.getMessage()
                    + " " + diagnose(value.getClass()), ex);
        }
    }

    /**
     * Explains <em>why</em> a type could not be put on the wire.
     *
     * <p>This used to say "annotate the type @DataModel" unconditionally, which sent developers to
     * look at a model class that was already annotated — the actual cause was usually that the
     * annotation processor never ran, which is invisible from the source. The three cases are
     * genuinely different problems and are now reported as such.</p>
     *
     * @param type the runtime type that failed to serialize
     * @return a sentence naming the likely cause and what to check
     */
    // Package-private so WasmRmiServerEngineTest can assert each branch. This message only ever
    // appears when something is already broken, which is exactly when it must not be wrong.
    static String diagnose(Class<?> type) {
        if (!type.isAnnotationPresent(DataModel.class)) {
            return "Annotate " + type.getName() + " with @DataModel, or use a supported built-in type.";
        }
        if (!hasGeneratedSerializer(type)) {
            return type.getName() + " IS annotated @DataModel, but no "
                    + type.getSimpleName() + "_Serializer was generated for it, so the annotation "
                    + "processor did not run for that module. JDK 23 disabled implicit annotation "
                    + "processing (JEP 470), so zerozstack-apt on the classpath alone is ignored. "
                    + "Declare it explicitly in the module that owns this type, via "
                    + "maven-compiler-plugin <annotationProcessorPaths> with "
                    + "com.zeroz4j:zerozstack-apt. Verify by looking for "
                    + type.getSimpleName() + "_Serializer.class in that module's target/classes.";
        }
        return type.getName() + " is annotated @DataModel and its serializer was generated, so the "
                + "problem is inside the value rather than the type itself — most often a field "
                + "whose own type is neither @DataModel nor a supported built-in.";
    }

    /** Whether the processor emitted a serializer for this type, alongside it in its own package. */
    private static boolean hasGeneratedSerializer(Class<?> type) {
        try {
            Class.forName(type.getName() + "_Serializer", false, type.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError notGenerated) {
            return false;
        }
    }

    /**
     * Broadcasts a typed event to all active client sessions.
     *
     * @param <T>     payload type bound by the topic
     * @param topic   shared {@link EventTopic} descriptor
     * @param payload the payload to broadcast; may be null for {@code EventTopic<Void>} events
     *
     * <p><b>Under the hood:</b> Delegates to {@link #broadcastPush(String, Object)} using {@link EventTopic#name()}.</p>
     */
    @Override
    public <T> void publish(EventTopic<T> topic, T payload) {
        broadcastPush(topic.name(), payload);
    }

    @Override
    public <T> void publish(EventTopic<T> topic, T payload, Scope scope, String target) {
        if (scope == null) {
            throw new IllegalArgumentException("publish(..., scope, target): scope must not be null");
        }
        if (scope != Scope.GLOBAL && (target == null || target.isEmpty())) {
            throw new IllegalArgumentException(
                    "publish with scope " + scope + " needs a target: the " + targetNameFor(scope)
                    + " to reach. Without it the event would silently reach nobody.");
        }
        assertSerializable(payload, "event payload for topic '" + topic.name() + "'");
        ServerRuntime runtime = runtime();
        runtime.requireRunning("publish an event");
        for (Session session : runtime.sessions()) {
            if (matchesScope(session, scope, target)) {
                sendPush(session, topic.name(), payload);
            }
        }
    }

    /**
     * Drops a connection the server has just found closed, from whichever server owns it.
     *
     * @param session the closed connection
     */
    private static void forgetClosed(Session session) {
        ServerRuntime owner = ServerRuntime.ofOrNull(session);
        if (owner != null) {
            owner.forget(session);
        }
    }

    /** The WebSocket protocol allows a close reason of at most 123 bytes. */
    private static final int MAX_CLOSE_REASON_BYTES = 123;

    @Override
    public int disconnect(String principalName, String reason) {
        if (principalName == null || principalName.trim().isEmpty()) {
            // Deliberately not "close everything": an application computing a principal name that
            // came back null would otherwise sign every one of its users out.
            LOG.warning("[zeroz4j] disconnect() called with no principal name; nothing was closed.");
            return 0;
        }
        int closed = 0;
        ServerRuntime runtime = runtime();
        runtime.requireRunning("disconnect a user");
        for (Session session : runtime.sessions()) {
            if (matchesScope(session, Scope.USER, principalName) && closeSession(session, reason)) {
                closed++;
            }
        }
        LOG.info("[zeroz4j] Disconnected " + closed + " connection(s) of '" + principalName
                + "': " + reason);
        return closed;
    }

    @Override
    public boolean disconnectSession(String sessionId, String reason) {
        if (sessionId == null || sessionId.isEmpty()) {
            return false;
        }
        ServerRuntime runtime = runtime();
        runtime.requireRunning("disconnect a connection");
        for (Session session : runtime.sessions()) {
            if (sessionId.equals(session.getId())) {
                boolean closed = closeSession(session, reason);
                if (closed) {
                    LOG.info("[zeroz4j] Disconnected session " + sessionId + ": " + reason);
                }
                return closed;
            }
        }
        return false;
    }

    /**
     * Closes one session, never throwing.
     *
     * <p>A revocation loop must reach every session even if one of them is already half-dead, so a
     * failure here is logged and counted as not-closed rather than propagated to the caller.</p>
     */
    private static boolean closeSession(Session session, String reason) {
        try {
            ServerRuntime owner = ServerRuntime.ofOrNull(session);
            session.close(new jakarta.websocket.CloseReason(
                    jakarta.websocket.CloseReason.CloseCodes.VIOLATED_POLICY, truncate(reason)));
            if (owner != null) {
                owner.forget(session);
            }
            return true;
        } catch (Exception ex) {
            LOG.warning("[zeroz4j] Could not close session " + session.getId() + ": " + ex.getMessage());
            return false;
        }
    }

    /**
     * Trims a close reason to what the protocol allows.
     *
     * <p>Containers reject an over-long reason by throwing, which would turn "revoke this account"
     * into "revoke nothing" over a message nobody reads carefully. Measured in bytes, not
     * characters, because the limit is on the encoded frame.
     */
    static String truncate(String reason) {
        if (reason == null) {
            return "";
        }
        byte[] bytes = reason.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length <= MAX_CLOSE_REASON_BYTES) {
            return reason;
        }
        java.nio.ByteBuffer truncated = java.nio.ByteBuffer.wrap(bytes, 0, MAX_CLOSE_REASON_BYTES);
        java.nio.charset.CharsetDecoder decoder = java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.IGNORE);
        try {
            return decoder.decode(truncated).toString();
        } catch (java.nio.charset.CharacterCodingException impossible) {
            // IGNORE was configured above, so a malformed trailing sequence is dropped rather than
            // thrown. Kept as a belt-and-braces fallback.
            return new String(bytes, 0, MAX_CLOSE_REASON_BYTES - 3,
                    java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    /**
     * @param session the candidate session
     * @param scope   how far the push should reach
     * @param target  session id or user name, per the scope
     * @return whether this session should receive the push
     */
    /**
     * Registers a session as active. Test support only — in production {@code @OnOpen} does this.
     *
     * @param session the session to register
     */
    void addActiveSessionForTesting(Session session) {
        runtime().addSessionForTesting(session);
    }

    /**
     * @param session the candidate session
     * @param scope   how far the push should reach
     * @param target  session id or user name, per the scope
     * @return whether this session should receive the push
     */
    /** Names what a scope's target actually is, so a missing one is reported in the caller's terms. */
    static String targetNameFor(Scope scope) {
        switch (scope) {
            case SESSION: return "session id";
            case CLIENT:  return "client id";
            case TENANT:  return "tenant id";
            default:      return "user name";
        }
    }

    static boolean matchesScope(Session session, Scope scope, String target) {
        if (scope == Scope.GLOBAL) {
            return true;
        }
        if (scope == Scope.SESSION) {
            return session.getId().equals(target);
        }
        if (scope == Scope.CLIENT) {
            Object clientId = session.getUserProperties().get(RmiEndpointConfigurator.CLIENT_KEY);
            return clientId != null && clientId.equals(target);
        }
        if (scope == Scope.TENANT) {
            Object tenant = session.getUserProperties().get(RmiEndpointConfigurator.TENANT_KEY);
            return tenant != null && tenant.equals(target);
        }
        Principal principal =
                (Principal) session.getUserProperties().get(RmiEndpointConfigurator.PRINCIPAL_KEY);
        return principal != null && principal.getName().equals(target);
    }

    /**
     * Handles a client's whole-object mutation of a {@code @ClientWritable} live model.
     *
     * <p>Two-pass decode: the payload is first deserialized with a throwaway mapper into a
     * fresh instance for authorization (annotation + roles) and validation — the canonical
     * object is untouched if the mutation is rejected. On acceptance the buffer is rewound
     * and deserialized through the real mapper, which applies the state to the canonical
     * instance in place; listeners are notified and the change is re-broadcast to all
     * sessions. On rejection the writer receives a session-targeted corrective sync of the
     * canonical state, reverting its optimistic local change.</p>
     *
     * <p><b>Trust model.</b> The second pass writes into whatever canonical instance each handle in
     * the payload names, so authorization has to cover <em>every</em> object the payload reaches,
     * not just the outermost one. It once covered only the outermost one, and a payload could
     * therefore carry, nested inside a model the client may write, the handle of a model it may not
     * — which was then overwritten and broadcast to every session. A {@link LiveMutationGuard} is
     * installed for the whole decode and refuses the entire mutation the moment the payload reaches
     * a canonical object that is not itself {@code @ClientWritable}, or whose declared write role
     * the connection does not hold. Because the guard is installed for the <em>first</em> pass, the
     * refusal happens before any canonical object has been touched.</p>
     *
     * <p>Authorization is decided on the class of the object the server holds, never on the class
     * name in the frame: the frame's class name is the client's claim, and a payload naming a
     * writable class over a restricted object's handle would otherwise pass.</p>
     */
    @SuppressWarnings("unchecked")
    /**
     * Resolves a lazy reference for the requesting client and returns its contents.
     *
     * <p>The handle must be one this session was previously sent: {@link LazyHandles} binds every
     * handle to the session it was disclosed to, so a handle cannot be replayed by a session that was
     * never permitted to see the data. An unknown handle is answered with an error frame rather than
     * silently returning null, so a bug is not mistaken for an empty collection.</p>
     *
     * @param messageId correlation id to answer on
     * @param buffer    positioned at the handle argument
     * @param session   the requesting session
     */
    void handleLazyResolve(int messageId, ByteBuffer buffer, Session session) {
        try {
            Object handleArg = BinarySerializer.readValue(buffer, mapper);
            String handle = handleArg == null ? null : handleArg.toString();

            ServerRuntime runtime = runtimeOf(session);
            Object lazy = runtime.lazyHandles().resolve(handle, session.getId());
            if (lazy == null) {
                throw new RefusedException(FrameworkText.unknownLazyHandle(handle));
            }

            com.zeroz4j.api.LazyAdapter adapter = com.zeroz4j.api.BinaryRegistry.getLazyAdapter();
            if (adapter == null) {
                throw new IllegalStateException("No LazyAdapter installed on the server");
            }
            Object contents = adapter.contentsOf(lazy);

            GrowableBuffer responseBuffer = new GrowableBuffer();
            responseBuffer.putInt(messageId);
            responseBuffer.put(SyncFrameTypes.RPC_RESPONSE);
            try (LazyHandles.Write write = LazyHandles.writingTo(runtime, session)) {
                BinarySerializer.writeValue(responseBuffer, contents, mapper);
            }
            WsWrites.send(session, responseBuffer.toByteArray());

        } catch (Exception ex) {
            // Same rule as an ordinary call: the refusal reaches the caller, a failure inside the
            // loading of the subgraph does not - it would describe the store, not the request.
            sendError(session, messageId, ex);
        }
    }

    /**
     * Answers a reconnected client's re-sync request: for every handle it presents that this
     * server still knows, the object's current state is re-sent as an ordinary 0x10 update frame,
     * which the client applies in place. Re-serializing also walks each object's {@code Lazy}
     * fields, re-registering their handles for this (new) session — which is what brings lazy
     * resolution back to life after a reconnect.
     *
     * <p><b>Trust model:</b> being sent an object is what earns the right to re-read it. The server
     * keeps a record of every handle it has written toward each browser ({@link Disclosures}) and
     * answers only for handles in the caller's own record. Presenting a handle used to be proof
     * enough on the theory that a handle can only be learned by being sent the object — which is not
     * so, because an object nested inside a broadcast event or signal travels with its own handle
     * attached, teaching every recipient the names of things they were never given.
     *
     * <p>The record is kept per browser, not per connection, because a reconnect is a new connection
     * and a per-connection record would be empty exactly when re-sync needs it.
     *
     * <p>A handle the caller was never sent, and a handle this server no longer knows at all, are
     * treated identically: no frame, no error, and a counted log line. The second case has two
     * causes since 0.8.0 — the server restarted since the client fetched the object, or the
     * application here has let go of it and the registry, which holds its objects weakly, has let
     * go too. Either way the client's copy stays as it is and the application re-fetches it the way
     * it first obtained it.
     *
     * <p>At most {@link SyncFrameTypes#MAX_RESYNC_HANDLES} handles are examined. A client caps its
     * own list at the same number; this is the ceiling applied to whatever actually arrives, so one
     * connection cannot make the server write an unbounded number of frames.
     *
     * @param handles the handle list from the client
     * @param session the reconnected session
     */
    void handleResync(java.util.List<?> handles, Session session) {
        ServerRuntime runtime = runtimeOf(session);
        int sent = 0;
        int unknown = 0;
        int undisclosed = 0;
        int examined = 0;
        for (Object handleObj : handles) {
            if (!(handleObj instanceof String)) {
                continue;
            }
            if (++examined > SyncFrameTypes.MAX_RESYNC_HANDLES) {
                // The client caps its own list; this is the same ceiling applied to whatever
                // actually arrives, so no one connection can make the server write an unbounded
                // number of frames. The record of what this browser was sent holds no more than
                // this many objects anyway, so nothing answerable is being skipped.
                LOG.warning("[zeroz4j] Re-sync for session " + session.getId() + ": the request "
                    + "named " + handles.size() + " handles, more than the "
                    + SyncFrameTypes.MAX_RESYNC_HANDLES + " a request may carry. The rest were "
                    + "ignored; the client re-fetches those objects the way it first obtained them.");
                break;
            }
            if (!runtime.disclosures().wasDisclosedToSession(session, (String) handleObj)) {
                undisclosed++;
                continue;
            }
            Object obj = mapper.getObject((String) handleObj);
            if (obj == null) {
                unknown++;
                continue;
            }
            try {
                GrowableBuffer buffer = new GrowableBuffer();
                buffer.putInt(0); // broadcast-style frame: no correlation
                buffer.put(SyncFrameTypes.SUBSCRIBE);
                try (LazyHandles.Write write = LazyHandles.writingTo(runtime, session)) {
                    BinarySerializer.writeValue(buffer, obj, mapper);
                }
                WsWrites.send(session, buffer.toByteArray());
                sent++;
            } catch (Exception e) {
                LOG.warning("[zeroz4j] Re-sync failed for handle " + handleObj + ": " + e.getMessage());
            }
        }
        if (undisclosed > 0) {
            LOG.warning("[zeroz4j] Re-sync for session " + session.getId() + ": " + sent
                + " object(s) re-sent, " + undisclosed + " handle(s) not answered because this "
                + "client was never sent those objects, or was sent them long enough ago that the "
                + "record has expired. Nothing is restored for them; the application re-fetches "
                + "them the way it first obtained them.");
        }
        if (unknown > 0) {
            LOG.warning("[zeroz4j] Re-sync for session " + session.getId() + ": " + sent
                + " object(s) re-sent, " + unknown + " handle(s) unknown -- either this server "
                + "restarted since the client fetched them, or the application here no longer "
                + "holds those objects (the registry keeps them weakly). Those objects stay stale "
                + "until the application re-fetches them.");
        } else if (undisclosed == 0 && sent > 0) {
            LOG.info("[zeroz4j] Re-sync for session " + session.getId() + ": " + sent + " object(s) re-sent.");
        }
    }

    void handleLiveMutation(ByteBuffer buffer, Session session) {
        int payloadStart = buffer.position();

        Set<String> userRoles = (Set<String>) session.getUserProperties().get(RmiEndpointConfigurator.ROLES_KEY);
        LiveMutationGuard guard = new LiveMutationGuard(mapper, userRoles);

        Object proposed;
        Message refusal = null;
        ObjectMapper tempMapper = new ObjectMapper();
        ObjectMapper.setResolutionGuard(guard);
        ObjectMapper.setModelGuard(guard);
        try {
            proposed = BinarySerializer.readValue(buffer, tempMapper);
        } catch (LiveMutationGuard.Denied denied) {
            // The payload reached a canonical object this connection may not write. Nothing has been
            // applied: this pass decodes into a throwaway mapper, so no canonical object was touched.
            proposed = null;
            refusal = denied.reason();
        } catch (Exception e) {
            LOG.warning("[zeroz4j] Rejected undecodable live mutation from session "
                + session.getId() + ": " + e.getMessage());
            return;
        } finally {
            // Cleared before anything else runs: answering the refusal reads the registry itself.
            ObjectMapper.setResolutionGuard(null);
            ObjectMapper.setModelGuard(null);
        }
        if (refusal != null) {
            rejectNestedWrite(session, guard.rootHandleId(), refusal);
            return;
        }
        if (proposed == null) {
            return;
        }

        String canonicalId = tempMapper.getId(proposed);
        Object canonical = canonicalId != null ? mapper.getObject(canonicalId) : null;

        // The class the server holds decides, not the class name the client sent. A payload naming a
        // writable class over a restricted object's handle would otherwise walk straight through.
        if (canonical != null && !canonical.getClass().isAssignableFrom(proposed.getClass())) {
            Message reason = FrameworkText.liveWrongType(
                proposed.getClass().getSimpleName(), canonical.getClass().getSimpleName());
            LOG.warning("[zeroz4j] Rejected live mutation from session " + session.getId()
                + ": frame class " + proposed.getClass().getName() + " does not match canonical "
                + canonical.getClass().getName() + " for handle " + canonicalId);
            syncEngine.notifyChanged(canonical, Scope.SESSION, session.getId());
            sendMutationRejected(session, canonical.getClass().getName(), reason);
            return;
        }

        Class<?> authorizedType = canonical != null ? canonical.getClass() : proposed.getClass();
        ClientWritable writable = authorizedType.getAnnotation(ClientWritable.class);
        boolean allowed = writable != null && canonical != null;
        if (allowed && writable.value().length > 0) {
            boolean hasRole = false;
            if (userRoles != null) {
                for (String required : writable.value()) {
                    if (userRoles.contains(required)) {
                        hasRole = true;
                        break;
                    }
                }
            }
            allowed = hasRole;
        }
        java.util.List<String> violations = allowed
            ? ValidationRegistry.validate(proposed)
            : java.util.Collections.emptyList();

        if (allowed && violations.isEmpty()) {
            buffer.position(payloadStart);
            Object applied;
            // The guard stays on for the applying pass too. It cannot refuse anything the first pass
            // already accepted; it is here so no future path can apply an unchecked decode.
            ObjectMapper.setResolutionGuard(guard);
            ObjectMapper.setModelGuard(guard);
            try {
                applied = BinarySerializer.readValue(buffer, mapper); // in-place apply
            } catch (LiveMutationGuard.Denied denied) {
                applied = null;
                refusal = denied.reason();
            } finally {
                ObjectMapper.setResolutionGuard(null);
                ObjectMapper.setModelGuard(null);
            }
            if (refusal != null) {
                rejectNestedWrite(session, guard.rootHandleId(), refusal);
                return;
            }
            Principal principal = (Principal) session.getUserProperties().get(RmiEndpointConfigurator.PRINCIPAL_KEY);
            try {
                for (LiveMutationListener listener : lookup(LiveMutationListener.class)) {
                    listener.onMutated(applied, principal);
                }
            } catch (Exception e) {
                LOG.warning("[zeroz4j] LiveMutationListener error: " + e.getMessage());
            }
            syncEngine.notifyChanged(applied);
        } else if (canonical != null) {
            // Every rejection reason is both logged and sent to the writer. Previously a failed role
            // check produced no log line at all, so an absent entry was indistinguishable from an
            // accepted mutation, and the client was never told why its change reverted.
            Message reason;
            if (writable == null) {
                reason = FrameworkText.liveNotClientWritable(authorizedType.getName());
                LOG.warning("[zeroz4j] Rejected live mutation of non-@ClientWritable "
                    + authorizedType.getName() + " from session " + session.getId());
            } else if (!violations.isEmpty()) {
                reason = FrameworkText.liveValidationFailed(String.join("; ", violations));
                LOG.info("[zeroz4j] Rejected invalid live mutation of "
                    + authorizedType.getSimpleName() + ": " + String.join("; ", violations));
            } else {
                reason = FrameworkText.liveRequiresRole(Arrays.toString(writable.value()));
                LOG.warning("[zeroz4j] Rejected live mutation of "
                    + authorizedType.getSimpleName() + " from session " + session.getId()
                    + ": " + ServerMessages.inEnglish(reason));
            }

            // Corrective: revert the writer's optimistic local change to server truth.
            syncEngine.notifyChanged(canonical, Scope.SESSION, session.getId());
            sendMutationRejected(session, authorizedType.getName(), reason);
        }
    }

    /**
     * Refuses a change that reached a canonical object the writer may not write.
     *
     * <p>Handled like every other refusal: the writer is snapped back to server truth and told why,
     * so its optimistic local edit does not spring back with no explanation. The outermost object is
     * the one snapped back, because that is the one the client believes it edited.</p>
     *
     * @param session      the writer
     * @param rootHandleId the outermost handle in the refused payload, or null if none was reached
     * @param reason       what to tell the writer
     */
    private void rejectNestedWrite(Session session, String rootHandleId, Message reason) {
        Object root = rootHandleId != null ? mapper.getObject(rootHandleId) : null;
        String className = root != null ? root.getClass().getName() : "";
        LOG.warning("[zeroz4j] Rejected live mutation from session " + session.getId()
            + ": " + ServerMessages.inEnglish(reason) + " (outermost object " + className + ")");
        if (root != null) {
            syncEngine.notifyChanged(root, Scope.SESSION, session.getId());
        }
        sendMutationRejected(session, className, reason);
    }

    /**
     * Tells the writer why its mutation was refused, on the reserved {@code 0x15 REJECT} opcode.
     *
     * <p>The corrective sync that precedes this already restores server truth; this frame exists so
     * the client can surface a reason instead of the change silently springing back.</p>
     *
     * @param session   the writer
     * @param className the model whose mutation was refused
     * @param reason    what to tell the writer, rendered here in their own language because this
     *                  is the one place that knows which connection is being answered
     */
    private void sendMutationRejected(Session session, String className, Message reason) {
        try {
            GrowableBuffer buffer = new GrowableBuffer(256);
            buffer.putInt(0);
            buffer.put(SyncFrameTypes.REJECT);
            BinarySerializer.writeString(buffer, className);
            BinarySerializer.writeString(buffer, ServerMessages.render(reason, localeOf(session)));
            WsWrites.send(session, buffer.toByteArray());
        } catch (Exception ex) {
            LOG.warning("[zeroz4j] Failed to send mutation rejection: " + ex.getMessage());
        }
    }

    private static void validateArgument(Object arg) {
        if (arg == null) {
            return;
        }
        if (arg instanceof java.util.List) {
            for (Object element : (java.util.List<?>) arg) {
                validateArgument(element);
            }
            return;
        }
        java.util.List<String> violations = ValidationRegistry.validate(arg);
        if (!violations.isEmpty()) {
            // A ClientVisibleException, so the violations survive the error sanitizer: telling the
            // caller which field it got wrong is the entire point of server-side validation.
            throw new ClientVisibleException(FrameworkText.validationFailed(
                arg.getClass().getSimpleName(), String.join("; ", violations)));
        }
    }

    /**
     * Sends a shared-signal value (0x17 SIGNAL_UPD) to a single session.
     *
     * @param session target session
     * @param name    shared signal wire name
     * @param value   current value
     * @param mapper  object mapper for serialization
     */
    /**
     * Answers a keepalive ping with one empty {@link SyncFrameTypes#PONG} frame.
     *
     * <p>Five bytes and no payload. It is sent so that the tunnel carries traffic in the
     * server-to-client direction as well: a proxy times each direction separately - nginx has
     * {@code proxy_read_timeout} and {@code proxy_send_timeout} - so a ping the server merely
     * swallowed would keep only one of the two timers alive.
     *
     * <p>Not logged. At one every twenty-odd seconds per open tab this would become the most
     * frequent line in any server log and bury everything worth reading.
     */
    static void sendPong(Session session) {
        if (!session.isOpen()) {
            forgetClosed(session);
            return;
        }
        try {
            GrowableBuffer buffer = new GrowableBuffer();
            buffer.putInt(0);
            buffer.put(SyncFrameTypes.PONG);
            WsWrites.send(session, buffer.toByteArray());
        } catch (Exception e) {
            LOG.warning("[zeroz4j] Keepalive answer failed for session " + session.getId()
                    + ": " + e.getMessage());
        }
    }

    static void sendSignalUpdate(Session session, String name, Object value, ObjectMapper mapper) {
        if (!session.isOpen()) {
            forgetClosed(session);
            return;
        }
        try {
            GrowableBuffer buffer = new GrowableBuffer();
            buffer.putInt(0);
            buffer.put(SyncFrameTypes.SIGNAL_UPD);
            BinarySerializer.writeString(buffer, name);
            try (LazyHandles.Write write = LazyHandles.writingTo(ServerRuntime.of(session), session)) {
                BinarySerializer.writeValue(buffer, value, mapper);
            }
            WsWrites.send(session, buffer.toByteArray());
        } catch (Exception e) {
            LOG.warning("[zeroz4j] Signal update error for session " + session.getId() + ": " + e.getMessage());
        }
    }

    /**
     * Broadcasts a shared-signal value (0x17 SIGNAL_UPD) to all active sessions.
     *
     * @param name   shared signal wire name
     * @param value  current value
     * @param mapper object mapper for serialization
     */
    static void broadcastSignalUpdate(ServerRuntime runtime, String name, Object value,
                                      ObjectMapper mapper) {
        // As with events: a shared signal whose value cannot be serialized used to fail silently,
        // leaving set() looking successful while nothing propagated.
        assertSerializable(value, "value of shared signal '" + name + "'");
        for (Session session : runtime.sessions()) {
            sendSignalUpdate(session, name, value, mapper);
        }
    }

    /**
     * Sends one target's value of a scoped signal to the sessions belonging to that target.
     *
     * <p>The frame carries the family's wire name, not the per-target one: every client subscribes
     * under the same name and is told only its own value, so no client learns that other targets
     * exist, let alone what they hold.</p>
     *
     * @param familyName the family's base wire name
     * @param value      the new value
     * @param scope      which targets the family is keyed by
     * @param target     the target whose sessions should receive it
     * @param mapper     object mapper for serialization
     */
    static void broadcastSignalUpdateScoped(ServerRuntime runtime, String familyName, Object value,
                                            Scope scope, String target, ObjectMapper mapper) {
        assertSerializable(value, "value of scoped signal '" + familyName + "'");
        for (Session session : runtime.sessions()) {
            if (matchesScope(session, scope, target)) {
                sendSignalUpdate(session, familyName, value, mapper);
            }
        }
    }

    /**
     * Sends a server-initiated push notification to a specific client session.
     *
     * @param session the target client WebSocket session
     * @param topic   the notification topic string
     * @param payload the message payload object
     *
     * <p><b>Under the hood:</b> Checks {@code session.isOpen()}. Serializes push frame (0x02 + topic string + serialized payload)
     * using {@link BinarySerializer#writeValue(GrowableBuffer, Object, ObjectMapper)} and transmits via {@link WsWrites#send}.</p>
     */
    public void sendPush(Session session, String topic, Object payload) {
        // Outside the catch below on purpose. Everything inside it is a failure to deliver one push,
        // which is logged and moved past; being handed another server's connection is a fault in the
        // caller, and swallowing it is what let a test watch the wrong server and pass.
        ServerRuntime runtime = runtimeOf(session);
        if (!session.isOpen()) {
            forgetClosed(session);
            return;
        }
        try {
            GrowableBuffer buffer = new GrowableBuffer();
            buffer.putInt(0);
            buffer.put(SyncFrameTypes.RPC_PUSH);
            BinarySerializer.writeString(buffer, topic);
            try (LazyHandles.Write write = LazyHandles.writingTo(runtime, session)) {
                BinarySerializer.writeValue(buffer, payload, mapper);
            }
            WsWrites.send(session, buffer.toByteArray());
        } catch (Exception e) {
            LOG.warning("[zeroz4j] Push error for session " + session.getId() + ": " + e.getMessage());
        }
    }

    /**
     * Processes incoming binary RPC frames from WebSocket clients.
     * Enforces {@code @Secured} and {@code @RolesAllowed} security rules prior to invocation.
     *
     * @param payload binary payload buffer received from client
     * @param session active WebSocket session
     *
     * <p><b>Under the hood:</b> Puts the frame on the session's ordered queue, which hands it to a thread when the frame before it has been handled. Parses correlation ID,
     * interface name, method name, and arguments. Validates against service and method registries. Checks security principal
     * and roles. Sets thread-local {@link RmiRequestContext}. Invokes target method via reflection. Writes success frame (0x01)
     * or error frame (0x0F) back to client.</p>
     */
    @OnMessage
    @SuppressWarnings("unchecked")
    public void processIncomingBinaryPayload(ByteBuffer payload, Session session) {
        byte[] data = new byte[payload.remaining()];
        payload.get(data);

        // The keepalive is answered here, on the container's own read thread, and never becomes a
        // task. It has to be answered even while the connection is at its in-flight limit - a
        // connection that stops answering pings is killed by the first proxy in the path - and it is
        // five bytes with no work behind it, so making it wait for a permit would cost the very
        // thing that makes it safe to answer before any check.
        // Refuses out loud a connection that belongs to another server in this process.
        ServerRuntime runtime = runtimeOf(session);

        if (isKeepaliveFrame(data)) {
            if (keepaliveAllowed(runtime, session.getId())) {
                sendPong(session);
            }
            return;
        }

        SessionFrameQueue frames = sessionQueues.get(session.getId());
        if (frames == null) {
            return; // Session is already closing or closed
        }

        // The frame goes on the end of this connection's queue and is handled after the one before
        // it - the order the browser wrote them in, which is the order the transport already
        // delivered them in. Queueing happens here, on the container's read thread, and waits when
        // the queue is full, so a connection that outruns the server is slowed down rather than
        // refused. Nothing in the framework queues a frame from inside a frame - this method is the
        // only place that queues, and only @OnMessage calls it - so waiting here cannot deadlock the
        // connection against itself.
        frames.submit(() -> {

            // Activate a CDI request context for this invocation: virtual threads have
            // none, and @RequestScoped beans (e.g. the per-tenant EmbeddedStorageManager
            // producer) must resolve inside service calls. A servlet container did this
            // implicitly; here it is the engine's job.
            try {
                jakarta.enterprise.context.control.RequestContextController requestContext =
                    lookup(jakarta.enterprise.context.control.RequestContextController.class).get();
                boolean contextActivated = requestContext.activate();
                try {
                    dispatchFrame(data, session);
                } finally {
                    if (contextActivated) {
                        // A frame still being handled when the container goes down finds the
                        // request scope already torn down, and deactivating it throws. That is the
                        // end of this connection either way. Undeploy is the ordinary way to reach
                        // it: the container stops while connections are still open.
                        try {
                            requestContext.deactivate();
                        } catch (RuntimeException containerGone) {
                            LOG.fine("[zeroz4j] The request scope for a frame on connection "
                                    + session.getId() + " was already gone when the frame "
                                    + "finished; the container is shutting down. "
                                    + containerGone.getMessage());
                        }
                    }
                }
            } catch (Throwable escaped) {
                // Nothing leaves a frame's thread. What escapes here is not a failed call - those
                // are answered on 0x0F further in - but a failure of the machinery around one, and
                // an uncaught exception on a thread named "zeroz-rmi-24" is a bare stack trace on
                // stderr belonging to no connection and no request.
                LOG.log(Level.SEVERE, "[zeroz4j] A frame on connection " + session.getId()
                        + " could not be handled: " + escaped, escaped);
            }
        });
    }

    /**
     * Whether a frame is the keepalive ping, decided without decoding anything else.
     *
     * <p>Reads only the correlation id and the service name. A frame that is too short or malformed
     * is not the keepalive, and is left to the ordinary path to report.</p>
     *
     * @param data the raw frame
     * @return true when this is a ping for the reserved keepalive service
     */
    static boolean isKeepaliveFrame(byte[] data) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(data);
            buffer.getInt();
            return SyncFrameTypes.KEEPALIVE_SERVICE.equals(BinarySerializer.readString(buffer));
        } catch (RuntimeException notAFrameWeUnderstand) {
            return false;
        }
    }

    /**
     * @return how many frames one connection may have waiting or being handled at once. The name
     *         this setting had before 0.8.0 is read when the current one is not set, so a
     *         deployment that configured it then does not silently fall back to the default.
     */
    private int maxQueuedFrames() {
        Integer configured = config().positiveInt(MAX_QUEUED_FRAMES_PROPERTY);
        if (configured != null) {
            return configured;
        }
        return config().positiveInt(MAX_CONCURRENT_FRAMES_PROPERTY, DEFAULT_MAX_QUEUED_FRAMES);
    }

    @SuppressWarnings("unchecked")
    private void dispatchFrame(byte[] data, Session session) {
        {
            {

                ByteBuffer buffer = ByteBuffer.wrap(data);
                int messageId = buffer.getInt();

                // The caller's identity is bound to this thread BEFORE anything in the frame is
                // decoded. Decoding runs application code - a lazy adapter, a custom validator - and
                // that code asking who is calling used to get whatever the previous frame left
                // behind, or nothing at all. Cleared in the finally at the end of the dispatch.
                Principal principal = (Principal) session.getUserProperties()
                        .get(RmiEndpointConfigurator.PRINCIPAL_KEY);
                Set<String> userRoles = (Set<String>) session.getUserProperties()
                        .get(RmiEndpointConfigurator.ROLES_KEY);
                if (userRoles == null) userRoles = Collections.emptySet();
                RmiRequestContext.setContext(principal, userRoles, session.getId(),
                        (String) session.getUserProperties().get(RmiEndpointConfigurator.TENANT_KEY),
                        (String) session.getUserProperties().get(RmiEndpointConfigurator.CLIENT_KEY),
                        localeOf(session));

                try {
                    String interfaceName = BinarySerializer.readString(buffer);
                    String methodName = BinarySerializer.readString(buffer);
                    int argumentCount = buffer.getInt();

                    // Framework-internal frames: shared signal subscriptions and client writes
                    if (SyncFrameTypes.SIGNALS_SERVICE.equals(interfaceName)) {
                        if ("subscribe".equals(methodName) && argumentCount == 1) {
                            Object signalName = BinarySerializer.readValue(buffer, mapper);
                            if (signalName instanceof String) {
                                ServerSignalTransport.handleSubscribe((String) signalName, session);
                            }
                        } else if ("set".equals(methodName) && argumentCount == 2) {
                            Object signalName = BinarySerializer.readValue(buffer, mapper);
                            Object newValue = BinarySerializer.readValue(buffer, mapper);
                            if (signalName instanceof String) {
                                ServerSignalTransport.handleClientSet((String) signalName, newValue, session);
                            }
                        }
                        return;
                    }

                    // Framework-internal frames: two-way LiveSync mutations
                    if (SyncFrameTypes.LIVESYNC_SERVICE.equals(interfaceName)) {
                        if ("mutate".equals(methodName) && argumentCount == 1) {
                            handleLiveMutation(buffer, session);
                        }
                        return;
                    }

                    // Framework-internal frames: resolving a lazy reference
                    if (SyncFrameTypes.LAZY_SERVICE.equals(interfaceName)) {
                        if ("resolve".equals(methodName) && argumentCount == 1) {
                            handleLazyResolve(messageId, buffer, session);
                        }
                        return;
                    }

                    // Framework-internal frames: re-synchronization after a reconnect
                    if (SyncFrameTypes.RESYNC_SERVICE.equals(interfaceName)) {
                        if ("sync".equals(methodName) && argumentCount == 1) {
                            Object handles = BinarySerializer.readValue(buffer, mapper);
                            if (handles instanceof java.util.List) {
                                handleResync((java.util.List<?>) handles, session);
                            }
                        }
                        return;
                    }

                    // The keepalive. Answered before the service registry is consulted and with no
                    // work of any kind: it exists to make a byte travel in each direction, and a
                    // heartbeat that did real work would be a way to load the server from outside.
                    if (SyncFrameTypes.KEEPALIVE_SERVICE.equals(interfaceName)) {
                        if (keepaliveAllowed(runtimeOf(session), session.getId())) {
                            sendPong(session);
                        }
                        return;
                    }

                    // Validate against service whitelist
                    Object beanInstance = serviceRegistry.get(interfaceName);
                    if (beanInstance == null) {
                        throw new RefusedException(FrameworkText.unknownService(interfaceName));
                    }

                    Map<String, Method> methods = methodRegistry.get(interfaceName);
                    Method targetMethod = methods != null ? methods.get(methodName) : null;
                    if (targetMethod == null) {
                        throw new NoSuchServiceMethodException(
                                FrameworkText.unknownMethod(methodName, interfaceName));
                    }

                    // --- Security enforcement ---
                    String methodKey = interfaceName + "#" + methodName;

                    // Check @Secured (interface or method level)
                    boolean requiresAuth = Boolean.TRUE.equals(securedInterfaces.get(interfaceName))
                                        || Boolean.TRUE.equals(securedMethods.get(methodKey));
                    if (requiresAuth && principal == null) {
                        throw new RefusedException(
                                FrameworkText.authenticationRequired(interfaceName, methodName));
                    }

                    // Check @RolesAllowed (method-level overrides interface-level)
                    Set<String> requiredRoles = methodRoles.getOrDefault(methodKey, Collections.emptySet());
                    if (requiredRoles.isEmpty()) {
                        requiredRoles = interfaceRoles.getOrDefault(interfaceName, Collections.emptySet());
                    }
                    if (!requiredRoles.isEmpty()) {
                        boolean hasRole = false;
                        for (String required : requiredRoles) {
                            if (userRoles.contains(required)) {
                                hasRole = true;
                                break;
                            }
                        }
                        if (!hasRole) {
                            throw new RefusedException(
                                FrameworkText.accessDenied(requiredRoles, userRoles));
                        }
                    }
                    // --- End security enforcement ---

                    Object[] extractedArguments = new Object[argumentCount];
                    for (int i = 0; i < argumentCount; i++) {
                        extractedArguments[i] = BinarySerializer.readValue(buffer, mapper);
                    }

                    // Model validation annotations are authoritative server-side:
                    // reject calls whose arguments violate their declared constraints.
                    for (Object arg : extractedArguments) {
                        validateArgument(arg);
                    }

                    Object executionResult = targetMethod.invoke(beanInstance, extractedArguments);

                    GrowableBuffer responseBuffer = new GrowableBuffer();
                    responseBuffer.putInt(messageId);
                    responseBuffer.put(SyncFrameTypes.RPC_RESPONSE);
                    try (LazyHandles.Write write =
                                 LazyHandles.writingTo(runtimeOf(session), session)) {
                        BinarySerializer.writeValue(responseBuffer, executionResult, mapper);
                    }
                    WsWrites.send(session, responseBuffer.toByteArray());

                } catch (Throwable ex) {
                    Throwable actual = ex;
                    if (ex instanceof InvocationTargetException) {
                        actual = ex.getCause();
                    }
                    sendError(session, messageId, actual);
                } finally {
                    RmiRequestContext.clear();
                }
            }
        }
    }

    /**
     * Answers a failed call on {@code 0x0F RPC_ERROR}, with a message the caller is allowed to read.
     *
     * @param session   the caller
     * @param messageId the correlation id to answer on
     * @param failure   what went wrong
     */
    private static void sendError(Session session, int messageId, Throwable failure) {
        String reference = newErrorReference();
        LOG.log(Level.SEVERE, "[zeroz4j] RMI error [ref " + reference + "]: "
                + failure.getMessage(), failure);
        try {
            GrowableBuffer errorBuffer = new GrowableBuffer(512);
            errorBuffer.putInt(messageId);
            errorBuffer.put(SyncFrameTypes.RPC_ERROR);
            BinarySerializer.writeString(errorBuffer,
                    clientSafeMessage(failure, reference, localeOf(session)));
            WsWrites.send(session, errorBuffer.toByteArray());
        } catch (Exception ioEx) {
            LOG.warning("[zeroz4j] Failed to send error: " + ioEx.getMessage());
        }
    }

    /**
     * Decides what a caller is told about a failure.
     *
     * <p>Two kinds of message travel word for word. One is a {@link ClientVisibleException}, which is
     * how an application says "this sentence is for the caller". The other is the framework's own
     * refusals - authentication required, access denied, unknown service, unknown method, failed
     * argument validation - which exist to be read and which clients already act on.</p>
     *
     * <p>Everything else is unplanned, and the message of an unplanned failure describes the
     * machinery: class names, field names, query fragments, container internals. An anonymous caller
     * can provoke those failures deliberately and read the system's shape out of the answers. So the
     * caller gets one sentence and a short reference code, and the real message and stack trace go
     * to the server log under the same code, which is what lets support match a user's screenshot to
     * a log line.</p>
     *
     * @param failure   what went wrong
     * @param reference the code that also appears in the log line
     * @return the message to put on the wire
     */
    static String clientSafeMessage(Throwable failure, String reference) {
        return clientSafeMessage(failure, reference, RmiRequestContext.getLocale());
    }

    /**
     * The same decision, said in one named language.
     *
     * <p>A refusal thrown as a {@link Message} is turned into words <b>here</b> and nowhere else,
     * because this is the one place that knows both what happened and who is being told. The log
     * line above keeps English from the same value.</p>
     *
     * @param failure   what went wrong
     * @param reference the code that also appears in the log line
     * @param locale    the caller's language
     * @return the message to put on the wire
     * @since 0.9.0
     */
    static String clientSafeMessage(Throwable failure, String reference, java.util.Locale locale) {
        if (failure instanceof CarriesClientMessage) {
            Message carried = ((CarriesClientMessage) failure).clientMessage();
            if (carried != null) {
                return ServerMessages.render(carried, locale);
            }
        }
        if (failure instanceof ClientVisibleException
                || failure instanceof SecurityException
                || failure instanceof NoSuchMethodException) {
            String message = failure.getMessage();
            if (message != null && !message.isEmpty()) {
                return message;
            }
        }
        return ServerMessages.render(FrameworkText.unexpectedFailure(reference), locale);
    }

    /**
     * The language of whoever is on the other end of one connection.
     *
     * <p>Resolved once at the handshake and kept on the connection, so every frame it sends is
     * answered in the same language with nothing looked up again.</p>
     *
     * @param session the connection
     * @return the locale; the deployment's own when the connection carries none
     */
    static java.util.Locale localeOf(Session session) {
        Object tag = session == null ? null
                : session.getUserProperties().get(RmiEndpointConfigurator.LOCALE_KEY);
        if (tag instanceof java.util.Locale) {
            return (java.util.Locale) tag;
        }
        if (tag instanceof String) {
            return LocaleResolution.localeOf((String) tag);
        }
        return RmiRequestContext.getLocale();
    }

    /** A short code, unique enough to find one log line among a day of them. */
    private static String newErrorReference() {
        long bits = java.util.concurrent.ThreadLocalRandom.current().nextLong() & 0xFFFFFFFFL;
        String hex = Long.toHexString(bits);
        while (hex.length() < 8) {
            hex = "0" + hex;
        }
        return hex;
    }

    /**
     * Whether this connection may be answered another keepalive ping right now.
     *
     * <p>The keepalive answers before any other check, by design - that is what makes it cheap. It
     * is therefore also the cheapest thing to send in a loop, and every ping costs a task on the
     * connection's executor and a frame on the wire. A connection that pings faster than
     * {@link #DEFAULT_PING_MIN_INTERVAL_MILLIS} is not keeping itself alive; the extra pings are
     * dropped and nothing is written back.</p>
     *
     * @param sessionId the connection that pinged
     * @return true when a pong should be sent
     */
    private static boolean keepaliveAllowed(ServerRuntime runtime, String sessionId) {
        return runtime.keepaliveAllowed(sessionId, pingMinIntervalMillis(runtime));
    }

    private static long pingMinIntervalMillis(ServerRuntime runtime) {
        return runtime.config().positiveLong(PING_MIN_INTERVAL_PROPERTY,
                DEFAULT_PING_MIN_INTERVAL_MILLIS);
    }

    /** Forgets every connection's keepalive budget. Test support only. */
    void clearKeepaliveBudgetForTesting() {
        runtime().clearKeepaliveBudget();
    }
}
