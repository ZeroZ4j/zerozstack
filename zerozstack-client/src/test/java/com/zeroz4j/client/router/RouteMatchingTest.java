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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Matching decides which view a URL reaches, so a wrong answer is a user looking at the wrong page.
 * These tests are plain JUnit — the matcher deliberately has no browser dependency, which is what
 * makes the routing rules testable at all.
 */
class RouteMatchingTest {

    @BeforeEach
    @AfterEach
    void reset() {
        RouteRegistry.resetForTesting();
    }

    private static RouteDefinition route(String pattern, String className) {
        return route(pattern, className, null, Collections.emptySet(), false);
    }

    private static RouteDefinition route(String pattern, String className, String layoutClassName,
                                         Set<String> roles, boolean layout) {
        return new RouteDefinition(pattern, className, layoutClassName, roles,
                Object::new, layout, "Label", 100);
    }

    // ---------------------------------------------------------------- patterns

    @Test
    void aLiteralPathMatchesItself() {
        RouteDefinition tasks = route("/tasks", "TaskListView");

        assertNotNull(tasks.match("/tasks"));
        assertNull(tasks.match("/projects"));
    }

    @Test
    void aParameterSegmentIsCaptured() {
        Map<String, String> params = route("/tasks/:id", "TaskDetailView").match("/tasks/42");

        assertEquals("42", params.get("id"));
    }

    @Test
    void severalParametersAreCaptured() {
        Map<String, String> params =
                route("/teams/:team/tasks/:id", "X").match("/teams/alpha/tasks/42");

        assertEquals("alpha", params.get("team"));
        assertEquals("42", params.get("id"));
    }

    @Test
    void aDifferentSegmentCountNeverMatches() {
        RouteDefinition detail = route("/tasks/:id", "TaskDetailView");

        assertNull(detail.match("/tasks"));
        assertNull(detail.match("/tasks/42/edit"));
    }

    @Test
    void trailingSlashesAndMissingLeadingSlashesAreTolerated() {
        RouteDefinition tasks = route("tasks/", "TaskListView");

        assertEquals("/tasks", tasks.pattern());
        assertNotNull(tasks.match("/tasks/"));
        assertNotNull(tasks.match("/tasks"));
    }

    @Test
    void theRootPathMatches() {
        RouteDefinition home = route("/", "HomeView");

        assertNotNull(home.match("/"));
        assertNull(home.match("/tasks"));
    }

    @Test
    void anEncodedParameterIsDecoded() {
        Map<String, String> params = route("/users/:name", "X").match("/users/Sch%C3%B6ning");

        assertEquals("Schöning", params.get("name"),
                "a parameter arrives percent-encoded and is used as a value, not a URL");
    }

    // ---------------------------------------------------------------- specificity

    @Test
    void aLiteralRouteWinsOverAParameterisedOne() {
        // Registered in the order that would give the wrong answer if declaration order decided it.
        RouteRegistry.register(route("/tasks/:id", "TaskDetailView"));
        RouteRegistry.register(route("/tasks/new", "NewTaskView"));

        assertEquals("NewTaskView",
                RouteRegistry.match("/tasks/new").definition().targetClassName(),
                "/tasks/new must not be read as a task with the id 'new'");
        assertEquals("TaskDetailView",
                RouteRegistry.match("/tasks/42").definition().targetClassName());
    }

    @Test
    void anUnmatchedPathReturnsNothing() {
        RouteRegistry.register(route("/tasks", "TaskListView"));

        assertNull(RouteRegistry.match("/nowhere"));
    }

    @Test
    void aLayoutIsNeverMatchedDirectly() {
        RouteRegistry.register(route("/", "AppShell", null, Collections.emptySet(), true));

        assertNull(RouteRegistry.match("/"),
                "a layout is chrome reached through a child route, not a destination");
    }

    @Test
    void twoRoutesClaimingOnePathAreRefused() {
        RouteRegistry.register(route("/tasks", "TaskListView"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> RouteRegistry.register(route("/tasks", "OtherTaskView")));

        assertTrue(error.getMessage().contains("/tasks"),
                "which one wins would otherwise depend on build order: " + error.getMessage());
    }

    @Test
    void reRegisteringTheSameClassIsIgnored() {
        RouteRegistry.register(route("/tasks", "TaskListView"));
        RouteRegistry.register(route("/tasks", "TaskListView"));

        assertEquals(1, RouteRegistry.all().size(),
                "a registrar running twice must not duplicate the table");
    }

    // ---------------------------------------------------------------- definitions

    @Test
    void rolesAndLayoutAreCarried() {
        RouteDefinition admin = route("/admin", "AdminView", "AppShell", Set.of("admin"), false);

        assertEquals(Set.of("admin"), admin.requiredRoles());
        assertEquals("AppShell", admin.layoutClassName());
        assertTrue(!admin.isLayout());
    }

    @Test
    void aRouteCanBeFoundByItsClassSoLayoutsResolve() {
        RouteRegistry.register(route("/", "AppShell", null, Collections.emptySet(), true));

        assertNotNull(RouteRegistry.byClassName("AppShell"));
        assertNull(RouteRegistry.byClassName("NotRegistered"));
    }
}
