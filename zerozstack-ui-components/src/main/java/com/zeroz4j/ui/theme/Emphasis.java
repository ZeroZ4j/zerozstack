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
 * <p>Size and loudness are two different questions and they were being answered with one number.
 * "Small" and "quiet" arrived together in every hand-written class list, so text that had to be
 * small and <i>fully</i> present — an error under a field, a value in a table — came out faded
 * along with everything else. Fading an error is wrong: it is the one line the reader must not
 * miss.</p>
 *
 * <p>Each size in {@link TextStyle} names one of these as its own, so asking for a size alone
 * still gives the right answer. Say one of these as well only where the text disagrees with its
 * size:</p>
 *
 * <pre>{@code
 * TextStyle.CAPTION.applyTo(errorLine, Emphasis.FULL);     // small, but nothing is taken off it
 * TextStyle.SECONDARY.span("3 of 12", Emphasis.FAINT);     // there, and out of the way
 * }</pre>
 *
 * <p><b>It is a fade, not a colour.</b> The text keeps whatever colour it inherits and simply
 * gives some of it up, so it stays right on a dark background, a light one, a tinted notice or a
 * coloured card — and two pieces of quiet text on one page cannot end up different greys. The
 * library used to write {@code text-base-content/60} and its neighbours instead, which names a
 * colour and therefore goes wrong the moment the surface underneath is not the plain page.</p>
 */
public enum Emphasis {

    /** As present as the words around it. Errors, values, anything that must be read. */
    FULL(""),

    /** A step back from the prose: timestamps, counts, explanations. */
    QUIET("opacity-70"),

    /** As far back as text goes and still be text: units, hints, the words under a picture. */
    FAINT("opacity-60");

    private final String classNames;

    Emphasis(String classNames) {
        this.classNames = classNames;
    }

    /**
     * Returns the stylesheet classes this loudness is made of, which is nothing at all for
     * {@link #FULL}.
     *
     * <p>Applications should not need this — {@link TextStyle#applyTo(com.zeroz4j.ui.component.HasStyle, Emphasis)}
     * and its neighbours put them on for you. It is here for components inside this library that
     * build their own elements.</p>
     *
     * @return the space-separated class names, possibly empty
     */
    public String getClassNames() {
        return classNames;
    }
}
