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
package com.zeroz4j.ui.theme;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The class list the production stylesheet is built from. If a component's class is missing from it,
 * that class has no CSS in production and the component looks broken only there.
 */
class ProductionClassListTest {

    private static Set<String> load() throws IOException {
        try (InputStream in = ProductionClassListTest.class
                .getResourceAsStream("/META-INF/zeroz4j/css/library-classes.txt")) {
            assertNotNull(in, "library-classes.txt is not in the jar: the exec step in pom.xml did not run");
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return Stream.of(text.split("\n")).collect(Collectors.toSet());
        }
    }

    @Test
    void listsClassesWrittenAsLiterals() throws IOException {
        Set<String> classes = load();
        assertTrue(classes.contains("btn"), "btn");
        assertTrue(classes.contains(Surface.BASE), "the surface base class");
    }

    @Test
    void listsClassesBuiltByConcatenation() throws IOException {
        Set<String> classes = load();
        for (ThemeColor color : ThemeColor.values()) {
            assertTrue(classes.contains("btn-" + color.getClassNameSuffix()), "btn-" + color.getClassNameSuffix());
        }
        for (ThemeSize size : ThemeSize.values()) {
            assertTrue(classes.contains("badge-" + size.getClassNameSuffix()), "badge-" + size.getClassNameSuffix());
        }
        assertTrue(classes.contains("alert-error"), "alert-error");
        assertTrue(classes.contains("btn-outline"), "btn-outline");
    }
}
