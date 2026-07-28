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

    /** The current value of a &lt;select&gt; element (TeaVM does not wrap HTMLSelectElement.value). */
    @JSBody(params = {"select"}, script = "return select.value;")
    public static native String selectValue(HTMLElement select);
}

