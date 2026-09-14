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
package com.zeroz4j.client.router;

import com.zeroz4j.api.Disposable;
import com.zeroz4j.api.i18n.FrameworkText;
import com.zeroz4j.client.WasmRmiClient;
import com.zeroz4j.client.WasmRmiClientChannel;
import com.zeroz4j.signals.Effect;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/**
 * The router's built-in busy indicator and failure message, switched on with
 * {@link Router#showBusyIndicator(boolean)} and {@link Router#showFailureMessage(boolean)}.
 *
 * <p>Framework-internal. Both are an ordinary {@link Router.LifecycleListener} and nothing more, so
 * everything here is something an application could build for itself from the public API.</p>
 *
 * <h2>The class contract</h2>
 * <ul>
 *   <li>{@code #zeroz4j-busy-bar} - the bar along the top edge.</li>
 *   <li>{@code #zeroz4j-busy-spinner} - the card, {@code role="status"} and
 *       {@code aria-live="polite"}, holding {@code .zeroz4j-busy-ring} and the visually hidden
 *       {@code .zeroz4j-busy-label}.</li>
 *   <li>{@code html.zeroz4j-busy} - present while the indicator is showing, and only then.</li>
 *   <li>{@code #zeroz4j-navigation-failure} - the failure message, holding
 *       {@code .zeroz4j-navigation-failure-text}, {@code .zeroz4j-navigation-failure-note},
 *       {@code .zeroz4j-navigation-failure-retry} and {@code .zeroz4j-navigation-failure-dismiss}.</li>
 * </ul>
 *
 * <p>The look comes from a stylesheet this class puts at the <em>start</em> of {@code <head>}, so
 * any stylesheet of the application's own comes later and wins at equal specificity. Every color and
 * size is read from a custom property with a DaisyUI token as its fallback, which is what makes it
 * right in a light and a dark theme without a rule for either. The properties are listed in
 * {@code docs/ROUTING.md}.</p>
 *
 * <h2>Why the busy indicator is not a popover</h2>
 * <p>The connection banner puts itself in the browser's top layer so it shows over an open modal
 * dialog. The busy indicator deliberately does not: a navigation is started by somebody using the
 * page, not by a dialog, and a spinner drawn over a dialog somebody is still reading would say the
 * dialog is busy.</p>
 */
final class NavigationFeedback implements Router.LifecycleListener {

    /** A navigation shorter than this shows nothing at all, so a fast page never flickers. */
    static final int SHOW_AFTER_MILLIS = 300;

    private enum Kind { CONNECTION, OTHER, NOT_FOUND, FORBIDDEN }

    private static final NavigationFeedback INSTANCE = new NavigationFeedback();

    private static boolean busyEnabled;
    private static boolean failureEnabled;
    private static Disposable registration;
    private static Disposable labelEffect;
    private static Disposable failureEffect;
    private static Kind failureKind;

    private NavigationFeedback() {
    }

    static void busyIndicator(boolean enabled) {
        busyEnabled = enabled;
        if (enabled) {
            installStyles();
            installBusyElements();
            if (labelEffect == null) {
                labelEffect = Effect.create(NavigationFeedback::redrawBusyLabel);
            }
            register();
            Navigation latest = Router.latestNavigation();
            if (latest != null && latest.outcome() == Navigation.Outcome.PENDING) {
                busyStart(SHOW_AFTER_MILLIS);
            }
        } else {
            busyStop();
        }
    }

    static void failureMessage(boolean enabled) {
        failureEnabled = enabled;
        if (enabled) {
            installStyles();
            register();
        } else {
            hideFailure();
        }
    }

    private static void register() {
        if (registration == null) {
            registration = Router.addLifecycleListener(INSTANCE);
        }
    }

    @Override
    public void onNavigationStarted(Navigation navigation) {
        // The message was about a navigation the person has now moved on from, Retry included.
        hideFailure();
        if (busyEnabled) {
            // A no-op while the countdown is running or the indicator is already up, so a redirect
            // or an overtaking navigation neither restarts the 300 ms nor makes it flicker.
            busyStart(SHOW_AFTER_MILLIS);
        }
    }

