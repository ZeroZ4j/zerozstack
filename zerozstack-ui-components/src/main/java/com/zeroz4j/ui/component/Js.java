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

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.dom.html.HTMLElement;

/** Tiny browser-API bridge for capabilities TeaVM JSO does not wrap. */
public final class Js {

    private Js() {}

    /** Receives a pasted image as a data: URI. */
    @JSFunctor
    public interface DataCallback extends JSObject {
        void accept(String dataUri);
    }

    /** A plain notification with no payload. */
    @JSFunctor
    public interface VoidCallback extends JSObject {
        void call();
    }

    /**
     * Fires {@code callback} whenever {@code element} changes size, via {@code ResizeObserver}.
     *
     * <p>The window {@code resize} event is not enough for anything that draws to measured
     * pixels: a chart inside a drawer, a split pane or a collapsing card is resized by layout
     * without the window ever changing. Silently does nothing where {@code ResizeObserver} is
     * unavailable, so callers still need their own initial draw.</p>
     */
    @JSBody(params = {"element", "callback"}, script =
        "if (typeof ResizeObserver === 'undefined') { return; }"
        + "var observer = new ResizeObserver(function(){ callback(); });"
        + "observer.observe(element);")
    public static native void onResize(HTMLElement element, VoidCallback callback);

    /**
     * Fires {@code callback} with a data: URI whenever an image is pasted into {@code element}
     * (Ctrl+V of a screenshot). Prevents the default paste for images so no stray text lands
     * in the composer.
     */
    @JSBody(params = {"element", "callback"}, script =
        "element.addEventListener('paste', function(e){"
        + " var items = e.clipboardData && e.clipboardData.items; if(!items) return;"
        + " for (var i=0;i<items.length;i++){"
        + "  if (items[i].type && items[i].type.indexOf('image')===0){"
        + "   e.preventDefault();"
        + "   var blob = items[i].getAsFile(); if(!blob) continue;"
        + "   var reader = new FileReader();"
        + "   reader.onload = function(ev){ callback(ev.target.result); };"
        + "   reader.readAsDataURL(blob);"
        + "  }"
        + " }"
        + "});")
    public static native void onPasteImage(HTMLElement element, DataCallback callback);

    @JSBody(params = {"key"}, script = "return window.localStorage.getItem(key);")
    public static native String localGet(String key);

    @JSBody(params = {"key", "value"}, script = "window.localStorage.setItem(key, value);")
    public static native void localSet(String key, String value);

    /**
     * Copies text to the clipboard, and puts the keyboard back where it was.
     *
     * <p>The copying is done by making a text box, selecting it, and asking the browser to copy
     * the selection - which works in every embedded browser, unlike the newer clipboard call.
     * Selecting that box takes the keyboard off whatever was just pressed, and removing the box
     * leaves the keyboard on nothing at all. So anybody who pressed a Copy button with the
     * keyboard was dumped to the top of the page and had to Tab all the way back down. Both Copy
     * buttons in this library did that, and the browser proof caught it.</p>
     */
    @JSBody(params = {"text"}, script =
        "var was = document.activeElement;"
        + "var ta = document.createElement('textarea');"
        + "ta.value = text; ta.style.position = 'fixed'; ta.style.opacity = '0';"
        + "document.body.appendChild(ta); ta.select();"
        + "try { document.execCommand('copy'); } catch (e) {}"
        + "document.body.removeChild(ta);"
        + "if (was && was.focus) { try { was.focus(); } catch (e) {} }")
    public static native void copyToClipboard(String text);

    @JSBody(params = {"theme"}, script = "document.body.setAttribute('data-theme', theme);")
    public static native void setTheme(String theme);

    /**
     * Opens {@code dialog} with the element's own {@code showModal()}, putting it in the
     * browser's top layer: Escape closes it, focus is trapped inside it, and the page behind
     * it stops responding.
     *
     * <p>Returns {@code false}, having changed nothing, in the three cases where the browser
     * would throw instead: the element is not a {@code <dialog>} or the browser has no
     * {@code showModal()}, the element is not in the page yet, or the call was refused for any
     * other reason. A stale {@code open} attribute — left behind when an open dialog was taken
     * out of the page — is cleared first, because the browser refuses {@code showModal()} on an
     * element that still claims to be open.</p>
     */
    @JSBody(params = {"dialog"}, script =
        "if (typeof dialog.showModal !== 'function') { return false; }"
        + "if (dialog.open && !dialog.isConnected) { dialog.removeAttribute('open'); }"
        + "if (!dialog.isConnected) { return false; }"
        + "if (dialog.open) { return true; }"
        + "try { dialog.showModal(); } catch (e) { return false; }"
        + "return true;")
    public static native boolean dialogShowModal(HTMLElement dialog);

