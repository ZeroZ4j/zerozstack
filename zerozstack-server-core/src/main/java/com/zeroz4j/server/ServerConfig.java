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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * One server's settings.
 *
 * <p>Every setting has a name — see {@link ServerSettings} — and by default the value comes from
 * the system property of that name, exactly as it always has. A deployment that passes
 * {@code -Dzeroz.ws.maxBinaryMessageBytes=8388608} on the command line needs to know nothing about
 * this class.</p>
 *
 * <h2>Why it exists</h2>
 *
 * <p>System properties belong to the whole Java process. Two servers started in one process
 * therefore could not be configured differently: whatever one of them set, the other one read. That
 * made it impossible to write a test for "a large message is refused" without changing the setting
 * for every other test running at the same time.</p>
 *
 * <p>A {@code ServerConfig} is a small set of values that belong to <b>one</b> server. Anything it
 * does not carry is still read from the system properties, so nothing existing changes:</p>
 *
 * <pre>{@code
 * ServerConfig strict = ServerConfig.builder()
 *         .set(ServerSettings.MAX_BINARY_MESSAGE_BYTES, 1024)
 *         .build();
 * }</pre>
 *
 * <p>A test that must not be affected by whatever the JVM was started with asks for a config that
 * ignores system properties altogether:</p>
 *
 * <pre>{@code
 * ServerConfig clean = ServerConfig.isolated()
 *         .set(ServerSettings.UPLOAD_MAX_BYTES, 4096)
 *         .build();
 * }</pre>
 *
 * <p>Instances are immutable and safe to share between threads.</p>
 *
 * @since 0.8.0
 */
public final class ServerConfig {

    private static final Logger LOG = Logger.getLogger(ServerConfig.class.getName());

    /** The one every server gets unless it is given another. */
    private static final ServerConfig SYSTEM_PROPERTIES =
            new ServerConfig(Collections.emptyMap(), true);

    /** Setting name to its value; an empty {@link Optional} means "treat this as not set at all". */
    private final Map<String, Optional<String>> values;

    /** Whether a setting this config does not carry falls back to the system property. */
    private final boolean readsSystemProperties;

    private ServerConfig(Map<String, Optional<String>> values, boolean readsSystemProperties) {
        this.values = values;
        this.readsSystemProperties = readsSystemProperties;
    }

    /**
     * The settings a server gets when nobody says otherwise: the system properties, read fresh on
     * every question so a value changed at runtime is picked up.
     *
     * @return the shared system-property config
     */
    public static ServerConfig fromSystemProperties() {
        return SYSTEM_PROPERTIES;
    }

    /**
     * Starts building settings for one server. Anything not set here still comes from the system
     * properties.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder(true);
    }

    /**
     * Starts building settings for one server that ignores the system properties completely.
     *
     * <p>This is what a test wants: the result depends only on what the test set, so it does not
     * change when the build is run with different {@code -D} flags.</p>
     *
     * @return a new builder
     */
    public static Builder isolated() {
        return new Builder(false);
    }

    /**
     * @return whether a setting this config does not carry is looked up as a system property
     */
    public boolean readsSystemProperties() {
        return readsSystemProperties;
    }

    /**
     * The raw text of a setting.
     *
     * @param name the setting name
     * @return the value, or null when it is not set
     */
    public String get(String name) {
        Optional<String> own = values.get(name);
        if (own != null) {
            return own.orElse(null);
        }
        return readsSystemProperties ? System.getProperty(name) : null;
    }

