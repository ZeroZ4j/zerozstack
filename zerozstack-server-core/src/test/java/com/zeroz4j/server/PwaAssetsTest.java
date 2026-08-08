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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The service worker and the offline page ship from this module's own {@code META-INF/resources},
 * which is how every application gets them without copying anything.
 *
 * <p>Two of the things checked here fail silently in production if they break — a stale cache name
 * and a page that needs the network to render — so they are worth a test rather than a review.</p>
 */
class PwaAssetsTest {

    @Test
    void bothAssetsAreOnTheClasspathWhereTheServerLooksForThem() {
        assertEquals("zeroz4j-sw.js", StaticContent.resolve("zeroz4j-sw.js"));
        assertEquals("zeroz4j-offline.html", StaticContent.resolve("/zeroz4j-offline.html"));
    }

    @Test
    void theyAreServedAsScriptAndPage() {
        assertEquals("application/javascript", StaticContent.contentType("zeroz4j-sw.js"));
        assertEquals("text/html", StaticContent.contentType("zeroz4j-offline.html"));
        // A manifest served as octet-stream is refused as an install source by several browsers.
        assertEquals("application/manifest+json", StaticContent.contentType("manifest.webmanifest"));
    }

    @Test
    void theServiceWorkerCacheNameCarriesTheBuildVersion() throws Exception {
        String worker = read("zeroz4j-sw.js");

        // Resource filtering is configured in this module's pom for this one file. Lose it and the
        // cache name becomes the literal placeholder — which never changes, so a deployment would
        // serve the previous bundle against the new server, with no error anywhere.
        assertFalse(worker.contains("${project.version}"),
                "zeroz4j-sw.js was packaged unfiltered; check <resources> in server-core's pom");
        assertTrue(worker.contains("const CACHE = 'zeroz4j-shell-' + VERSION;"), worker);
    }

    @Test
    void theServiceWorkerLeavesTheSocketAlone() throws Exception {
        String worker = read("zeroz4j-sw.js");

        assertTrue(worker.contains("wasm-rmi"), "the RMI path must be excluded explicitly");
        assertTrue(worker.contains("request.method !== 'GET'"), "writes must never be intercepted");
    }

    @Test
    void theOfflinePageNeedsNothingFromTheNetwork() throws Exception {
        String page = read("zeroz4j-offline.html");

        // It is shown precisely when nothing can be fetched. A stylesheet, font or script from
        // anywhere else would leave an unstyled or blank page at the one moment it has a job to do.
        assertFalse(page.contains("http://"), page);
        assertFalse(page.contains("https://"), page);
        assertFalse(page.contains("<link"), page);

        // Served as the navigation response, so it has to be a whole document.
        assertTrue(page.startsWith("<!DOCTYPE html>"), page);
        assertTrue(page.contains("<title>"), page);
    }

    private static String read(String name) throws Exception {
        try (InputStream stream = StaticContent.open(name)) {
            assertNotNull(stream, name + " is not on the classpath");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
