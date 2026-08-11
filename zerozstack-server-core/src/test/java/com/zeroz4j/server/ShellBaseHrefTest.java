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

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shell answers its own URL and every client route that falls back to it, so a relative asset
 * reference in it resolves against a different directory depending on which route was asked for.
 * A {@code <base href>} is what makes one document correct for all of them — and it is the only
 * thing that can be, because the depth of the route is not known when the page is written.
 */
class ShellBaseHrefTest {

    private static final String SHELL =
            "<!DOCTYPE html>\n<html lang=\"en\">\n    <head>\n"
            + "        <meta charset=\"UTF-8\"/>\n"
            + "        <link rel=\"manifest\" href=\"manifest.webmanifest\"/>\n"
            + "    </head>\n    <body>\n"
            + "        <script src=\"js/classes.js\"></script>\n"
            + "    </body>\n</html>\n";

    @Test
    void aContextPathBecomesTheBase() {
        String html = StaticContent.withBaseHref(SHELL, "/coachapp");
        assertTrue(html.contains("<base href=\"/coachapp/\">"), html);
    }

    @Test
    void theBaseAlwaysEndsInASlash() {
        // Without the trailing slash the browser resolves against the PARENT directory, so
        // <base href="/coachapp"> would send js/classes.js to /js/classes.js — the exact bug the
        // base element was added to fix, now silently reintroduced.
        assertEquals("/coachapp/", StaticContent.baseHref("/coachapp"));
        assertEquals("/coachapp/", StaticContent.baseHref("/coachapp/"));
        assertEquals("/coachapp/", StaticContent.baseHref("coachapp"));
    }

    @Test
    void anApplicationAtTheSiteRootStillGetsOne() {
        // Not a no-op: it is what makes a deep link like /messages/42 resolve js/classes.js to
        // /js/classes.js instead of /messages/js/classes.js.
        assertEquals("/", StaticContent.baseHref(null));
        assertEquals("/", StaticContent.baseHref(""));
        assertEquals("/", StaticContent.baseHref("/"));
        assertTrue(StaticContent.withBaseHref(SHELL, "").contains("<base href=\"/\">"));
    }

    @Test
    void theBaseGoesInsideTheHeadAndBeforeEverythingRelative() {
        String html = StaticContent.withBaseHref(SHELL, "/coachapp");
        int head = html.indexOf("<head>");
        int base = html.indexOf("<base ");
        int manifest = html.indexOf("manifest.webmanifest");
        assertTrue(head < base, "the base element must be inside the head");
        assertTrue(base < manifest, "a relative reference before the base is resolved without it");
    }

    @Test
    void anApplicationThatDeclaredItsOwnBaseIsLeftAlone() {
        String own = SHELL.replace("<head>", "<head><base href=\"/somewhere/else/\">");
        assertEquals(own, StaticContent.withBaseHref(own, "/coachapp"));
    }

    @Test
    void aDocumentWithNoHeadIsNotRewritten() {
        String fragment = "<html><body>nothing to do here</body></html>";
        assertEquals(fragment, StaticContent.withBaseHref(fragment, "/coachapp"));
        assertFalse(StaticContent.withBaseHref(fragment, "/coachapp").contains("<base"));
    }

    @Test
    void headWithAttributesIsStillFound() {
        String html = StaticContent.withBaseHref(
                "<html><head profile=\"x\"><title>t</title></head></html>", "/app");
        assertTrue(html.indexOf("<base href=\"/app/\">") > html.indexOf("<head profile"), html);
        assertTrue(html.indexOf("<base") < html.indexOf("<title>"), html);
    }

    @Test
    void nullIsNotADocument() {
        assertEquals(null, StaticContent.withBaseHref(null, "/coachapp"));
    }

    // ------------------------------------------------------------------ a shell that is not on the classpath

    @Test
    void aShellFromSomewhereOtherThanTheClasspathIsRewrittenToo() {
        // The WAR case: index.html lives in the archive root, which a WAR's classloader cannot see.
        // Everything the shell needs - the base element above all - must still be applied to it, or
        // a WAR packaged the ordinary way is a page that cannot load its own bundle.
        byte[] bytes = StaticContent.shellBytes("/coachapp", only(StaticContent.SHELL, SHELL));

        assertTrue(new String(bytes, StandardCharsets.UTF_8).contains("<base href=\"/coachapp/\">"));
    }

    @Test
    void resolutionFollowsWhereverTheAssetsAre() {
        StaticContent.Assets assets = only("js/classes.js", "// the bundle");

        assertEquals("js/classes.js", StaticContent.resolve("/js/classes.js", assets));
        // No shell there, so a client route has nothing to fall back to and must stay a 404 rather
        // than a zero-length 200.
        assertNull(StaticContent.resolve("/messages/42", assets));
        assertNull(StaticContent.resolve("/", assets));
    }

    /** An Assets holding exactly one file, standing in for a place that is not the classpath. */
    private static StaticContent.Assets only(String path, String body) {
        return new StaticContent.Assets() {

            @Override public boolean exists(String candidate) {
                return path.equals(candidate);
            }

            @Override public java.io.InputStream open(String candidate) {
                return path.equals(candidate)
                        ? new java.io.ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8))
                        : null;
            }
        };
    }
}
