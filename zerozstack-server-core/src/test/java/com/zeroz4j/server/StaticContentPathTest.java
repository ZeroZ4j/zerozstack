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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A request path is refused before anything is looked up.
 *
 * <p>The JAX-RS runtime and the servlet container both hand this code an already percent-decoded
 * path, so {@code ..%2f..%2f} arrives as {@code ../../}. Whether the class loader happens to
 * collapse that is not the framework's control to rely on, and the servlet binding resolves real
 * files where it would not hold at all — so the path is rejected outright, and these tests assert
 * that the rejection happens <em>before</em> the lookup rather than after it.</p>
 */
class StaticContentPathTest {

    /** Remembers every path it was asked about, so a test can assert nothing was looked up. */
    private static final class RecordingAssets implements StaticContent.Assets {

        private final Set<String> files = Set.of("index.html", "js/classes.js", "styles/app.css");
        private final List<String> asked = new ArrayList<>();

        @Override
        public boolean exists(String path) {
            asked.add(path);
            return files.contains(path);
        }

        @Override
        public InputStream open(String path) {
            asked.add(path);
            return files.contains(path)
                    ? new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)) : null;
        }
    }

    private static final char BACKSLASH = (char) 92;
    private static final char NUL = (char) 0;

    private void refused(String path) {
        RecordingAssets assets = new RecordingAssets();
        assertNull(StaticContent.resolve(path, assets),
                "'" + path + "' must answer 404, the same as any unknown asset");
        assertTrue(assets.asked.isEmpty(),
                "'" + path + "' reached the resource lookup: " + assets.asked);
        assertFalse(StaticContent.isSafePath(path));
    }

    @Test
    void aDecodedTraversalNeverReachesTheLookup() {
        refused("../../etc/passwd");
        refused("/../../etc/passwd");
        refused("..");
        refused("js/../../../etc/passwd");
        refused("a/b/../../../..");
    }

    @Test
    void aPathStillCarryingAnEncodedTraversalIsRefusedToo() {
        // Both bindings decode before this code runs, so these only arrive when the request was
        // double-encoded. They are still refused: this class decodes a copy to look at, never to
        // serve, so a double-encoded traversal cannot survive one more decoding step downstream.
        refused("%2e%2e/%2e%2e/etc/passwd");
        refused("..%2f..%2fetc/passwd");
        refused("%2E%2E%2Fetc%2Fpasswd");
    }

    @Test
    void aBackslashIsRefused() {
        refused("..".concat(String.valueOf(BACKSLASH)).concat("..").concat(String.valueOf(BACKSLASH)));
        refused("js" + BACKSLASH + "classes.js");
        refused("%5c%5cserver%5cshare");
    }

    @Test
    void aNullByteOrControlCharacterIsRefused() {
        refused("index.html" + NUL + ".js");
        refused("js/classes.js%00.png");
        refused("logo" + (char) 13 + (char) 10 + ".png");
    }

    @Test
    void theContainersOwnDirectoriesAreRefused() {
        // The classpath lookup could not reach these anyway - they would have to sit under
        // META-INF/resources/ - but the servlet binding serves the archive root, where it can.
        refused("WEB-INF/web.xml");
        refused("META-INF/MANIFEST.MF");
        refused("web-inf/web.xml");
        refused("/META-INF/");
    }

    @Test
    void aRealAssetIsStillServed() {
        RecordingAssets assets = new RecordingAssets();
        assertEquals("js/classes.js", StaticContent.resolve("js/classes.js", assets));
        assertEquals("styles/app.css", StaticContent.resolve("/styles/app.css", assets));
    }

    @Test
    void aDeepClientRouteStillFallsBackToTheShell() {
        // This is the feature the rejection must not break: a bookmarked route has no file behind
        // it, and answering 404 would make every client route work exactly until a reload.
        RecordingAssets assets = new RecordingAssets();
        assertEquals("index.html", StaticContent.resolve("projects/42/tasks/7", assets));
        assertEquals("index.html", StaticContent.resolve("/admin", assets));
        assertEquals("index.html", StaticContent.resolve("", assets));
        assertEquals("index.html", StaticContent.resolve("/", assets));
        assertEquals("index.html", StaticContent.resolve(null, assets));
    }

    @Test
    void anOrdinaryNameThatMerelyLooksAlarmingIsAllowed() {
        assertTrue(StaticContent.isSafePath("reports/2024..2025"));
        assertTrue(StaticContent.isSafePath("100%25-done"));
        assertTrue(StaticContent.isSafePath("js/classes.js"));
        assertTrue(StaticContent.isSafePath("assets/logo..png"));
    }

    @Test
    void aMissingAssetIsStillA404AndNotTheShell() {
        RecordingAssets assets = new RecordingAssets();
        assertNull(StaticContent.resolve("js/missing.js", assets));
        assertNull(StaticContent.resolve("favicon.ico", assets));
    }
}
