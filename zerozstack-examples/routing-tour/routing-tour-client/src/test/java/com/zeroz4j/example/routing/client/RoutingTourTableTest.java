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
package com.zeroz4j.example.routing.client;

import com.zeroz4j.client.router.RouteDefinition;
import com.zeroz4j.client.router.RouteRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the real route table, generated at compile time from this module's {@code @Route}
 * classes and loaded exactly as the browser loads it.
 *
 * <p>This is the end-to-end check on routing that a plain unit test cannot give: the annotations
 * were read, the table was written, the ServiceLoader entry works, and the resulting patterns match
 * the URLs the tour actually links to.</p>
 */
class RoutingTourTableTest {

    @BeforeAll
    static void loadTheGeneratedTable() {
        RouteRegistry.init();
    }

    private static String targetFor(String path) {
        RouteRegistry.RouteMatch match = RouteRegistry.match(path);
        assertNotNull(match, "no route matched " + path);
        return match.definition().targetClassName();
    }

    @Test
    void theGeneratedTableWasLoaded() {
        assertTrue(RouteRegistry.all().size() >= 7,
                "expected every @Route in this module, found: " + RouteRegistry.all());
    }

    @Test
    void everyLinkTheTourOffersResolves() {
        assertEquals(HomeView.class.getName(), targetFor("/"));
        assertEquals(ProjectListView.class.getName(), targetFor("/projects"));
        assertEquals(ProjectDetailView.class.getName(), targetFor("/projects/1"));
        assertEquals(TaskDetailView.class.getName(), targetFor("/projects/1/tasks/11"));
        assertEquals(AdminView.class.getName(), targetFor("/admin"));
        assertEquals(NotFoundView.class.getName(), targetFor("/not-found"));
        assertEquals(ForbiddenView.class.getName(), targetFor("/forbidden"));
    }

    @Test
    void theLiteralRouteBeatsTheParameterisedOne() {
        assertEquals(NewProjectView.class.getName(), targetFor("/projects/new"),
                "/projects/new must not be read as the project whose id is 'new'");
    }

    @Test
    void pathParametersAreExtracted() {
        assertEquals("42", RouteRegistry.match("/projects/42").pathParams().get("id"));

        RouteRegistry.RouteMatch task = RouteRegistry.match("/projects/1/tasks/11");
        assertEquals("1", task.pathParams().get("projectId"));
        assertEquals("11", task.pathParams().get("taskId"));
    }

    @Test
    void anUnknownPathMatchesNothingSoTheNotFoundRouteTakesOver() {
        assertNull(RouteRegistry.match("/nowhere"));
    }

    @Test
    void everyViewIsNestedInTheShell() {
        for (RouteDefinition definition : RouteRegistry.all()) {
            if (definition.isLayout()) {
                continue;
            }
            assertEquals(AppShell.class.getName(), definition.layoutClassName(),
                    definition.targetClassName() + " should sit inside the shell");
        }
    }

    @Test
    void theShellIsRegisteredAsALayoutAndIsNeverMatchedDirectly() {
        RouteDefinition shell = RouteRegistry.byClassName(AppShell.class.getName());

        assertNotNull(shell, "the layout must be in the table for children to link to it");
        assertTrue(shell.isLayout());
        assertEquals(HomeView.class.getName(), targetFor("/"),
                "'/' belongs to the view, not to the layout that happens to declare it");
    }

    @Test
    void theGuardedRouteCarriesItsRole() {
        RouteDefinition admin = RouteRegistry.byClassName(AdminView.class.getName());

        assertEquals(java.util.Set.of("admin"), admin.requiredRoles());
    }

    @Test
    void unguardedRoutesRequireNothing() {
        assertTrue(RouteRegistry.byClassName(ProjectListView.class.getName())
                .requiredRoles().isEmpty());
    }
}
