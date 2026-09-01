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
 *
 * <h2>Why it is a popover</h2>
 *
 * <p>The bar used to be an ordinary element carrying the largest stacking number a browser
 * accepts, and it still lost to an open modal dialog. A modal dialog is drawn in the browser's
 * own <em>top layer</em>, which sits above the whole page whatever number anything on the page
 * carries — see {@code docs/guides/ui-layering.md}. So the one moment a user most needs telling
 * that the connection is gone, mid-way through a dialog, was the one moment nothing was shown.
 *
 * <p>The only way into the top layer is to be put there, and there are exactly two ways to do
 * that: be a modal dialog, or be a popover. A modal dialog is wrong — it would take the keyboard,
 * dim the page and interrupt whatever was being typed. A popover in {@code manual} state does
 * none of that: nothing outside it is blocked, and the browser only moves focus into a popover
 * that asks for it, which this one does not (it holds one line of text and nothing focusable).
 * Typing carries on uninterrupted.
 *
 * <p>Two things it costs, both accepted:</p>
 * <ul>
 *   <li><b>Order inside the top layer is by arrival.</b> A dialog opened <em>after</em> the bar
 *       appears is drawn over it. The bar re-announces itself on every state change, which puts
 *       it back on top, but between those moments a newly opened dialog can cover it.</li>
 *   <li><b>A browser too old for popovers</b> falls back to exactly the previous behaviour — a
 *       fixed bar with a very high stacking number, correct everywhere except under a dialog.
 *       Support arrived in Chrome and Edge 114, Safari 17 and Firefox 125.</li>
 * </ul>
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

    // The inline style overrides the browser's own popover styling as well as doing the paint:
    // a popover is centred and boxed by default, and this is a full-width strip at the top edge.
    // Nothing here may set display, because a popover's visibility belongs to showPopover and
    // hidePopover; an inline display would fight them.
    //
    // The bar's own element is called "bar" and not something shorter, and that is not a matter of
    // taste. TeaVM inlines this script as text and renames only the parameters, and when the build
    // is minified - which every generated application's build is, because that is the compiler's
    // default - it renames them to single letters, "b" for the first one. Through 0.6.0 and 0.7.0
    // this script called its own element "b" as well, so "b" stopped meaning the text and started
    // meaning the element, and the bar came up reading "[object HTMLDivElement]" in every
    // application ever generated from the archetype. Nothing errored; the examples all looked
    // right, because their builds turn minifying off. JsBodyNamingContractTest now fails the build
    // if any embedded script names something with a single letter.
    @JSBody(params = { "text" }, script =
        "var bar = document.getElementById('zeroz4j-connection-banner');" +
        "if (!bar) {" +
        "  bar = document.createElement('div');" +
        "  bar.id = 'zeroz4j-connection-banner';" +
        "  bar.setAttribute('role', 'status');" +
        "  bar.style.cssText = 'position:fixed;top:0;left:0;right:0;bottom:auto;" +
        "width:auto;height:auto;max-width:none;max-height:none;margin:0;border:0;border-radius:0;" +
        "overflow:hidden;z-index:2147483647;" +
        "background:#b91c1c;color:#ffffff;font:14px/1.4 system-ui,sans-serif;" +
        "text-align:center;padding:6px 12px;box-shadow:0 1px 4px rgba(0,0,0,0.3);';" +
        "  if (typeof bar.showPopover === 'function') { bar.setAttribute('popover', 'manual'); }" +
        "  document.body.appendChild(bar);" +
        "}" +
        "bar.textContent = text;" +
        "if (bar.hasAttribute('popover')) {" +
        "  try { bar.hidePopover(); } catch (ignored) { }" +
        "  try { bar.showPopover(); } catch (ignored) { }" +
        "} else {" +
        "  bar.style.display = 'block';" +
        "}")
    private static native void showNative(String text);

    @JSBody(params = {}, script =
        "var bar = document.getElementById('zeroz4j-connection-banner');" +
        "if (bar) {" +
        "  if (bar.hasAttribute('popover')) {" +
        "    try { bar.hidePopover(); } catch (ignored) { }" +
        "  } else {" +
        "    bar.style.display = 'none';" +
        "  }" +
        "}")
    private static native void hideNative();
}
