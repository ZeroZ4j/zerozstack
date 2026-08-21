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
import com.zeroz4j.api.validation.ValidationRegistry;
import com.zeroz4j.api.RolesAllowed;
import com.zeroz4j.api.Secured;
import jakarta.annotation.PostConstruct;
import java.util.logging.Logger;
import java.util.logging.Level;
import jakarta.inject.Inject;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.RejectedExecutionException;

/**
 * Server-side Jakarta EE WebSocket endpoint for zeroz4j.
 * Listens on '/wasm-rmi' and uses Project Loom's Virtual Threads to dispatch
 * binary RPC method invocations to CDI beans.
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>CDI Discovery &amp; Scanning:</b> At startup ({@link #scanServiceRegistry()}), scans the CDI bean manager for beans implementing {@link RmiService}
 *       interfaces, builds service/method reflection registries, and populates security whitelists ({@code @Secured}, {@code @RolesAllowed}).</li>
 *   <li><b>Virtual Thread Concurrency:</b> Per-session {@link ExecutorService} (Project Loom virtual threads) handles inbound frames without blocking I/O threads.</li>
 *   <li><b>Frame Dispatch:</b> Operates on incoming binary frames in {@link #processIncomingBinaryPayload(ByteBuffer, Session)}. Reads correlation ID, interface name, method name, unmarshals arguments using {@link BinarySerializer}, enforces security, populates {@link RmiRequestContext}, invokes method via reflection, and writes return value (0x01 SUCCESS) or exception (0x0F ERROR).</li>
 * </ul>
 */
@ServerEndpoint(value = "/wasm-rmi", configurator = RmiEndpointConfigurator.class)
@ApplicationScoped
public class WasmRmiServerEngine implements EventPublisher {

    private static final Logger LOG = Logger.getLogger(WasmRmiServerEngine.class.getName());

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

    @Inject
    SyncEngine syncEngine;

    @Inject
    ObjectMapper mapper;

    @Inject
    LiveMutexManager liveMutexManager;

    /**
     * STATIC on purpose: Tomcat's WebSocket container instantiates a @ServerEndpoint PER
     * CONNECTION (the configurator does not override getEndpointInstance), while pushers
     * obtain the CDI @ApplicationScoped instance - a per-instance set left broadcastPush
     * talking to an always-empty list. Shared statically, every instance sees every session.
     */
    private static final Set<Session> activeSessions = ConcurrentHashMap.newKeySet();
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

    /** Structured Concurrency: Map each WebSocket session to its own Virtual Thread Executor */
    private final Map<String, ExecutorService> sessionExecutors = new ConcurrentHashMap<>();

    /** Session id -> when that session was last answered a keepalive ping. */
    private static final Map<String, Long> pingLastAnswered = new ConcurrentHashMap<>();

    /** Session id -> permits for frames of that session being decoded and executed right now. */
    private final Map<String, java.util.concurrent.Semaphore> sessionPermits = new ConcurrentHashMap<>();