    @Override
    public void onNavigationFinished(Navigation navigation, RouteParams params) {
        busyStop();
        hideFailure();
    }

    @Override
    public void onNavigationFailed(Navigation navigation, Throwable reason) {
        busyStop();
        if (failureEnabled) {
            showFailure(kindOf(reason));
        }
    }

    private static Kind kindOf(Throwable reason) {
        if (Navigation.isConnectionProblem(reason)) {
            return Kind.CONNECTION;
        }
        if (reason instanceof RouteNotFoundException) {
            return Kind.NOT_FOUND;
        }
        if (reason instanceof RouteForbiddenException) {
            return Kind.FORBIDDEN;
        }
        return Kind.OTHER;
    }

    // ---------------------------------------------------------------- the failure message

    private static void showFailure(Kind kind) {
        hideFailure();
        failureKind = kind;
        boolean offersRetry = kind == Kind.CONNECTION || kind == Kind.OTHER;
        createFailure(offersRetry, NavigationFeedback::retryPressed, NavigationFeedback::hideFailure);
        // Inside an effect, so the words follow a language switch and the note follows the
        // connection: it says Retry is waiting while the socket is down, and goes once it is back.
        failureEffect = Effect.create(NavigationFeedback::redrawFailure);
    }

    private static void redrawFailure() {
        Kind kind = failureKind;
        if (kind == null) {
            return;
        }
        boolean connected =
                WasmRmiClient.connectionState().get() == WasmRmiClientChannel.State.CONNECTED;
        String text;
        switch (kind) {
            case CONNECTION:
                text = FrameworkText.uiNavigationConnectionFailed().text();
                break;
            case NOT_FOUND:
                text = FrameworkText.uiNavigationNotFound().text();
                break;
            case FORBIDDEN:
                text = FrameworkText.uiNavigationForbidden().text();
                break;
            default:
                text = FrameworkText.uiNavigationFailed().text();
                break;
        }
        String note = connected ? "" : FrameworkText.uiNavigationWaitingForConnection().text();
        updateFailure(text, note, FrameworkText.uiRetry().text(), FrameworkText.uiDismiss().text(),
                !connected);
    }

    private static void retryPressed() {
        if (WasmRmiClient.connectionState().get() != WasmRmiClientChannel.State.CONNECTED) {
            // The button says why it is waiting. Retrying now would only fail again at once with
            // the same message, which reads as the button doing nothing.
            return;
        }
        Router.retry();
    }

    private static void hideFailure() {
        failureKind = null;
        if (failureEffect != null) {
            Disposable effect = failureEffect;
            failureEffect = null;
            effect.dispose();
        }
        removeFailure();
    }

    // ---------------------------------------------------------------- the busy indicator

    private static void redrawBusyLabel() {
        setBusyLabel(FrameworkText.uiLoading().text());
    }

    // ---------------------------------------------------------------- browser

    /** A button's action. */
    @JSFunctor
    interface Action extends JSObject {
        void run();
    }

