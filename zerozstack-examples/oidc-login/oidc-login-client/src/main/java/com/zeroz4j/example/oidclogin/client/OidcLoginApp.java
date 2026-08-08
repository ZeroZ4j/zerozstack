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
package com.zeroz4j.example.oidclogin.client;

import com.zeroz4j.api.RmiSecurityContext;
import com.zeroz4j.client.OidcClient;
import com.zeroz4j.client.Zeroz4jClient;
import com.zeroz4j.example.oidclogin.api.IdentityService;
import com.zeroz4j.example.oidclogin.api.IdentityService_Stub;
import com.zeroz4j.ui.component.Button;
import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.Span;
import org.teavm.jso.JSBody;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.html.HTMLElement;

/**
 * Logs in against Keycloak, then shows what the server made of the resulting token.
 *
 * <p>The entire login is {@link OidcClient#start}: it works out whether this page load is a fresh
 * visit, a return from the provider, or an already-authenticated reload, and only reaches the
 * callback once a token is held.</p>
 */
public class OidcLoginApp {

    private static final IdentityService service = new IdentityService_Stub();

    public static void main(String[] args) {
        OidcClient.Config config = new OidcClient.Config(
                "http://localhost:18081/realms/zeroz-tour", "zeroz-app");

        OidcClient.start(config, () ->
                // appendToken puts the access token on the socket URL and installs a provider, so a
                // reconnect later carries whichever token is current by then rather than this one.
                Zeroz4jClient.connect(OidcClient.appendToken(webSocketUrl()), () ->
                        RmiSecurityContext.onAuthenticated(OidcLoginApp::build)));
    }

    private static void build() {
        Div root = box("flex flex-col gap-5 p-8 max-w-3xl mx-auto");
        root.add(text("Signed in with OpenID Connect", "text-2xl font-bold"));
        root.add(text("The browser ran an authorization-code flow with PKCE against Keycloak. The "
                + "server verified the resulting token's signature, issuer and audience before "
                + "accepting this connection.", "opacity-70"));

        root.add(text("Client-side view: " + RmiSecurityContext.getUsername()
                + " " + RmiSecurityContext.getRoles(), "font-mono text-sm opacity-60"));

        Span output = text("", "font-mono text-sm whitespace-pre-wrap");
        root.add(box("flex flex-wrap gap-2",
                call("Public call", output, service::publicGreeting),
                call("@Secured call", output, service::whoAmI),
                call("@RolesAllowed(\"planner\")", output, service::plannerOnly)));
        root.add(box("p-4 rounded bg-base-200 min-h-24", output));

        Button logout = new Button("Log out");
        logout.addClassName("btn-outline");
        logout.addClassName("w-fit");
        logout.addClickListener(event -> OidcClient.logout());
        root.add(logout);

        HTMLElement appRoot = Window.current().getDocument().getElementById("app-root");
        appRoot.appendChild(root.getElement());
    }

    /** A button that makes one RMI call and prints whatever comes back, including a refusal. */
    private static Button call(String label, Span output, Call action) {
        Button button = new Button(label);
        button.addClassName("btn-primary");
        button.addClickListener(event -> new Thread(() -> {
            try {
                output.setText(action.invoke());
            } catch (Exception ex) {
                // A server-side refusal arrives here. Showing it is the point: the annotations are
                // enforced on the server whatever the client believes about its own roles.
                output.setText("Refused by the server: " + ex.getMessage());
            }
        }).start());
        return button;
    }

    private interface Call {
        String invoke();
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
        + "return (l.protocol === 'https:' ? 'wss://' : 'ws://') + l.host + '/wasm-rmi';")
    private static native String webSocketUrl();
}