    /**
     * How many frames from one connection may be decoding and executing at the same time.
     *
     * <p>The container delivers one message at a time per connection, but handing each one straight
     * to a thread-per-task executor undoes that: the read loop is free to deliver the next message
     * immediately, so a single connection could have as many frames in flight as it could write
     * bytes. Decoding is where a small message becomes a large object graph, so unbounded
     * concurrency multiplies the worst case a message-size limit is meant to cap.</p>
     *
     * <p>Thirty-two, and a caller that finds no permit waits rather than being refused. Nothing is
     * dropped and no call fails, so the number does not have to exceed any burst an application can
     * produce - a screen firing a dozen calls on load, or a reconnect flushing queued edits, is
     * simply served a few at a time. It only has to be high enough that ordinary traffic never
     * queues noticeably, and low enough to be a real ceiling. Waiting happens on that one
     * connection's read loop, which is the backpressure wanted; other connections are untouched.</p>
     */
    static final String MAX_CONCURRENT_FRAMES_PROPERTY = "zeroz.ws.maxConcurrentFramesPerSession";
    static final int DEFAULT_MAX_CONCURRENT_FRAMES = 32;

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
            BeanManager bm = CDI.current().getBeanManager();
            for (Bean<?> bean : bm.getBeans(Object.class)) {
                Class<?> beanClass = bean.getBeanClass();
                for (Class<?> iface : beanClass.getInterfaces()) {
                    if (iface.isAnnotationPresent(RmiService.class)) {
                        String ifaceName = iface.getName();
                        Object instance = CDI.current().select(iface).get();
                        serviceRegistry.put(ifaceName, instance);

                        // Interface-level security
                        boolean ifaceSecured = iface.isAnnotationPresent(Secured.class);
                        securedInterfaces.put(ifaceName, ifaceSecured);

                        RolesAllowed ifaceRolesAnn = iface.getAnnotation(RolesAllowed.class);
                        if (ifaceRolesAnn != null) {
                            Set<String> roles = new HashSet<>(Arrays.asList(ifaceRolesAnn.value()));
                            interfaceRoles.put(ifaceName, roles);
                            RmiEndpointConfigurator.knownRoles.addAll(roles);
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
                                RmiEndpointConfigurator.knownRoles.addAll(roles);
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
        ServerSignalTransport.install(mapper);
        Disclosures.install();
    }

    /**
     * Shuts down all virtual thread executors gracefully upon bean destruction.
     *
     * <p><b>Under the hood:</b> Executed via {@code @PreDestroy}. Iterates through {@code sessionExecutors} and calls {@code shutdownNow()}.</p>
     */
    @PreDestroy
    public void shutdown() {
        for (ExecutorService exec : sessionExecutors.values()) {
            exec.shutdownNow();
        }
        sessionExecutors.clear();
    }

    /**
     * Handles WebSocket connection open lifecycle events.
     *
     * @param session the newly connected WebSocket session
     * @param config  the endpoint configuration containing handshake user properties
     *
     * <p><b>Under the hood:</b> Adds session to {@code activeSessions}, creates a virtual thread executor for the session in {@code sessionExecutors},
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

        activeSessions.add(session);
        applyWebSocketLimits(session);
        // Threads come from the resolved factory rather than being created here, so a deployment
        // inside a Jakarta EE server can supply a ManagedThreadFactory whose threads carry the
        // container's naming, transaction and identity context. With no provider registered this is
        // a virtual-thread factory, identical to newVirtualThreadPerTaskExecutor().
        sessionExecutors.put(session.getId(),
                Executors.newThreadPerTaskExecutor(SessionThreads.factory()));
        sessionPermits.put(session.getId(),
                new java.util.concurrent.Semaphore(maxConcurrentFrames(), true));

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

        // Before the first byte is written to this connection: everything the server sends it is
        // recorded against its browser, and that record is what later decides whether it may ask for
        // an object again. Keyed by browser rather than by connection, so a reconnect can re-sync.
        Disclosures.sessionOpened(session);

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
     * <p><b>Under the hood:</b> Removes session from {@code activeSessions}, shuts down virtual thread executor,
     * releases all live mutex locks owned by the session via {@link LiveMutexManager#releaseAll}, and unregisters session from {@code syncEngine}.</p>
     */
    @OnClose
    public void onClose(Session session) {
        activeSessions.remove(session);

        // Structured Concurrency: Terminate all tasks for this session
        ExecutorService sessionExecutor = sessionExecutors.remove(session.getId());
        if (sessionExecutor != null) {
            sessionExecutor.shutdownNow();
        }

        // Release any distributed locks held by this session
        if (liveMutexManager != null) {
            liveMutexManager.releaseAll("session:" + session.getId());
        }

        // LiveSync: clean up the SyncSession
        syncEngine.removeSession(session.getId());

        // Shared signals: drop parked subscriptions
        ServerSignalTransport.sessionClosed(session);

        // Lazy references: release the handles disclosed to this session
        LazyHandles.sessionClosed(session.getId());

        // Keepalive budget and in-flight permits for a session that no longer exists.
        pingLastAnswered.remove(session.getId());
        sessionPermits.remove(session.getId());

        // The record of what this browser was sent survives on purpose: the next connection is the
        // same browser and must still be able to re-sync. Only the connection mapping goes.
        Disclosures.sessionClosed(session.getId());

        // Tell the application, after framework cleanup: apps keep registries keyed by session id
        // (scoped pushes, rooms) and previously had no way to learn a session was gone.
        try {
            Principal principal = (Principal) session.getUserProperties().get(RmiEndpointConfigurator.PRINCIPAL_KEY);
            CDI.current().getBeanManager().getEvent()
                .select(SessionClosedEvent.class)
                .fire(new SessionClosedEvent(session.getId(), principal != null ? principal.getName() : null));
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
    private static void applyWebSocketLimits(Session session) {
        Integer configuredMaxBinary = positiveIntProperty(MAX_BINARY_BYTES_PROPERTY);
        int maxBinaryBytes = configuredMaxBinary != null ? configuredMaxBinary : DEFAULT_MAX_BINARY_BYTES;
        session.setMaxBinaryMessageBufferSize(maxBinaryBytes);

        Integer idleMinutes = positiveIntProperty(IDLE_TIMEOUT_MINUTES_PROPERTY);
        if (idleMinutes != null) {
            session.setMaxIdleTimeout(idleMinutes * 60_000L);
        }

        if (LIMITS_REPORTED.compareAndSet(false, true)) {
            LOG.info("[zeroz4j] Largest binary message accepted: " + maxBinaryBytes + " bytes ("
                    + (configuredMaxBinary != null ? "set by " : "framework default; change with ")
                    + MAX_BINARY_BYTES_PROPERTY + "). A message over that closes the connection"
                    + " without an error response.");
        }
    }

    /**
     * Guards the startup report above, so the limits are named once per server rather than once per
     * connection. Declared beside the method that uses it rather than with the other fields, which
     * are a different concern.
     */
    private static final java.util.concurrent.atomic.AtomicBoolean LIMITS_REPORTED =
            new java.util.concurrent.atomic.AtomicBoolean();

    /**
     * Reads a positive integer system property.
     *
     * @return the value, or null when unset, unparseable or not positive — an unusable setting is
     *         logged and ignored rather than applied, because a zero or negative limit means
     *         something different to each container. The caller then falls back to whatever it
     *         would have used with the property unset.
     */
    private static Integer positiveIntProperty(String name) {
        String configured = System.getProperty(name);
        if (configured == null || configured.trim().isEmpty()) {
            return null;
        }
        try {
            int value = Integer.parseInt(configured.trim());
            if (value <= 0) {
                LOG.warning("[zeroz4j] Ignoring " + name + "=" + configured
                        + ": it must be a positive number.");
                return null;
            }
            return value;
        } catch (NumberFormatException ex) {
            LOG.warning("[zeroz4j] Ignoring non-numeric " + name + "='" + configured + "'.");
            return null;
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
     * <p><b>Under the hood:</b> Iterates through {@code activeSessions} and calls {@link #sendPush(Session, String, Object)} for each.</p>
     */
    public void broadcastPush(String topic, Object payload) {
        // Serialize once up front so an unserializable payload reaches the CALLER. Previously the
        // failure was caught per session and logged, so publish() appeared to succeed while the event
        // reached nobody -- the single most confusing failure in the framework.
        assertSerializable(payload, "event payload for topic '" + topic + "'");
        for (Session session : activeSessions) {
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
        for (Session session : activeSessions) {
            if (matchesScope(session, scope, target)) {
                sendPush(session, topic.name(), payload);
            }
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
        for (Session session : activeSessions) {
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
        for (Session session : activeSessions) {
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
            session.close(new jakarta.websocket.CloseReason(
                    jakarta.websocket.CloseReason.CloseCodes.VIOLATED_POLICY, truncate(reason)));
            activeSessions.remove(session);
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
    static void addActiveSessionForTesting(Session session) {
        activeSessions.add(session);
    }

    /** Empties the active-session set. Test support only. */
    static void clearActiveSessionsForTesting() {
        activeSessions.clear();
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

            Object lazy = LazyHandles.resolve(handle, session.getId());
            if (lazy == null) {
                throw new SecurityException(
                        "Unknown or unauthorized lazy handle: " + handle);
            }

            com.zeroz4j.api.LazyAdapter adapter = com.zeroz4j.api.BinaryRegistry.getLazyAdapter();
            if (adapter == null) {
                throw new IllegalStateException("No LazyAdapter installed on the server");
            }
            Object contents = adapter.contentsOf(lazy);

            GrowableBuffer responseBuffer = new GrowableBuffer();
            responseBuffer.putInt(messageId);
            responseBuffer.put(SyncFrameTypes.RPC_RESPONSE);
            LazyHandles.setCurrentSession(session.getId());
            try {
                BinarySerializer.writeValue(responseBuffer, contents, mapper);
            } finally {
                LazyHandles.setCurrentSession(null);
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
     * treated identically: no frame, no error, and a counted log line. The second case means the
     * server restarted since the client fetched the object (the registry is in memory). Either way
     * the client's copy stays as it is and the application re-fetches it the way it first obtained
     * it.
     *
     * @param handles the handle list from the client
     * @param session the reconnected session
     */
    void handleResync(java.util.List<?> handles, Session session) {
        int sent = 0;
        int unknown = 0;
        int undisclosed = 0;
        for (Object handleObj : handles) {
            if (!(handleObj instanceof String)) {
                continue;
            }
            if (!Disclosures.wasDisclosedTo(session, (String) handleObj)) {
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
                LazyHandles.setCurrentSession(session.getId());
                try {
                    BinarySerializer.writeValue(buffer, obj, mapper);
                } finally {
                    LazyHandles.setCurrentSession(null);
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
                + " object(s) re-sent, " + unknown + " handle(s) unknown -- the server restarted "
                + "since the client fetched them; those objects stay stale until the application "
                + "re-fetches them.");
        } else if (undisclosed == 0 && sent > 0) {
            LOG.info("[zeroz4j] Re-sync for session " + session.getId() + ": " + sent + " object(s) re-sent.");
        }
    }

    void handleLiveMutation(ByteBuffer buffer, Session session) {
        int payloadStart = buffer.position();

        Set<String> userRoles = (Set<String>) session.getUserProperties().get(RmiEndpointConfigurator.ROLES_KEY);
        LiveMutationGuard guard = new LiveMutationGuard(mapper, userRoles);

        Object proposed;
        String refusal = null;
        ObjectMapper tempMapper = new ObjectMapper();
        ObjectMapper.setResolutionGuard(guard);
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
            String reason = "The change claims to be a " + proposed.getClass().getSimpleName()
                + " but names an object the server holds as a " + canonical.getClass().getSimpleName()
                + ". Nothing was changed.";
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
            try {
                applied = BinarySerializer.readValue(buffer, mapper); // in-place apply
            } catch (LiveMutationGuard.Denied denied) {
                applied = null;
                refusal = denied.reason();
            } finally {
                ObjectMapper.setResolutionGuard(null);
            }
            if (refusal != null) {
                rejectNestedWrite(session, guard.rootHandleId(), refusal);
                return;
            }
            Principal principal = (Principal) session.getUserProperties().get(RmiEndpointConfigurator.PRINCIPAL_KEY);
            try {
                for (LiveMutationListener listener : CDI.current().select(LiveMutationListener.class)) {
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
            String reason;
            if (writable == null) {
                reason = authorizedType.getName() + " is not @ClientWritable";
                LOG.warning("[zeroz4j] Rejected live mutation of non-@ClientWritable "
                    + authorizedType.getName() + " from session " + session.getId());
            } else if (!violations.isEmpty()) {
                reason = "Validation failed: " + String.join("; ", violations);
                LOG.info("[zeroz4j] Rejected invalid live mutation of "
                    + authorizedType.getSimpleName() + ": " + String.join("; ", violations));
            } else {
                reason = "Requires one of the roles " + Arrays.toString(writable.value());
                LOG.warning("[zeroz4j] Rejected live mutation of "
                    + authorizedType.getSimpleName() + " from session " + session.getId()
                    + ": " + reason);
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
    private void rejectNestedWrite(Session session, String rootHandleId, String reason) {
        Object root = rootHandleId != null ? mapper.getObject(rootHandleId) : null;
        String className = root != null ? root.getClass().getName() : "";
        LOG.warning("[zeroz4j] Rejected live mutation from session " + session.getId()
            + ": " + reason + " (outermost object " + className + ")");
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
     * @param reason    human-readable explanation
     */
    private void sendMutationRejected(Session session, String className, String reason) {
        try {
            GrowableBuffer buffer = new GrowableBuffer(256);
            buffer.putInt(0);
            buffer.put(SyncFrameTypes.REJECT);
            BinarySerializer.writeString(buffer, className);
            BinarySerializer.writeString(buffer, reason);
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
            throw new ClientVisibleException("Validation failed for "
                + arg.getClass().getSimpleName() + ": " + String.join("; ", violations));
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
            activeSessions.remove(session);
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
            activeSessions.remove(session);
            return;
        }
        try {
            GrowableBuffer buffer = new GrowableBuffer();
            buffer.putInt(0);
            buffer.put(SyncFrameTypes.SIGNAL_UPD);
            BinarySerializer.writeString(buffer, name);
            LazyHandles.setCurrentSession(session.getId());
            try {
                BinarySerializer.writeValue(buffer, value, mapper);
            } finally {
                LazyHandles.setCurrentSession(null);
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
    static void broadcastSignalUpdate(String name, Object value, ObjectMapper mapper) {
        // As with events: a shared signal whose value cannot be serialized used to fail silently,
        // leaving set() looking successful while nothing propagated.
        assertSerializable(value, "value of shared signal '" + name + "'");
        for (Session session : activeSessions) {
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
    static void broadcastSignalUpdateScoped(String familyName, Object value, Scope scope,
                                            String target, ObjectMapper mapper) {
        assertSerializable(value, "value of scoped signal '" + familyName + "'");
        for (Session session : activeSessions) {
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
        if (!session.isOpen()) {
            activeSessions.remove(session);
            return;
        }
        try {
            GrowableBuffer buffer = new GrowableBuffer();
            buffer.putInt(0);
            buffer.put(SyncFrameTypes.RPC_PUSH);
            BinarySerializer.writeString(buffer, topic);
            LazyHandles.setCurrentSession(session.getId());
            try {
                BinarySerializer.writeValue(buffer, payload, mapper);
            } finally {
                LazyHandles.setCurrentSession(null);
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
     * <p><b>Under the hood:</b> Submits processing task to session's virtual thread executor. Parses correlation ID,
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
        if (isKeepaliveFrame(data)) {
            if (keepaliveAllowed(session.getId())) {
                sendPong(session);
            }
            return;
        }

        ExecutorService sessionExecutor = sessionExecutors.get(session.getId());
        if (sessionExecutor == null) {
            return; // Session is already closing or closed
        }

        // One connection may have only so many frames decoding at once. Acquired here, on the
        // container's read thread, so a connection that outruns the limit is slowed down rather than
        // refused. Nothing in the framework submits to a session's executor from inside a task on
        // that same executor - this method is the only submitter, and only @OnMessage calls it - so
        // waiting here cannot deadlock the connection against itself.
        java.util.concurrent.Semaphore permits = sessionPermits.computeIfAbsent(session.getId(),
                k -> new java.util.concurrent.Semaphore(maxConcurrentFrames(), true));
        try {
            permits.acquire();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            LOG.warning("[zeroz4j] Dropped incoming message: the read thread was interrupted.");
            return;
        }

        boolean submitted = false;
        try {
            sessionExecutor.submit(() -> {

                // Activate a CDI request context for this invocation: virtual threads have
                // none, and @RequestScoped beans (e.g. the per-tenant EmbeddedStorageManager
                // producer) must resolve inside service calls. A servlet container did this
                // implicitly; here it is the engine's job.
                try {
                    jakarta.enterprise.context.control.RequestContextController requestContext =
                        CDI.current().select(jakarta.enterprise.context.control.RequestContextController.class).get();
                    boolean contextActivated = requestContext.activate();
                    try {
                        dispatchFrame(data, session);
                    } finally {
                        if (contextActivated) {
                            requestContext.deactivate();
                        }
                    }
                } finally {
                    permits.release();
                }
            });
            submitted = true;
        } catch (RejectedExecutionException e) {
            LOG.warning("[zeroz4j] Dropped incoming message because server is shutting down.");
        } finally {
            if (!submitted) {
                permits.release();
            }
        }
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

    /** @return the configured in-flight limit for one connection */
    private static int maxConcurrentFrames() {
        Integer configured = positiveIntProperty(MAX_CONCURRENT_FRAMES_PROPERTY);
        return configured != null ? configured : DEFAULT_MAX_CONCURRENT_FRAMES;
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
                        (String) session.getUserProperties().get(RmiEndpointConfigurator.CLIENT_KEY));

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
                        if (keepaliveAllowed(session.getId())) {
                            sendPong(session);
                        }
                        return;
                    }

                    // Validate against service whitelist
                    Object beanInstance = serviceRegistry.get(interfaceName);
                    if (beanInstance == null) {
                        throw new SecurityException("Rejected RMI call to unregistered service: " + interfaceName);
                    }

                    Map<String, Method> methods = methodRegistry.get(interfaceName);
                    Method targetMethod = methods != null ? methods.get(methodName) : null;
                    if (targetMethod == null) {
                        throw new NoSuchMethodException("No method '" + methodName + "' on service: " + interfaceName);
                    }

                    // --- Security enforcement ---
                    String methodKey = interfaceName + "#" + methodName;

                    // Check @Secured (interface or method level)
                    boolean requiresAuth = Boolean.TRUE.equals(securedInterfaces.get(interfaceName))
                                        || Boolean.TRUE.equals(securedMethods.get(methodKey));
                    if (requiresAuth && principal == null) {
                        throw new SecurityException("Authentication required for: " + interfaceName + "#" + methodName);
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
                            throw new SecurityException("Access denied: requires role "
                                + requiredRoles + " but user has " + userRoles);
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
                    LazyHandles.setCurrentSession(session.getId());
                    try {
                        BinarySerializer.writeValue(responseBuffer, executionResult, mapper);
                    } finally {
                        LazyHandles.setCurrentSession(null);
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
            BinarySerializer.writeString(errorBuffer, clientSafeMessage(failure, reference));
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
        if (failure instanceof ClientVisibleException
                || failure instanceof SecurityException
                || failure instanceof NoSuchMethodException) {
            String message = failure.getMessage();
            if (message != null && !message.isEmpty()) {
                return message;
            }
        }
        return "The server could not complete this request. Reference: " + reference;
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
    private static boolean keepaliveAllowed(String sessionId) {
        long minimumGap = pingMinIntervalMillis();
        long now = System.currentTimeMillis();
        Long previous = pingLastAnswered.get(sessionId);
        if (previous != null && now - previous < minimumGap) {
            return false;
        }
        pingLastAnswered.put(sessionId, now);
        return true;
    }

    private static long pingMinIntervalMillis() {
        Integer configured = positiveIntProperty(PING_MIN_INTERVAL_PROPERTY);
        return configured != null ? configured.longValue() : DEFAULT_PING_MIN_INTERVAL_MILLIS;
    }

    /** Forgets every connection's keepalive budget. Test support only. */
    static void clearKeepaliveBudgetForTesting() {
        pingLastAnswered.clear();
    }
}
