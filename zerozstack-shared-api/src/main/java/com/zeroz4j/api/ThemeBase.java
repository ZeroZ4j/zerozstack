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
package com.zeroz4j.api;

/**
 * The library's page and panel colors, for the {@code light} and {@code dark} themes.
 *
 * <p>DaisyUI's stock themes put the page, a panel and an edge within a few percent of each other,
 * so a screen reads as one flat gray. Here the page is {@code base-200}, a panel on it is
 * {@code base-100} and a hairline is {@code base-300}, and the page is drawn in {@code base-200}
 * whatever class is on {@code body}. Every surface and every DaisyUI card gets the hairline as a
 * border. Only those colors change; primary, accent and the status colors stay DaisyUI's.</p>
 *
 * <p>The server puts this at the end of {@code <head>}, in a cascade layer of its own. That layer
 * comes after DaisyUI's, so it beats the stock themes, and any stylesheet outside a layer beats
 * it, so an application's own theme file wins in any order. A theme with any other name is not
 * touched.</p>
 */
public final class ThemeBase {

    /** The id of the style element, so it goes in once. */
    public static final String STYLE_ID = "zeroz4j-theme-base";

    private static final String LIGHT = "color-scheme:light;"
        + "--color-base-100:#FFFFFF;--color-base-200:#E7EBF0;--color-base-300:#C3CCD7;"
        + "--color-base-content:#1B2430;--zeroz4j-surface-edge:var(--color-base-300);";

    private static final String DARK = "color-scheme:dark;"
        + "--color-base-100:oklch(25.5% 0.014 255);--color-base-200:oklch(15.5% 0.012 255);"
        + "--color-base-300:oklch(38% 0.016 255);--color-base-content:oklch(94% 0.008 250);"
        + "--zeroz4j-surface-edge:var(--color-base-300);";

    /** The stylesheet text. */
    public static final String CSS = "@layer zeroz4j-theme{"
        + ":root:not([data-theme]),[data-theme=\"light\"]{" + LIGHT + "}"
        + "@media (prefers-color-scheme:dark){:root:not([data-theme]){" + DARK + "}}"
        + "[data-theme=\"dark\"]{" + DARK + "}"
        + "html,body{background-color:var(--color-base-200)}"
        + ":where(:root:not([data-theme]),[data-theme=\"light\"],[data-theme=\"dark\"])"
        + " .card:not(.card-border,.card-dash,.image-full){border:1px solid var(--color-base-300)}"
        + "}";

    private ThemeBase() {
    }
}
