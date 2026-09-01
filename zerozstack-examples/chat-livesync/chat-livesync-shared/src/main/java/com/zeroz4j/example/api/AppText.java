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
package com.zeroz4j.example.api;

import com.zeroz4j.api.i18n.MessageCatalog;

/**
 * This example's words, in every language it has.
 *
 * <p>An empty class on purpose. The annotation processor reads
 * {@code src/main/resources/i18n/app.properties} beside it and writes two classes:</p>
 *
 * <ul>
 *   <li>{@code AppText_Text} - one method per key, so a misspelled key and a wrong number of
 *       values are both compile errors rather than something wrong on somebody's screen;</li>
 *   <li>{@code AppText_Catalog} - the English compiled in, which is what the browser shows before
 *       the connection is up.</li>
 * </ul>
 *
 * <p>It lives in the <b>shared</b> module because that is the one module compiled into both the
 * browser and the server, so one file is the source for a button's label and for the sentence a
 * service refuses a value with.</p>
 */
@MessageCatalog(baseName = "i18n/app", fallback = "en")
public final class AppText {

    private AppText() {
    }
}
