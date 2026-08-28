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
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.security.SecureRandom;
import java.util.logging.Logger;

/**
 * Issues and consumes the one-time passes that make an upload possible.
 *
 * <p>A file is not sent over the live connection — it is posted to a separate HTTP address, and that
 * address has no WebSocket session to read an identity from. The pass is what carries the identity
 * across: it is minted on the live connection, where the caller is already authenticated, and spent
 * on the HTTP one.</p>
 *
 * <p>Three properties do the work:</p>
 * <ul>
 *   <li><b>Single use.</b> {@link #consume} removes the pass before it inspects it, so two requests
 *       racing on the same token cannot both win.</li>
 *   <li><b>Short lived.</b> It only has to survive the gap between the page asking and the browser
 *       starting the request — one minute by default.</li>
 *   <li><b>Bound to the browser it was issued to.</b> The upload request presents the same signed
 *       client-id cookie the live connection did, and a mismatch is refused. The token alone is
 *       therefore not enough on any other machine.</li>
 * </ul>
 *
 * <p>Framework-internal.</p>
 */
public final class UploadPasses {

    private static final Logger LOG = Logger.getLogger(UploadPasses.class.getName());

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    /**
     * Passes issued and not yet spent. Small by construction: one entry per file in flight, each
     * removed on use and swept out within a minute if the browser never comes back.
     */
    private static final Map<String, UploadPass> ISSUED = new ConcurrentHashMap<>();

    private UploadPasses() {}

    /**
     * Mints a pass for the identity currently on this thread.
     *
     * <p>Call it from an RMI service method, where {@link RmiRequestContext} is populated. The
     * identity is taken from the connection and never from an argument, so a browser cannot ask for
     * a pass belonging to somebody else.</p>
     *
     * @param fileName    the name the browser reported; recorded, not trusted
     * @param contentType the type the browser reported; recorded, not trusted
     * @param sizeBytes   the size the browser reported
     * @return the new pass
     * @throws UploadRefusedException when the declared size is already over the configured maximum,
     *                                which is the earliest point at which it can be refused
     */
    public static UploadPass issue(String fileName, String contentType, long sizeBytes) {
        return issue(ServerConfig.fromSystemProperties(), fileName, contentType, sizeBytes);
    }

    /**
     * Mints a pass for the identity currently on this thread, using one server's settings.
     *
     * @param config      that server's settings
     * @param fileName    the name the browser reported; recorded, not trusted
     * @param contentType the type the browser reported; recorded, not trusted
     * @param sizeBytes   the size the browser reported
     * @return the new pass
     * @throws UploadRefusedException when the declared size is already over that server's maximum
     */
    public static UploadPass issue(ServerConfig config, String fileName, String contentType,
                                   long sizeBytes) {
        long max = UploadLimits.maxBytes(config);
        if (sizeBytes > max) {
            throw new UploadRefusedException(413, "That file is too big. The largest we can take is "
                    + UploadLimits.describeMaxSize(config) + ".");
        }
        if (sizeBytes < 0L) {
            throw new UploadRefusedException(400, "That file could not be read. Try choosing it again.");
        }

        sweepExpired();

        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = ENCODER.encodeToString(raw);

        Principal principal = RmiRequestContext.getPrincipal();
        Set<String> roles = RmiRequestContext.getRoles();
        UploadPass pass = new UploadPass(token, principal, roles,
                RmiRequestContext.getSessionId(), RmiRequestContext.getTenantId(),
                RmiRequestContext.getClientId(),
                fileName == null ? "" : fileName,
                contentType == null ? "" : contentType,
                sizeBytes,
                System.currentTimeMillis() + UploadLimits.passLifetimeMillis(config));
        ISSUED.put(token, pass);
        return pass;
    }

    /**
     * Spends a pass, or refuses.
     *
     * <p>The removal happens first and unconditionally: whatever the outcome, this token is finished
     * afterwards. That is what makes a replayed request fail even when the original succeeded.</p>
     *
     * @param token             the token the upload request presented, or null
     * @param presentedClientId the verified client id from the upload request's cookie, or null when
     *                          the request carried none
     * @return the pass, ready to use
     * @throws UploadRefusedException when the token is missing, unknown, already spent, expired, or
     *                                was issued to a different browser
     */
    public static UploadPass consume(String token, String presentedClientId) {
        if (token == null || token.isEmpty()) {
            throw new UploadRefusedException(401,
                    "We could not accept that file. Reload the page and try again.");
        }
        UploadPass pass = ISSUED.remove(token);
        if (pass == null) {
            throw new UploadRefusedException(401,
                    "We could not accept that file. Reload the page and try again.");
        }
        if (pass.isExpired(System.currentTimeMillis())) {
            throw new UploadRefusedException(401,
                    "That took too long to start. Please choose the file again.");
        }
        if (pass.getClientId() != null && !pass.getClientId().equals(presentedClientId)) {
            LOG.warning("[zeroz4j] Upload pass presented by a different browser than it was issued to;"
                    + " refused.");
            throw new UploadRefusedException(403,
                    "We could not accept that file. Reload the page and try again.");
        }
        return pass;
    }

    /** Drops passes nobody came back for. Called on every issue, which is often enough. */
    private static void sweepExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, UploadPass>> entries = ISSUED.entrySet().iterator();
        while (entries.hasNext()) {
            if (entries.next().getValue().isExpired(now)) {
                entries.remove();
            }
        }
    }

    /** @return how many passes are issued and unspent; test support and diagnostics only */
    static int outstanding() {
        return ISSUED.size();
    }

    /** Forgets every issued pass. Test support only. */
    static void clearForTesting() {
        ISSUED.clear();
    }
}
