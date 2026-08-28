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

import com.zeroz4j.ui.component.HasStyle;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.Span;

/**
 * The five sizes of text an application has, by name.
 *
 * <p>Text is the one thing every screen has and the one thing no component owns, so it is the
 * thing applications describe over and over instead of asking for. One application built on this
 * library wrote out its own version of "quiet supporting text" a dozen times, and because it was
 * typed out rather than named, the quietness came out different nearly every time — three
 * different sizes and four different degrees of fade, on pages sitting next to each other. This
 * library did the same to itself: its own components spell out quiet text thirteen different ways,
 * in five degrees of fade, across fifty-six places.</p>
 *
 * <p>There is one definition of each size here and no way to say "nearly that", which is the
 * whole point:</p>
 *
 * <pre>{@code
 * add(TextStyle.PAGE_TITLE.span("Deliveries"));
 * add(TextStyle.SECONDARY.span("Updated a moment ago"));
 *
 * TextStyle.CAPTION.applyTo(myOwnLabel);   // on a component you already have
 * }</pre>
 *
 * <p><b>Quiet is opacity, not a colour.</b> The two quiet sizes fade the colour they inherit
 * rather than naming one. Text therefore stays right on a dark background, a light one, a tinted
 * notice or a coloured card, without the caller choosing per surface — and a page cannot end up
 * with two shades of grey that were meant to be the same one.</p>
 *
 * <p><b>How loud is a separate question from how big.</b> Each size names its own loudness, so
 * asking for a size alone is still the whole answer nearly every time. Where a piece of text
 * disagrees with its size — a small error line that must not be faded, a count that should be
 * further out of the way — say an {@link Emphasis} as well:</p>
 *
 * <pre>{@code
 * TextStyle.CAPTION.applyTo(errorLine, Emphasis.FULL);
 * TextStyle.SECONDARY.span("3 of 12", Emphasis.FAINT);
 * }</pre>
 *
 * <p>Five is deliberate. A scale nobody can hold in their head is a scale that gets ignored and
 * typed out again, which is the problem this exists to end.</p>
 */
public enum TextStyle {

    /** The name of the screen. One per page, at the top. */
    PAGE_TITLE("text-3xl font-bold leading-tight", Emphasis.FULL),

    /** The heading over a group of things — a card, a panel, a block of a form. */
    SECTION_TITLE("text-lg font-semibold leading-snug", Emphasis.FULL),

    /** Ordinary prose: the words the reader is here to read. */
    BODY("text-base leading-relaxed", Emphasis.FULL),

    /** Supporting words, a step quieter than the prose. Timestamps, counts, explanations. */
    SECONDARY("text-sm leading-normal", Emphasis.QUIET),

    /** The smallest label there is: units, hints, the words under a picture. */
    CAPTION("text-xs leading-normal", Emphasis.FAINT);

    private final String sizeClasses;
    private final Emphasis ownEmphasis;

    TextStyle(String sizeClasses, Emphasis ownEmphasis) {
        this.sizeClasses = sizeClasses;
        this.ownEmphasis = ownEmphasis;
    }

    /** How loud this size is when nobody says otherwise. */
    public Emphasis getEmphasis() {
        return ownEmphasis;
    }

    /**
     * Returns the stylesheet classes this size is made of.
     *
     * <p>Applications should not need this — {@link #applyTo} and {@link #span} put them on for
     * you. It is here for components inside this library that build their own elements.</p>
     *
     * @return the space-separated class names
     */
    public String getClassNames() {
        return getClassNames(ownEmphasis);
    }

    /**
     * Returns the stylesheet classes this size is made of, at a loudness of your choosing.
     *
     * @param emphasis how present the text should be; null means this size's own
     * @return the space-separated class names
     */
    public String getClassNames(Emphasis emphasis) {
        String fade = (emphasis == null ? ownEmphasis : emphasis).getClassNames();
        return fade.isEmpty() ? sizeClasses : sizeClasses + " " + fade;
    }

    /**
     * Puts this size on a component, taking any other size off it first.
     *
     * <p>Applying a second size therefore replaces the first rather than fighting it.</p>
     *
     * @param target the component to style
     * @param <C> the component's own type, so the call can be chained
     * @return the same component
     */
    public <C extends HasStyle> C applyTo(C target) {
        return applyTo(target, ownEmphasis);
    }

    /**
     * Puts this size on a component at a loudness of your choosing, taking any other size and any
     * other loudness off it first.
     *
     * @param target the component to style
     * @param emphasis how present the text should be; null means this size's own
     * @param <C> the component's own type, so the call can be chained
     * @return the same component
     */
    public <C extends HasStyle> C applyTo(C target, Emphasis emphasis) {
        if (target == null) {
            return null;
        }
        clear(target);
        target.addClassName(getClassNames(emphasis));
        return target;
    }

    /**
     * Takes every size off a component, leaving whatever else it was wearing.
     *
     * @param target the component to strip
     */
    public static void clear(HasStyle target) {
        if (target == null) {
            return;
        }
        for (TextStyle style : values()) {
            target.removeClassName(style.sizeClasses);
        }
        for (Emphasis emphasis : Emphasis.values()) {
            target.removeClassName(emphasis.getClassNames());
        }
    }

    /**
     * Creates a piece of inline text in this size.
     *
     * @param text the words to show
     * @return a new {@link Span}
     */
    public Span span(String text) {
        return span(text, ownEmphasis);
    }

    /**
     * Creates a piece of inline text in this size, at a loudness of your choosing.
     *
     * @param text the words to show
     * @param emphasis how present the text should be; null means this size's own
     * @return a new {@link Span}
     */
    public Span span(String text, Emphasis emphasis) {
        Span span = new Span(text);
        span.addClassName(getClassNames(emphasis));
        return span;
    }

    /**
     * Creates a paragraph in this size — a block that starts on its own line.
     *
     * @param text the words to show
     * @return a new {@link Div}
     */
    public Div paragraph(String text) {
        return paragraph(text, ownEmphasis);
    }

    /**
     * Creates a paragraph in this size, at a loudness of your choosing.
     *
     * @param text the words to show
     * @param emphasis how present the text should be; null means this size's own
     * @return a new {@link Div}
     */
    public Div paragraph(String text, Emphasis emphasis) {
        Div div = new Div(text);
        div.addClassName(getClassNames(emphasis));
        return div;
    }
}