    /**
     * The stylesheet, put first in {@code <head>} so the application's own stylesheets come after
     * it and win. Every value goes through a custom property; see {@code docs/ROUTING.md}.
     */
    @JSBody(script =
        "if (document.getElementById('zeroz4j-navigation-style')) { return; }"
        + "var style = document.createElement('style');"
        + "style.id = 'zeroz4j-navigation-style';"
        + "var accent = 'var(--zeroz4j-busy-color, var(--color-primary, #2563eb))';"
        + "var layer = 'var(--zeroz4j-busy-z-index, 9999)';"
        + "style.textContent = ''"
        // The bar.
        + "+ '#zeroz4j-busy-bar{position:fixed;top:0;left:0;width:100%;margin:0;"
        + "height:var(--zeroz4j-busy-bar-height, 3px);background:' + accent + ';"
        + "transform:scaleX(0);transform-origin:left;opacity:0;z-index:' + layer + ';pointer-events:none}'"
        + "+ 'html.zeroz4j-busy #zeroz4j-busy-bar{opacity:1;animation:zeroz4j-busy-sweep 1.1s ease-in-out infinite}'"
        + "+ '@keyframes zeroz4j-busy-sweep{0%{transform:scaleX(0);opacity:1}55%{transform:scaleX(0.62);opacity:1}"
        + "100%{transform:scaleX(1);opacity:0}}'"
        // The card, centered in the window plus an offset an application sets beside a side menu.
        + "+ '#zeroz4j-busy-spinner{position:fixed;"
        + "top:calc(50% + var(--zeroz4j-busy-offset-y, 0px));left:calc(50% + var(--zeroz4j-busy-offset-x, 0px));"
        + "width:var(--zeroz4j-busy-card-size, 72px);height:var(--zeroz4j-busy-card-size, 72px);"
        + "transform:translate(-50%, -50%);box-sizing:border-box;margin:0;"
        + "display:flex;align-items:center;justify-content:center;"
        // A shade of the page's own ink over the page's own ground: a little darker than a light
        // page and a little lighter than a dark one, so the card reads as a card on both.
        + "background:var(--zeroz4j-busy-card-background, color-mix(in srgb, var(--color-base-content, #1f2937) 7%, var(--color-base-100, #ffffff)));"
        + "border:1px solid var(--zeroz4j-busy-card-border, color-mix(in srgb, var(--color-base-content, #1f2937) 14%, transparent));"
        + "border-radius:var(--zeroz4j-busy-card-radius, 16px);"
        + "box-shadow:0 4px 16px rgba(0,0,0,0.12), 0 1px 3px rgba(0,0,0,0.08);"
        + "z-index:' + layer + ';pointer-events:none;visibility:hidden;opacity:0;transition:opacity 0.15s ease-out}'"
        + "+ 'html.zeroz4j-busy #zeroz4j-busy-spinner{visibility:visible;opacity:1}'"
        + "+ '#zeroz4j-busy-spinner .zeroz4j-busy-ring{display:block;box-sizing:border-box;border-radius:50%;"
        + "width:var(--zeroz4j-busy-spinner-size, 48px);height:var(--zeroz4j-busy-spinner-size, 48px);"
        + "border:4px solid color-mix(in srgb, ' + accent + ' 28%, transparent);"
        + "border-top-color:' + accent + ';border-right-color:' + accent + '}'"
        + "+ 'html.zeroz4j-busy #zeroz4j-busy-spinner .zeroz4j-busy-ring{animation:zeroz4j-busy-spin 0.8s linear infinite}'"
        + "+ '@keyframes zeroz4j-busy-spin{to{transform:rotate(360deg)}}'"
        + "+ '@keyframes zeroz4j-busy-pulse{0%,100%{opacity:1}50%{opacity:0.45}}'"
        + "+ '@media (prefers-reduced-motion: reduce){"
        + "html.zeroz4j-busy #zeroz4j-busy-spinner .zeroz4j-busy-ring{border-color:' + accent + ';"
        + "animation:zeroz4j-busy-pulse 1.6s ease-in-out infinite}"
        + "html.zeroz4j-busy #zeroz4j-busy-bar{transform:scaleX(1);animation:zeroz4j-busy-pulse 1.6s ease-in-out infinite}}'"
        + "+ '#zeroz4j-busy-spinner .zeroz4j-busy-label{position:absolute;width:1px;height:1px;padding:0;"
        + "margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}'"
        // "The whole page is busy" has to win over "this one thing is clickable".
        + "+ 'html.zeroz4j-busy, html.zeroz4j-busy *{cursor:wait !important}'"
        // The failure message.
        + "+ '#zeroz4j-navigation-failure{position:fixed;"
        // At the bottom, so it neither covers the application's own header and navigation nor
        // sits under the connection banner, which takes the top edge while reconnecting.
        + "bottom:var(--zeroz4j-failure-bottom, 24px);"
        + "left:calc(50% + var(--zeroz4j-failure-offset-x, var(--zeroz4j-busy-offset-x, 0px)));"
        + "transform:translateX(-50%);box-sizing:border-box;width:max-content;"
        + "max-width:min(34rem, calc(100vw - 2rem));margin:0;padding:12px 16px;"
        + "display:flex;flex-direction:column;gap:8px;"
        + "background:var(--zeroz4j-failure-background, color-mix(in srgb, var(--color-base-content, #1f2937) 7%, var(--color-base-100, #ffffff)));"
        + "color:var(--zeroz4j-failure-color, var(--color-base-content, #1f2937));"
        + "border:1px solid color-mix(in srgb, var(--color-base-content, #1f2937) 14%, transparent);"
        + "border-left:4px solid var(--zeroz4j-failure-accent, var(--color-error, #dc2626));"
        + "border-radius:var(--zeroz4j-failure-radius, 12px);"
        + "box-shadow:0 8px 24px rgba(0,0,0,0.16), 0 1px 3px rgba(0,0,0,0.1);"
        + "font:14px/1.45 system-ui, sans-serif;z-index:' + layer + '}'"
        + "+ '#zeroz4j-navigation-failure p{margin:0}'"
        + "+ '#zeroz4j-navigation-failure .zeroz4j-navigation-failure-text{font-weight:600}'"
        + "+ '#zeroz4j-navigation-failure .zeroz4j-navigation-failure-note:empty{display:none}'"
        + "+ '#zeroz4j-navigation-failure .zeroz4j-navigation-failure-actions{display:flex;gap:8px;flex-wrap:wrap}'"
        + "+ '#zeroz4j-navigation-failure button{font:inherit;font-weight:600;margin:0;padding:6px 14px;"
        + "border-radius:8px;border:1px solid transparent;cursor:pointer}'"
        + "+ '#zeroz4j-navigation-failure .zeroz4j-navigation-failure-retry{background:' + accent + ';"
        + "color:var(--color-primary-content, #ffffff)}'"
        + "+ '#zeroz4j-navigation-failure .zeroz4j-navigation-failure-dismiss{background:transparent;"
        + "color:inherit;border-color:color-mix(in srgb, var(--color-base-content, #1f2937) 25%, transparent)}'"
        + "+ '#zeroz4j-navigation-failure button[aria-disabled=\"true\"]{opacity:0.55;cursor:not-allowed}'"
        + "+ '#zeroz4j-navigation-failure button:focus-visible{outline:2px solid ' + accent + ';outline-offset:2px}';"
        + "document.head.insertBefore(style, document.head.firstChild);")
    private static native void installStyles();

