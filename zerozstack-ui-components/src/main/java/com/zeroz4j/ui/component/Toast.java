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
import com.zeroz4j.ui.theme.Layer;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.events.Event;

/**
 * A short message that floats in a corner of the window — "Saved", "Could not reach the server".
 *
 * <pre>{@code
 * Toast saved = new Toast("Saved");
 * page.add(saved);
 * }</pre>
 *
 * <h2>What it does for the reader</h2>
 *
 * <p>The message is announced by a screen reader when it appears, politely: it waits for whatever
 * is being read to finish rather than interrupting. Use {@link #setUrgent(boolean)} for the rare
 * message that must cut in — something has failed and the user is about to lose work.</p>
 *
 * <p><b>A toast never takes the keyboard.</b> A message arriving while somebody is typing must not
 * move them out of the box they are typing in, so it does not, and it holds nothing: Tab walks
 * straight past it unless you have put a button inside, and then Tab reaches that button in its
 * normal turn.</p>
 *
 * <p>Escape removes it, so a message covering a corner of the screen is never stuck there. Turn
 * that off with {@link #setCloseOnEsc(boolean)} if the message is the only place something
 * important is written.</p>
 *
 * <h2>Stacking</h2>
 *
 * <p>It sits on {@link Layer#TOAST}, above panels and menus, because a message about what you just
 * did in a drawer is no use hidden behind that drawer. It is <b>not</b> above an open modal
 * {@link Dialog}: a modal dialog is in the browser's top layer, which no stacking number reaches.
 * See {@link Layer}.</p>
 */
public class Toast extends Component implements HasComponents, HasText, HasStyle, HasSize,
        HasLayer<Toast> {

    private boolean closeOnEsc = true;
    private org.teavm.jso.dom.events.EventListener<Event> escapeListener;

    public Toast() {
        super("div");
        addClassName("toast");
        setLayer(Layer.TOAST);
        getElement().setAttribute("role", "status");
        getElement().setAttribute("aria-live", "polite");
    }

    public Toast(String text) {
        this();
        setText(text);
    }

    @Override
    public Component getComponent() {
        return this;
    }

    /**
     * Whether a screen reader interrupts whatever it is saying to read this message. Off by
     * default, which is right for nearly everything: a message about something that has already
     * happened can wait a sentence. Turn it on only where waiting would cost the user something.
     *
     * @param urgent true to interrupt
     */
    public void setUrgent(boolean urgent) {
        getElement().setAttribute("aria-live", urgent ? "assertive" : "polite");
        getElement().setAttribute("role", urgent ? "alert" : "status");
    }

    /**
     * @return whether a screen reader interrupts to read this message
     * @see #setUrgent(boolean)
     */
    public boolean isUrgent() {
        return "assertive".equals(getElement().getAttribute("aria-live"));
    }

    /**
     * Whether Escape removes the message. On by default.
     *
     * <p>The key is listened for on the whole page, not on the message, because the keyboard is
     * never inside a toast — that is deliberate, and it is why Escape has to be caught further
     * out.</p>
     *
     * @param closeOnEsc true to let Escape remove it
     */
    public void setCloseOnEsc(boolean closeOnEsc) {
        this.closeOnEsc = closeOnEsc;
    }

    /**
     * @return whether Escape removes the message
     * @see #setCloseOnEsc(boolean)
     */
    public boolean isCloseOnEsc() {
        return closeOnEsc;
    }

    /**
     * Puts the message on the page, in the corner it was told to sit in.
     *
     * <p>Use this rather than appending the element yourself. A message that arrives on the page
     * without the component being told is a message that never starts: Escape did not close it,
     * because the key listener is registered when the message starts. Every toast in this
     * library's own gallery was raised the other way, and Escape did nothing on any of them.</p>
     */
    public void show() {
        Window.current().getDocument().getBody().appendChild(getElement());
        attach();
    }

    /**
     * Takes the message off the page. Safe to call more than once, and safe to call on one that was
     * never added.
     */
    public void close() {
        if (getElement().getParentNode() != null) {
            getElement().getParentNode().removeChild(getElement());
        }
        detach();
    }

    @Override
    protected void onAttach() {
        super.onAttach();
        if (escapeListener != null) {
            return;
        }
        // Registered straight on the document rather than through addDomEventListener: the body
        // only reads a flag and removes an element, nothing that suspends, and a green thread
        // would run it after the browser had already moved on.
        escapeListener = evt -> {
            if (closeOnEsc && "Escape".equals(Js.eventKey(evt))) {
                close();
            }
        };
        Window.current().getDocument().addEventListener("keydown", escapeListener);
    }

    @Override
    protected void onDetach() {
        if (escapeListener != null) {
            Window.current().getDocument().removeEventListener("keydown", escapeListener);
            escapeListener = null;
        }
        super.onDetach();
    }
}