    /**
     * Closes {@code dialog} if it is natively open. Does nothing otherwise, so it is safe to
     * call on a dialog that was only ever shown by class. The browser fires the element's
     * {@code close} event afterwards, not during this call.
     */
    @JSBody(params = {"dialog"}, script = "if (dialog.open) { dialog.close(); }")
    public static native void dialogClose(HTMLElement dialog);

    /** Whether {@code dialog} currently carries the native {@code open} state. */
    @JSBody(params = {"dialog"}, script = "return !!dialog.open;")
    public static native boolean dialogIsOpen(HTMLElement dialog);

    /**
     * The element the keyboard is on right now, or null when the browser says the page body is.
     *
     * <p>Used to remember where focus was before an overlay opened, so it can be put back
     * afterwards. The body is reported as null on purpose: putting focus back on the body is the
     * same as putting it nowhere, and doing it explicitly steals it from anything that has moved
     * on since.</p>
     */
    @JSBody(params = {}, script =
        "var el = document.activeElement;"
        + "if (!el || el === document.body || el === document.documentElement) { return null; }"
        + "return el;")
    public static native HTMLElement activeElement();

    /**
     * Puts the keyboard on {@code element}. Does nothing if it is null, is no longer in the page,
     * or refuses focus.
     */
    @JSBody(params = {"element"}, script =
        "if (!element || !element.isConnected || typeof element.focus !== 'function') { return; }"
        + "try { element.focus(); } catch (e) {}")
    public static native void focus(HTMLElement element);

    /**
     * Puts the keyboard on the first thing inside {@code container} that can take it — a button, a
     * link, a field. If there is nothing, {@code container} itself is made focusable and takes it,
     * so that the keyboard is inside the overlay rather than left behind on the page.
     *
     * <p>An element carrying {@code autofocus} wins over document order, which is how a "Cancel"
     * button is made the one waiting for Enter.</p>
     *
     * <p>It keeps trying for about half a second. A panel that slides in is invisible for the first
     * tenth of a second — the stylesheet delays it — and the browser refuses to put the keyboard on
     * something invisible, so one attempt at the moment of opening lands nowhere at all.</p>
     */
    @JSBody(params = {"container"}, script =
        "if (!container) { return; }"
        + "var selector = 'a[href], area[href], button:not([disabled]), input:not([disabled])"
        + ":not([type=hidden]), select:not([disabled]), textarea:not([disabled]),"
        + " iframe, object, embed, [contenteditable], [tabindex]:not([tabindex=\"-1\"])';"
        + "var tries = 0;"
        + "var attempt = function () {"
        + " tries++;"
        + " if (!container.isConnected) { return; }"
        + " if (container.contains(document.activeElement) && document.activeElement !== document.body) { return; }"
        + " var wanted = container.querySelector('[autofocus]');"
        + " if (!wanted) {"
        + "  var all = container.querySelectorAll(selector);"
        + "  for (var i = 0; i < all.length; i++) {"
        + "   var e = all[i];"
        + "   if (e.offsetWidth > 0 || e.offsetHeight > 0 || e.getClientRects().length > 0) { wanted = e; break; }"
        + "  }"
        + " }"
        + " if (!wanted) {"
        + "  if (!container.hasAttribute('tabindex')) { container.setAttribute('tabindex', '-1'); }"
        + "  wanted = container;"
        + " }"
        + " try { wanted.focus(); } catch (e) {}"
        + " if (!container.contains(document.activeElement) && tries < 40) {"
        + "  requestAnimationFrame(attempt);"
        + " }"
        + "};"
        + "attempt();")
    public static native void focusFirstInside(HTMLElement container);

    /** Whether {@code element} matches a CSS selector — ":hover", ":focus-within" and so on. */
    @JSBody(params = {"element", "selector"}, script =
        "if (!element) { return false; }"
        + "try { return element.matches(selector); } catch (e) { return false; }")
    public static native boolean matches(HTMLElement element, String selector);

