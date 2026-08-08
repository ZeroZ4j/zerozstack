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
package com.zeroz4j.client.router;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The typed accessors exist so a route parameter is not parsed by hand at every call site. These
 * tests pin where they throw and where they fall back, because those two behaviours are deliberately
 * different.
 */
class RouteParamsTest {

    private static RouteParams params(String path, Map<String, String> pathParams,
                                      Map<String, String> query) {
        return new RouteParams(path, pathParams, query);
    }

    private static Map<String, String> map(String... keyValues) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    @Test
    void pathParametersAreReadable() {
        RouteParams p = params("/tasks/42", map("id", "42"), map());

        assertEquals("42", p.get("id"));
        assertEquals(42L, p.getLong("id"));
        assertEquals(42, p.getInt("id"));
        assertEquals("/tasks/42", p.path());
    }

    @Test
    void aMissingPathParameterThrowsRatherThanReturningZero() {
        RouteParams p = params("/tasks", map(), map());

        assertNull(p.get("id"));
        // Returning 0 would send the view looking for a record with that id.
        assertThrows(IllegalArgumentException.class, () -> p.getLong("id"));
    }

    @Test
    void aNonNumericPathParameterThrowsWithTheOffendingValue() {
        RouteParams p = params("/tasks/abc", map("id", "abc"), map());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> p.getLong("id"));

        assertTrue(error.getMessage().contains("abc"), error.getMessage());
    }

    @Test
    void queryParametersFallBackInsteadOfThrowing() {
        // A query parameter is usually a user-adjustable option, where failing the whole navigation
        // over a typo would be worse than using the default.
        RouteParams p = params("/tasks", map(), map("page", "notanumber"));

        assertEquals(1L, p.queryLong("page", 1L));
        assertEquals("all", p.query("filter", "all"));
        assertNull(p.query("filter"));
    }

    @Test
    void anEmptyQueryValueUsesTheFallback() {
        RouteParams p = params("/tasks", map(), map("filter", ""));

        assertEquals("all", p.query("filter", "all"));
        assertEquals(2L, p.queryLong("page", 2L));
    }

    @Test
    void parametersAreReadOnly() {
        RouteParams p = params("/tasks/42", map("id", "42"), map());

        assertThrows(UnsupportedOperationException.class, () -> p.all().put("id", "99"));
    }
}
