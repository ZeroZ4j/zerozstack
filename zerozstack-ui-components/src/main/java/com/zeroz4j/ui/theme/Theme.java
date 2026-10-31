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

import com.zeroz4j.signals.Signal;
import com.zeroz4j.signals.ValueSignal;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/**
 * The page's color theme: light, dark, or whatever the operating system is set to.
 *
 * <p>Call {@link #start()} once in {@code main}. The choice is kept in this browser's
 * {@code localStorage} and put on the {@code html} element as {@code data-theme}, holding the name
 * of the theme that is showing ({@code light} or {@code dark} unless {@link #configure} says
 * otherwise). {@link Choice#SYSTEM} follows the operating system and changes with it while the
 * page is open. {@code data-theme-choice} on the same element carries the choice itself.</p>
 *
 * <p>The framework's shell reads the same stored choice before the first paint, so a returning
 * visitor does not see the wrong theme flash.</p>
 *
 * <p><b>Application CSS.</b> Custom properties that follow the theme need two selectors: the
 * attribute for the dark theme, and a media query for the moment before this class has run.</p>
 *
 * <pre>
 * :root { --panel: #fff; }
 * :root[data-theme="dark"] { --panel: #1c1c1e; }
 * &#64;media (prefers-color-scheme: dark) {
 *   :root:not([data-theme]) { --panel: #1c1c1e; }
 * }
 * </pre>
 *
 * <p>Use {@link com.zeroz4j.ui.component.ThemeController} for the sun and moon button and
 * {@link com.zeroz4j.ui.component.ThemeSelector} for a Light, Dark, System control.</p>
 */
public final class Theme {

    /** What a person can choose. */
    public enum Choice {
        /** The light theme. */
        LIGHT,
        /** The dark theme. */
        DARK,
        /** Whatever the operating system is set to, now and when it changes. */
        SYSTEM
    }

    /** {@code localStorage} key holding the choice: {@code light}, {@code dark} or {@code system}. */
    public static final String STORAGE_KEY = "zeroz4j.theme";

    /** {@code localStorage} key holding the two theme names, light first, comma separated. */
    public static final String NAMES_KEY = "zeroz4j.theme.names";

    private static final ValueSignal<Choice> CHOICE = new ValueSignal<>(Choice.SYSTEM);
    private static final ValueSignal<Choice> SHOWING = new ValueSignal<>(Choice.LIGHT);

    private static String lightName = "light";
    private static String darkName = "dark";
    private static boolean started;

    private Theme() {
    }

    /** Receives the operating system's change of color scheme. */
    @JSFunctor
    private interface SystemListener extends JSObject {
        void changed();
    }

    /**
     * Sets the names of the two themes, for an application that uses its own DaisyUI theme names.
     * Call before {@link #start()}; a later call applies the new names at once.
     *
     * @param light the {@code data-theme} value for the light theme
     * @param dark  the {@code data-theme} value for the dark theme
     */
    public static void configure(String light, String dark) {
        lightName = light;
        darkName = dark;
        if (started) {
            apply();
        }
    }

    /**
     * Reads the stored choice (System when there is none), applies it, and starts following the
     * operating system. Safe to call again; later calls do nothing.
     */
    public static void start() {
        if (started) {
            return;
        }
        started = true;
        CHOICE.set(parse(read(STORAGE_KEY)));
        onSystemChange(() -> new Thread(() -> {
            if (CHOICE.get() == Choice.SYSTEM) {
                apply();
            }
        }).start());
        apply();
    }

    /**
     * Stores and applies a choice.
     *
     * @param choice light, dark or system
     */
    public static void choose(Choice choice) {
        start();
        CHOICE.set(choice);
        write(STORAGE_KEY, choice.name().toLowerCase());
        apply();
    }

    /**
     * What the person chose.
     *
     * @return the choice as a read-only signal; System when nothing was chosen
     */
    public static Signal<Choice> choice() {
        start();
        return CHOICE;
    }

    /**
     * Which theme is on the screen now: {@link Choice#LIGHT} or {@link Choice#DARK}, also when the
     * choice is System and the operating system decides.
     *
     * @return the showing theme, as a read-only signal
     */
    public static Signal<Choice> showing() {
        start();
        return SHOWING;
    }

    /**
     * The name of the showing theme.
     *
     * @return the {@code data-theme} value now on the {@code html} element
     */
    public static String showingName() {
        start();
        return SHOWING.get() == Choice.DARK ? darkName : lightName;
    }

    private static void apply() {
        Choice chosen = CHOICE.get();
        boolean dark = chosen == Choice.DARK || chosen == Choice.SYSTEM && systemPrefersDark();
        SHOWING.set(dark ? Choice.DARK : Choice.LIGHT);
        setAttributes(dark ? darkName : lightName, chosen.name().toLowerCase());
        write(NAMES_KEY, lightName + "," + darkName);
    }

    private static Choice parse(String stored) {
        if ("light".equals(stored)) {
            return Choice.LIGHT;
        }
        return "dark".equals(stored) ? Choice.DARK : Choice.SYSTEM;
    }

    private static String read(String key) {
        try {
            return localGet(key);
        } catch (RuntimeException failure) {
            return null;
        }
    }

    private static void write(String key, String value) {
        try {
            localSet(key, value);
        } catch (RuntimeException failure) {
            // Storage is blocked or full: the choice still holds for this page.
        }
    }

    @JSBody(params = {"key"}, script = "return window.localStorage.getItem(key);")
    private static native String localGet(String key);

    @JSBody(params = {"key", "value"}, script = "window.localStorage.setItem(key, value);")
    private static native void localSet(String key, String value);

    @JSBody(script = "return !!(window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches);")
    private static native boolean systemPrefersDark();

    @JSBody(params = {"listener"}, script =
        "if (!window.matchMedia) { return; }"
        + "var query = window.matchMedia('(prefers-color-scheme: dark)');"
        + "var handler = function() { listener(); };"
        + "if (query.addEventListener) { query.addEventListener('change', handler); }"
        + "else if (query.addListener) { query.addListener(handler); }")
    private static native void onSystemChange(SystemListener listener);

    @JSBody(params = {"name", "choice"}, script =
        "var root = document.documentElement;"
        + "root.setAttribute('data-theme', name);"
        + "root.setAttribute('data-theme-choice', choice);"
        + "if (document.body) { document.body.removeAttribute('data-theme'); }")
    private static native void setAttributes(String name, String choice);
}
