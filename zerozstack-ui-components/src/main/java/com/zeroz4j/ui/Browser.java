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
package com.zeroz4j.ui;

import org.teavm.jso.JSBody;
import org.teavm.jso.dom.html.HTMLElement;

/**
 * Small, safe wrappers for browser calls an application needs and TeaVM does not wrap.
 *
 * <p>Every method here tolerates the browser refusing: storage that is blocked or full, a missing
 * element, an API an embedded browser does not have. None of them throws.</p>
 */
public final class Browser {

    private Browser() {
    }

    /**
     * Reads a value from this browser's {@code localStorage}.
     *
     * @param key the key
     * @return the stored text, or null when there is none or storage is blocked
     */
    @JSBody(params = {"key"}, script =
        "try { return window.localStorage.getItem(key); } catch (ignored) { return null; }")
    public static native String storageGet(String key);

    /**
     * Writes a value to this browser's {@code localStorage}.
     *
     * @param key   the key
     * @param value the text to store
     * @return true when it was stored, false when storage is blocked or full
     */
    @JSBody(params = {"key", "value"}, script =
        "try { window.localStorage.setItem(key, value); return true; } catch (ignored) { return false; }")
    public static native boolean storageSet(String key, String value);

    /**
     * Removes a value from this browser's {@code localStorage}. Does nothing when storage is blocked.
     *
     * @param key the key
     */
    @JSBody(params = {"key"}, script =
        "try { window.localStorage.removeItem(key); } catch (ignored) {}")
    public static native void storageRemove(String key);

    /**
     * Opens an address in a new tab. The opened page gets no handle back to this one.
     *
     * @param url the address
     */
    @JSBody(params = {"url"}, script = "window.open(url, '_blank', 'noopener,noreferrer');")
    public static native void open(String url);

    /**
     * Leaves this page for another address with a full page load, which is how to reach a server
     * path the router does not own, such as a sign-out. Use {@code Router.navigate} for a route.
     *
     * @param url the address
     */
    @JSBody(params = {"url"}, script = "window.location.assign(url);")
    public static native void goTo(String url);

    /**
     * The path of the current address, from the site root.
     *
     * @return for example {@code /app/orders}
     */
    @JSBody(script = "return window.location.pathname;")
    public static native String path();

    /**
     * Scrolls {@code element} to its end once the browser has laid out what was just added. Two
     * animation frames pass first, because scrolling in the same turn as the change measures the
     * old height and stops short.
     *
     * @param element the scrolling element, such as a message list
     */
    @JSBody(params = {"element"}, script =
        "if (!element) { return; }"
        + "requestAnimationFrame(function () { requestAnimationFrame(function () {"
        + " element.scrollTop = element.scrollHeight; }); });")
    public static native void scrollToBottom(HTMLElement element);

    /**
     * Scrolls the page so {@code element} is at the top of the view, smoothly, once the browser has
     * laid out what was just added.
     *
     * @param element the element to bring into view
     */
    @JSBody(params = {"element"}, script =
        "if (!element) { return; }"
        + "requestAnimationFrame(function () { requestAnimationFrame(function () {"
        + " element.scrollIntoView({behavior: 'smooth', block: 'start'}); }); });")
    public static native void scrollIntoView(HTMLElement element);

    /**
     * Downloads a file from an address on this site without leaving the page. A hidden link with the
     * {@code download} attribute is clicked, which every browser treats as a download. Opening a tab
     * for an attachment leaves a blank tab behind. The session cookie travels with it.
     *
     * @param url the address of the file; null or empty does nothing
     */
    public static void download(String url) {
        download(url, "");
    }

    /**
     * Downloads a file, suggesting a name for it.
     *
     * @param url      the address of the file; null or empty does nothing
     * @param fileName the suggested name; empty lets the server decide
     */
    public static void download(String url, String fileName) {
        if (url == null || url.isEmpty()) {
            return;
        }
        clickHiddenLink(url, fileName == null ? "" : fileName);
    }

    @JSBody(params = {"url", "fileName"}, script =
        "var link = document.createElement('a');"
        + "link.href = url; link.setAttribute('download', fileName); link.style.display = 'none';"
        + "document.body.appendChild(link); link.click();"
        + "setTimeout(function () { document.body.removeChild(link); }, 0);")
    private static native void clickHiddenLink(String url, String fileName);

    /**
     * Writes a warning to the browser console.
     *
     * @param message the text
     */
    @JSBody(params = {"message"}, script = "console.warn(message);")
    public static native void warn(String message);
}
