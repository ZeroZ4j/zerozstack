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

import jakarta.enterprise.inject.spi.CDI;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Finds the {@link FileUploadHandler} for an upload kind and works out the size limit for it.
 *
 * <p>The kind is set by the page ({@code FileUpload.setKind}), travels in the upload pass the server
 * issued, and is checked here on the server. The browser's word is never the limit.</p>
 *
 * @since 0.11.0
 */
public final class UploadKinds {

    private static final Logger LOG = Logger.getLogger(UploadKinds.class.getName());

    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    /** Set by tests that run without a CDI container. Null in every real deployment. */
    private static volatile List<FileUploadHandler> handlersForTesting;

    private UploadKinds() {}

    /**
     * Whether a kind is acceptable as a name.
     *
     * @param kind the kind, null counts as empty
     * @return true for empty and for letters, digits, dot, dash and underscore up to 64 characters
     */
    public static boolean isValid(String kind) {
        return kind == null || kind.isEmpty() || VALID.matcher(kind).matches();
    }

    /**
     * Turns null into the empty kind.
     *
     * @param kind the kind or null
     * @return never null
     */
    public static String normalize(String kind) {
        return kind == null ? "" : kind;
    }

    /** @return every deployed {@link FileUploadHandler}, or the ones a test installed */
    public static List<FileUploadHandler> handlers() {
        List<FileUploadHandler> injected = handlersForTesting;
        if (injected != null) {
            return injected;
        }
        List<FileUploadHandler> found = new ArrayList<>();
        try {
            for (FileUploadHandler handler : CDI.current().select(FileUploadHandler.class)) {
                found.add(handler);
            }
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "[zeroz4j] Could not look up a FileUploadHandler", ex);
        }
        return found;
    }

    /**
     * The handler for a kind.
     *
     * @param kind the kind, empty for the default handler
     * @param warn whether to log when two handlers claim the same kind
     * @return the handler, or null when none has this kind
     */
    public static FileUploadHandler handlerFor(String kind, boolean warn) {
        String wanted = normalize(kind);
        List<FileUploadHandler> matching = new ArrayList<>();
        for (FileUploadHandler handler : handlers()) {
            if (wanted.equals(normalize(handler.getKind()))) {
                matching.add(handler);
            }
        }
        if (matching.isEmpty()) {
            return null;
        }
        if (matching.size() > 1 && warn) {
            StringBuilder names = new StringBuilder();
            for (FileUploadHandler handler : matching) {
                if (names.length() > 0) {
                    names.append(", ");
                }
                names.append(handler.getClass().getName());
            }
            LOG.warning("[zeroz4j] More than one FileUploadHandler is deployed for kind \"" + wanted
                    + "\"; using " + matching.get(0).getClass().getName() + ". Found: " + names);
        }
        return matching.get(0);
    }

    /**
     * The largest file accepted for a kind: the property {@code zeroz.upload.maxBytes.<kind>}, then
     * the handler's {@link FileUploadHandler#getMaxBytes()}, then {@code zeroz.upload.maxBytes}.
     *
     * @param config the server's settings
     * @param kind   the kind, empty for the default handler
     * @return the limit in bytes
     */
    public static long maxBytes(ServerConfig config, String kind) {
        String wanted = normalize(kind);
        if (!wanted.isEmpty() && isValid(wanted)) {
            long own = config.positiveLong(ServerSettings.UPLOAD_MAX_BYTES_PREFIX + wanted, -1L);
            if (own > 0L) {
                return own;
            }
        }
        if (wanted.isEmpty() && config.get(ServerSettings.UPLOAD_MAX_BYTES) != null) {
            // An operator who sets the server-wide limit has set the limit for the default handler.
            long global = config.positiveLong(ServerSettings.UPLOAD_MAX_BYTES, -1L);
            if (global > 0L) {
                return global;
            }
        }
        FileUploadHandler handler = handlerFor(wanted, false);
        if (handler != null && handler.getMaxBytes() > 0L) {
            return handler.getMaxBytes();
        }
        return UploadLimits.maxBytes(config);
    }

    /**
     * The largest limit of any kind. A binding that must set one ceiling for request bodies before
     * any request arrives uses this.
     *
     * @param config the server's settings
     * @return the largest limit in bytes
     */
    public static long largestMaxBytes(ServerConfig config) {
        long largest = UploadLimits.maxBytes(config);
        for (FileUploadHandler handler : handlers()) {
            largest = Math.max(largest, maxBytes(config, handler.getKind()));
        }
        return largest;
    }

    /** Test support only: runs against handlers supplied directly, with no CDI. */
    static void setHandlersForTesting(List<FileUploadHandler> handlers) {
        handlersForTesting = handlers;
    }
}
