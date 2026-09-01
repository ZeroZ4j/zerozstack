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
package com.zeroz4j.example.client;

import com.zeroz4j.api.i18n.ClientMessages;
import com.zeroz4j.client.Zeroz4jClient;
import com.zeroz4j.example.api.AppText_Catalog;
import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.component.Login;
import com.zeroz4j.api.RmiSecurityContext;
import org.teavm.jso.JSBody;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.html.HTMLElement;

public class ExampleClientApp {

    private static boolean started = false;

    public static void main(String[] args) {
        // The English this build compiled in. Optional: every word arrives over the connection
        // anyway. It is what the sign-in card below shows, because that card is drawn before there
        // is a connection to receive anything on.
        ClientMessages.useFallback(AppText_Catalog.BASE_NAME, AppText_Catalog::lookup);

        HTMLElement appRoot = Window.current().getDocument().getElementById("app-root");

        Login[] loginHolder = new Login[1];
        loginHolder[0] = new Login((username, password) -> {
            // Credentials ride the WebSocket handshake; DevAuth validates them server-side.
            String wsUrl = getWebSocketUrl()
                    + "?user=" + encode(username) + "&password=" + encode(password);
            Zeroz4jClient.connect(wsUrl, () -> {
                // On a green thread: building MainLayout makes an RMI call, and this callback runs
                // on a stack that began in native JavaScript, where TeaVM cannot suspend.
                RmiSecurityContext.onAuthenticated(() -> new Thread(() -> {
                    if (started) {
                        return;
                    }
                    started = true;
                    // replaceContents, never setInnerHTML(""): the sign-in card leaves the
                    // page properly, so anything it started stops.
                    Component.replaceContents(appRoot, new MainLayout());
                }).start());
                // The server says so outright, so there is nothing to infer and nothing to wait for.
                RmiSecurityContext.onAuthenticationFailed(() ->
                        loginHolder[0].showError("Sign-in failed - try demo/demo or admin/admin"));
            });
        });
        loginHolder[0].setHint("Demo users: demo / demo · admin / admin");
        Component.replaceContents(appRoot, loginHolder[0]);
    }

    @JSBody(params = {"value"}, script = "return encodeURIComponent(value);")
    private static native String encode(String value);

    @JSBody(script =
        "var l = window.location;" +
        "var path = l.pathname;" +
        "var idx = path.lastIndexOf('/');" +
        "if (idx !== -1) { path = path.substring(0, idx + 1); } else { path = '/'; }" +
        "return (l.protocol === 'https:' ? 'wss://' : 'ws://') + l.host + path + 'wasm-rmi';")
    private static native String getWebSocketUrl();
}
