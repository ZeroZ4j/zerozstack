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
package com.zeroz4j.ui.domproof;

import org.teavm.jso.JSBody;
import org.teavm.jso.dom.html.HTMLElement;

/**
 * The browser facts a Java object cannot report about itself.
 *
 * <p>Every question here is asked of the live page rather than of the component: is this node in
 * the document, does the browser lay it out with a size, what does a reader actually see, which
 * element has focus. A field can hold a perfectly correct error message in a field of its own
 * class while the user sees nothing, and only these calls can tell the two apart.</p>
 */
final class Browser {

    private Browser() {
    }

    /** True when the node is in the live document rather than dangling in memory. */
    @JSBody(params = {"el"}, script = "return !!(el && el.isConnected);")
    static native boolean attached(HTMLElement el);

    /**
     * True when a person looking at the page would see this node: it is in the document, the
     * browser gives it a box with width and height, and nothing has made it invisible.
     */
    @JSBody(params = {"el"}, script =
            "if (!el || !el.isConnected) { return false; }"
            + "var s = window.getComputedStyle(el);"
            + "if (s.display === 'none' || s.visibility === 'hidden') { return false; }"
            + "if (parseFloat(s.opacity) === 0) { return false; }"
            + "var r = el.getBoundingClientRect();"
            + "return r.width > 0 && r.height > 0;")
    static native boolean visible(HTMLElement el);

    /**
     * Everything on the page a reader can see, as text. This is the one that matters: text inside
     * a hidden node, or inside a node nobody inserted, does not appear here.
     */
    @JSBody(params = {}, script = "return document.body.innerText || '';")
    static native String pageText();

    @JSBody(params = {"el"}, script = "return el ? (el.textContent || '') : '';")
    static native String textOf(HTMLElement el);

    @JSBody(params = {"el"}, script = "return el ? el.tagName.toLowerCase() : '';")
    static native String tagOf(HTMLElement el);

    @JSBody(params = {"id"}, script = "return document.getElementById(id);")
    static native HTMLElement byId(String id);

    @JSBody(params = {"sel"}, script = "return document.querySelector(sel);")
    static native HTMLElement query(String sel);

    /** A click the way a person makes one, not a Java method call on the component. */
    @JSBody(params = {"el"}, script =
            "el.dispatchEvent(new MouseEvent('click', "
            + "{bubbles: true, cancelable: true, view: window}));")
    static native void click(HTMLElement el);

    @JSBody(params = {"el", "type"}, script =
            "el.dispatchEvent(new Event(type, {bubbles: true}));")
    static native void fire(HTMLElement el, String type);

    @JSBody(params = {"el", "value"}, script = "el.value = value;")
    static native void typeInto(HTMLElement el, String value);

    @JSBody(params = {"el", "checked"}, script = "el.checked = checked;")
    static native void tick(HTMLElement el, boolean checked);

    @JSBody(params = {}, script = "return document.activeElement;")
    static native HTMLElement focused();

    @JSBody(params = {"a", "b"}, script = "return a === b;")
    static native boolean same(HTMLElement a, HTMLElement b);

    /** The position of a node among its parent's element children, or -1. */
    @JSBody(params = {"el"}, script =
            "if (!el || !el.parentElement) { return -1; }"
            + "var kids = el.parentElement.children;"
            + "for (var i = 0; i < kids.length; i++) { if (kids[i] === el) { return i; } }"
            + "return -1;")
    static native int indexAmongSiblings(HTMLElement el);

    /** The element that would be inserted for this one - itself, or the group it now sits in. */
    @JSBody(params = {"el"}, script =
            "var n = el; while (n.parentElement && n.parentElement !== document.body) "
            + "{ n = n.parentElement; } return n;")
    static native HTMLElement outermostBefore(HTMLElement el);
}
