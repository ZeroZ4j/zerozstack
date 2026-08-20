/*
 * Copyright 2026 Franz Schöning
 * Project: https://www.zeroz4j.com
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
package com.zeroz4j.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Identifiers must be well formed, and — the point of the class — must never be asked for from a
 * browser function that only exists on an {@code https://} or {@code localhost} page.
 */
class IdsTest {

    @Test
    @DisplayName("an identifier is a canonical version-4 UUID")
    void shape() {
        for (int i = 0; i < 200; i++) {
            String id = Ids.newId();
            assertEquals(36, id.length(), id);
            assertEquals(id.toLowerCase(), id, "identifiers are lower case: " + id);
            assertTrue(id.matches(
                    "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"), id);
            // Anything that read the old UUID.randomUUID() output must still be able to read this.
            assertEquals(id, UUID.fromString(id).toString());
        }
    }

    @Test
    @DisplayName("identifiers do not repeat")
    void distinct() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 20_000; i++) {
            assertTrue(seen.add(Ids.newId()), "repeated identifier after " + seen.size());
        }
    }

    /**
     * The last resort: a browser with no {@code crypto} object at all leaves TeaVM falling back to
     * an ordinary pseudo-random generator. That path must still produce something well formed,
     * because a malformed identifier fails far away from here and looks like something else.
     */
    @Test
    @DisplayName("the plain pseudo-random last resort still produces a valid identifier")
    void pseudoRandomFallback() {
        Random plain = new Random(12345);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            String id = Ids.newId(plain);
            assertTrue(id.matches(
                    "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"), id);
            assertTrue(seen.add(id), "repeated identifier from the fallback");
        }
    }

    /**
     * The regression guard.
     *
     * <p>{@code UUID.randomUUID()} is compiled by TeaVM into {@code crypto.randomUUID()}, which is
     * absent on a plain {@code http://} page and takes the whole application down with
     * "crypto.randomUUID is not a function". The two classes below run in the browser, so neither
     * may mention it. Reading the compiled class rather than the source is deliberate: it catches
     * the call arriving through a constant, a helper or a rename.</p>
     */
    @Test
    @DisplayName("nothing on the browser path asks for a UUID from the browser")
    void browserPathNeverCallsRandomUuid() throws IOException {
        for (Class<?> onTheBrowserPath : new Class<?>[] { Ids.class, ObjectMapper.class }) {
            String compiled = readClassFile(onTheBrowserPath);
            assertFalse(compiled.contains("randomUUID"),
                    onTheBrowserPath.getSimpleName() + " references randomUUID, which does not exist"
                            + " on a page served over plain http");
            assertFalse(compiled.contains("java/util/UUID"),
                    onTheBrowserPath.getSimpleName() + " references java.util.UUID, whose random"
                            + " constructor is unavailable on a page served over plain http");
        }
    }

    private static String readClassFile(Class<?> type) throws IOException {
        String resource = type.getName().replace('.', '/') + ".class";
        try (InputStream in = IdsTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertTrue(in != null, "could not read " + resource);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) > 0) {
                out.write(chunk, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.ISO_8859_1);
        }
    }
}
