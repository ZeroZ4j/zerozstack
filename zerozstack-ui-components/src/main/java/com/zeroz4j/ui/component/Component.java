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

import java.util.ArrayList;
import java.util.List;
import org.teavm.jso.JSBody;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.html.HTMLElement;

/**
 * Abstract base class for all DOM-less UI components in zeroz4j.
 *
 * <p>Wraps a native TeaVM JSO {@link HTMLElement} and manages visibility, DOM attachment lifecycle events,
 * and virtual-thread event dispatching for synchronous RMI execution.</p>
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>Virtual Thread Boundary:</b> Native browser DOM event listeners are executed on the main UI thread, which cannot be suspended by TeaVM's {@code @Async} coroutine runtime.
 *       The {@link #threaded(org.teavm.jso.dom.events.EventListener)} method wraps listeners inside {@code new Thread(() -> listener.handleEvent(evt)).start()},
 *       allowing component event handlers to perform synchronous blocking backend RMI calls without throwing coroutine state errors.</li>
 *   <li><b>Element Access:</b> Directly manages the underlying {@link HTMLElement} created via {@code Window.current().getDocument().createElement(tagName)}.</li>
 * </ul>
 */
public abstract class Component {

    private final HTMLElement element;

    /**
     * The components a container put inside this one, in the order they were added.
     *
     * <p>This is what makes {@link #onDetach()} reach a whole screen rather than only its outermost
     * box. It holds what {@link HasComponents#add} put there; anything appended straight to the
     * element with {@code getElement().appendChild(...)} is invisible to it, and gets no lifecycle
     * call, which is one more reason to add components with {@code add}.</p>
     */
    private final List<Component> children = new ArrayList<>();

    /** Whether a container has put this component on the page and not taken it off again. */
    private boolean attached;

    /**
     * Constructs a new component wrapping a newly created HTML element with the specified tag name.
     *
     * @param tagName the HTML tag name (e.g., "div", "button", "input")
     *
     * <p><b>Under the hood:</b> Invokes {@code Window.current().getDocument().createElement(tagName)}.</p>
     */
    public Component(String tagName) {
        this.element = Window.current().getDocument().createElement(tagName);
    }
    
    /**
     * Constructs a new component wrapping an existing HTML element instance.
     *
     * @param element the existing native TeaVM JSO HTML element
     */
    public Component(HTMLElement element) {
        this.element = element;
    }

    /**
     * Retrieves the underlying native TeaVM JSO HTML element wrapped by this component.
     *
     * @return native {@link HTMLElement} instance
     */
    public HTMLElement getElement() {
        return element;
    }

    /**
     * Returns the element a container should insert to place this component on the page.
     *
     * <p>For nearly every component this is the same element as {@link #getElement()}. It differs
     * only where a component has to wrap itself in something larger to be complete: an input field
     * given a caption, for instance, is a caption, a control and a message line, and inserting the
     * control alone would leave the other two behind. Containers add and remove this element;
     * everything else — styling, sizing, events, focus — still goes to {@link #getElement()}.</p>
     *
     * @return the outermost element of this component
     */
    public HTMLElement getOuterElement() {
        return element;
    }
    
    /**
     * Gives this component a name for anybody who cannot see it.
     *
     * <p>Most controls name themselves out of the words inside them: a button that says "Save" is
     * announced as "Save". This is for the ones that cannot — an icon on its own, a splitter, a
     * canvas, a spinner. Without it a screen reader says "button" and stops, which tells the
     * listener nothing at all.</p>
     *
     * <p>Prefer visible words wherever there is room for them. A name only a screen reader can
     * hear is the fallback, not the goal, and one that disagrees with the visible label is worse
     * than none — somebody using voice control says what they can see.</p>
     *
     * @param label the words, or null to take the name away again
     */
    public void setAriaLabel(String label) {
        if (label == null || label.isEmpty()) {
            element.removeAttribute("aria-label");
        } else {
            element.setAttribute("aria-label", label);
        }
    }

    /** The name set with {@link #setAriaLabel(String)}, or null when it has none. */
    public String getAriaLabel() {
        return element.getAttribute("aria-label");
    }

    /**
     * Sets the HTML {@code id} attribute of the underlying DOM element.
     *
     * @param id element identifier string
     */
    public void setId(String id) {
        element.setAttribute("id", id);
    }
    
    /**
     * Retrieves the HTML {@code id} attribute of the underlying DOM element.
     *
     * @return element identifier string, or empty string/null if omitted
     */
    public String getId() {
        return element.getAttribute("id");
    }

    /**
     * Controls the CSS visibility of the underlying element by toggling the {@code display: none} style property.
     *
     * @param visible true to show the component; false to set {@code display: none}
     */
    public void setVisible(boolean visible) {
        HTMLElement outer = getOuterElement();
        if (visible) {
            outer.getStyle().removeProperty("display");
        } else {
            outer.getStyle().setProperty("display", "none");
        }
    }

    /**
     * Lifecycle callback invoked when the component is attached to the active DOM tree.
     */
    protected void onAttach() {
    }

    /**
     * Lifecycle callback invoked when the component is detached from the active DOM tree.
     *
     * <p>This is where a component stops whatever it started: timers, effects, subscriptions,
     * listeners it put on the document. It runs when a container takes the component off the page,
     * and it runs for everything inside that component too.</p>
     */
    protected void onDetach() {
    }

    /** Whether this component has been put on the page by a container and not taken off again. */
    public boolean isAttached() {
        return attached;
    }

    /** The components a container has put inside this one. Maintained by {@link HasComponents}. */
    List<Component> trackedChildren() {
        return children;
    }

