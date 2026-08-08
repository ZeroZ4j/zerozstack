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

import com.zeroz4j.signals.ObservableSignal;
import com.zeroz4j.signals.ValueSignal;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/**
 * Makes an application installable, and offers web push.
 *
 * <p>One call at startup registers the framework's service worker:</p>
 *
 * <pre>{@code
 * public static void main(String[] args) {
 *     Pwa.install();                  // independent of the socket, so it goes before connect
 *     Zeroz4jClient.connect(wsUrl, () -> RmiSecurityContext.onResolved(App::buildUi));
 * }
 * }</pre>
 *
 * <p>The page also needs a manifest, which the application supplies — a static
 * {@code manifest.webmanifest} in {@code META-INF/resources}, or an endpoint built with
 * {@code PwaManifest} when it varies per tenant:</p>
 *
 * <pre>{@code
 * <link rel="manifest" href="/manifest.webmanifest">
 * <meta name="theme-color" content="#1f2937">
 * }</pre>
 *
 * <h2>What installing gives you, stated plainly</h2>
 * <p>A home-screen launch in a standalone window, a faster second start because the shell is cached,
 * and a readable page instead of a browser error when the app is opened with no network.</p>
 *
 * <p><b>It does not make the application work offline, and it is not meant to.</b> Every view here
 * loads its data over the WebSocket, so with no connection there is nothing to render. That is the
 * architecture, not a gap — the same reason a Vaadin Flow application shows an offline page rather
 * than running offline. An application that must function without a network needs a client-side
 * store, which this framework deliberately does not have.</p>
 *
 * <p>For the connection dropping <em>while</em> the app is running, nothing here is involved:
 * {@link Zeroz4jClient} already shows a reconnecting banner and recovers by itself.</p>
 */
public final class Pwa {

    /** Where the framework's own service worker is served from. */
    private static final String DEFAULT_WORKER = "/zeroz4j-sw.js";

    /** Driven by the browser's own {@code beforeinstallprompt} and {@code appinstalled} events. */
    private static final ValueSignal<Boolean> INSTALLABLE = new ValueSignal<>(Boolean.FALSE);

    private static boolean hooked;

    private Pwa() {}

    /** Receives the outcome of the install prompt. */
    @JSFunctor
    public interface InstallOutcome extends JSObject {
        /**
         * @param outcome {@code "accepted"}, {@code "dismissed"}, or {@code "unavailable"} when
         *                there was no browser offer to show
         */
        void accept(String outcome);
    }

    /** Internal: carries the browser's install offer across into the {@link #INSTALLABLE} signal. */
    @JSFunctor
    private interface AvailabilityCallback extends JSObject {
        void changed(boolean available);
    }

    /** Receives a web push subscription's endpoint and keys. */
    @JSFunctor
    public interface SubscriptionCallback extends JSObject {
        /**
         * @param endpoint the push service URL to deliver to, or null when subscribing failed
         * @param p256dh   the client's public key, base64url
         * @param auth     the shared auth secret, base64url
         * @param error    what went wrong, or null on success
         */
        void accept(String endpoint, String p256dh, String auth, String error);
    }

    /**
     * Registers the framework's service worker, making the application installable.
     *
     * <p>Safe to call unconditionally: a browser without service-worker support, or a page not in a
     * secure context, is a no-op rather than an error. Registration is asynchronous and does not
     * block startup.</p>
     */
    public static void install() {
        install(DEFAULT_WORKER);
    }

    /**
     * Registers a service worker of your own instead of the framework's.
     *
     * <p>Use this when you need different caching. Note that the framework's worker deliberately
     * caches only the shell — anything more ambitious runs into the fact that there is no
     * client-side data to cache.</p>
     *
     * @param path the worker's URL, absolute from the site root
     */
    public static void install(String path) {
        hookInstallOffer();
        registerServiceWorker(path);
    }

    /**
     * Whether installation can be offered right now, as a signal.
     *
     * <p>Browsers fire {@code beforeinstallprompt} only when their own criteria are met, and never
     * when the app is already installed — and they fire it on their own schedule, which is usually
     * <em>after</em> the page has built its UI. So this is a signal rather than a plain boolean: bind
     * a button's visibility to it and the button appears the moment the offer arrives, and goes away
     * again once it has been used.</p>
     *
     * <pre>{@code
     * Button install = new Button("Install");
     * Effect.create(() -> install.setVisible(Pwa.installable().get()));
     * }</pre>
     *
     * @return a read-only signal, true while {@link #promptInstall(InstallOutcome)} would show a
     *         prompt
     */
    public static ObservableSignal<Boolean> installable() {
        hookInstallOffer();
        return INSTALLABLE;
    }

    /**
     * Whether the browser has offered installation and the offer has not been used yet.
     *
     * <p>The one-shot form of {@link #installable()}. Prefer the signal for anything that has to stay
     * correct, because the offer usually arrives after the UI has been built.</p>
     *
     * @return true when {@link #promptInstall(InstallOutcome)} will show a prompt
     */
    public static boolean canInstall() {
        return hasInstallPrompt();
    }

    /**
     * Shows the browser's install prompt.
     *
     * <p>Must be called from a user gesture — a click handler — or the browser refuses it. The
     * captured offer is single-use: after a prompt, {@link #canInstall()} is false until the browser
     * offers again.</p>
     *
     * @param outcome receives what the user chose
     */
    public static void promptInstall(InstallOutcome outcome) {
        showInstallPrompt(outcome);
    }

