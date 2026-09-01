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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What language a connection reads, decided once, from what the handshake carried.
 *
 * <p>This module's own test catalog offers German, French and Brazilian Portuguese
 * ({@code src/test/resources/i18n/probe*.properties}), so narrowing has something real to narrow
 * against rather than a list a test invented.</p>
 */
class LocaleResolutionTest {

    private static final ServerConfig ENGLISH_DEPLOYMENT = ServerConfig.isolated().build();

    private static ServerConfig speaking(String language) {
        return ServerConfig.isolated().set(ServerSettings.I18N_DEFAULT_LOCALE, language).build();
    }

    // ================================================================= the order

    @Test
    @DisplayName("the handshake parameter wins, because the browser already knew the answer")
    void theParameterWins() {
        assertEquals("de", LocaleResolution.atHandshake(ENGLISH_DEPLOYMENT,
                "de", "zeroz-lang=fr", "fr-FR,fr;q=0.9"));
    }

    @Test
    @DisplayName("then the cookie, which is what makes a choice survive a restart")
    void thenTheCookie() {
        assertEquals("fr", LocaleResolution.atHandshake(ENGLISH_DEPLOYMENT,
                null, "other=x; zeroz-lang=fr; more=y", "de-DE,de;q=0.9"));
    }

    @Test
    @DisplayName("then the browser's own setting, which is what a first visit gets for free")
    void thenAcceptLanguage() {
        assertEquals("de", LocaleResolution.atHandshake(ENGLISH_DEPLOYMENT,
                null, null, "de-DE,de;q=0.9,en;q=0.8"));
    }

    @Test
    @DisplayName("the browser's list is read best first, and a language nobody translated is skipped")
    void theBestOfferedPreferenceWins() {
        // Japanese is first and this deployment has no Japanese, so the next one it can serve wins.
        assertEquals("fr", LocaleResolution.atHandshake(ENGLISH_DEPLOYMENT,
                null, null, "ja;q=1.0,fr;q=0.8,de;q=0.5"));
    }

    @Test
    @DisplayName("quality values order the list rather than being ignored")
    void qualityValuesOrderTheList() {
        assertEquals("de", LocaleResolution.atHandshake(ENGLISH_DEPLOYMENT,
                null, null, "fr;q=0.2,de;q=0.9"));
    }

    @Test
    @DisplayName("then the deployment's own setting")
    void thenTheDeploymentSetting() {
        assertEquals("de", LocaleResolution.atHandshake(speaking("de"), null, null, null));
    }

    @Test
    @DisplayName("and finally English")
    void andFinallyEnglish() {
        assertEquals("en", LocaleResolution.atHandshake(ENGLISH_DEPLOYMENT, null, null, null));
    }

    // ================================================================= narrowing

    @Test
    @DisplayName("a region is dropped when only the language is translated")
    void aRegionIsDroppedWhenOnlyTheLanguageExists() {
        assertEquals("de", LocaleResolution.atHandshake(ENGLISH_DEPLOYMENT, "de-AT", null, null));
    }

    @Test
    @DisplayName("a region is kept when that exact region is translated")
    void aRegionIsKeptWhenItExists() {
        assertEquals("pt-br", LocaleResolution.atHandshake(ENGLISH_DEPLOYMENT, "pt-BR", null, null));
    }

    @Test
    @DisplayName("a language nobody translated falls to the deployment's own, never to half of it")
    void anUntranslatedLanguageFallsBack() {
        assertEquals("en", LocaleResolution.atHandshake(ENGLISH_DEPLOYMENT, "ja", null, null));
        assertEquals("de", LocaleResolution.atHandshake(speaking("de"), "ja", null, null));
    }

    @Test
    @DisplayName("the deployment's own language is always available, translated or not")
    void theDeploymentsOwnLanguageIsAlwaysAvailable() {
        // Its words are the file with no language suffix, so there is nothing to look for.
        assertEquals("nl", LocaleResolution.atHandshake(speaking("nl"), "nl", null, null));
    }

    // ================================================================= what a browser can send

