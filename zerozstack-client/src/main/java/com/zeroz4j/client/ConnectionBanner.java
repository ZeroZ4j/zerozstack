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
package com.zeroz4j.client;

import org.teavm.jso.JSBody;

/**
 * The built-in "Connection lost — reconnecting…" bar.
 *
 * <p>Shown automatically while the channel is {@code RECONNECTING} and hidden again on
 * {@code CONNECTED}. It exists so that a dropped connection is <em>never</em> invisible by
 * default: before it, every application had to discover the silent-dead-page failure mode in
 * production and then build this bar itself. An application that renders its own indicator (from
 * {@link WasmRmiClient#connectionState()}) turns this one off with
 * {@link Zeroz4jClient#showConnectionBanner(boolean)}.
 *
 * <p>Deliberately raw DOM with inline styles, no dependency on the component library or on any
 * CSS framework: it must render identically in an application that loads no stylesheet at all,
 * and must not participate in application layout — it overlays, fixed to the top edge.
 */
final class ConnectionBanner {

    private ConnectionBanner() {}

    /** Shows the bar with the given text, creating the element on first use. */
    static void show(String text) {
        showNative(text);
    }

    /** Hides the bar. A no-op when it was never shown. */
    static void hide() {
        hideNative();
    }

    @JSBody(params = { "text" }, script =
        "var b = document.getElementById('zeroz4j-connection-banner');" +
        "if (!b) {" +
        "  b = document.createElement('div');" +
        "  b.id = 'zeroz4j-connection-banner';" +
        "  b.setAttribute('role', 'status');" +
        "  b.style.cssText = 'position:fixed;top:0;left:0;right:0;z-index:2147483647;" +
        "background:#b91c1c;color:#ffffff;font:14px/1.4 system-ui,sans-serif;" +
        "text-align:center;padding:6px 12px;box-shadow:0 1px 4px rgba(0,0,0,0.3);';" +
        "  document.body.appendChild(b);" +
        "}" +
        "b.textContent = text;" +
        "b.style.display = 'block';")
    private static native void showNative(String text);

    @JSBody(params = {}, script =
        "var b = document.getElementById('zeroz4j-connection-banner');" +
        "if (b) { b.style.display = 'none'; }")
    private static native void hideNative();
}