    /**
     * The bar and the card, created once. The countdown and the visible state live on the card
     * element itself, so there is exactly one of each however often this is called.
     *
     * <p>Appended to {@code <body>} rather than {@code <html>}: DaisyUI puts its theme on whichever
     * element carries {@code data-theme}, often {@code <body>}, and the colors only reach elements
     * inside it.</p>
     */
    @JSBody(script =
        "if (document.getElementById('zeroz4j-busy-spinner')) { return; }"
        + "var bar = document.createElement('div');"
        + "bar.id = 'zeroz4j-busy-bar';"
        + "bar.setAttribute('aria-hidden', 'true');"
        + "document.body.appendChild(bar);"
        + "var card = document.createElement('div');"
        + "card.id = 'zeroz4j-busy-spinner';"
        + "card.setAttribute('role', 'status');"
        + "card.setAttribute('aria-live', 'polite');"
        + "var ring = document.createElement('span');"
        + "ring.className = 'zeroz4j-busy-ring';"
        + "ring.setAttribute('aria-hidden', 'true');"
        + "card.appendChild(ring);"
        + "var label = document.createElement('span');"
        + "label.className = 'zeroz4j-busy-label';"
        + "card.appendChild(label);"
        + "card.zeroz4jTimer = null;"
        + "card.zeroz4jVisible = false;"
        + "card.zeroz4jLabel = 'Loading';"
        + "document.body.appendChild(card);")
    private static native void installBusyElements();

    /**
     * The words the card announces. Kept on the card and written into the label only while it is
     * showing: a live region is announced when its text changes, so an empty label while hidden is
     * what makes "Loading" be heard each time.
     */
    @JSBody(params = { "text" }, script =
        "var card = document.getElementById('zeroz4j-busy-spinner');"
        + "if (!card) { return; }"
        + "card.zeroz4jLabel = text;"
        + "if (card.zeroz4jVisible) { card.querySelector('.zeroz4j-busy-label').textContent = text; }")
    private static native void setBusyLabel(String text);

