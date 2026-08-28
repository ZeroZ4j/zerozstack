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
 * How high above the page something floats.
 *
 * <p>Ask for a layer by name. Never write a stacking number yourself: the moment two parts of an
 * application each pick their own, the one that picked higher wins, and which one that is has
 * nothing to do with which one should be on top. The names below are ordered, and the order is the
 * whole point — a menu covers a sticky header, a panel that dims the page covers the menu, a
 * message covers the panel, and a tip covers everything an application can draw.</p>
 *
 * <pre>{@code
 * toast.setLayer(Layer.TOAST);
 * menu.setLayer(Layer.DROPDOWN);
 * }</pre>
 *
 * <h2>The rule no number can beat</h2>
 *
 * <p>The browser keeps a place of its own — the <b>top layer</b> — and everything in it is drawn
 * above <b>every</b> layer on this scale, whatever number is on it. A modal {@link
 * com.zeroz4j.ui.component.Dialog} lives there, because that is what {@code showModal()} does. So
 * does anything using the browser's own popover support. You cannot out-bid it and you should not
 * try: if something has to cover a modal dialog, it has to be in the top layer too.</p>
 *
 * <p>This is the fault the scale was written for. An application had picked its own numbers, one of
 * them very large, and an overlay still came out underneath a dialog. Nothing was wrong with the
 * number. The dialog was simply not on the same scale as the number.</p>
 *
 * <h2>A layer needs a positioned element</h2>
 *
 * <p>A stacking number does nothing to an element the stylesheet has left in the normal flow. Every
 * component in this library that takes a layer already positions itself, so this only matters if
 * you set a layer on something of your own: give it {@code position: relative}, {@code absolute} or
 * {@code fixed} as well, or nothing will change.</p>
 *
 * <h2>Where the numbers come from</h2>
 *
 * <p>The scale starts at 1000 because the stylesheet this library is built on — daisyUI — uses
 * numbers up to 999 for parts of its own components. Starting above all of them means a component
 * on this scale is above every piece of stylesheet furniture without anybody having to check.</p>
 *
 * <p>The tiers are a hundred apart on purpose. An application that genuinely has a tier of its own
 * — a page-wide loading veil, say — has ninety-nine free values between any two of these, and can
 * read the neighbours with {@link #getZIndex()} instead of guessing.</p>
 */
public enum Layer {

    /**
     * Ordinary page content, at the bottom. Use it to take a layer off something again; nothing in
     * this library sits here by choice.
     */
    PAGE("page", 0),

    /**
     * A bar that stays put while the page scrolls underneath it — a header, a toolbar, a footer.
     * The lowest tier that is above the page at all.
     */
    STICKY("sticky", 1000),

    /**
     * A menu opened from a control: a dropdown, a right-click menu, the list a select box drops
     * down. It has to clear a sticky header, because a menu is often opened from a button in one.
     */
    DROPDOWN("dropdown", 1100),

    /**
     * A panel that covers the page and dims what is behind it — a drawer sliding in from the side,
     * or a dialog the browser is not owning. Above menus, so that opening one covers a menu that
     * was already open.
     */
    OVERLAY("overlay", 1200),

    /**
     * A short message the user has to be able to read wherever it appears — "Saved", "Could not
     * reach the server". Above panels, because a message about the thing you just did in a drawer
     * is no use hidden behind that drawer.
     */
    TOAST("toast", 1300),

    /**
     * A tip that appears next to whatever the pointer is on. The top of the scale, because a tip
     * can be attached to a control inside anything else — including a button inside a message —
     * and it is small, brief, and never in the way of something you were reading.
     */
    TOOLTIP("tooltip", 1400);

    private final String name;
    private final int zIndex;

    Layer(String name, int zIndex) {
        this.name = name;
        this.zIndex = zIndex;
    }

    /**
     * The stacking number for this tier. Read it to place something of your own between two tiers;
     * do not copy it into code as a literal, because then it stops following this scale.
     *
     * @return the CSS {@code z-index} value this tier stands for
     */
    public int getZIndex() {
        return zIndex;
    }

    /**
     * The stylesheet class this tier puts on an element — {@code zz-layer-toast} and so on. It
     * carries no styling of its own; it is there so that a person reading the page, and a test
     * reading the page, can both see which tier something was put on.
     *
     * @return the marker class name for this tier
     */
    public String getClassName() {
        return "zz-layer-" + name;
    }
}
