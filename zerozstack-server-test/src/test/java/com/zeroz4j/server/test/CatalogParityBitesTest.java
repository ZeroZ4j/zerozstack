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
package com.zeroz4j.server.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A check nobody has watched fail is a check nobody knows works.
 *
 * <p>Each of these plants one fault in a catalog and proves the check finds it and says something a
 * person can act on. Between them they cover the three ways a translation goes wrong.</p>
 */
class CatalogParityBitesTest {

    @Test
    @DisplayName("a key the translation is missing is found")
    void aMissingKeyIsFound(@TempDir Path folder) throws IOException {
        write(folder, "app.properties", "task.add = Add task\ntask.remove = Remove task\n");
        write(folder, "app_de.properties", "task.add = Aufgabe hinzufuegen\n");

        List<String> findings = CatalogParity.check(folder, "app");

        assertEquals(1, findings.size(), findings.toString());
        assertTrue(findings.get(0).contains("app_de.properties"), findings.get(0));
        assertTrue(findings.get(0).contains("task.remove"), findings.get(0));
        System.out.println("planted a missing key, the check said:" + findings.get(0));
    }

    @Test
    @DisplayName("a key the translation has and the fallback language does not is found")
    void anExtraKeyIsFound(@TempDir Path folder) throws IOException {
        write(folder, "app.properties", "task.add = Add task\n");
        write(folder, "app_de.properties",
                "task.add = Aufgabe hinzufuegen\ntask.oldName = Alter Name\n");

        List<String> findings = CatalogParity.check(folder, "app");

        assertEquals(1, findings.size(), findings.toString());
        assertTrue(findings.get(0).contains("task.oldName"), findings.get(0));
        assertTrue(findings.get(0).contains("Nothing will ever read it"), findings.get(0));
        System.out.println("planted a key nothing reads, the check said:" + findings.get(0));
    }

    @Test
    @DisplayName("a translation whose blanks disagree with the original is found")
    void aBlankMismatchIsFound(@TempDir Path folder) throws IOException {
        write(folder, "app.properties", "task.remaining = {0} of {1} tasks left\n");
        write(folder, "app_de.properties", "task.remaining = {0} Aufgaben offen\n");

        List<String> findings = CatalogParity.check(folder, "app");

        assertEquals(1, findings.size(), findings.toString());
        assertTrue(findings.get(0).contains("task.remaining"), findings.get(0));
        assertTrue(findings.get(0).contains("[0]"), findings.get(0));
        assertTrue(findings.get(0).contains("[0, 1]"), findings.get(0));
        System.out.println("planted a blank that disagrees, the check said:" + findings.get(0));
    }

    @Test
    @DisplayName("a catalog whose languages agree produces nothing at all")
    void anHonestCatalogPasses(@TempDir Path folder) throws IOException {
        write(folder, "app.properties", "task.add = Add task\ntask.remaining = {0} of {1} left\n");
        write(folder, "app_de.properties",
                "task.add = Aufgabe hinzufuegen\ntask.remaining = {0} von {1} offen\n");

        assertTrue(CatalogParity.check(folder, "app").isEmpty());
    }

    @Test
    @DisplayName("the failure a build sees names the file, the key and what is wrong with it")
    void theBuildFailureIsReadable(@TempDir Path folder) throws IOException {
        write(folder, "app.properties", "task.remaining = {0} of {1} tasks left\n");
        write(folder, "app_de.properties", "task.remaining = {0} Aufgaben offen\n");

        AssertionError failure = assertThrows(AssertionError.class,
                () -> CatalogParity.assertConsistent(folder, "app"));

        assertTrue(failure.getMessage().contains("app_de.properties"), failure.getMessage());
        assertTrue(failure.getMessage().contains("task.remaining"), failure.getMessage());
        System.out.println("what a build would print:" + System.lineSeparator()
                + failure.getMessage());
    }

    @Test
    @DisplayName("a fallback language that is not there is itself the finding")
    void anAbsentFallbackFileIsReported(@TempDir Path folder) throws IOException {
        write(folder, "app_de.properties", "task.add = Aufgabe hinzufuegen\n");

        List<String> findings = CatalogParity.check(folder, "app");

        assertEquals(1, findings.size(), findings.toString());
        assertTrue(findings.get(0).contains("is not there"), findings.get(0));
    }

    private static void write(Path folder, String name, String contents) throws IOException {
        Files.write(folder.resolve(name), contents.getBytes(StandardCharsets.UTF_8));
    }
}
