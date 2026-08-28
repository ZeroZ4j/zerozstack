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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

/**
 * What a deployment allows an upload to be, and where a part-received file is parked.
 *
 * <h2>Configuration</h2>
 * <table border="1">
 *   <caption>System properties</caption>
 *   <tr><th>Property</th><th>Default</th><th>Meaning</th></tr>
 *   <tr><td>{@code zeroz.upload.maxBytes}</td><td>26214400 (25 MB)</td>
 *       <td>The largest file the upload address accepts. Refused from the declared length before the
 *           body is read, and again from the bytes actually counted.</td></tr>
 *   <tr><td>{@code zeroz.upload.passSeconds}</td><td>60</td>
 *       <td>How long an issued upload pass stays usable. It only has to survive the gap between the
 *           page asking for it and the browser starting the request.</td></tr>
 *   <tr><td>{@code zeroz.upload.tempDir}</td><td>the JVM temp directory</td>
 *       <td>Where the framework writes a file while it is arriving. Put it on the same filesystem as
 *           wherever the application moves files to, so the move is a rename.</td></tr>
 * </table>
 *
 * <p>Every value is read on each call rather than cached, so a test — or an operator with a JMX
 * console — can change one without a restart.</p>
 */
public final class UploadLimits {

    private static final Logger LOG = Logger.getLogger(UploadLimits.class.getName());

    /** System property naming the largest acceptable upload, in bytes. */
    public static final String MAX_BYTES_PROPERTY = ServerSettings.UPLOAD_MAX_BYTES;
    /** System property naming how many seconds an upload pass stays usable. */
    public static final String PASS_SECONDS_PROPERTY = ServerSettings.UPLOAD_PASS_SECONDS;
    /** System property naming the directory a part-received upload is written to. */
    public static final String TEMP_DIR_PROPERTY = ServerSettings.UPLOAD_TEMP_DIR;

    /** 25 MB — big enough for a photograph or a slide deck, small enough to be a real limit. */
    public static final long DEFAULT_MAX_BYTES = 25L * 1024L * 1024L;
    /** One minute, which is far longer than the gap a pass has to survive. */
    public static final long DEFAULT_PASS_SECONDS = 60L;

    private UploadLimits() {}

    /**
     * The largest file this deployment accepts.
     *
     * @return the configured maximum in bytes, or {@link #DEFAULT_MAX_BYTES}
     */
    public static long maxBytes() {
        return maxBytes(ServerConfig.fromSystemProperties());
    }

    /**
     * The largest file one server accepts.
     *
     * @param config that server's settings
     * @return the configured maximum in bytes, or {@link #DEFAULT_MAX_BYTES}
     */
    public static long maxBytes(ServerConfig config) {
        return config.positiveLong(MAX_BYTES_PROPERTY, DEFAULT_MAX_BYTES);
    }

    /**
     * How long an issued pass stays usable.
     *
     * @return the configured lifetime in milliseconds
     */
    public static long passLifetimeMillis() {
        return passLifetimeMillis(ServerConfig.fromSystemProperties());
    }

    /**
     * How long an issued pass stays usable on one server.
     *
     * @param config that server's settings
     * @return the configured lifetime in milliseconds
     */
    public static long passLifetimeMillis(ServerConfig config) {
        return config.positiveLong(PASS_SECONDS_PROPERTY, DEFAULT_PASS_SECONDS) * 1000L;
    }

    /**
     * The directory a file is written to while it arrives.
     *
     * <p>Created if it does not exist. Falls back to the JVM temp directory when the configured one
     * cannot be created, because refusing every upload over a misconfigured path would be a worse
     * failure than ignoring it with a warning.</p>
     *
     * @return an existing directory
     * @throws IOException when even the fallback cannot be used
     */
    public static Path tempDirectory() throws IOException {
        return tempDirectory(ServerConfig.fromSystemProperties());
    }

    /**
     * The directory one server writes a file to while it arrives.
     *
     * @param config that server's settings
     * @return an existing directory
     * @throws IOException when even the fallback cannot be used
     */
    public static Path tempDirectory(ServerConfig config) throws IOException {
        String configured = config.get(TEMP_DIR_PROPERTY);
        if (configured != null && !configured.trim().isEmpty()) {
            try {
                Path dir = Paths.get(configured.trim());
                Files.createDirectories(dir);
                return dir;
            } catch (IOException | RuntimeException ex) {
                LOG.warning("[zeroz4j] " + TEMP_DIR_PROPERTY + "=" + configured
                        + " could not be used (" + ex.getMessage()
                        + "); falling back to the JVM temp directory.");
            }
        }
        Path fallback = Paths.get(System.getProperty("java.io.tmpdir"), "zeroz4j-uploads");
        Files.createDirectories(fallback);
        return fallback;
    }

    /**
     * The maximum size written the way a person would say it, for a message on a screen.
     *
     * @return for example {@code "25 MB"}
     */
    public static String describeMaxSize() {
        return describeSize(maxBytes());
    }

    /**
     * One server's maximum size written the way a person would say it.
     *
     * @param config that server's settings
     * @return for example 25 MB
     */
    public static String describeMaxSize(ServerConfig config) {
        return describeSize(maxBytes(config));
    }

    /**
     * A byte count written the way a person would say it.
     *
     * @param bytes the count
     * @return for example {@code "1.4 MB"}, {@code "812 KB"}, {@code "40 bytes"}
     */
    public static String describeSize(long bytes) {
        if (bytes < 1024L) {
            return bytes + (bytes == 1L ? " byte" : " bytes");
        }
        if (bytes < 1024L * 1024L) {
            return round(bytes / 1024.0) + " KB";
        }
        if (bytes < 1024L * 1024L * 1024L) {
            return round(bytes / (1024.0 * 1024.0)) + " MB";
        }
        return round(bytes / (1024.0 * 1024.0 * 1024.0)) + " GB";
    }

    /** Whole numbers stay whole ("25 MB"); anything else keeps one decimal ("1.4 MB"). */
    private static String round(double value) {
        long whole = Math.round(value);
        if (Math.abs(value - whole) < 0.05) {
            return Long.toString(whole);
        }
        long tenths = Math.round(value * 10.0);
        return (tenths / 10) + "." + (tenths % 10);
    }

}
