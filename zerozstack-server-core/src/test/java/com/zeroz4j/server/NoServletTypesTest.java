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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This module must contain no servlet type.
 *
 * <p>That is not a style rule. A class here travels inside every WAR that depends on the framework,
 * and a servlet type carrying {@code @WebFilter} or {@code @WebServlet} claims a URL in an
 * application that never asked for it — which is exactly what {@code RmiSecurityFilter} did until
 * 0.6.0, answering 401 to every page of a deployment whose only fault was depending on this module.
 * A servlet binding belongs in {@code zerozstack-server-jakarta}, where a deployment maps it or does
 * not.</p>
 *
 * <p>The rule is asserted rather than reviewed because the old filter survived four releases: it
 * could not load on Helidon, so no example and no test ever ran it, and nothing said it was there.
 * Reading the compiled classes catches an inherited or transitive reference that reading the imports
 * would not.</p>
 *
 * <p>{@code jakarta.websocket} is a different API and is expected here — the RMI endpoint is a
 * WebSocket endpoint.</p>
 */
class NoServletTypesTest {

    @Test
    void noCompiledClassReferencesTheServletApi() throws IOException {
        Path classes = Paths.get("target", "classes");
        assertTrue(Files.isDirectory(classes),
                "target/classes is missing; run the test through Maven so the module is compiled");

        List<String> offenders = new ArrayList<>();
        try (Stream<Path> tree = Files.walk(classes)) {
            for (Path file : (Iterable<Path>) tree.filter(p -> p.toString().endsWith(".class"))::iterator) {
                // The constant pool holds every referenced type as a slash-separated name, so a
                // plain byte search finds an implemented interface, a field type and an annotation
                // alike — without a bytecode library this module does not otherwise need.
                String bytes = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
                if (bytes.contains("jakarta/servlet")) {
                    offenders.add(classes.relativize(file).toString());
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                "zerozstack-server-core must reference no servlet type, because every class here is "
                + "loaded inside somebody else's WAR. Move it to zerozstack-server-jakarta. Found: "
                + offenders);
    }
}
