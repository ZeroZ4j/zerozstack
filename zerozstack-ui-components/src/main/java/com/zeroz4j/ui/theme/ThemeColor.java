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
package com.zeroz4j.ui.theme;

public enum ThemeColor {
    PRIMARY("primary"),
    SECONDARY("secondary"),
    ACCENT("accent"),
    NEUTRAL("neutral"),
    INFO("info"),
    SUCCESS("success"),
    WARNING("warning"),
    ERROR("error"),
    /** Button-specific style variant, not a standard DaisyUI color modifier. */
    GHOST("ghost"),
    /** Button-specific style variant, not a standard DaisyUI color modifier. */
    LINK("link");

    private final String classNameSuffix;

    ThemeColor(String classNameSuffix) {
        this.classNameSuffix = classNameSuffix;
    }

    public String getClassNameSuffix() {
        return classNameSuffix;
    }
}
