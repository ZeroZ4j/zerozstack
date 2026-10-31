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
import org.teavm.jso.JSBody;

/**
 * How high a surface sits above the page, by name.
 *
 * <p>{@link TextStyle} names how big text is, {@link Emphasis} how loud, {@link Layer} what floats
 * above what. This names the last thing a screen kept spelling out: whether a box is the page
 * itself, a panel on the page, a tile inside a panel, a well sunk into one, a card lifted off one,
 * or a dialog above everything. Without a name every screen asked for a color, and on a dark theme
 * where the page, the box and its edge are three near-identical grays, three applications each
 * ended up repairing the same fault in their own stylesheet.</p>
 *
 * <pre>{@code
 * Surface.PANEL.applyTo(card);
 * add(Surface.TILE.div());
 * }</pre>
 *
 * <h2>Light and dark</h2>
 *
 * <p>Each surface is drawn from the theme's own page and text colors, so it is right in any
 * theme with no choice from the caller. The edge is a faint mix of the text color, which reads on a
 * light page and on a dark one whatever the theme's border color happens to be. A shadow is dark,
 * and on a dark theme a raised surface is also a little lighter than the one under it, because
 * that is how height shows there.</p>
 *
 * <h2>How it is applied</h2>
 *
 * <p>The first time a surface is applied, the library puts a short stylesheet at the start of
 * {@code <head>}, so the application's own styles come after it. It is in the {@code components}
 * layer, so a utility class on the same element, such as {@code rounded-none}, still wins. Every
 * value is a custom property: {@code --zeroz4j-surface-edge}, {@code --zeroz4j-surface-shade} and
 * {@code --zeroz4j-surface-radius} can be set on {@code :root} or on one element.</p>
 *
 * <p>A surface sets background, border, radius and shadow. It sets no padding and no layout.</p>
 */
public enum Surface {

    /** The page itself, the deepest surface. Nothing is behind it. */
    PAGE("page"),

    /** A box on the page: a card, a section, a form block. */
    PANEL("panel"),

    /** A small box on a page or a panel: a figure, a list row, a chart frame. Flat, with an edge. */
    TILE("tile"),

    /** An area sunk into a panel: a code block, an input group, a log. */
    SUNKEN("sunken"),

    /** A box lifted off its panel: a hovering card, a drag preview. */
    RAISED("raised"),

    /** What sits above the page: a dialog, a menu, a popover. */
    OVERLAY("overlay");

    /** Class every surface carries. */
    static final String BASE = "zeroz4j-surface";

    /** The id of the stylesheet element. */
    static final String STYLE_ID = "zeroz4j-surface-style";

    private final String name;

    Surface(String name) {
        this.name = name;
    }

    /**
     * Returns the class names this surface is made of.
     *
     * <p>Applications should use {@link #applyTo} or {@link #div}, which also make sure the
     * stylesheet is there. This is for components inside the library and for tests.</p>
     *
     * @return the space-separated class names
     */
    public String getClassNames() {
        return BASE + " " + BASE + "-" + name;
    }

    /**
     * Puts this surface on a component, taking any other surface off it first.
     *
     * @param target the component to style
     * @param <C> the component's own type, so the call can be chained
     * @return the same component
     */
    public <C extends HasStyle> C applyTo(C target) {
        if (target == null) {
            return null;
        }
        install();
        clear(target);
        target.addClassName(getClassNames());
        return target;
    }

    /**
     * Creates an empty block on this surface.
     *
     * @return a new {@link Div}
     */
    public Div div() {
        return applyTo(new Div());
    }

    /**
     * Takes every surface off a component, leaving whatever else it was wearing.
     *
     * @param target the component to strip
     */
    public static void clear(HasStyle target) {
        if (target == null) {
            return;
        }
        for (Surface surface : values()) {
            target.removeClassName(surface.getClassNames());
        }
    }

    /**
     * Puts the surface stylesheet in the page if it is not there yet. Applying a surface does this;
     * call it only to have the stylesheet before any surface exists.
     */
    public static void install() {
        installStyles(stylesheet());
    }

    /**
     * The stylesheet text.
     *
     * @return the CSS this class installs
     */
    static String stylesheet() {
        String edge = "var(--zeroz4j-surface-edge, color-mix(in oklab, "
            + "var(--color-base-content, currentColor) 14%, transparent))";
        String shade = "var(--zeroz4j-surface-shade, light-dark(rgb(0 0 0 / 0.10), rgb(0 0 0 / 0.55)))";
        String radius = "var(--zeroz4j-surface-radius, var(--radius-box, 1rem))";
        String rim = "inset 0 1px 0 light-dark(transparent, rgb(255 255 255 / 0.05))";
        String s = ".zeroz4j-surface.zeroz4j-surface-";
        return "@layer theme, base, components, utilities;"
            + "@layer components{"
            + s + "page{background-color:var(--color-base-200);color:var(--color-base-content)}"
            + s + "panel{background-color:var(--color-base-100);border:1px solid " + edge + ";"
            + "border-radius:" + radius + ";box-shadow:0 1px 2px " + shade + "," + rim + "}"
            + s + "tile{background-color:var(--color-base-100);border:1px solid " + edge + ";"
            + "border-radius:calc(" + radius + " * 0.75)}"
            + s + "sunken{background-color:var(--color-base-200);border:1px solid " + edge + ";"
            + "border-radius:calc(" + radius + " * 0.75);box-shadow:inset 0 1px 2px " + shade + "}"
            + s + "raised{background-color:var(--color-base-100);"
            + "background-image:linear-gradient(light-dark(transparent, rgb(255 255 255 / 0.04)),"
            + "light-dark(transparent, rgb(255 255 255 / 0.04)));"
            + "border:1px solid " + edge + ";border-radius:" + radius + ";"
            + "box-shadow:0 4px 12px " + shade + "," + rim + "}"
            + s + "overlay{background-color:var(--color-base-100);"
            + "background-image:linear-gradient(light-dark(transparent, rgb(255 255 255 / 0.07)),"
            + "light-dark(transparent, rgb(255 255 255 / 0.07)));"
            + "border:1px solid " + edge + ";border-radius:" + radius + ";"
            + "box-shadow:0 12px 32px " + shade + "," + rim + "}"
            + "}";
    }

    @JSBody(params = {"css"}, script =
        "if (document.getElementById('zeroz4j-surface-style')) { return; }"
        + "var style = document.createElement('style');"
        + "style.id = 'zeroz4j-surface-style';"
        + "style.textContent = css;"
        + "document.head.insertBefore(style, document.head.firstChild);")
    private static native void installStyles(String css);
}
