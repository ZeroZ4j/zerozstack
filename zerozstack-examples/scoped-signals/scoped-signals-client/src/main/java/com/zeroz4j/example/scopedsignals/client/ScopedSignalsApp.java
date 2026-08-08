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
package com.zeroz4j.example.scopedsignals.client;

import com.zeroz4j.api.RmiSecurityContext;
import com.zeroz4j.client.Zeroz4jClient;
import com.zeroz4j.example.scopedsignals.api.ShopService;
import com.zeroz4j.example.scopedsignals.api.ShopService_Stub;
import com.zeroz4j.example.scopedsignals.api.ShopSignals;
import com.zeroz4j.signals.Effect;
import com.zeroz4j.ui.component.Button;
import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.Span;
import org.teavm.jso.JSBody;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.html.HTMLElement;

/**
 * Shows the three reaches side by side: global, per-browser, per-user.
 *
 * <p>Reading them is the same in all three cases — an {@code Effect} over a signal. Only the
 * declaration differs, and the framework decides who receives what.</p>
 */
public class ScopedSignalsApp {

    private static final ShopService service = new ShopService_Stub();

    public static void main(String[] args) {
        Zeroz4jClient.connect(webSocketUrl(), () ->
                // onAuthenticated fires from the AUTH frame handler, a stack that began in native
                // JavaScript. build() makes an RMI call, which suspends — so it needs a green thread
                // exactly as the click handlers below do.
                RmiSecurityContext.onAuthenticated(() ->
                        new Thread(ScopedSignalsApp::build).start()));
    }

    private static void build() {
        Div root = box("flex flex-col gap-6 p-8 max-w-3xl mx-auto");

        root.add(text("Scoped signals", "text-2xl font-bold"));
        root.add(text("Open this page in a second browser (or a private window) and watch which "
                + "panels move together.", "opacity-70"));

        Span identity = text("", "text-sm opacity-60 font-mono");
        root.add(identity);

        // ---- global -------------------------------------------------------
        Span visitors = text("", "text-lg");
        Effect.create(() -> visitors.setText("Visitors (global): " + ShopSignals.VISITORS.get()));
        root.add(panel("Signals.shared — everyone", visitors,
                button("Count a visitor", () -> service.countVisitor()),
                "Every connected browser sees the same number. Correct for public state, a leak "
                        + "for anything else."));

        // ---- per browser --------------------------------------------------
        Span basket = text("", "text-lg");
        Effect.create(() -> basket.setText("Basket (this browser): "
                + ShopSignals.BASKET.mine().get().getItems()));
        root.add(panel("Scope.CLIENT — this browser, no login needed", basket,
                button("Add an item", () -> service.addToBasket("Item "
                        + (ShopSignals.BASKET.mine().get().size() + 1))),
                "Survives reload and reconnect. Another browser has its own basket and never sees "
                        + "this one. It identifies a browser, not a person."));

        // ---- per user -----------------------------------------------------
        Span notice = text("", "text-lg");
        Effect.create(() -> {
            String current = ShopSignals.NOTICE.mine().get();
            notice.setText("Notice (this user): " + (current.isEmpty() ? "—" : current));
        });
        root.add(panel("Scope.USER — every device of one person", notice,
                button("Notify myself", () -> service.noticeMyself("Hello, "
                        + RmiSecurityContext.getUsername())),
                "Reaches this user's other tabs and devices. Sign in as a different user in another "
                        + "browser and it stays put."));

        Button clear = button("Clear my basket", () -> service.clearBasket());
        root.add(clear);

        identity.setText(service.whoAmI());

        HTMLElement appRoot = Window.current().getDocument().getElementById("app-root");
        appRoot.appendChild(root.getElement());
    }

    private static Component panel(String title, Component value, Component action, String note) {
        return box("flex flex-col gap-2 p-4 rounded bg-base-200",
                text(title, "font-semibold"),
                value,
                action,
                text(note, "text-sm opacity-60"));
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

    private static Span text(String value, String classes) {
        Span span = new Span(value);
        for (String cls : classes.split(" ")) {
            if (!cls.isEmpty()) {
                span.addClassName(cls);
            }
        }
        return span;
    }

    @JSBody(script =
        "var l = window.location;"
        + "var p = new URLSearchParams(l.search);"
        + "return (l.protocol === 'https:' ? 'wss://' : 'ws://') + l.host + '/wasm-rmi'"
        + "     + '?user=' + encodeURIComponent(p.get('user') || 'demo')"
        + "     + '&password=' + encodeURIComponent(p.get('password') || 'demo');")
    private static native String webSocketUrl();
}
