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

import com.zeroz4j.api.LiveMutexRpc;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.Session;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/**
 * Where a browser's lock request arrives, and where it is allowed in or turned away.
 *
 * <h2>The rule</h2>
 *
 * <p><b>A client may lock only an object the server actually sent it.</b> An object handle is the
 * name an object travels under, and a name is not a permission: an object nested inside a broadcast
 * event or a shared signal goes out with its name attached, so everyone who received the outer
 * payload learned the names of things they were never given. Before this check, any connected
 * browser — signed in or not — could name any handle at all and take its lock.</p>
 *
 * <p>The test is prior disclosure, not identity. The server already writes down which handles it has
 * written toward each browser ({@link Disclosures}), and that record is what decides. No sign-in is
 * required, because the shipped examples with no login at all must keep working.</p>
 *
 * <h2>What that fixes</h2>
 *
 * <ul>
 *   <li>Nobody can take the lock on an object they were never shown, so nobody can hold other
 *       people's editing sessions hostage for the full wait.</li>
 *   <li>A name nobody was sent never reaches the lock table, so a loop over invented names creates
 *       nothing to remember.</li>
 * </ul>
 *
 * <h2>Releasing is not checked the same way</h2>
 *
 * <p>{@link #releaseLock(String)} tests ownership instead, which here is the stronger test: the only
 * lock a caller can free is one it holds, and it can only hold a lock it was allowed to take. Adding
 * the disclosure check would make things worse, not better — a browser's record can expire or be
 * evicted between taking a lock and giving it up, and a release refused for that reason would strand
 * the lock until the session closed.</p>
 */
@ApplicationScoped
public class LiveMutexRpcImpl implements LiveMutexRpc {

    /**
     * Set to {@code true} to allow locking only on connections that signed in.
     *
     * <p>Off by default, and it must stay off by default: several shipped examples have no login of
     * any kind and would stop working. An application that does have logins can turn it on.</p>
     */
    static final String REQUIRE_AUTHENTICATION_PROPERTY = "zeroz.livemutex.requireAuthentication";

    /** How much of a rejected handle is quoted back, so a hostile name cannot flood a log line. */
    private static final int MAX_QUOTED_HANDLE = 64;

    @Inject
    LiveMutexManager manager;

    /**
     * Takes the lock for an object on behalf of the calling browser, waiting if somebody else has it.
     *
     * @param objectId the object's handle
     * @throws IllegalStateException if there is no WebSocket session on this thread
     * @throws SecurityException     if this browser was never sent that object, or if
     *                               {@code zeroz.livemutex.requireAuthentication} is on and the
     *                               connection is anonymous
     *
     * <p><b>Under the hood:</b> asks {@link Disclosures#wasDisclosedTo} whether this connection's
     * browser was sent the object, then calls {@code manager.lock(objectId, "session:" + sessionId)}.
     * Arrives on {@code 0x01 RPC_CALL}; a refusal is answered on {@code 0x0F RPC_ERROR} with the
     * sentence below, unchanged.</p>
     */
    @Override
    public void acquireLock(String objectId) {
        String sessionId = requireSession();
        if (objectId == null || objectId.isEmpty()) {
            throw new SecurityException("Refused: a lock request named no object.");
        }
        if (requireAuthentication() && RmiRequestContext.getPrincipal() == null) {
            throw new SecurityException(
                    "Refused: this server only lets signed-in users lock items for editing "
                    + "(" + REQUIRE_AUTHENTICATION_PROPERTY + " is on). Sign in and try again.");
        }
        if (!Disclosures.wasDisclosedTo(callerConnection(sessionId), objectId)) {
            throw new SecurityException(
                    "Refused: this client was never sent the item " + quote(objectId)
                    + ", so it cannot lock it. You may lock only items the server has sent you. "
                    + "If you did hold this item, the server's record of sending it has since "
                    + "expired (see zeroz.disclosure.idleHours) — fetch the item again from your "
                    + "service and lock the copy you get back.");
        }
        manager.lock(objectId, "session:" + sessionId);
    }

    /**
     * Gives up the lock for an object, if this session is the one holding it.
     *
     * <p>Does nothing when this session is not the holder. That ownership test is what protects it,
     * so it deliberately does not repeat the disclosure check {@link #acquireLock(String)} makes.</p>
     *
     * @param objectId the object's handle
     * @throws IllegalStateException if there is no WebSocket session on this thread
     *
     * <p><b>Under the hood:</b> calls {@code manager.unlock(objectId, "session:" + sessionId)}.
     * Arrives on {@code 0x01 RPC_CALL}.</p>
     */
    @Override
    public void releaseLock(String objectId) {
        String sessionId = requireSession();
        manager.unlock(objectId, "session:" + sessionId);
    }

    // ------------------------------------------------------------------ internals

    /** @return the calling connection's session id */
    private static String requireSession() {
        String sessionId = RmiRequestContext.getSessionId();
        if (sessionId == null) {
            throw new IllegalStateException("No active WebSocket session for RPC.");
        }
        return sessionId;
    }

    /**
     * The caller's connection, as far as {@link Disclosures} needs to know it.
     *
     * <p>{@link Disclosures#wasDisclosedTo} takes the connection because it keys its record by the
     * browser id the connection carries, falling back to the session id when there is none. An RMI
     * method does not get handed the connection object — it gets the caller's identity on the thread
     * — so this supplies exactly the two answers that question consults, from that identity. Nothing
     * else about the connection is available here, and asking for it fails loudly rather than
     * quietly returning a wrong answer.</p>
     *
     * @param sessionId the calling connection's session id
     * @return a view of the connection that answers {@code getId()} and {@code getUserProperties()}
     */
    private static Session callerConnection(String sessionId) {
        Map<String, Object> properties = new HashMap<>();
        String clientId = RmiRequestContext.getClientId();
        if (clientId != null && !clientId.isEmpty()) {
            properties.put(RmiEndpointConfigurator.CLIENT_KEY, clientId);
        }
        return (Session) Proxy.newProxyInstance(
                LiveMutexRpcImpl.class.getClassLoader(),
                new Class<?>[] { Session.class },
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getId":
                            return sessionId;
                        case "getUserProperties":
                            return properties;
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "equals":
                            return proxy == args[0];
                        case "toString":
                            return "lock caller on session " + sessionId;
                        default:
                            throw new UnsupportedOperationException(
                                    "An RMI thread knows who is calling, not which socket they are "
                                    + "on: Session." + method.getName() + "() cannot be answered "
                                    + "here.");
                    }
                });
    }

    /** @return whether locking is restricted to signed-in connections */
    private static boolean requireAuthentication() {
        return Boolean.parseBoolean(System.getProperty(REQUIRE_AUTHENTICATION_PROPERTY));
    }

    /**
     * @param handle a handle the caller supplied
     * @return it, shortened, safe to put in a message
     */
    private static String quote(String handle) {
        return handle.length() <= MAX_QUOTED_HANDLE
                ? handle
                : handle.substring(0, MAX_QUOTED_HANDLE) + "...";
    }
}
