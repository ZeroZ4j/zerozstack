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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Real URLs mean the browser asks the <em>server</em> for {@code /projects/42} whenever a deep link
 * is opened or reloaded. Answering 404 made every client route work exactly until it was refreshed —
 * which no unit test caught, because it only appears when the application actually runs.
 */
class StaticContentTest {

    @Test
    void aClientRouteFallsBackToTheApplicationShell() {
        assertEquals("index.html", StaticContent.resolve("projects/42"));
        assertEquals("index.html", StaticContent.resolve("projects/1/tasks/11"));
        assertEquals("index.html", StaticContent.resolve("admin"));
    }

    @Test
    void theRootServesTheShell() {
        assertEquals("index.html", StaticContent.resolve(""));
        assertEquals("index.html", StaticContent.resolve("/"));
        assertEquals("index.html", StaticContent.resolve(null));
    }

    @Test
    void anExistingAssetIsStillServedItself() {
        assertEquals("test-asset.js", StaticContent.resolve("test-asset.js"));
        assertEquals("index.html", StaticContent.resolve("index.html"));
    }

    @Test
    void aMissingAssetStaysA404() {
        // Returning the HTML shell here would hand the browser a page where it expected a script,
        // and the failure would surface as an unreadable syntax error instead of a missing file.
        assertNull(StaticContent.resolve("js/does-not-exist.js"));
        assertNull(StaticContent.resolve("styles/missing.css"));
        assertNull(StaticContent.resolve("favicon.ico"));
    }
}
