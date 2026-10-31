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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shell sets the stored theme before the first paint. */
class ShellThemeScriptTest {

    private static final String SHELL = "<html><head><title>x</title></head><body></body></html>";

    @Test
    void theScriptSitsAtTheEndOfTheHead() {
        String html = StaticContent.withThemeScript(SHELL);
        int script = html.indexOf(StaticContent.THEME_SCRIPT);
        assertTrue(script > html.indexOf("<title>"), html);
        assertTrue(script < html.indexOf("</head>"), html);
    }

    @Test
    void aShellThatHandlesTheThemeItselfIsLeftAlone() {
        String own = SHELL.replace("<title>", "<script>localStorage.getItem('zeroz4j.theme')</script><title>");
        assertEquals(own, StaticContent.withThemeScript(own));
    }

    @Test
    void aDocumentWithoutAHeadIsLeftAlone() {
        assertEquals("<p>x</p>", StaticContent.withThemeScript("<p>x</p>"));
    }
}