    /**
     * Starts listening for the browser's install offer, once per page.
     *
     * <p>The guard is on the Java side as well as in the page, because the listener pushed across is
     * a fresh functor each time and would otherwise pile up.</p>
     */
    private static void hookInstallOffer() {
        if (hooked) {
            return;
        }
        hooked = true;
        listenForInstallOffer(available -> INSTALLABLE.set(Boolean.valueOf(available)));
    }

    /**
     * Whether this page is running as an installed application rather than in a browser tab.
     *
     * @return true when launched from the home screen or in a standalone window
     */
    public static boolean isInstalled() {
        return isStandalone();
    }

    /**
     * Subscribes this browser to web push.
     *
     * <p>Requires the service worker to be registered, and prompts for notification permission if it
     * has not been granted. Hand the returned endpoint and keys to your server; delivery is ordinary
     * server-side HTTPS to that endpoint and needs nothing from this framework.</p>
     *
     * @param vapidPublicKey your VAPID public key, base64url
     * @param callback       receives the subscription, or an error when the user refused permission
     *                       or the browser declined
     */
    public static void subscribeToPush(String vapidPublicKey, SubscriptionCallback callback) {
        subscribe(vapidPublicKey, callback);
    }

    // ------------------------------------------------------------------ browser

    // The browser fires beforeinstallprompt once and does not repeat it, so the page-level listener
    // goes in as early as anything here runs. Everything that wants to know is notified from it.
    @JSBody(params = { "onChange" }, script =
        "if (!window.__zeroz4jInstall) {"
        + "  window.__zeroz4jInstall = { offer: null, listeners: [] };"
        + "  window.__zeroz4jInstall.notify = function (available) {"
        + "    var l = window.__zeroz4jInstall.listeners;"
        + "    for (var i = 0; i < l.length; i++) { l[i](available); }"
        + "  };"
        + "  window.addEventListener('beforeinstallprompt', function (e) {"
        // Without preventDefault some browsers show their own mini-infobar and the captured event
        // is spent on it, leaving the application's own button with nothing to prompt with.
        + "    e.preventDefault();"
        + "    window.__zeroz4jInstall.offer = e;"
        + "    window.__zeroz4jInstall.notify(true);"
        + "  });"
        + "  window.addEventListener('appinstalled', function () {"
        + "    window.__zeroz4jInstall.offer = null;"
        + "    window.__zeroz4jInstall.notify(false);"
        + "  });"
        + "}"
        + "window.__zeroz4jInstall.listeners.push(onChange);"
        // An offer that arrived before this listener existed would otherwise be missed.
        + "if (window.__zeroz4jInstall.offer) { onChange(true); }")
    private static native void listenForInstallOffer(AvailabilityCallback onChange);

    @JSBody(params = { "path" }, script =
        "if (!('serviceWorker' in navigator)) { return; }"
        + "navigator.serviceWorker.register(path).catch(function (e) {"
        + "  console.warn('[zeroz4j] Service worker registration failed: ' + e);"
        + "});")
    private static native void registerServiceWorker(String path);

    @JSBody(params = {}, script = "return !!(window.__zeroz4jInstall && window.__zeroz4jInstall.offer);")
    private static native boolean hasInstallPrompt();

    @JSBody(params = {}, script =
        "return window.matchMedia('(display-mode: standalone)').matches"
        + " || window.navigator.standalone === true;")
    private static native boolean isStandalone();

    @JSBody(params = { "outcome" }, script =
        "var state = window.__zeroz4jInstall;"
        + "if (!state || !state.offer) { outcome('unavailable'); return; }"
        + "var e = state.offer;"
        + "state.offer = null;"                            // single use, whatever the answer
        + "state.notify(false);"
        + "e.prompt();"
        + "e.userChoice.then(function (choice) { outcome(choice.outcome); })"
        + "            .catch(function () { outcome('dismissed'); });")
    private static native void showInstallPrompt(InstallOutcome outcome);

    @JSBody(params = { "key", "callback" }, script =
        "if (!('serviceWorker' in navigator) || !('PushManager' in window)) {"
        + "  callback(null, null, null, 'This browser does not support web push.'); return;"
        + "}"
        + "function b64ToBytes(b64) {"
        + "  var padded = (b64 + '='.repeat((4 - b64.length % 4) % 4))"
        + "                 .replace(/-/g, '+').replace(/_/g, '/');"
        + "  var raw = atob(padded);"
        + "  var bytes = new Uint8Array(raw.length);"
        + "  for (var i = 0; i < raw.length; i++) { bytes[i] = raw.charCodeAt(i); }"
        + "  return bytes;"
        + "}"
        + "navigator.serviceWorker.ready.then(function (registration) {"
        + "  return registration.pushManager.subscribe({"
        + "    userVisibleOnly: true,"                     // silent push is refused by browsers anyway
        + "    applicationServerKey: b64ToBytes(key)"
        + "  });"
        + "}).then(function (sub) {"
        + "  var json = sub.toJSON();"
        + "  var keys = json.keys || {};"
        + "  callback(sub.endpoint, keys.p256dh || null, keys.auth || null, null);"
        + "}).catch(function (e) {"
        + "  callback(null, null, null, String(e));"
        + "});")
    private static native void subscribe(String vapidPublicKey, SubscriptionCallback callback);
}
