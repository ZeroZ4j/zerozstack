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
package com.zeroz4j.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An application deployed under a context path — {@code /coachapp}, {@code /clientportal} — has to
 * translate between the routes it declares and the locations the browser shows. Every case here is
 * one that works at the site root and fails under a context path, which is why none of them showed
 * up in development.
 */
class AppBaseTest {

    private static final String ROOT = "/";
    private static final String WAR = "/coachapp/";

    // ------------------------------------------------------------------ location -> route

    @Test
    void atTheSiteRootALocationIsAlreadyARoute() {
        assertEquals("/", AppBase.toRoute(ROOT, "/"));
        assertEquals("/messages", AppBase.toRoute(ROOT, "/messages"));
        assertEquals("/messages/42", AppBase.toRoute(ROOT, "/messages/42"));
    }

    @Test
    void underAContextPathTheContextIsStripped() {
        assertEquals("/messages/42", AppBase.toRoute(WAR, "/coachapp/messages/42"));
        assertEquals("/messages", AppBase.toRoute(WAR, "/coachapp/messages"));
    }

    @Test
    void theApplicationRootIsTheRootRouteWithOrWithoutItsTrailingSlash() {
        // "/coachapp" and "/coachapp/" are the same page to a user and to WildFly, which redirects
        // one to the other. Both have to mean the route "/" or the landing view never renders.
        assertEquals("/", AppBase.toRoute(WAR, "/coachapp/"));
        assertEquals("/", AppBase.toRoute(WAR, "/coachapp"));
    }

    @Test
    void aQueryStringSurvivesTheTranslation() {
        assertEquals("/messages?tab=unread", AppBase.toRoute(WAR, "/coachapp/messages?tab=unread"));
        assertEquals("/?problem=1", AppBase.toRoute(WAR, "/coachapp?problem=1"));
        assertEquals("/?problem=1", AppBase.toRoute(WAR, "/coachapp/?problem=1"));
    }

    @Test
    void aLocationOutsideTheApplicationIsLeftAlone() {
        // Stripping something that is not there would turn another application's URL into one of
        // this application's routes, which is worse than not matching.
        assertEquals("/clientportal/journey", AppBase.toRoute(WAR, "/clientportal/journey"));
    }

    @Test
    void anEmptyLocationIsTheRootRoute() {
        assertEquals("/", AppBase.toRoute(WAR, null));
        assertEquals("/", AppBase.toRoute(WAR, ""));
    }

    // ------------------------------------------------------------------ route -> location

    @Test
    void atTheSiteRootARouteIsAlreadyALocation() {
        assertEquals("/messages/42", AppBase.toLocation(ROOT, "/messages/42"));
        assertEquals("/messages", AppBase.toLocation(ROOT, "messages"));
    }

    @Test
    void underAContextPathTheContextIsPutBack() {
        assertEquals("/coachapp/messages/42", AppBase.toLocation(WAR, "/messages/42"));
        assertEquals("/coachapp/", AppBase.toLocation(WAR, "/"));
        assertEquals("/coachapp/messages", AppBase.toLocation(WAR, "messages"));
    }

    @Test
    void aLocationHandedBackInIsNotPrefixedTwice() {
        // An anchor's href has to be a real URL for middle-click to work, so it carries the context
        // path; clicking it hands that same string to Router.navigate. Prefixing it again would push
        // /coachapp/coachapp/messages/42.
        assertEquals("/coachapp/messages/42", AppBase.toLocation(WAR, "/coachapp/messages/42"));
    }

    @Test
    void translationRoundTrips() {
        for (String route : new String[] {"/", "/messages", "/messages/42", "/a/b/c"}) {
            assertEquals(route, AppBase.toRoute(WAR, AppBase.toLocation(WAR, route)), route);
            assertEquals(route, AppBase.toRoute(ROOT, AppBase.toLocation(ROOT, route)), route);
        }
    }

    // ------------------------------------------------------------------ reading the base

    @Test
    void theBaseIsAlwaysADirectory() {
        assertEquals("/coachapp/", AppBase.normalize("/coachapp/"));
        assertEquals("/", AppBase.normalize("/"));
        assertEquals("/", AppBase.normalize(null));
        assertEquals("/", AppBase.normalize(""));
    }

    @Test
    void aBaseUriWithNoBaseElementDropsTheDocumentItself() {
        // With no <base> in the page, document.baseURI is the document's own URL. On a deep link
        // that is /coachapp/messages/42, and taking it whole would make every relative asset
        // resolve under /coachapp/messages/ — which is the blank page a hard refresh gives.
        assertEquals("/coachapp/messages/", AppBase.normalize("/coachapp/messages/42"));
        assertEquals("/", AppBase.normalize("/index.html"));
    }
}
