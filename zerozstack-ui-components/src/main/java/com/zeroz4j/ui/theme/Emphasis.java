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

/**
 * How loud a piece of text is, independently of how big it is.
 *
 * <p>{@link TextStyle} answers "how big"; this answers "how strongly it speaks". Keeping the two
 * apart is what lets a measurement be small <em>and</em> at full strength. Naming sizes alone could
 * not express that: the smallest sizes were quiet by definition, so a gauge reading or an error
 * line had to be styled by hand.</p>
 *
 * <p>Quiet is a fade of whatever color the text already sits on, never a named grey. The same words
 * are then right on a page, inside a tinted notice and on a dark background, and two greys meant to
 * match cannot drift apart.</p>
 *
 * <p>Each level carries that fade twice: as class names for text the browser styles, and as a
 * number for text drawn into a chart, where class names do not reach. One definition, two
 * mechanisms, so a chart's labels and the words beneath it cannot disagree.</p>
 */
public enum Emphasis {

    /** Says it plainly. For anything somebody has to read: a measurement, an error, a total. */
    FULL("", 1.0),

    /** Present, but not competing. The ordinary choice for supporting text. */
    QUIET("opacity-70", 0.7),

    /** Barely there. For text that is background until somebody goes looking for it. */
    FAINT("opacity-60", 0.6);

    private final String classNames;
    private final double opacity;

    Emphasis(String classNames, double opacity) {
        this.classNames = classNames;
        this.opacity = opacity;
    }

    /**
     * The class names carrying this level, for text the browser styles.
     *
     * @return the class names; empty for {@link #FULL}
     */
    public String getClassNames() {
        return classNames;
    }

    /**
     * The same level, for callers that read one class name.
     *
     * @return the class name; empty for {@link #FULL}
     */
    public String getClassName() {
        return classNames;
    }

    /**
     * The same level as a number, for text drawn into a chart.
     *
     * @return the fade; 1.0 at full strength
     */
    public double getOpacity() {
        return opacity;
    }
}
