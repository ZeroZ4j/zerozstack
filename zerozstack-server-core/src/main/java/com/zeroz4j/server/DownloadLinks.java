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

import java.security.Principal;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.enterprise.inject.spi.CDI;

/**
 * The addresses the server has issued for files, and the {@link FileDownloadHandler} lookup.
 *
 * <p>An address is a random token bound to the browser it was issued to (the signed browser-id
 * cookie), to the kind and id the page asked for, and to the identity of the live connection. Unlike
 * an upload pass it can be used many times until it expires, because a media element makes a request
 * for each range it seeks to.</p>
 *
 * <p>Framework-internal.</p>
 *
 * @since 0.11.0
 */
public final class DownloadLinks {

    private static final Logger LOG = Logger.getLogger(DownloadLinks.class.getName());

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Map<String, Link> ISSUED = new ConcurrentHashMap<>();

    /** Default lifetime of an address, in minutes. */
    public static final long DEFAULT_LINK_MINUTES = 60L;

    private static final int MAX_ID_LENGTH = 512;

    /** Set by tests that run without a CDI container. Null in every real deployment. */
    private static volatile List<FileDownloadHandler> handlersForTesting;

    private DownloadLinks() {}

    /** One issued address. */
    static final class Link {
        final String kind;
        final String id;
        final Principal principal;
        final Set<String> roles;
        final String tenantId;
        final String clientId;
        final String sessionId;
        final long expiresAt;

        Link(String kind, String id, Principal principal, Set<String> roles, String tenantId,
             String clientId, String sessionId, long expiresAt) {
            this.kind = kind;
            this.id = id;
            this.principal = principal;
            this.roles = roles == null ? Collections.emptySet()
                    : Collections.unmodifiableSet(new LinkedHashSet<>(roles));
            this.tenantId = tenantId;
            this.clientId = clientId;
            this.sessionId = sessionId;
            this.expiresAt = expiresAt;
        }

        DownloadRequest toRequest() {
            return new DownloadRequest(kind, id, principal, roles, tenantId, clientId, sessionId);
        }
    }

    /**
     * Issues an address for a file, for the identity currently on this thread.
     *
     * @param config that server's settings
     * @param kind   the kind the page named, empty for the default handler
     * @param id     the id the page named
     * @return the address, relative to wherever the application is mounted
     * @throws UploadRefusedException when no handler has that kind, or the id is unusable
     */
    public static String issue(ServerConfig config, String kind, String id) {
        kind = UploadKinds.normalize(kind);
        if (!UploadKinds.isValid(kind) || handlerFor(kind, false) == null) {
            throw new UploadRefusedException(404, "We could not find that file.");
        }
        if (id == null || id.isEmpty() || id.length() > MAX_ID_LENGTH) {
            throw new UploadRefusedException(400, "We could not find that file.");
        }
        sweepExpired();
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = ENCODER.encodeToString(raw);
        long minutes = config.positiveLong(ServerSettings.DOWNLOAD_LINK_MINUTES, DEFAULT_LINK_MINUTES);
        ISSUED.put(token, new Link(kind, id, RmiRequestContext.getPrincipal(),
                RmiRequestContext.getRoles(), RmiRequestContext.getTenantId(),
                RmiRequestContext.getClientId(), RmiRequestContext.getSessionId(),
                System.currentTimeMillis() + minutes * 60_000L));
        return FileDownloadServer.DOWNLOAD_PATH + "?" + FileDownloadServer.PASS_PARAMETER + "=" + token;
    }

    /**
     * Looks an address up.
     *
     * @param token           the token from the address
     * @param presentedClient the browser id from the request's cookie, or null
     * @return the link, or null when unknown, expired or presented by another browser
     */
    static Link find(String token, String presentedClient) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        Link link = ISSUED.get(token);
        if (link == null) {
            return null;
        }
        if (System.currentTimeMillis() > link.expiresAt) {
            ISSUED.remove(token);
            return null;
        }
        if (link.clientId != null && !link.clientId.equals(presentedClient)) {
            LOG.warning("[zeroz4j] Download address presented by a different browser than it was "
                    + "issued to; refused.");
            return null;
        }
        return link;
    }

    /** @return every deployed {@link FileDownloadHandler}, or the ones a test installed */
    static List<FileDownloadHandler> handlers() {
        List<FileDownloadHandler> injected = handlersForTesting;
        if (injected != null) {
            return injected;
        }
        List<FileDownloadHandler> found = new ArrayList<>();
        try {
            for (FileDownloadHandler handler : CDI.current().select(FileDownloadHandler.class)) {
                found.add(handler);
            }
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "[zeroz4j] Could not look up a FileDownloadHandler", ex);
        }
        return found;
    }

    /**
     * The handler for a kind.
     *
     * @param kind the kind, empty for the default handler
     * @param warn whether to log when two handlers claim the same kind
     * @return the handler, or null
     */
    static FileDownloadHandler handlerFor(String kind, boolean warn) {
        String wanted = UploadKinds.normalize(kind);
        FileDownloadHandler first = null;
        for (FileDownloadHandler handler : handlers()) {
            if (wanted.equals(UploadKinds.normalize(handler.getKind()))) {
                if (first == null) {
                    first = handler;
                } else if (warn) {
                    LOG.warning("[zeroz4j] More than one FileDownloadHandler is deployed for kind \""
                            + wanted + "\"; using " + first.getClass().getName() + ", ignoring "
                            + handler.getClass().getName() + ".");
                }
            }
        }
        return first;
    }

    private static void sweepExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Link>> entries = ISSUED.entrySet().iterator();
        while (entries.hasNext()) {
            if (now > entries.next().getValue().expiresAt) {
                entries.remove();
            }
        }
    }

    /** Test support only. */
    static void setHandlersForTesting(List<FileDownloadHandler> handlers) {
        handlersForTesting = handlers;
    }

    /** Test support only. */
    static void clearForTesting() {
        ISSUED.clear();
    }
}
