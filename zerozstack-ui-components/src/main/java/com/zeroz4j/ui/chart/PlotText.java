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
package com.zeroz4j.ui.chart;

import com.zeroz4j.ui.theme.Emphasis;
import com.zeroz4j.ui.theme.TextStyle;

/**
 * The four kinds of words a chart draws inside its own picture, by name.
 *
 * <p>This is {@link TextStyle} for the part of a chart that is drawn rather than laid out. A chart
 * is an SVG picture, and text inside a picture carries a font size and a fade as numbers on the
 * element, not as stylesheet classes - so the type scale cannot reach it. It stopped at the edge
 * of the drawing, and the drawing drifted exactly as everything else had: the twenty-four labels
 * in this package were written at two sizes and <em>seven</em> different degrees of fade.</p>
 *
 * <p>The names below are the same idea as the type scale, so somebody who has learned one can
 * guess the other: a headline, an ordinary label, and the smallest thing there is. Strength is the
 * same second question too, and the same {@link Emphasis} answers it - so the fade on an axis
 * label and the fade on the legend underneath it are one number in one place.</p>
 *
 * <pre>{@code
 * add(text(PlotText.LABEL, x, y, "Monday", "middle"));                    // the usual case
 * add(monoText(PlotText.CAPTION, x, y, "41", "start"));                   // a number on a bar
 * add(monoText(PlotText.FIGURE, dialSize, cx, cy, "96 %", "middle"));     // the space picks the size
 * add(text(PlotText.CAPTION, Emphasis.FULL, x, y, name, "middle"));       // words on a coloured block
 * }</pre>
 *
 * <p>Four, because four things were really there. The words in the margin and the numbers inside
 * the plot are different sizes because they compete with different things; a headline reading is a
 * different thing again; and the sentence a panel shows when it has nothing to draw is the one
 * piece of prose a chart writes, which at label size is uncomfortable to read. Everything else the
 * charts drew turned out to be one of those four written down differently.</p>
 */
public enum PlotText {

    /**
     * The one big number in the middle of a dial or a ring - the reading the panel exists to give.
     *
     * <p>Its size is decided by the hole it has to fit, so every caller passes one; the size here
     * is only what it falls back to.</p>
     */
    FIGURE(20, Emphasis.FULL, "700"),

    /**
     * The words that name a position: tick values, category names, axis titles, row names, and the
     * caption under a headline reading. They sit in the margin around the plot.
     */
    LABEL(10, Emphasis.QUIET, null),

    /**
     * The smallest there is: a number printed inside the plot beside the mark it measures, or at
     * the end of a scale. It shares the drawing with the data, so it is a step smaller than a
     * label.
     */
    CAPTION(9, Emphasis.QUIET, null),

    /**
     * The sentence a panel shows when it has nothing to draw - "No data for the selected range".
     *
     * <p>The only prose a chart writes, and the only text in a chart that somebody has to read
     * rather than glance at, so it is a step larger than a label rather than a step smaller.</p>
     */
    MESSAGE(12, Emphasis.QUIET, null);

    private final double fontSize;
    private final Emphasis naturalEmphasis;
    private final String fontWeight;

    PlotText(double fontSize, Emphasis naturalEmphasis, String fontWeight) {
        this.fontSize = fontSize;
        this.naturalEmphasis = naturalEmphasis;
        this.fontWeight = fontWeight;
    }

    /**
     * The size this role is drawn at when the caller does not pick one.
     *
     * @return a font size in pixels
     */
    public double getFontSize() {
        return fontSize;
    }

    /**
     * The strength this role comes with when nothing says otherwise.
     *
     * @return {@link Emphasis#QUIET} for the two supporting roles, {@link Emphasis#FULL} for the
     *     headline reading
     */
    public Emphasis getNaturalEmphasis() {
        return naturalEmphasis;
    }

    /**
     * The weight this role is drawn at, if it has one of its own.
     *
     * @return an SVG {@code font-weight} value, or null to leave the browser's normal weight
     */
    public String getFontWeight() {
        return fontWeight;
    }
}