    /** Starts the countdown, unless it is already running or the indicator is already up. */
    @JSBody(params = { "delayMillis" }, script =
        "var card = document.getElementById('zeroz4j-busy-spinner');"
        + "if (!card || card.zeroz4jTimer || card.zeroz4jVisible) { return; }"
        + "card.zeroz4jTimer = setTimeout(function () {"
        + "  card.zeroz4jTimer = null;"
        + "  card.zeroz4jVisible = true;"
        + "  document.documentElement.classList.add('zeroz4j-busy');"
        + "  card.querySelector('.zeroz4j-busy-label').textContent = card.zeroz4jLabel;"
        + "}, delayMillis);")
    private static native void busyStart(int delayMillis);

    /** Stops the countdown and hides the indicator. A no-op when neither is happening. */
    @JSBody(script =
        "var card = document.getElementById('zeroz4j-busy-spinner');"
        + "if (!card) { return; }"
        + "if (card.zeroz4jTimer) { clearTimeout(card.zeroz4jTimer); card.zeroz4jTimer = null; }"
        + "card.zeroz4jVisible = false;"
        + "document.documentElement.classList.remove('zeroz4j-busy');"
        + "card.querySelector('.zeroz4j-busy-label').textContent = '';")
    private static native void busyStop();

    /**
     * The failure message, put first in {@code <body>} so Tab reaches Retry before anything on the
     * page, and drawn at the bottom of the window. {@code role="alert"} so a screen reader says it as soon as it appears. Real
     * {@code <button>} elements, so both are reachable with Tab and pressed with Enter or Space.
     * Focus is not moved: somebody may be typing.
     */
    @JSBody(params = { "offersRetry", "onRetry", "onDismiss" }, script =
        "var box = document.createElement('div');"
        + "box.id = 'zeroz4j-navigation-failure';"
        + "box.setAttribute('role', 'alert');"
        + "var text = document.createElement('p');"
        + "text.className = 'zeroz4j-navigation-failure-text';"
        + "box.appendChild(text);"
        + "var note = document.createElement('p');"
        + "note.className = 'zeroz4j-navigation-failure-note';"
        + "box.appendChild(note);"
        + "var actions = document.createElement('div');"
        + "actions.className = 'zeroz4j-navigation-failure-actions';"
        + "if (offersRetry) {"
        + "  var retry = document.createElement('button');"
        + "  retry.type = 'button';"
        + "  retry.className = 'zeroz4j-navigation-failure-retry';"
        + "  retry.addEventListener('click', function () {"
        + "    if (retry.getAttribute('aria-disabled') === 'true') { return; }"
        + "    onRetry();"
        + "  });"
        + "  actions.appendChild(retry);"
        + "}"
        + "var dismiss = document.createElement('button');"
        + "dismiss.type = 'button';"
        + "dismiss.className = 'zeroz4j-navigation-failure-dismiss';"
        + "dismiss.addEventListener('click', function () { onDismiss(); });"
        + "actions.appendChild(dismiss);"
        + "box.appendChild(actions);"
        + "document.body.insertBefore(box, document.body.firstChild);")
    private static native void createFailure(boolean offersRetry, Action onRetry, Action onDismiss);

    @JSBody(params = { "text", "note", "retryText", "dismissText", "retryWaits" }, script =
        "var box = document.getElementById('zeroz4j-navigation-failure');"
        + "if (!box) { return; }"
        + "box.querySelector('.zeroz4j-navigation-failure-text').textContent = text;"
        + "box.querySelector('.zeroz4j-navigation-failure-note').textContent = note;"
        + "box.querySelector('.zeroz4j-navigation-failure-dismiss').textContent = dismissText;"
        + "var retry = box.querySelector('.zeroz4j-navigation-failure-retry');"
        + "if (retry) {"
        + "  retry.textContent = retryText;"
        + "  if (retryWaits) { retry.setAttribute('aria-disabled', 'true'); }"
        + "  else { retry.removeAttribute('aria-disabled'); }"
        + "}")
    private static native void updateFailure(String text, String note, String retryText,
                                             String dismissText, boolean retryWaits);

    @JSBody(script =
        "var box = document.getElementById('zeroz4j-navigation-failure');"
        + "if (box) { box.remove(); }")
    private static native void removeFailure();
}
