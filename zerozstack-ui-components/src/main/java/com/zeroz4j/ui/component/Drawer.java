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
import org.teavm.jso.dom.html.HTMLElement;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A panel that slides in from the side of the window.
 *
 * <p>A drawer is two areas. The <b>panel</b> is the thing that slides in; {@link #add(Component...)}
 * puts things there. The <b>page</b> is everything the panel slides over;
 * {@link #addToPage(Component...)} puts things there.</p>
 *
 * <pre>{@code
 * Drawer nav = new Drawer("Main menu");
 * nav.addToPage(new Button("Menu", e -> nav.open()));
 * nav.add(new Link("Home", "/"), new Link("Settings", "/settings"));
 * }</pre>
 *
 * <h2>Opening and closing</h2>
 *
 * <p>Open and close it from code with {@link #open()} and {@link #close()}. Escape closes it, and so
 * does clicking the dimmed page beside it. Turn either off — {@link #setCloseOnEsc(boolean)},
 * {@link #setCloseOnOutsideClick(boolean)} — and leave a button, or there is no way out.</p>
 *
 * <h2>Focus</h2>
 *
 * <p>Opening moves the keyboard into the panel and closing puts it back on whatever opened it.
 * While the panel is open the keyboard is <b>held inside it</b>: Tab off the last thing in the
 * panel comes back round to the first, and never reaches the dimmed page behind. That is done by
 * this component, not by the browser — only a modal {@link Dialog} gets it for free.</p>
 *
 * <p>{@link #setModal(boolean)} turns that off, along with the dim, for a drawer used as a sidebar
 * that lives beside the page rather than over it. Then the page is genuinely still in use, and
 * holding the keyboard in the sidebar would be wrong.</p>
 *
 * <h2>Stacking</h2>
 *
 * <p>The panel sits on {@link Layer#OVERLAY} — above menus, below messages. It is <b>not</b> above
 * an open modal {@link Dialog}: that is in the browser's top layer, which no stacking number
 * reaches. Applications used to write a number on the panel by hand, and the numbers disagreed with
 * each other; see {@link Layer}.</p>
 *
 * <h2>What changed at 0.8.0</h2>
 *
 * <p>Before this release {@code Drawer} was an empty box with a stylesheet class on it. Every
 * application had to build the checkbox that opens it, the panel, the dim and the stacking number
 * by hand, and each one did it differently. Code doing that still works — the parts are still
 * there — but it should move to {@code open()}, {@code close()}, {@code add} and
 * {@code addToPage}.</p>
 */
public class Drawer extends Component implements HasComponents, HasStyle, HasSize,
        HasLayer<Drawer> {

    private static final AtomicInteger DRAWER_ID_COUNTER = new AtomicInteger();

    private final Toggle toggle;
    private final Div page;
    private final Div side;
    private final Div panel;
    private final Overlay overlay;
    private final Heading heading;

    private final List<EventListener<ComponentEvent<Drawer>>> closeListeners = new ArrayList<>();

    private boolean modal = true;
    private boolean closeOnEsc = true;
    private boolean closeOnOutsideClick = true;
    private boolean opened;
    private String title;
    private HTMLElement openedFrom;

    private org.teavm.jso.dom.events.EventListener<Event> escapeListener;

    /** A drawer with no heading. */
    public Drawer() {
        this(null);
    }

    /**
     * A drawer with {@code title} as its heading and as the name a screen reader announces.
     *
     * @param title the heading, or null for a drawer with no heading
     */
    public Drawer(String title) {
        super("div");
        addClassName("drawer");

        String toggleId = "zeroz-drawer-" + DRAWER_ID_COUNTER.incrementAndGet();

        toggle = new Toggle();
        toggle.setId(toggleId);

        page = new Div();
        page.addClassName("drawer-content");

        side = new Div();
        side.addClassName("drawer-side");
        HasLayer.applyTo(side, Layer.OVERLAY);

        overlay = new Overlay();
        overlay.addClassName("drawer-overlay");
        overlay.getElement().setAttribute("for", toggleId);
        overlay.getElement().setAttribute("aria-label", "Close");

        panel = new Div();
        panel.addClassName("menu bg-base-200 text-base-content min-h-full w-80 p-4");
        panel.getElement().setAttribute("role", "dialog");

        heading = new Heading();
        heading.setId(toggleId + "-title");
        heading.addClassName("text-lg font-bold mb-2");
        heading.setVisible(false);
        panel.add(heading);

        side.add(overlay);
        side.add(panel);

        getElement().appendChild(toggle.getElement());
        getElement().appendChild(page.getElement());
        getElement().appendChild(side.getElement());

        setTitle(title);

        // Straight on the elements rather than through addDomEventListener: none of these bodies
        // suspends, and each has to act inside the browser's own call rather than a beat later.
        toggle.getElement().addEventListener("change", (org.teavm.jso.dom.events.EventListener<Event>) evt -> {
            if (Js.checkboxIsChecked(toggle.getElement())) {
                if (!opened) {
                    openInternal(true);
                }
            } else if (opened) {
                if (closeOnOutsideClick) {
                    closeInternal(true);
                } else {
                    // The dim is a label pointing at the checkbox, so the browser unticks it before
                    // anything can object. Put it back rather than let the drawer vanish.
                    Js.checkboxSetChecked(toggle.getElement(), true);
                }
            }
        });
    }

    /** The sliding side is what floats over the page, so that is what carries the layer. */
    @Override
    public Component getLayerComponent() {
        return side;
    }

    @Override
    public Component getComponent() {
        return this;
    }

    /** Slides the panel in. Does nothing if it is already showing. */
    public void open() {
        if (opened) {
            return;
        }
        Js.checkboxSetChecked(toggle.getElement(), true);
        openInternal(false);
    }

    /** Slides the panel away and fires the close listeners. Does nothing if it is already away. */
    public void close() {
        if (!opened) {
            return;
        }
        Js.checkboxSetChecked(toggle.getElement(), false);
        closeInternal(false);
    }

    /** Whether the panel is showing. */
    public boolean isOpened() {
        return opened;
    }

    private void openInternal(boolean fromClient) {
        opened = true;
        openedFrom = Js.activeElement();
        if (modal) {
            Js.trapFocusIn(panel.getElement());
        }
        Js.focusFirstInside(panel.getElement());
        if (escapeListener == null) {
            escapeListener = evt -> {
                if (closeOnEsc && opened && "Escape".equals(Js.eventKey(evt))) {
                    close();
                }
            };
            Window.current().getDocument().addEventListener("keydown", escapeListener);
        }
    }

    private void closeInternal(boolean fromClient) {
        opened = false;
        Js.releaseFocusTrap(panel.getElement());
        if (escapeListener != null) {
            Window.current().getDocument().removeEventListener("keydown", escapeListener);
            escapeListener = null;
        }
        HTMLElement target = openedFrom;
        openedFrom = null;
        // Measured against the sliding side, not the whole drawer: the button that opens a drawer
        // is normally on the page area, which is inside the drawer element too. Checking the whole
        // drawer refused to give the keyboard back to the very button it came from.
        if (target != null && !Js.contains(side.getElement(), target)) {
            Js.focus(target);
        }
        fireClose(fromClient);
    }

    private void fireClose(boolean fromClient) {
        if (closeListeners.isEmpty()) {
            return;
        }
        List<EventListener<ComponentEvent<Drawer>>> snapshot = new ArrayList<>(closeListeners);
        ComponentEvent<Drawer> event = new ComponentEvent<>(this, fromClient);
        // A green thread, like every other component event, so a close listener may call a service.
        new Thread(() -> {
            for (EventListener<ComponentEvent<Drawer>> listener : snapshot) {
                listener.onComponentEvent(event);
            }
        }).start();
    }

    /**
     * Called every time the drawer closes, however it closed. {@code isFromClient()} on the event is
     * false only when the application closed it itself.
     *
     * @param listener the callback to run on each close
     * @return a handle that removes the listener again
     */
    public DomListenerRegistration addCloseListener(EventListener<ComponentEvent<Drawer>> listener) {
        closeListeners.add(listener);
        return () -> closeListeners.remove(listener);
    }

    /**
     * Whether the panel covers the page, dims it, and holds the keyboard. On by default.
     *
     * <p>Off, the panel sits beside the page instead: the page stays live, the keyboard can walk
     * from one into the other, and there is no dim to click. That is a sidebar, not a drawer over
     * the page — use it for navigation that is meant to stay open. Set it before opening.</p>
     *
     * @param modal true for a panel that takes the page over
     */
    public void setModal(boolean modal) {
        this.modal = modal;
        if (modal) {
            removeClassName("drawer-open");
        } else {
            // The stylesheet's own name for a panel that lives beside the page instead of over it:
            // no dim, the page still in use, and the panel always there.
            addClassName("drawer-open");
            Js.releaseFocusTrap(panel.getElement());
        }
    }

    /**
     * @return whether the panel covers the page and holds the keyboard
     * @see #setModal(boolean)
     */
    public boolean isModal() {
        return modal;
    }

    /**
     * Whether Escape closes the drawer. On by default. Turning it off means the user has no way out
     * but a button you provide.
     *
     * @param closeOnEsc true to let Escape close it
     */
    public void setCloseOnEsc(boolean closeOnEsc) {
        this.closeOnEsc = closeOnEsc;
    }

    /**
     * @return whether Escape closes the drawer
     * @see #setCloseOnEsc(boolean)
     */
    public boolean isCloseOnEsc() {
        return closeOnEsc;
    }

    /**
     * Whether clicking the dimmed page beside the panel closes the drawer. On by default. Turn it
     * off for a drawer holding half-written input.
     *
     * @param closeOnOutsideClick true to let a click beside the panel close it
     */
    public void setCloseOnOutsideClick(boolean closeOnOutsideClick) {
        this.closeOnOutsideClick = closeOnOutsideClick;
    }

    /**
     * @return whether clicking beside the panel closes the drawer
     * @see #setCloseOnOutsideClick(boolean)
     */
    public boolean isCloseOnOutsideClick() {
        return closeOnOutsideClick;
    }

    /**
     * Which side the panel slides in from. The start of the line by default, which is the left in
     * English and the right in a language written right to left.
     *
     * @param end true to slide in from the end of the line instead
     */
    public void setSlideFromEnd(boolean end) {
        if (end) {
            addClassName("drawer-end");
        } else {
            removeClassName("drawer-end");
        }
    }

    /**
     * Sets the drawer's heading, which is also the name a screen reader announces when it opens.
     *
     * @param title the heading text, or null for no heading
     */
    public void setTitle(String title) {
        this.title = title;
        if (title == null || title.isEmpty()) {
            heading.setText("");
            heading.setVisible(false);
            panel.getElement().removeAttribute("aria-labelledby");
            return;
        }
        heading.setText(title);
        heading.setVisible(true);
        panel.getElement().setAttribute("aria-labelledby", heading.getId());
    }

    /**
     * @return the heading text, or null if the drawer has none
     * @see #setTitle(String)
     */
    public String getTitle() {
        return title;
    }

    /** Adds components to the sliding panel. */
    @Override
    public void add(Component... components) {
        panel.add(components);
    }

    @Override
    public void remove(Component... components) {
        panel.remove(components);
    }

    @Override
    public void removeAll() {
        panel.removeAll();
    }

    /** Adds components to the page the panel slides over. */
    public void addToPage(Component... components) {
        page.add(components);
    }

    /** Removes components from the page the panel slides over. */
    public void removeFromPage(Component... components) {
        page.remove(components);
    }

    /**
     * Closes the drawer when it is taken off the page while open, so the keyboard is not left held
     * inside something nobody can see.
     */
    @Override
    protected void onDetach() {
        if (opened) {
            close();
        }
        super.onDetach();
    }

    /** The hidden checkbox the stylesheet watches to decide whether the panel is in or out. */
    private static final class Toggle extends Component {
        private Toggle() {
            super("input");
            getElement().setAttribute("type", "checkbox");
            getElement().setClassName("drawer-toggle");
        }
    }

    /** The dim beside the panel. A label pointing at the checkbox, so a click unticks it. */
    private static final class Overlay extends Component implements HasStyle {
        private Overlay() {
            super("label");
        }

        @Override
        public Component getComponent() {
            return this;
        }
    }

    /** The heading at the top of the panel. */
    private static final class Heading extends Component implements HasStyle, HasText {
        private Heading() {
            super("h2");
        }

        @Override
        public Component getComponent() {
            return this;
        }
    }
}