    @Test
    @DisplayName("a language tag that is not a language tag is ignored rather than looked up")
    void rubbishIsIgnored() {
        assertEquals("en", LocaleResolution.atHandshake(ENGLISH_DEPLOYMENT,
                "../../etc/passwd", null, null));
        assertEquals("en", LocaleResolution.atHandshake(ENGLISH_DEPLOYMENT,
                "de/../../secrets", null, null));
        assertEquals("en", LocaleResolution.atHandshake(ENGLISH_DEPLOYMENT, "", null, null));
        assertEquals("en", LocaleResolution.atHandshake(ENGLISH_DEPLOYMENT,
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", null, null));
    }

    @Test
    @DisplayName("underscores and capitals are read the same as the proper spelling")
    void spellingVariantsAreAccepted() {
        assertEquals("de", LocaleResolution.atHandshake(ENGLISH_DEPLOYMENT, "DE", null, null));
        assertEquals("pt-br", LocaleResolution.atHandshake(ENGLISH_DEPLOYMENT, "pt_BR", null, null));
    }

    // ================================================================= what the deployment has

    @Test
    @DisplayName("the offered languages are read off the classpath, not configured by hand")
    void offeredLanguagesComeFromTheClasspath() {
        Set<String> offered = MessageCatalogs.offeredLanguages();
        assertTrue(offered.contains("de"), offered.toString());
        assertTrue(offered.contains("fr"), offered.toString());
        assertTrue(offered.contains("pt-br"), offered.toString());
        assertFalse(offered.contains("ja"), offered.toString());
    }

    @Test
    @DisplayName("a catalog file is read as UTF-8, which is what a translator types")
    void catalogsAreReadAsUtf8() {
        assertEquals("Guten Tag", MessageCatalogs.read("i18n/probe", "de").getProperty("probe.greeting"));
        assertEquals("Good day", MessageCatalogs.read("i18n/probe", null).getProperty("probe.greeting"));
    }

    @Test
    @DisplayName("a language tag becomes a locale, and rubbish becomes English rather than nothing")
    void tagsBecomeLocales() {
        assertEquals(Locale.GERMAN.getLanguage(), LocaleResolution.localeOf("de").getLanguage());
        assertEquals("en", LocaleResolution.localeOf(null).getLanguage());
        assertEquals("en", LocaleResolution.localeOf("!!!").getLanguage());
    }

    /**
     * The framework ships its own words in German, and that must not make every deployment offer
     * German.
     *
     * <p>This is the trap the second language would otherwise be. {@code i18n/zeroz4j_de.properties}
     * is inside {@code zerozstack-shared-api}, so it is on the classpath of every application ever
     * built on this framework, translated or not. If it counted as a language the deployment can
     * answer in, an English-only application would answer a German browser with German refusals
     * over an English screen - which is the half-translated screen the narrowing rule exists to
     * prevent, arrived at from the opposite direction.</p>
     *
     * <p>So what a deployment offers is decided by the deployment's own catalogs. The framework's
     * words ride along with whichever of them it has.</p>
     */
    @Test
    @DisplayName("the framework's own German does not make a deployment offer German")
    void theFrameworksOwnLanguagesAreNotTheDeploymentsOwn() {
        MessageCatalogs.forgetForTesting();
        java.util.Set<String> offered = MessageCatalogs.offeredLanguages();

        assertTrue(offered.contains("de"),
                "this module's own test catalog has German, so German is offered here");
        assertTrue(MessageCatalogs.read(com.zeroz4j.api.i18n.FrameworkText.CATALOG, "de")
                        .containsKey(com.zeroz4j.api.i18n.FrameworkKeys.ACCESS_DENIED),
                "and the framework's shipped German is readable, which is the point of shipping it");

        // What a deployment offers comes from its own catalogs. Take the framework's file name and
        // ask what it contributes: nothing.
        java.util.Set<String> fromFrameworkFilesOnly = new java.util.LinkedHashSet<>();
        MessageCatalogs.languageOfForTesting("zeroz4j_de.properties", fromFrameworkFilesOnly);
        MessageCatalogs.languageOfForTesting("zeroz4j_ja.properties", fromFrameworkFilesOnly);
        assertTrue(fromFrameworkFilesOnly.isEmpty(),
                "a file of the framework's own catalog contributes no language to the offered list. "
                        + "Got: " + fromFrameworkFilesOnly);

        java.util.Set<String> fromAnApplicationsFiles = new java.util.LinkedHashSet<>();
        MessageCatalogs.languageOfForTesting("app_de.properties", fromAnApplicationsFiles);
        assertTrue(fromAnApplicationsFiles.contains("de"),
                "an application's own catalog is what decides, and it still does");
    }
}
