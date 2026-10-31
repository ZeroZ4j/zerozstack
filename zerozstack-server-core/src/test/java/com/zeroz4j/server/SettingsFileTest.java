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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Settings and secrets come from the application's property file, under a {@code -D}. */
class SettingsFileTest {

    private static final String NAME = "zeroz.test.fromFile";

    @TempDir
    Path folder;

    @BeforeEach
    void pointAtTheFile() throws IOException {
        Path file = folder.resolve("app.properties");
        Files.writeString(file, NAME + "=in-the-file\n");
        System.setProperty(SettingsFile.LOCATION_PROPERTY, file.toString());
        SettingsFile.resetForTesting();
    }

    @AfterEach
    void forgetTheFile() {
        System.clearProperty(SettingsFile.LOCATION_PROPERTY);
        System.clearProperty(NAME);
        SettingsFile.resetForTesting();
    }

    @Test
    void aSettingWithNoSystemPropertyComesFromTheFile() {
        assertEquals("in-the-file", ServerConfig.fromSystemProperties().get(NAME));
    }

    @Test
    void aSystemPropertyBeatsTheFile() {
        System.setProperty(NAME, "on-the-command-line");
        assertEquals("on-the-command-line", ServerConfig.fromSystemProperties().get(NAME));
    }

    @Test
    void anIsolatedConfigReadsNothingOutsideItself() {
        assertNull(ServerConfig.isolated().build().get(NAME));
    }

    @Test
    void aMissingFileIsNoSettings() {
        System.setProperty(SettingsFile.LOCATION_PROPERTY, folder.resolve("nothing").toString());
        SettingsFile.resetForTesting();
        assertNull(ServerConfig.fromSystemProperties().get(NAME));
    }
}
