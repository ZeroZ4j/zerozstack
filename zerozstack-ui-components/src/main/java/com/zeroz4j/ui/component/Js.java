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

    /** Copies text to the clipboard (execCommand fallback works in every embedded browser). */
    @JSBody(params = {"text"}, script =
        "var ta = document.createElement('textarea');"
        + "ta.value = text; ta.style.position = 'fixed'; ta.style.opacity = '0';"
        + "document.body.appendChild(ta); ta.select();"
        + "try { document.execCommand('copy'); } catch (e) {}"
        + "document.body.removeChild(ta);")
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

    /** Whether {@code event}'s target is exactly {@code element} and not something inside it. */
    @JSBody(params = {"event", "element"}, script = "return event.target === element;")
    public static native boolean targets(org.teavm.jso.dom.events.Event event, HTMLElement element);

    /** The current value of a &lt;select&gt; element (TeaVM does not wrap HTMLSelectElement.value). */
    @JSBody(params = {"select"}, script = "return select.value;")
    public static native String selectValue(HTMLElement select);
}

