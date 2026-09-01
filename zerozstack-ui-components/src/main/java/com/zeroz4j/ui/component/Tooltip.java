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
package com.zeroz4j.ui.component;

import com.zeroz4j.ui.component.mixin.HasColorVariants;
import com.zeroz4j.ui.component.mixin.HasLayer;
import com.zeroz4j.ui.component.mixin.HasPositionVariant;
import com.zeroz4j.ui.theme.Layer;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.events.Event;

/**
 * A few words that appear next to whatever the pointer is resting on.
 *
 * <pre>{@code
 * Tooltip tip = new Tooltip("Deletes the file for everybody");
 * tip.add(new Button("Delete"));
 * }</pre>
 *
 * <h2>It is not something you use, it is something you read</h2>
 *
 * <p>A tooltip takes no keyboard focus, holds nothing, and has no open and close for an application
 * to call. It appears while the pointer is on the thing it belongs to and goes away again. Put
 * nothing in one that a person has to click: they cannot reach it.</p>
 *
 * <p>Escape hides it. That is not decoration — a tip that covers the thing underneath it and cannot
 * be got rid of without moving the mouse is a real problem for somebody using magnification. The
 * tip comes back as soon as the pointer leaves and returns.</p>
 *
 * <h2>The words go on the tip, not next to the button</h2>
 *
 * <p>{@link #setText(String)} sets the words the tip shows. It used to set the text of the wrapper
 * itself, which put them on the page beside whatever the tooltip was wrapped around, permanently
 * and in the wrong place, while the tip itself stayed empty.</p>
 *
 * <h2>Stacking</h2>
 *
 * <p>While the tip is showing it is at the very top of the scale, on {@link Layer#TOOLTIP}, because
 * a tip can be attached to a control inside anything else — a button inside a message, a field
 * inside a drawer — and it is small and brief enough never to be in the way. It is still below an
 * open modal {@link Dialog}, which is in the browser's top layer; see {@link Layer}.</p>
 *
 * <p><b>The rest of the time it is ordinary page content.</b> A tooltip wraps the control it
 * belongs to, so leaving the layer on permanently would float that control above every drawer and
 * message on the page — a button that stayed visible over a drawer covering it. The layer goes on
 * when the tip appears and comes off when it goes.</p>
 */
public class Tooltip extends Component implements HasComponents, HasText, HasStyle, HasSize,
        HasColorVariants<Tooltip>,
        HasPositionVariant<Tooltip>,
        HasLayer<Tooltip> {

    private boolean dismissed;
    /** Held while Escape has taken the words off, so they can be put back. */
    private String hiddenText;

    public Tooltip() {
        super("div");
        addClassName("tooltip");
        getElement().setAttribute("role", "tooltip");
        // The tip is capped at 20rem wide and wraps - but only where the words give it somewhere
        // to wrap. A long address, a file path or a stack frame has no spaces in it, so the cap
        // did nothing and one tip drew 2,719 pixels wide, taking the page sideways with it.
        // overflow-wrap is inherited, so setting it here reaches the tip, which is drawn by the
        // stylesheet and has no element of its own to set anything on.
        getElement().getStyle().setProperty("overflow-wrap", "anywhere");

        // Escape is listened for on the whole page, not on the tooltip. A tip usually shows because
        // the pointer is over it, and the pointer being over something does not put the keyboard on
        // it — so a key pressed then never reaches the tooltip's own element at all.
        Window.current().getDocument().addEventListener("keydown",
                (org.teavm.jso.dom.events.EventListener<Event>) evt -> {
            if ("Escape".equals(Js.eventKey(evt)) && isShowing()) {
                dismiss();
            }
        });
        // These four are on the element: they only put words and a layer back, and the browser has
        // already done the drawing by the time they run.
        getElement().addEventListener("mouseenter", (org.teavm.jso.dom.events.EventListener<Event>) evt -> rise());
        getElement().addEventListener("focusin", (org.teavm.jso.dom.events.EventListener<Event>) evt -> rise());
        getElement().addEventListener("mouseleave", (org.teavm.jso.dom.events.EventListener<Event>) evt -> settle());
        getElement().addEventListener("focusout", (org.teavm.jso.dom.events.EventListener<Event>) evt -> settle());
    }

    public Tooltip(String text) {
        this();
        setText(text);
    }

    @Override
    public Component getComponent() {
        return this;
    }

    @Override
    public String getThemePrefix() {
        return "tooltip";
    }

    /**
     * The words the tip shows.
     *
     * <p>They are held on the element as {@code data-tip}, which is where the stylesheet reads them
     * from. Anything added to the tooltip with {@link #add(Component...)} — the button the tip
     * belongs to — is untouched.</p>
     *
     * @param text the words to show, or null for none
     */
    @Override
    public void setText(String text) {
        if (dismissed) {
            hiddenText = text;
            return;
        }
        if (text == null) {
            getElement().removeAttribute("data-tip");
        } else {
            getElement().setAttribute("data-tip", text);
        }
    }

    /**
     * @return the words the tip shows, or null if it has none
     */
    @Override
    public String getText() {
        return dismissed ? hiddenText : getElement().getAttribute("data-tip");
    }

    /**
     * Hides the tip until the pointer leaves and comes back. What Escape does; call it yourself if
     * something else in your application should have the same effect.
     */
    public void dismiss() {
        if (dismissed) {
            return;
        }
        hiddenText = getElement().getAttribute("data-tip");
        dismissed = true;
        // The stylesheet draws the tip from this attribute and from nothing else, so taking it away
        // is what makes the tip go. Nothing about the layout of the wrapper changes.
        getElement().removeAttribute("data-tip");
    }

    /** Whether Escape has hidden the tip and the pointer has not left yet. */
    public boolean isDismissed() {
        return dismissed;
    }

    /**
     * Whether the tip is on the screen right now — the pointer is over the thing it belongs to, or
     * the keyboard is on something inside it.
     *
     * @return true while the tip is showing
     */
    public boolean isShowing() {
        return !dismissed
                && (Js.matches(getElement(), ":hover") || Js.matches(getElement(), ":focus-within"));
    }

    /** The pointer arrived, or the keyboard did: float above everything while the tip is up. */
    private void rise() {
        setLayer(Layer.TOOLTIP);
    }

    /** The pointer left: back to ordinary page content, and the words come back if Escape took them. */
    private void settle() {
        setLayer(null);
        if (!dismissed) {
            return;
        }
        dismissed = false;
        if (hiddenText != null) {
            getElement().setAttribute("data-tip", hiddenText);
        }
        hiddenText = null;
    }
}
