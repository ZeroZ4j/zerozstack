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
package com.zeroz4j.example.pwa.client;

import com.zeroz4j.api.RmiSecurityContext;
import com.zeroz4j.client.Pwa;
import com.zeroz4j.client.Zeroz4jClient;
import com.zeroz4j.example.pwa.api.PushService;
import com.zeroz4j.example.pwa.api.PushService_Stub;
import com.zeroz4j.signals.Effect;
import com.zeroz4j.signals.ValueSignal;
import com.zeroz4j.ui.component.Button;
import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.Span;
import com.zeroz4j.ui.theme.TextStyle;
import org.teavm.jso.JSBody;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.html.HTMLElement;

/**
 * What installing a zeroz4j application gets you — and, just as plainly, what it does not.
 *
 * <p>Three things happen here. The service worker is registered, which is what makes the browser
 * willing to install the app at all. The install button binds its visibility to
 * {@link Pwa#installable()}, so it appears when the browser decides the app qualifies rather than
 * when the page loads. And a push subscription is taken out and handed to the server.</p>
 *
 * <p>The fourth thing is the one to actually try: stop the server, reload, and read the offline page.
 * That is the honest ceiling for a framework whose every view loads its data over a socket.</p>
 */
public class PwaInstallApp {

    private static final PushService service = new PushService_Stub();

    private static final ValueSignal<String> pushStatus = new ValueSignal<>("Not subscribed.");

    public static void main(String[] args) {
        // Deliberately before connect: registering the service worker has nothing to do with the
        // socket, and doing it first means a reload during a server outage still finds the shell.
        Pwa.install();

        Zeroz4jClient.connect(webSocketUrl(), () ->
                // onResolved fires from the AUTH frame handler, a stack that began in native
                // JavaScript. build() makes an RMI call, which suspends — hence the green thread.
                RmiSecurityContext.onResolved(() -> new Thread(PwaInstallApp::build).start()));
    }

    private static void build() {
        Div root = box("flex flex-col gap-6 p-8 max-w-3xl mx-auto");

        root.add(TextStyle.PAGE_TITLE.span("Installing a zeroz4j app"));
        root.add(TextStyle.SECONDARY.span("Everything below is what a service worker and a manifest "
                + "buy you. Nothing below makes the application work without a network."));

        // ---- install ------------------------------------------------------
        Span installState = TextStyle.SECONDARY.span("");
        Button install = button("Install this app", () -> Pwa.promptInstall(outcome ->
                installState.setText("accepted".equals(outcome)
                        ? "Installed. Launch it from your home screen or app list."
                        : "unavailable".equals(outcome)
                                ? "The browser has not offered installation."
                                : "Dismissed. The browser will offer again later.")));

        // The browser fires its install offer on its own schedule, usually after this UI is built.
        // Binding to the signal means the button appears then, rather than never.
        Effect.create(() -> {
            boolean offered = Pwa.installable().get();
            install.setVisible(offered);
            if (!offered && installState.getText().isEmpty()) {
                installState.setText(Pwa.isInstalled()
                        ? "Already running as an installed app."
                        : "Waiting for the browser to offer installation. It wants a manifest, "
                                + "icons, a service worker and a secure origin — and it takes its "
                                + "time deciding.");
            }
        });

        root.add(panel("Installable", installState, install,
                "A manifest and a registered service worker are the whole requirement. The manifest "
                        + "here is built per request by PwaManifest — try /?brand=sunset to see it "
                        + "change name and colour."));

        // ---- push ---------------------------------------------------------
        Span pushLine = TextStyle.SECONDARY.span("");
        Effect.create(() -> pushLine.setText(pushStatus.get()));

        Button subscribe = button("Subscribe to push", PwaInstallApp::subscribe);

        root.add(panel("Web push", pushLine, subscribe,
                "The browser hands back an endpoint; the server stores it and can post to it later, "
                        + "with this app closed. Delivery itself is ordinary server-side HTTPS and "
                        + "needs nothing from the framework."));

        // ---- offline ------------------------------------------------------
        root.add(panel("Offline",
                TextStyle.BODY.span("Stop the server and reload."),
                new Span(""),
                "You get a page saying you are offline, not a broken app and not a browser error. "
                        + "That is the whole offline story here, on purpose: every view on this page "
                        + "loads its data over the WebSocket, so with no connection there is nothing "
                        + "to render."));

        HTMLElement appRoot = Window.current().getDocument().getElementById("app-root");
        appRoot.appendChild(root.getElement());
    }

    /**
     * Asks the server for its VAPID key, subscribes with it, and hands the subscription back.
     *
     * <p>Runs on a green thread already — the RMI calls suspend. The callback from the browser does
     * not, so the second RMI call needs a thread of its own.</p>
     */
    private static void subscribe() {
        pushStatus.set("Asking the server for its VAPID key…");
        String key = service.vapidPublicKey();

        pushStatus.set("Waiting for you to allow notifications…");
        Pwa.subscribeToPush(key, (endpoint, p256dh, auth, error) -> {
            if (error != null) {
                pushStatus.set("Could not subscribe: " + error);
                return;
            }
            new Thread(() -> pushStatus.set(
                    service.registerSubscription(endpoint, p256dh, auth))).start();
        });
    }

    private static Component panel(String title, Component value, Component action, String note) {
        return box("flex flex-col gap-2 p-4 rounded bg-base-200",
                TextStyle.SECTION_TITLE.span(title),
                value,
                action,
                TextStyle.SECONDARY.span(note));
    }

    private static Button button(String label, Runnable action) {
        Button button = new Button(label);
        button.addClassName("btn-primary");
        button.addClassName("w-fit");
        // The click handler runs on a stack that began in native JavaScript, where TeaVM cannot
        // suspend. An RMI call is a suspension, so it runs on a green thread instead.
        button.addClickListener(event -> new Thread(action).start());
        return button;
    }

    private static Div box(String classes, Component... children) {
        Div div = new Div(children);
        for (String cls : classes.split(" ")) {
            if (!cls.isEmpty()) {
                div.addClassName(cls);
            }
        }
        return div;
    }

    @JSBody(script =
        "var l = window.location;"
        + "return (l.protocol === 'https:' ? 'wss://' : 'ws://') + l.host + '/wasm-rmi';")
    private static native String webSocketUrl();
}
