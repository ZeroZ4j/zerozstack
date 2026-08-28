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
 * How loud a piece of text is, separately from how big it is.
 *
 * <p>Size and strength are two different questions, and tying them together is what made this
 * library describe "quiet supporting text" thirteen different ways. A size says how much room the
 * words take. A strength says whether they are the words the reader came for or the words that
 * support them. Every size can be either.</p>
 *
 * <pre>{@code
 * add(TextStyle.CAPTION.span("of 40 seats"));                    // small and quiet, the usual case
 * add(TextStyle.CAPTION.span("96 %", Emphasis.FULL));            // small, but a measurement
 * add(TextStyle.BODY.span("Not saved yet", Emphasis.QUIET));     // ordinary size, kept back
 * }</pre>
 *
 * <p><b>There is one quiet, and this is it.</b> Quiet is a fade of whatever colour the text has
 * inherited, never a named grey, so the same words read correctly on a page, on a tinted notice, on
 * a coloured card and on a dark background. And there is a single amount of fade for the whole
 * library - one number here, used by the text sizes and by the words a chart draws inside its own
 * picture - because two fades that were meant to look the same are exactly the drift this
 * replaces.</p>
 */
public enum Emphasis {

    /** The words the reader came for. Nothing is taken off them. */
    FULL("", 1.0),

    /** Supporting words. Faded, so they sit behind the words they support. */
    QUIET("opacity-70", 0.7);

    private final String className;
    private final double opacity;

    Emphasis(String className, double opacity) {
        this.className = className;
        this.opacity = opacity;
    }

    /**
     * The stylesheet class that makes text this loud, for anything built out of HTML elements.
     *
     * @return the class name, or an empty string for {@link #FULL}, which adds nothing
     */
    public String getClassName() {
        return className;
    }

    /**
     * The same strength as a number, for anything drawn rather than laid out.
     *
     * <p>SVG has no stylesheet classes to hand, so a chart sets {@code fill-opacity} instead. It is
     * the same fade, read from the same place, so a chart's axis labels and the legend underneath
     * them are quiet by the same amount.</p>
     *
     * @return 1.0 for {@link #FULL}, and the one shared fade for {@link #QUIET}
     */
    public double getOpacity() {
        return opacity;
    }
}
