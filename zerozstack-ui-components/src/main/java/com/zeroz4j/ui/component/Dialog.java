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

import com.zeroz4j.ui.theme.TextStyle;
import com.zeroz4j.ui.component.mixin.HasLayer;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.theme.Layer;
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.html.HTMLElement;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A panel that takes over the page until it is answered.
 *
 * <p>The component is a native {@code <dialog>} element. Opening it hands it to the browser with
 * {@code showModal()}, which is what makes Escape close it, keeps the keyboard inside it, dims and
 * disables the page behind it, and draws it above everything else whatever the stacking around it
 * is. Closing it — by any route — fires the close listeners exactly once.</p>
 *
 * <h2>Sizing</h2>
 *
 * <p>A dialog is two boxes: a full-window overlay, and the visible panel inside it.
 * {@link #setWidth(String)} and {@link #setHeight(String)} size <b>the panel</b>, which is the one
 * anybody means:</p>
 *
 * <pre>{@code
 * Dialog dialog = new Dialog();
 * dialog.setWidth("56rem");            // a wide panel, never wider than the window
 * dialog.add(new Div("Pick a file"));
 * dialog.addAction(new Button("Cancel", e -> dialog.close()));
 * dialog.open();
 * }</pre>
 *
 * <p>The class-name methods inherited from {@link HasStyle} still apply to the overlay, which is
 * where an overlay concern such as stacking belongs. Nothing an application writes needs to know
 * that the panel exists as a separate element.</p>
 *
 * <h2>Closing</h2>
 *
 * <p>By default Escape closes the dialog, and so does a click on the dimmed area outside the
 * panel. Turn either off for a question that must be answered —
 * {@link #setCloseOnEsc(boolean)}, {@link #setCloseOnOutsideClick(boolean)} — and remember to
 * leave the user a button when you do. {@link #setModal(boolean)} switches the native behaviour
 * off altogether and restores the appearance-only dialog of 0.7.0 and earlier.</p>
 *
 * <h2>The title, and what a screen reader says</h2>
 *
 * <p>Give the dialog a title and it gets three things at once: a heading at the top of the panel,
 * a name a screen reader announces when the dialog opens, and something for a test to recognise it
 * by.</p>
 *
 * <pre>{@code
 * Dialog dialog = new Dialog("Delete the account?");
 * }</pre>
 *
 * <p>Without a title the browser announces nothing but the word "dialog". If the design has no
 * room for a visible heading, give it a name that is read but not shown with
 * {@link #setAriaLabel(String)}.</p>
 *
 * <h2>Focus</h2>
 *
 * <p>Opening moves the keyboard into the dialog and closing puts it back on whatever opened it, so
 * that pressing Escape leaves you where you were. While a modal dialog is open the browser holds
 * the keyboard inside it: Tab walks round the dialog's own controls and never reaches the page
 * behind. A dialog with {@link #setModal(boolean)} turned off still gets the keyboard moved in and
 * put back, but nothing holds it in — that is what turning the browser's ownership off means.</p>
 *
 * <h2>Stacking</h2>
 *
 * <p>A modal dialog is in the browser's <b>top layer</b> and is therefore above everything the
 * page can draw, whatever stacking numbers are involved — see {@link Layer}. A dialog with
 * modality turned off has no such help, so it carries {@link Layer#OVERLAY} instead.</p>
 */
public class Dialog extends Component implements HasComponents, HasStyle, HasSize,
        HasLayer<Dialog> {

    private static final AtomicInteger DIALOG_ID_COUNTER = new AtomicInteger();

    private final Div modalBox;
    private final Div modalAction;
    private final Div content;
    private final Heading heading;

    private final List<EventListener<ComponentEvent<Dialog>>> closeListeners = new ArrayList<>();

    private boolean modal = true;
    private boolean closeOnEsc = true;
    private boolean closeOnOutsideClick = true;

    /** True between {@link #open()} and the close that follows it, by whatever route. */
    private boolean opened;
    /** True while the pointer went down on the overlay rather than inside the panel. */
    private boolean pressStartedOutside;
    /** Where the keyboard was when this dialog opened, so it can be put back on close. */
    private HTMLElement openedFrom;
    /** The title text, or null when the dialog has none. */
    private String title;

    /** A dialog with no heading. Give it one with {@link #setTitle(String)} if it needs a name. */
    public Dialog() {
        this(null);
    }

    /**
     * A dialog with {@code title} as its heading and as the name a screen reader announces.
     *
     * @param title the heading, or null for a dialog with no heading
     */
    public Dialog(String title) {
        super("dialog");
        addClassName("modal");
        setLayer(Layer.OVERLAY);

        modalBox = new Div();
        modalBox.addClassName("modal-box");

        heading = new Heading();
        heading.setId("zeroz-dialog-title-" + DIALOG_ID_COUNTER.incrementAndGet());
        heading.addClassName(TextStyle.SECTION_TITLE.getClassNames());
        heading.setVisible(false);

        content = new Div();

        modalAction = new Div();
        modalAction.addClassName("modal-action");

        modalBox.add(heading);
        modalBox.add(content);
        modalBox.add(modalAction);

        getElement().appendChild(modalBox.getElement());
        own(modalBox);

        setTitle(title);

        // These four are registered straight on the element rather than through
        // addDomEventListener, which starts a green thread and therefore runs the body after the
        // browser has already acted. Refusing Escape has to happen inside the browser's own call,
        // and the two pointer listeners have to agree on their order. None of them calls anything
        // that suspends; the application's own close listeners are dispatched on a thread by
        // fireClose, so those may still call a service.
        getElement().addEventListener("close",
                (org.teavm.jso.dom.events.EventListener<Event>) evt -> onClosedByBrowser());

        // Escape reaches the element as 'cancel' first. Refusing the default there is the only way
        // to keep a native dialog open, and it also stops the 'close' that would otherwise follow.
        getElement().addEventListener("cancel", (org.teavm.jso.dom.events.EventListener<Event>) evt -> {
            if (!closeOnEsc) {
                evt.preventDefault();
            }
        });

        // A click outside the panel lands on the dialog element itself, because the panel is a
        // child that covers the middle. Both the press and the release have to land there, so that
        // selecting text inside the panel and letting go outside it does not throw the work away.
        getElement().addEventListener("mousedown", (org.teavm.jso.dom.events.EventListener<Event>)
                evt -> pressStartedOutside = Js.targets(evt, getElement()));
        getElement().addEventListener("click", (org.teavm.jso.dom.events.EventListener<Event>) evt -> {
            boolean outside = pressStartedOutside && Js.targets(evt, getElement());
            pressStartedOutside = false;
            if (outside && closeOnOutsideClick) {
                closeInternal(true);
            }
        });
    }

    /**
     * Shows the dialog. Does nothing if it is already showing.
     *
     * <p>Unless {@link #setModal(boolean)} was turned off, the browser is asked to own it: Escape
     * closes it, focus cannot leave it, and the page behind it stops responding. A dialog that has
     * not been added to the page yet cannot be handed over, so it is shown by appearance only —
     * add it to a layout before opening it.</p>
     */
    public void open() {
        HTMLElement wasFocused = Js.activeElement();
        if (!opened) {
            openedFrom = wasFocused;
        }
        boolean browserOwnsIt = false;
        if (modal) {
            // Deliberately not guarded by the opened flag: this is a no-op on a dialog that is
            // genuinely showing, and on one whose element was taken out of the page and put back
            // it is the repair that lets it show again.
            browserOwnsIt = Js.dialogShowModal(getElement());
        }
        addClassName("modal-open");
        opened = true;
        if (!browserOwnsIt) {
            // showModal() moves the keyboard into the dialog by itself. Without it — modality
            // turned off, or an element not on the page yet — nothing does, and the keyboard is
            // left on the page behind, which is exactly the fault this dialog was rebuilt to end.
            Js.focusFirstInside(modalBox.getElement());
        }
    }

    /**
     * Hides the dialog and fires the close listeners. Does nothing if it is not showing, so
     * calling it from a button that Escape already beat to it is harmless.
     */
    public void close() {
        closeInternal(false);
    }

    /** Whether the dialog is showing. */
    public boolean isOpened() {
        return opened;
    }

    private void closeInternal(boolean fromClient) {
        if (!opened) {
            return;
        }
        opened = false;
        removeClassName("modal-open");
        // Fires the element's 'close' event afterwards; opened is already false by then, so
        // onClosedByBrowser() ignores it and the listeners are notified exactly once, here.
        Js.dialogClose(getElement());
        restoreFocus();
        fireClose(fromClient);
    }

    private void onClosedByBrowser() {
        if (!opened) {
            return;
        }
        opened = false;
        removeClassName("modal-open");
        restoreFocus();
        fireClose(true);
    }

    /**
     * Puts the keyboard back on whatever opened the dialog.
     *
     * <p>The browser already does this for a dialog it owns, and doing it again costs nothing and
     * lands on the same element. For one it does not own there is nothing else to do it, and
     * without this the keyboard is left on a control that is no longer on the screen.</p>
     */
    private void restoreFocus() {
        HTMLElement target = openedFrom;
        openedFrom = null;
        if (target != null && !Js.contains(getElement(), target)) {
            Js.focus(target);
        }
    }

    private void fireClose(boolean fromClient) {
        if (closeListeners.isEmpty()) {
            return;
        }
        List<EventListener<ComponentEvent<Dialog>>> snapshot = new ArrayList<>(closeListeners);
        ComponentEvent<Dialog> event = new ComponentEvent<>(this, fromClient);
        // Same green thread every other component event gets, so a close listener may call a
        // service. Component.threaded does exactly this for listeners registered the usual way.
        new Thread(() -> {
            for (EventListener<ComponentEvent<Dialog>> listener : snapshot) {
                listener.onComponentEvent(event);
            }
        }).start();
    }

    /**
     * Called once every time the dialog closes, however it closed: Escape, a click outside, or
     * {@link #close()}. {@code isFromClient()} on the event is false only when the application
     * closed it itself.
     *
     * @param listener the callback to run on each close
     * @return a handle that removes the listener again
     */
    public DomListenerRegistration addCloseListener(EventListener<ComponentEvent<Dialog>> listener) {
        closeListeners.add(listener);
        return () -> closeListeners.remove(listener);
    }

    /**
     * Whether the browser owns the open dialog. On by default.
     *
     * <p>Off, {@link #open()} only changes how the dialog looks, which is what every version up to
     * 0.7.0 did: Escape does nothing, focus can wander out and the page behind stays live. Set it
     * before opening.</p>
     *
     * <p>A click outside the panel is the one exit this does <b>not</b> take away, because it is
     * drawn and handled by the component rather than by the browser. To match 0.7.0 exactly, turn
     * that off as well:</p>
     *
     * <pre>{@code
     * dialog.setModal(false);
     * dialog.setCloseOnOutsideClick(false);
     * }</pre>
     *
     * @param modal true to hand the open dialog to the browser
     */
    public void setModal(boolean modal) {
        this.modal = modal;
    }

    /**
     * @return whether the browser owns the open dialog
     * @see #setModal(boolean)
     */
    public boolean isModal() {
        return modal;
    }

    /**
     * Whether Escape closes the dialog. On by default, and ignored while
     * {@link #setModal(boolean)} is off, because a dialog the browser does not own never hears
     * Escape at all. Turning it off means the user has no way out but a button you provide.
     *
     * @param closeOnEsc true to let Escape close the dialog
     */
    public void setCloseOnEsc(boolean closeOnEsc) {
        this.closeOnEsc = closeOnEsc;
    }

    /**
     * @return whether Escape closes the dialog
     * @see #setCloseOnEsc(boolean)
     */
    public boolean isCloseOnEsc() {
        return closeOnEsc;
    }

    /**
     * Whether a click on the dimmed area outside the panel closes the dialog. On by default.
     * Turning it off means the user has no way out but a button you provide.
     *
     * @param closeOnOutsideClick true to let a click outside the panel close the dialog
     */
    public void setCloseOnOutsideClick(boolean closeOnOutsideClick) {
        this.closeOnOutsideClick = closeOnOutsideClick;
    }

    /**
     * @return whether a click outside the panel closes the dialog
     * @see #setCloseOnOutsideClick(boolean)
     */
    public boolean isCloseOnOutsideClick() {
        return closeOnOutsideClick;
    }

    /**
     * Sets how wide the visible panel is — {@code "56rem"}, {@code "800px"}, {@code "60%"}. The
     * panel is never wider than the window, so a width chosen for a desktop still fits a phone.
     *
     * <p>This is the width of the panel, not of the full-window overlay it sits in. There is no
     * useful width for the overlay: it always covers the window.</p>
     *
     * @param width any CSS length
     */
    @Override
    public void setWidth(String width) {
        modalBox.setStyle("width", width);
        // Measured against the window, not against the panel's parent. The overlay lays the panel
        // out in a grid track sized by the panel itself, so "100%" of it is the panel's own width
        // and clamps nothing: a panel asked to be 56rem stayed 56rem in a 380-pixel window and hung
        // off both edges. The 2rem keeps a margin so the panel never touches the glass.
        modalBox.setStyle("max-width", "calc(100vw - 2rem)");
    }

    /**
     * Sets how tall the visible panel is. The panel is never taller than the window, and its
     * content scrolls inside it when there is more of it than fits.
     *
     * @param height any CSS length
     * @see #setWidth(String)
     */
    @Override
    public void setHeight(String height) {
        modalBox.setStyle("height", height);
        modalBox.setStyle("max-height", "calc(100vh - 2rem)");
    }

    /**
     * Closes the dialog when it is taken out of the page while showing, so that the application
     * is told rather than left believing a dialog it can no longer see is still open.
     */
    @Override
    protected void onDetach() {
        closeInternal(false);
        super.onDetach();
    }

    @Override
    public Component getComponent() {
        return this;
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

    public void addAction(Component component) {
        modalAction.add(component);
    }

    /**
     * Sets the dialog's heading, which is also the name a screen reader announces when it opens.
     *
     * <p>The heading appears at the top of the panel, above anything added with
     * {@link #add(Component...)}. Passing null removes it, and with it the name — after which the
     * dialog announces itself as nothing but "dialog" unless {@link #setAriaLabel(String)} gives it
     * one.</p>
     *
     * @param title the heading text, or null for no heading
     */
    public void setTitle(String title) {
        this.title = title;
        if (title == null || title.isEmpty()) {
            heading.setText("");
            heading.setVisible(false);
            getElement().removeAttribute("aria-labelledby");
            return;
        }
        heading.setText(title);
        heading.setVisible(true);
        // A name that is read wins over a heading that is pointed at, so a dialog given both keeps
        // the one the application asked for explicitly.
        if (getElement().getAttribute("aria-label") == null) {
            getElement().setAttribute("aria-labelledby", heading.getId());
        }
    }

    /**
     * @return the heading text, or null if the dialog has none
     * @see #setTitle(String)
     */
    public String getTitle() {
        return title;
    }

    /**
     * Names the dialog for a screen reader without showing the name on the screen.
     *
     * <p>Use this only where the design leaves no room for a heading. A visible
     * {@link #setTitle(String)} is better: everybody gets it, not only the people listening. Set
     * both and this one is what is announced.</p>
     *
     * @param ariaLabel the spoken name, or null to fall back to the heading
     */
    public void setAriaLabel(String ariaLabel) {
        if (ariaLabel == null || ariaLabel.isEmpty()) {
            getElement().removeAttribute("aria-label");
            if (title != null && !title.isEmpty()) {
                getElement().setAttribute("aria-labelledby", heading.getId());
            }
            return;
        }
        getElement().setAttribute("aria-label", ariaLabel);
        getElement().removeAttribute("aria-labelledby");
    }

    /**
     * @return the spoken name set with {@link #setAriaLabel(String)}, or null
     */
    public String getAriaLabel() {
        return getElement().getAttribute("aria-label");
    }

    /** The heading at the top of the panel. A real {@code <h2>}, so the page has an outline. */
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