    /**
     * The raw text of a setting, with a stand-in.
     *
     * @param name     the setting name
     * @param fallback what to answer when it is not set
     * @return the value, or {@code fallback}
     */
    public String get(String name, String fallback) {
        String value = get(name);
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    /**
     * A whole number that has to be greater than zero.
     *
     * <p>An unusable value is logged and ignored rather than applied: zero and negative numbers mean
     * something different to every container, so a broken setting must not quietly become a very
     * small limit.</p>
     *
     * @param name the setting name
     * @return the value, or null when unset, unreadable or not positive
     */
    public Integer positiveInt(String name) {
        String configured = get(name);
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

    /**
     * A whole number that has to be greater than zero, with a stand-in.
     *
     * @param name     the setting name
     * @param fallback what to answer when it is unset or unusable
     * @return the value, or {@code fallback}
     */
    public int positiveInt(String name, int fallback) {
        Integer value = positiveInt(name);
        return value != null ? value : fallback;
    }

    /**
     * A large whole number that has to be greater than zero, with a stand-in.
     *
     * @param name     the setting name
     * @param fallback what to answer when it is unset or unusable
     * @return the value, or {@code fallback}
     */
    public long positiveLong(String name, long fallback) {
        String configured = get(name);
        if (configured == null || configured.trim().isEmpty()) {
            return fallback;
        }
        try {
            long value = Long.parseLong(configured.trim());
            if (value > 0L) {
                return value;
            }
            LOG.warning("[zeroz4j] " + name + "=" + configured + " is not positive; using "
                    + fallback + ".");
        } catch (NumberFormatException ex) {
            LOG.warning("[zeroz4j] " + name + "=" + configured + " is not a number; using "
                    + fallback + ".");
        }
        return fallback;
    }

    /**
     * A yes-or-no setting. Anything other than {@code true} is no.
     *
     * @param name the setting name
     * @return whether it is switched on
     */
    public boolean flag(String name) {
        return Boolean.parseBoolean(get(name));
    }

    /**
     * A setting written as a comma-separated list.
     *
     * @param name      the setting name
     * @param lowercase whether to fold the entries to lower case
     * @return the entries, in the order given, with blanks dropped; empty when the setting is unset
     */
    public Set<String> list(String name, boolean lowercase) {
        String configured = get(name);
        if (configured == null || configured.trim().isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> parsed = new LinkedHashSet<>();
        for (String part : configured.split(",")) {
            String candidate = part.trim();
            if (!candidate.isEmpty()) {
                parsed.add(lowercase ? candidate.toLowerCase(Locale.ROOT) : candidate);
            }
        }
        return parsed;
    }

    /**
     * A builder holding exactly what this config holds, to change one setting and keep the rest.
     *
     * @return a new builder, reading the system properties if and only if this config does
     */
    public Builder toBuilder() {
        Builder copy = new Builder(readsSystemProperties);
        copy.values.putAll(values);
        return copy;
    }

    /**
     * A builder holding exactly what this config holds, but reading no system properties at all.
     *
     * @return a new builder that ignores the Java process's own settings
     */
    public Builder toIsolatedBuilder() {
        Builder copy = new Builder(false);
        copy.values.putAll(values);
        return copy;
    }

    /**
     * The settings this config carries itself, for a log line or an error message. System-property
     * values are not included, because they are not this config's.
     *
     * @return name to value, with a cleared setting shown as {@code (not set)}
     */
    public Map<String, String> ownSettings() {
        Map<String, String> described = new LinkedHashMap<>();
        for (Map.Entry<String, Optional<String>> entry : values.entrySet()) {
            described.put(entry.getKey(), entry.getValue().orElse("(not set)"));
        }
        return Collections.unmodifiableMap(described);
    }

    @Override
    public String toString() {
        return "ServerConfig" + ownSettings()
                + (readsSystemProperties ? " + system properties" : " (system properties ignored)");
    }

    /** Collects the settings for one server. Not thread-safe; build it on one thread and share the result. */
    public static final class Builder {

        private final Map<String, Optional<String>> values = new LinkedHashMap<>();
        private final boolean readsSystemProperties;

        private Builder(boolean readsSystemProperties) {
            this.readsSystemProperties = readsSystemProperties;
        }

        /**
         * Sets one setting for this server only.
         *
         * @param name  the setting name, from {@link ServerSettings}
         * @param value the value, written the way it would be written on the command line
         * @return this builder
         */
        public Builder set(String name, String value) {
            require(name);
            values.put(name, Optional.ofNullable(value));
            return this;
        }

        /**
         * Sets a numeric setting for this server only.
         *
         * @param name  the setting name, from {@link ServerSettings}
         * @param value the value
         * @return this builder
         */
        public Builder set(String name, long value) {
            return set(name, Long.toString(value));
        }

        /**
         * Sets a yes-or-no setting for this server only.
         *
         * @param name  the setting name, from {@link ServerSettings}
         * @param value the value
         * @return this builder
         */
        public Builder set(String name, boolean value) {
            return set(name, Boolean.toString(value));
        }

        /**
         * Makes one setting look unset to this server, whatever the system property says.
         *
         * @param name the setting name, from {@link ServerSettings}
         * @return this builder
         */
        public Builder unset(String name) {
            require(name);
            values.put(name, Optional.empty());
            return this;
        }

        /**
         * @return the finished settings
         */
        public ServerConfig build() {
            return new ServerConfig(Collections.unmodifiableMap(new LinkedHashMap<>(values)),
                    readsSystemProperties);
        }

        private static void require(String name) {
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "A setting needs a name. Use one of the constants in ServerSettings.");
            }
        }
    }
}