    /**
     * The element an event happened on, or null when it was not an element. TeaVM's {@code Event}
     * exposes a target that is a {@code Node}, and a listener registered on the document needs the
     * element to ask whether the click was inside something.
     */
    @JSBody(params = {"event"}, script =
        "var t = event.target;"
        + "return (t && t.nodeType === 1) ? t : null;")
    public static native HTMLElement eventTargetElement(org.teavm.jso.dom.events.Event event);

    /** Whether {@code element} is, or is inside, {@code container}. */
    @JSBody(params = {"container", "element"}, script =
        "return !!(container && element && container.contains(element));")
    public static native boolean contains(HTMLElement container, HTMLElement element);

    /**
     * Holds the keyboard inside {@code container}: Tab off the last thing in it comes back to the
     * first, and Shift+Tab off the first goes to the last.
     *
     * <p>This is what a modal {@code <dialog>} gets from the browser for nothing. Anything else
     * that covers the page has to do it itself, and without it a person using the keyboard walks
     * straight out of the panel into the page behind — which is still there, still clickable, and
     * hidden under a dim.</p>
     *
     * <p>Calling it twice on the same element is harmless. Undo it with
     * {@link #releaseFocusTrap(HTMLElement)}.</p>
     */
    @JSBody(params = {"container"}, script =
        "if (!container || container.__zzTrap) { return; }"
        + "var selector = 'a[href], area[href], button:not([disabled]), input:not([disabled])"
        + ":not([type=hidden]), select:not([disabled]), textarea:not([disabled]),"
        + " iframe, object, embed, [contenteditable], [tabindex]:not([tabindex=\"-1\"])';"
        + "container.__zzTrap = function (e) {"
        + " if (e.key !== 'Tab') { return; }"
        + " var all = container.querySelectorAll(selector);"
        + " var nodes = [];"
        + " for (var i = 0; i < all.length; i++) {"
        + "  var n = all[i];"
        + "  if (n.offsetWidth > 0 || n.offsetHeight > 0 || n.getClientRects().length > 0) { nodes.push(n); }"
        + " }"
        + " if (nodes.length === 0) { e.preventDefault(); return; }"
        + " var first = nodes[0];"
        + " var last = nodes[nodes.length - 1];"
        + " var here = document.activeElement;"
        + " var inside = container.contains(here);"
        + " if (e.shiftKey) {"
        + "  if (!inside || here === first) { e.preventDefault(); last.focus(); }"
        + " } else if (!inside || here === last) { e.preventDefault(); first.focus(); }"
        + "};"
        + "container.addEventListener('keydown', container.__zzTrap, true);")
    public static native void trapFocusIn(HTMLElement container);

    /** Lets the keyboard leave {@code container} again. Harmless if it was never held. */
    @JSBody(params = {"container"}, script =
        "if (!container || !container.__zzTrap) { return; }"
        + "container.removeEventListener('keydown', container.__zzTrap, true);"
        + "container.__zzTrap = null;")
    public static native void releaseFocusTrap(HTMLElement container);

    /** Whether a checkbox is ticked (TeaVM does not wrap HTMLInputElement.checked here). */
    @JSBody(params = {"box"}, script = "return !!box.checked;")
    public static native boolean checkboxIsChecked(HTMLElement box);

    /** Ticks or unticks a checkbox without firing a change event, as a property write does. */
    @JSBody(params = {"box", "checked"}, script = "box.checked = !!checked;")
    public static native void checkboxSetChecked(HTMLElement box, boolean checked);

    /** Opens or closes a &lt;details&gt; element (TeaVM does not wrap HTMLDetailsElement.open). */
    @JSBody(params = {"details", "open"}, script = "details.open = !!open;")
    public static native void detailsSetOpen(HTMLElement details, boolean open);

    /** Whether a &lt;details&gt; element is currently open. */
    @JSBody(params = {"details"}, script = "return !!details.open;")
    public static native boolean detailsIsOpen(HTMLElement details);

    /** The {@code key} of a keyboard event — "Escape", "Tab", "a" — as the browser reports it. */
    @JSBody(params = {"event"}, script = "return event.key;")
    public static native String eventKey(org.teavm.jso.dom.events.Event event);

    /** Whether {@code event}'s target is exactly {@code element} and not something inside it. */
    @JSBody(params = {"event", "element"}, script = "return event.target === element;")
    public static native boolean targets(org.teavm.jso.dom.events.Event event, HTMLElement element);

    /** The current value of a &lt;select&gt; element (TeaVM does not wrap HTMLSelectElement.value). */
    @JSBody(params = {"select"}, script = "return select.value;")
    public static native String selectValue(HTMLElement select);
}

