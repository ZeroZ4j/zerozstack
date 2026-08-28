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

import com.zeroz4j.ui.component.mixin.HasLayer;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.theme.Layer;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.events.Event;

/**
 * A button that drops a small panel of choices open underneath it.
 *
 * <pre>{@code
 * Dropdown actions = new Dropdown("Actions");
 * actions.add(new Button("Rename", e -> rename()));
 * actions.add(new Button("Delete", e -> delete()));
 * }</pre>
 *
 * <h2>Opening and closing</h2>
 *
 * <p>Clicking the button opens it, clicking it again closes it, and so does clicking anywhere else
 * on the page. Escape closes it too, and puts the keyboard back on the button — so somebody who
 * opened it by mistake gets out of it without reaching for the mouse. Turn Escape off with
 * {@link #setCloseOnEsc(boolean)} if you have a reason to.</p>
 *
 * <p>Your code can open and close it as well, with {@link #open()} and {@link #close()}.</p>
 *
 * <h2>Focus</h2>
 *
 * <p>The keyboard stays on the button when the panel opens; Tab then walks into the panel and out
 * the far side, as it does for anything else on a page. Nothing is held inside — a dropdown does
 * not take the page over, so it must not take the keyboard over either.</p>
 *
 * <h2>Stacking</h2>
 *
 * <p>The panel sits on {@link Layer#DROPDOWN}, which is above a sticky header — a dropdown is
 * usually opened from a button in one — and below a drawer or a dialog. Before this the panel
 * carried a hand-written stacking number of 1, which lost to nearly everything.</p>
 */
public class Dropdown extends Component implements HasComponents, HasStyle, HasLayer<Dropdown> {

    private final Summary summary;
    private final Div content;

    private boolean closeOnEsc = true;
    private org.teavm.jso.dom.events.EventListener<Event> outsideClickListener;

    public Dropdown(String label) {
        super("details");
        addClassName("dropdown");

        summary = new Summary();
        summary.addClassName("btn");
        summary.addClassName("m-1");
        summary.getElement().setTextContent(label);

        content = new Div();
        content.addClassName("dropdown-content");
        content.addClassName("menu");
        content.addClassName("p-2");
        content.addClassName("shadow");
        content.addClassName("bg-base-100");
        content.addClassName("rounded-box");
        content.addClassName("w-52");

        getElement().appendChild(summary.getElement());
        getElement().appendChild(content.getElement());

        setLayer(Layer.DROPDOWN);

        // Straight on the element rather than through addDomEventListener: closing a panel has to
        // happen inside the browser's own call, and none of these bodies suspends.
        getElement().addEventListener("keydown", (org.teavm.jso.dom.events.EventListener<Event>) evt -> {
            if (closeOnEsc && "Escape".equals(Js.eventKey(evt)) && isOpened()) {
                evt.preventDefault();
                close();
                Js.focus(summary.getElement());
            }
        });
        getElement().addEventListener("toggle", (org.teavm.jso.dom.events.EventListener<Event>) evt -> {
            if (isOpened()) {
                listenForClickOutside();
            } else {
                stopListeningForClickOutside();
            }
        });
    }

    /** The panel is what floats, not the button, so that is what carries the layer. */
    @Override
    public Component getLayerComponent() {
        return content;
    }

    @Override
    public Component getComponent() {
        return this;
    }

    /** Drops the panel open. Does nothing if it is already open. */
    public void open() {
        Js.detailsSetOpen(getElement(), true);
        listenForClickOutside();
    }

    /** Shuts the panel. Does nothing if it is already shut. */
    public void close() {
        Js.detailsSetOpen(getElement(), false);
        stopListeningForClickOutside();
    }

    /** Whether the panel is open. */
    public boolean isOpened() {
        return Js.detailsIsOpen(getElement());
    }

    /**
     * Whether Escape shuts the panel and puts the keyboard back on the button. On by default.
     *
     * @param closeOnEsc true to let Escape close it
     */
    public void setCloseOnEsc(boolean closeOnEsc) {
        this.closeOnEsc = closeOnEsc;
    }

    /**
     * @return whether Escape shuts the panel
     * @see #setCloseOnEsc(boolean)
     */
    public boolean isCloseOnEsc() {
        return closeOnEsc;
    }

    /**
     * Sets the words on the button.
     *
     * @param label the button text
     */
    public void setLabel(String label) {
        summary.getElement().setTextContent(label);
    }

    /**
     * @return the words on the button
     */
    public String getLabel() {
        return summary.getElement().getTextContent();
    }

    private void listenForClickOutside() {
        if (outsideClickListener != null) {
            return;
        }
        outsideClickListener = evt -> {
            org.teavm.jso.dom.html.HTMLElement clicked = Js.eventTargetElement(evt);
            if (!Js.contains(getElement(), clicked)) {
                close();
            }
        };
        Window.current().getDocument().addEventListener("click", outsideClickListener);
    }

    private void stopListeningForClickOutside() {
        if (outsideClickListener == null) {
            return;
        }
        Window.current().getDocument().removeEventListener("click", outsideClickListener);
        outsideClickListener = null;
    }

    @Override
    public void add(Component... components) {
        content.add(components);
    }

    @Override
    public void remove(Component... components) {
        content.remove(components);
    }

    @Override
    public void removeAll() {
        content.removeAll();
    }

    /** The button. A real {@code <summary>}, which is what makes the panel open with no script. */
    private static final class Summary extends Component implements HasStyle {
        private Summary() {
            super("summary");
        }

        @Override
        public Component getComponent() {
            return this;
        }
    }
}
