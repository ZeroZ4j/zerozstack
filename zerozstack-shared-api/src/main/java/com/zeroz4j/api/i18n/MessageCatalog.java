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
package com.zeroz4j.api.i18n;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares one catalog of translated text, so the words in it become methods.
 *
 * <p>Put this on an empty marker class in the application's <b>shared</b> module — the one module
 * compiled into both the browser and the server — beside the {@code .properties} files it names:</p>
 *
 * <pre>{@code
 * myapp-shared/src/main/resources/i18n/app.properties      the fallback language
 * myapp-shared/src/main/resources/i18n/app_de.properties   German
 * }</pre>
 *
 * <pre>{@code
 * @MessageCatalog(baseName = "i18n/app", fallback = "en")
 * public final class AppText {
 *     private AppText() { }
 * }
 * }</pre>
 *
 * <p>The annotation processor reads the fallback file and writes two classes beside the marker:</p>
 *
 * <ul>
 *   <li><b>{@code AppText_Text}</b> — one method per key, named by camel-casing the key, with one
 *       parameter per {@code {0}} placeholder. Each returns a {@link Message}, not a
 *       {@code String}: turning it into words is a separate act, so the same value can travel to
 *       the edge of the server and be rendered in the caller's language there.</li>
 *   <li><b>{@code AppText_Catalog}</b> — the fallback language compiled into Java, which is what
 *       the browser reads before anything has arrived over the connection.</li>
 * </ul>
 *
 * <p>Every <em>other</em> language file is never read at compile time. Adding French is dropping
 * {@code app_fr.properties} into the same folder and restarting the server: nothing is regenerated
 * and the browser bundle does not grow.</p>
 *
 * @since 0.9.0
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface MessageCatalog {

    /**
     * Where the {@code .properties} files are, as a classpath path with no suffix and no extension —
     * {@code "i18n/app"}, not {@code "com.example.i18n.app"}.
     *
     * <p>A path rather than a package name because both tiers reach it the same way: the server
     * reads it from its runtime classpath, and the compiler reads it from the compile classpath.</p>
     *
     * @return the classpath path of the catalog, without a language suffix
     */
    String baseName();

    /**
     * The language written in the file with no suffix — the one every other language falls back to,
     * and the one compiled into the browser.
     *
     * @return an IETF language tag such as {@code "en"}
     */
    String fallback() default "en";
}
