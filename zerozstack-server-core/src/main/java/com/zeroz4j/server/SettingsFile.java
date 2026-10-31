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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * The application's property file: where a deployment keeps its settings and secrets.
 *
 * <p>Read once, by the first setting that is asked for, from the first of these that exists:</p>
 * <ol>
 *   <li>the path in the system property {@code zeroz.settings.file};</li>
 *   <li>{@code zeroz4j.properties} in the working directory, which is where a secret belongs:
 *       outside the jar and out of version control;</li>
 *   <li>{@code zeroz4j.properties} on the classpath, for settings that are not secret.</li>
 * </ol>
 *
 * <p>A {@code -D} system property beats the file. Environment variables are not read, on purpose:
 * everything a process starts inherits them, and they turn up in crash reports. Only the file's name
 * is logged, never a value.</p>
 *
 * @since 0.11.0
 */
final class SettingsFile {

    private static final Logger LOG = Logger.getLogger(SettingsFile.class.getName());

    /** The system property naming the file. */
    static final String LOCATION_PROPERTY = "zeroz.settings.file";

    private static final String FILE_NAME = "zeroz4j.properties";

    private static volatile Properties loaded;

    private SettingsFile() {
    }

    /**
     * @param name the setting name
     * @return its value in the file, or null
     */
    static String get(String name) {
        Properties current = loaded;
        if (current == null) {
            synchronized (SettingsFile.class) {
                if (loaded == null) {
                    loaded = load();
                }
                current = loaded;
            }
        }
        return current.getProperty(name);
    }

    private static Properties load() {
        Properties properties = new Properties();
        String named = System.getProperty(LOCATION_PROPERTY);
        boolean wasNamed = named != null && !named.trim().isEmpty();
        Path file = Paths.get(wasNamed ? named.trim() : FILE_NAME);
        try {
            if (Files.isRegularFile(file)) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    properties.load(reader);
                }
                LOG.info("[zeroz4j] Settings read from " + file.toAbsolutePath());
                return properties;
            }
            if (wasNamed) {
                LOG.warning("[zeroz4j] " + LOCATION_PROPERTY + " names " + file.toAbsolutePath()
                        + ", which does not exist.");
            }
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            try (InputStream in = loader != null ? loader.getResourceAsStream(FILE_NAME)
                    : SettingsFile.class.getResourceAsStream("/" + FILE_NAME)) {
                if (in != null) {
                    try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                        properties.load(reader);
                    }
                    LOG.info("[zeroz4j] Settings read from " + FILE_NAME + " on the classpath");
                }
            }
        } catch (IOException | IllegalArgumentException ex) {
            LOG.warning("[zeroz4j] Could not read the settings file: " + ex.getMessage());
        }
        return properties;
    }

    /** Test support: makes the next question read the file again. */
    static void resetForTesting() {
        synchronized (SettingsFile.class) {
            loaded = null;
        }
    }
}
