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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The manifest is hand-rolled JSON, so the things worth testing are the ones a JSON library would
 * otherwise have guaranteed: that the document parses, and that a tenant's name cannot break out of
 * its string.
 */
class PwaManifestTest {

    @Test
    void carriesTheDefaultsAnInstallablePageNeeds() {
        String json = PwaManifest.named("Acme Portal", "Acme").toJson();

        assertTrue(json.contains("\"name\": \"Acme Portal\""), json);
        assertTrue(json.contains("\"short_name\": \"Acme\""), json);
        // standalone is the display mode that actually drops browser chrome; the rest are opt-in.
        assertTrue(json.contains("\"display\": \"standalone\""), json);
        assertTrue(json.contains("\"start_url\": \"/\""), json);
        assertTrue(json.contains("\"scope\": \"/\""), json);
    }

    @Test
    void fallsBackToTheFullNameWhenNoShortNameIsGiven() {
        assertTrue(PwaManifest.named("Acme Portal", null).toJson()
                .contains("\"short_name\": \"Acme Portal\""));
        assertTrue(PwaManifest.named("Acme Portal", "  ").toJson()
                .contains("\"short_name\": \"Acme Portal\""));
    }

    @Test
    void refusesToBuildWithoutAName() {
        // The name is what the installed application is called on the home screen. A manifest
        // without one installs as a blank icon, which is worse than a build that stops.
        assertThrows(IllegalArgumentException.class, () -> PwaManifest.named(null, "Acme"));
        assertThrows(IllegalArgumentException.class, () -> PwaManifest.named("   ", "Acme"));
    }

    @Test
    void escapesWhateverATenantNameContains() {
        // A tenant display name is user data and reaches this class unfiltered. A bare quote would
        // end the string early and turn the rest of the manifest into syntax errors.
        String json = PwaManifest.named("O\"Brien \\ Co", "OB")
                .description("Line one\nLine two\ttabbed")
                .toJson();

        assertTrue(json.contains("\"name\": \"O\\\"Brien \\\\ Co\""), json);
        assertTrue(json.contains("\"description\": \"Line one\\nLine two\\ttabbed\""), json);
        // One field per line: a raw newline that survived escaping would split one of them in two.
        assertEquals(11, json.trim().split("\n").length, json);
    }

    @Test
    void writesIconsWithSizesAndAnInferredMediaType() {
        String json = PwaManifest.named("Acme", "Acme")
                .icon("/icons/192.png", 192)
                .icon("/icons/vector.svg", 512)
                .icon("/icons/mask.png", "512x512", "image/png", "maskable")
                .toJson();

        assertTrue(json.contains("\"src\": \"/icons/192.png\", \"sizes\": \"192x192\", "
                + "\"type\": \"image/png\""), json);
        assertTrue(json.contains("\"type\": \"image/svg+xml\""), json);
        assertTrue(json.contains("\"purpose\": \"maskable\""), json);
    }

    @Test
    void staysValidJsonWithNoIcons() {
        String json = PwaManifest.named("Acme", "Acme").toJson();

        // An empty icons array must not leave a dangling comma or an unclosed bracket.
        assertTrue(json.contains("\"icons\": []"), json);
        assertTrue(json.trim().endsWith("}"), json);
    }

    @Test
    void includesTheOptionalFieldsOnlyWhenSet() {
        String bare = PwaManifest.named("Acme", "Acme").toJson();
        assertTrue(!bare.contains("orientation"), bare);
        assertTrue(!bare.contains("description"), bare);

        String full = PwaManifest.named("Acme", "Acme")
                .description("A portal")
                .orientation("portrait")
                .startUrl("/app")
                .scope("/app")
                .themeColor("#7c2d12")
                .backgroundColor("#000000")
                .display("fullscreen")
                .toJson();

        assertTrue(full.contains("\"description\": \"A portal\""), full);
        assertTrue(full.contains("\"orientation\": \"portrait\""), full);
        assertTrue(full.contains("\"start_url\": \"/app\""), full);
        assertTrue(full.contains("\"theme_color\": \"#7c2d12\""), full);
        assertTrue(full.contains("\"display\": \"fullscreen\""), full);
    }
}