    /**
     * Records a part this component built for itself and inserted by hand.
     *
     * <p>A component that wraps something - a card around its body, a dialog around its panel -
     * inserts that part's element directly rather than through {@code add}, because {@code add} is
     * what the application uses to put things <i>into</i> the wrapper. Saying so here is what keeps
     * the lifecycle unbroken: without it, taking the wrapper off the page would stop the wrapper
     * and leave everything the application put inside it running.</p>
     *
     * @param part the part to bring into this component's lifecycle
     */
    void own(Component part) {
        if (part != null && !children.contains(part)) {
            children.add(part);
        }
    }

    /**
     * Runs {@link #onAttach()} here and on everything inside, once.
     *
     * <p>Calling it a second time does nothing, so a component added to a container that is itself
     * added to another container is not started twice.</p>
     */
    void attach() {
        if (attached) {
            return;
        }
        attached = true;
        onAttach();
        for (Component child : new ArrayList<>(children)) {
            child.attach();
        }
    }

    /**
     * Runs {@link #onDetach()} on everything inside, innermost first, and then here.
     *
     * <p>Calling it on a component that is not on the page does nothing.</p>
     */
    void detach() {
        if (!attached) {
            return;
        }
        attached = false;
        for (Component child : new ArrayList<>(children)) {
            child.detach();
        }
        onDetach();
    }

    // ---------------------------------------------------------------- replacing what is inside

    /**
     * Puts {@code newContents} inside {@code host}, taking out and shutting down whatever was
     * there before.
     *
     * <p>This is the supported way to swap one screen for another inside a plain element - the
     * {@code <div id="app-root">} an application starts in, most often. Use it instead of
     * {@code host.setInnerHTML("")}: emptying an element by hand takes the old screen off the page
     * without telling it, so its timers keep ticking, its effects keep firing and its
     * subscriptions keep arriving, all rebuilding a screen nobody is looking at any more - and
     * throwing the keyboard off whatever the person moved on to. Every {@link #onDetach()} in the
     * old screen runs here, including the ones deep inside it.</p>
     *
     * <p>A container that is itself a component has the same operation as
     * {@link HasComponents#replaceContents(Component...)}; prefer that where you have one.</p>
     *
     * @param host the element to fill; nothing happens if it is null
     * @param newContents what to put in it - none, one, or several
     */
    public static void replaceContents(HTMLElement host, Component... newContents) {
        if (host == null) {
            return;
        }
        List<Component> placed = hostedIn(host);
        for (Component previous : new ArrayList<>(placed)) {
            previous.detach();
        }
        placed.clear();
        while (host.getLastChild() != null) {
            host.removeChild(host.getLastChild());
        }
        if (newContents == null) {
            return;
        }
        for (Component next : newContents) {
            if (next == null) {
                continue;
            }
            host.appendChild(next.getOuterElement());
            placed.add(next);
            next.attach();
        }
    }

    /**
     * What was last placed in {@code host} by {@link #replaceContents(HTMLElement, Component...)}.
     *
     * <p>A plain element cannot hold a Java list, so the list lives here and the element carries
     * only its position in it. There is one of these per host element, and an application has one
     * or two host elements, so the table does not grow with the screen.</p>
     */
    private static List<Component> hostedIn(HTMLElement host) {
        int slot = hostSlot(host);
        if (slot < 0 || slot >= HOSTED.size()) {
            HOSTED.add(new ArrayList<>());
            slot = HOSTED.size() - 1;
            setHostSlot(host, slot);
        }
        return HOSTED.get(slot);
    }

    private static final List<List<Component>> HOSTED = new ArrayList<>();

    @JSBody(params = { "element" },
            script = "return (typeof element.__zzHosted === 'number') ? element.__zzHosted : -1;")
    private static native int hostSlot(HTMLElement element);

    @JSBody(params = { "element", "slot" }, script = "element.__zzHosted = slot;")
    private static native void setHostSlot(HTMLElement element, int slot);

    /**
     * Wraps a native TeaVM DOM EventListener in a new virtual thread context.
     * This ensures that the event listener executes in a suspendable coroutine context,
     * allowing for synchronous backend RMI network calls without freezing or breaking the browser.
     *
     * @param <T>      the native event type extending {@link org.teavm.jso.dom.events.Event}
     * @param listener the original event listener callback
     * @return a wrapped event listener executing within a new suspendable Java thread
     *
     * <p><b>Under the hood:</b> Returns {@code evt -> new Thread(() -> listener.handleEvent(evt)).start()}.</p>
     */
    public static <T extends org.teavm.jso.dom.events.Event> org.teavm.jso.dom.events.EventListener<T> threaded(
            org.teavm.jso.dom.events.EventListener<T> listener) {
        return evt -> {
            new Thread(() -> listener.handleEvent(evt)).start();
        };
    }

    /**
     * Registers a DOM event listener that is automatically wrapped in a suspendable virtual thread context.
     * Recommended for primary UI interaction events (clicks, inputs, keypresses).
     *
     * @param <T>      the native event type extending {@link org.teavm.jso.dom.events.Event}
     * @param type     the DOM event type string (e.g., "click", "input", "change")
     * @param listener the event listener callback
     * @return a {@link DomListenerRegistration} handle to unregister the listener
     *
     * <p><b>Under the hood:</b> Wraps listener using {@link #threaded(org.teavm.jso.dom.events.EventListener)}
     * and calls {@code element.addEventListener(type, wrapped)}.</p>
     */
    public <T extends org.teavm.jso.dom.events.Event> DomListenerRegistration addDomEventListener(
            String type, org.teavm.jso.dom.events.EventListener<T> listener) {
        org.teavm.jso.dom.events.EventListener<T> wrapped = threaded(listener);
        element.addEventListener(type, wrapped);
        return () -> element.removeEventListener(type, wrapped);
    }
}
